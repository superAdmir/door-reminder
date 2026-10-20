package com.example.door_reminderapp;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers ad consent with the User Messaging Platform and, only once ads may be requested,
 * initializes the Mobile Ads SDK and loads a single anchored banner.
 */
final class AdsController {

    private static final String TAG = "AdsController";

    /** The SDK is initialized at most once per process; screens wait for it via the queue. */
    private static final Object SDK_LOCK = new Object();
    private static final List<Runnable> PENDING_AFTER_INIT = new ArrayList<>();
    private static boolean sdkInitStarted;
    private static boolean sdkInitialized;

    interface Listener {
        /** Called when the privacy options entry point may need to be shown or hidden. */
        void onPrivacyOptionsAvailabilityChanged();
    }

    private final Activity activity;
    private final FrameLayout container;
    private final Listener listener;
    private final ConsentInformation consentInformation;
    @Nullable
    private AdView adView;
    private boolean destroyed;

    AdsController(@NonNull Activity activity, @NonNull FrameLayout container,
                  @NonNull Listener listener) {
        this.activity = activity;
        this.container = container;
        this.listener = listener;
        this.consentInformation = UserMessagingPlatform.getConsentInformation(activity);
    }

    /** Refreshes consent status, shows the consent form if required, then loads ads. */
    void start() {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (formError != null) {
                        Log.w(TAG, "Consent form error " + formError.getErrorCode()
                                + ": " + formError.getMessage());
                    }
                    if (destroyed) return;
                    listener.onPrivacyOptionsAvailabilityChanged();
                    loadAdsIfAllowed();
                }),
                requestError -> {
                    Log.w(TAG, "Consent info update failed " + requestError.getErrorCode()
                            + ": " + requestError.getMessage());
                    if (!destroyed) loadAdsIfAllowed();
                });

        // Consent obtained in a previous session allows ads immediately.
        loadAdsIfAllowed();
    }

    boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (formError != null) {
                Log.w(TAG, "Privacy options form error " + formError.getErrorCode()
                        + ": " + formError.getMessage());
            }
            if (destroyed) return;
            if (consentInformation.canRequestAds()) {
                loadAdsIfAllowed();
            } else {
                removeBanner();
            }
        });
    }

    void resume() {
        if (adView != null) adView.resume();
    }

    void pause() {
        if (adView != null) adView.pause();
    }

    void destroy() {
        destroyed = true;
        removeBanner();
    }

    private void loadAdsIfAllowed() {
        if (destroyed || adView != null || !consentInformation.canRequestAds()) return;
        String adUnitId = activity.getString(R.string.admob_banner_unit_id);
        if (adUnitId.isEmpty()) return;

        runWhenSdkInitialized(activity.getApplicationContext(), () -> loadBanner(adUnitId));
    }

    private static void runWhenSdkInitialized(@NonNull Context appContext, @NonNull Runnable action) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        synchronized (SDK_LOCK) {
            if (sdkInitialized) {
                mainHandler.post(action);
                return;
            }
            PENDING_AFTER_INIT.add(action);
            if (sdkInitStarted) return;
            sdkInitStarted = true;
        }
        // Initialization does disk and network work; Google recommends a background thread.
        new Thread(() -> MobileAds.initialize(appContext, status -> {
            List<Runnable> actions;
            synchronized (SDK_LOCK) {
                sdkInitialized = true;
                actions = new ArrayList<>(PENDING_AFTER_INIT);
                PENDING_AFTER_INIT.clear();
            }
            for (Runnable pending : actions) mainHandler.post(pending);
        }), "mobile-ads-init").start();
    }

    private void loadBanner(@NonNull String adUnitId) {
        if (destroyed || adView != null || activity.isFinishing()) return;
        container.post(() -> {
            if (destroyed || adView != null) return;
            AdView view = new AdView(activity);
            view.setAdUnitId(adUnitId);
            view.setAdSize(bannerSize());
            view.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    container.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    Log.i(TAG, "Banner not loaded: " + error.getCode() + " " + error.getMessage());
                    container.setVisibility(View.GONE);
                }
            });
            container.removeAllViews();
            container.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER_HORIZONTAL));
            adView = view;
            view.loadAd(new AdRequest.Builder().build());
        });
    }

    @NonNull
    private AdSize bannerSize() {
        float density = activity.getResources().getDisplayMetrics().density;
        int widthPx = container.getWidth();
        if (widthPx <= 0) widthPx = activity.getResources().getDisplayMetrics().widthPixels;
        int widthDp = Math.max(1, (int) (widthPx / density));
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp);
    }

    private void removeBanner() {
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        container.removeAllViews();
        container.setVisibility(View.GONE);
    }
}
