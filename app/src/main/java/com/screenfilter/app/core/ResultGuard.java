package com.screenfilter.app.core;

/** Rejects recognition results from an old page, a paused session or a slow frame. */
public final class ResultGuard {
    public static final long MAX_AGE_MS = 1500;
    private ResultGuard() {}
    public static boolean accepts(long requestEpoch, long currentEpoch, long requestedAt, long now, boolean enabled) {
        return enabled && requestEpoch == currentEpoch && now >= requestedAt && now - requestedAt <= MAX_AGE_MS;
    }
}
