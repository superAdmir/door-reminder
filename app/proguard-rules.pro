# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so Play Console crash reports can be de-obfuscated with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room (used by WorkManager, a Mobile Ads SDK dependency) instantiates generated databases by
# reflection. Without this, R8 full mode removed WorkDatabase_Impl and release builds crashed on
# launch (verified on a device).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# App components are kept through the manifest; the Mobile Ads and UMP SDKs ship their own
# consumer rules, so no additional keep rules are required.