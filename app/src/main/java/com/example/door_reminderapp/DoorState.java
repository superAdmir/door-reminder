package com.example.door_reminderapp;

import androidx.annotation.Nullable;

/** Immutable snapshot of the door status the user last recorded. */
public final class DoorState {

    public static final long UNKNOWN_TIME = -1L;

    /** True if the user has recorded any status yet. */
    public final boolean hasRecord;
    public final boolean locked;
    /** When the status was last changed, or {@link #UNKNOWN_TIME}. */
    public final long changedAtMillis;
    /** Display text saved by version 1.0 that could not be converted to a timestamp. */
    @Nullable
    public final String legacyChangedAtText;

    DoorState(boolean hasRecord, boolean locked, long changedAtMillis,
              @Nullable String legacyChangedAtText) {
        this.hasRecord = hasRecord;
        this.locked = locked;
        this.changedAtMillis = changedAtMillis;
        this.legacyChangedAtText = legacyChangedAtText;
    }

    public static DoorState empty() {
        return new DoorState(false, false, UNKNOWN_TIME, null);
    }

    public boolean hasChangedAtTime() {
        return changedAtMillis != UNKNOWN_TIME;
    }
}
