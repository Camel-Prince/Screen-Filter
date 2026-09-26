package com.screenfilter.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.KeywordMatcher;
import com.screenfilter.app.core.RegionPolicy;
import java.util.ArrayList;
import java.util.List;

final class NodeReader {
    record TextRegion(String text, Box bounds) {}
    record Snapshot(List<TextRegion> texts, List<Box> cards, long fingerprint) {}
    private final List<TextRegion> texts = new ArrayList<>();
    private final List<Box> cards = new ArrayList<>();
    private int visited;
    private long fingerprint = 1;

    Snapshot read(AccessibilityNodeInfo root, Box window) {
        walk(root, window, false, 0);
        return new Snapshot(java.util.Collections.unmodifiableList(new ArrayList<>(texts)),
                java.util.Collections.unmodifiableList(new ArrayList<>(cards)), fingerprint);
    }

    private void walk(AccessibilityNodeInfo node, Box window, boolean parentList, int depth) {
        if (node == null || ++visited > 1200 || depth > 36 || !node.isVisibleToUser()) return;
        // Do not inspect an input field, password field, or its descendants.
        if (node.isPassword() || node.isEditable()) return;
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        Box bounds = new Box(rect.left, rect.top, rect.right, rect.bottom).intersect(window);
        if (bounds.area() == 0) return;
        if (parentList && RegionPolicy.plausibleCard(bounds, window)) cards.add(bounds);
        CharSequence raw = node.getText();
        CharSequence description = node.getContentDescription();
        String text = (raw == null ? "" : raw.toString()) + "\n" + (description == null ? "" : description);
        if (!text.trim().isEmpty()) {
            texts.add(new TextRegion(text, bounds));
            fingerprint = 31 * fingerprint + text.hashCode();
            fingerprint = 31 * fingerprint + bounds.hashCode();
        }
        String kind = String.valueOf(node.getClassName());
        boolean list = node.isScrollable() || node.getCollectionInfo() != null
                || kind.contains("RecyclerView") || kind.contains("ListView") || kind.contains("GridView");
        int count = Math.min(node.getChildCount(), 200);
        for (int i = 0; i < count; i++) walk(node.getChild(i), window, list, depth + 1);
    }

    static List<Box> matches(Snapshot snapshot, KeywordMatcher matcher, Box window, boolean expand, int padding) {
        List<Box> result = new ArrayList<>();
        for (TextRegion region : snapshot.texts()) {
            if (matcher.matches(region.text())) {
                result.add(RegionPolicy.choose(region.bounds(), snapshot.cards(), window, expand, padding));
            }
        }
        return RegionPolicy.compact(result);
    }
}
