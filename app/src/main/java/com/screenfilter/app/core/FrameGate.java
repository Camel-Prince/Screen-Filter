package com.screenfilter.app.core;

/** Independent screen identity and review state; invalidating work does not erase masks. */
public final class FrameGate {
    private volatile long version;
    private long reviewed = -1;
    private boolean observed;
    private long evidence;
    public long version() { return version; }
    public void change() { version++; }
    /** Repeated notifications about unchanged content must not cancel an in-flight review. */
    public boolean observe(long fingerprint) {
        if (observed && evidence == fingerprint) return false;
        observed = true; evidence = fingerprint; change(); return true;
    }
    public void resetEvidence() { observed = false; change(); }
    public boolean accept(long captured, long started, long now, boolean enabled) {
        return enabled && captured == version && now >= started && now - started <= 25000;
    }
    public void reviewed(long captured) { if (captured == version) reviewed = captured; }
    public boolean pending() { return reviewed != version; }
}
