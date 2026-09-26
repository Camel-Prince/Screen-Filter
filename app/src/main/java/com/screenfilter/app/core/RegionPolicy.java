package com.screenfilter.app.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RegionPolicy {
    private RegionPolicy() {}

    public static boolean plausibleCard(Box box, Box window) {
        return box.area() > 0 && box.width() >= window.width() / 5
                && box.height() >= 24 && box.height() <= window.height() * 0.72
                && box.area() <= window.area() * 0.72;
    }

    public static Box choose(Box text, List<Box> cards, Box window, boolean expand, int padding) {
        Box result = text;
        if (expand) {
            result = cards.stream().filter(b -> b.contains(text) && plausibleCard(b, window))
                    .min(Comparator.comparingLong(Box::area)).orElse(text);
        }
        return result.padded(padding, window);
    }

    public static List<Box> compact(List<Box> boxes) {
        List<Box> sorted = boxes.stream().filter(b -> b.area() > 0)
                .sorted(Comparator.comparingLong(Box::area).reversed())
                .collect(java.util.stream.Collectors.toList());
        List<Box> result = new ArrayList<>();
        for (Box box : sorted) {
            if (result.stream().noneMatch(b -> b.contains(box))) result.add(box);
            if (result.size() == 32) break;
        }
        return result;
    }

    public static Box mapImageBox(Box imageBox, int imageWidth, int imageHeight, Box window) {
        if (imageWidth <= 0 || imageHeight <= 0) return new Box(0, 0, 0, 0);
        return new Box(window.left() + (int) Math.floor(imageBox.left() * (double) window.width() / imageWidth),
                window.top() + (int) Math.floor(imageBox.top() * (double) window.height() / imageHeight),
                window.left() + (int) Math.ceil(imageBox.right() * (double) window.width() / imageWidth),
                window.top() + (int) Math.ceil(imageBox.bottom() * (double) window.height() / imageHeight))
                .intersect(window);
    }
}
