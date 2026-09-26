package com.screenfilter.app.core;

/** Independent screen identity and review state; invalidating work does not erase masks. */
public final class FrameGate {
    private volatile long version;
    private long reviewed = -1;
    public long version() { return version; }
    public void change() { version++; }
    public boolean accept(long captured, long started, long now, boolean enabled) {
        return enabled && captured == version && now >= started && now - started <= 25000;
    }
    public void reviewed(long captured) { if (captured == version) reviewed = captured; }
    public boolean pending() { return reviewed != version; }
}
