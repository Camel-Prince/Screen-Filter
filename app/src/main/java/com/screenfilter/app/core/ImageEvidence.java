package com.screenfilter.app.core;

/** Small in-memory color samples; tolerates minor raster noise, rejects substantial visual changes. */
public final class ImageEvidence {
    public static final int WIDTH = 48, HEIGHT = 72;
    private ImageEvidence() {}
    public static boolean comparable(int[] before, int[] after) {
        return comparable(before, after, new Box(0, 0, 1000, 1000));
    }
    public static boolean comparable(int[] before, int[] after, Box normalized) {
        if (before == null || after == null || before.length != WIDTH * HEIGHT || after.length != before.length) return false;
        Box area = RegionPolicy.mapImageBox(normalized, 1000, 1000, new Box(0, 0, WIDTH, HEIGHT));
        if (area.area() == 0) return false;
        int changed = 0;
        long total = 0;
        for (int y = area.top(); y < area.bottom(); y++) for (int x = area.left(); x < area.right(); x++) {
            int i = y * WIDTH + x;
            int difference = Math.abs(((before[i] >> 16) & 255) - ((after[i] >> 16) & 255))
                    + Math.abs(((before[i] >> 8) & 255) - ((after[i] >> 8) & 255))
                    + Math.abs((before[i] & 255) - (after[i] & 255));
            if (difference > 48) changed++;
            total += difference;
        }
        return changed <= area.area() / 50 && total <= area.area() * 9L;
    }
}
