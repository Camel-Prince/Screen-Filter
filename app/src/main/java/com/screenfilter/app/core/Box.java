package com.screenfilter.app.core;

/** Screen-space integer rectangle; kept independent of Android for geometry tests. */
public record Box(int left, int top, int right, int bottom) {
    public int width() { return Math.max(0, right - left); }
    public int height() { return Math.max(0, bottom - top); }
    public long area() { return (long) width() * height(); }
    public boolean contains(Box other) {
        return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom;
    }
    public Box intersect(Box other) {
        return new Box(Math.max(left, other.left), Math.max(top, other.top),
                Math.min(right, other.right), Math.min(bottom, other.bottom));
    }
    public Box padded(int padding, Box clip) {
        return new Box(left - padding, top - padding, right + padding, bottom + padding).intersect(clip);
    }
}
