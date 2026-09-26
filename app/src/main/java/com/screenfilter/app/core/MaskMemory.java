package com.screenfilter.app.core;

import java.util.ArrayList;
import java.util.List;

/** Blocked boxes follow a unique visible text anchor, never screen coordinates alone. */
public final class MaskMemory {
    public record Anchor(String text, Box bounds) {}
    public record Mask(Box bounds, String theme) {}
    private record Held(Mask mask, String anchor, Box origin) {}
    private List<Held> held = new ArrayList<>();
    private Box clip;
    public void clear() { held.clear(); clip = null; }
    public List<Mask> masks() {
        List<Mask> result = new ArrayList<>();
        for (Held item : held) {
            Box bounds = clip == null ? item.mask().bounds() : item.mask().bounds().intersect(clip);
            if (bounds.area() > 0) result.add(new Mask(bounds, item.mask().theme()));
        }
        return result;
    }
    public void add(Mask mask, List<Anchor> anchors) {
        Anchor selected = null;
        for (Anchor candidate : anchors) {
            if (candidate.text().length() < 4 || !mask.bounds().contains(candidate.bounds())) continue;
            if (anchors.stream().filter(a -> a.text().equals(candidate.text())).count() != 1) continue;
            if (selected == null || candidate.text().length() > selected.text().length()) selected = candidate;
        }
        held.removeIf(item -> mask.bounds().contains(item.mask().bounds()));
        if (held.stream().anyMatch(item -> item.mask().bounds().contains(mask.bounds()))) return;
        if (held.size() >= 32) held.remove(0);
        held.add(new Held(mask, selected == null ? "" : selected.text(), selected == null ? mask.bounds() : selected.bounds()));
    }
    public void reconcile(List<Anchor> anchors, Box clip, boolean changed) {
        this.clip = clip;
        List<Held> next = new ArrayList<>();
        for (Held item : held) {
            if (item.anchor().isEmpty()) { if (!changed) next.add(item); continue; }
            List<Anchor> found = new ArrayList<>();
            for (Anchor anchor : anchors) if (anchor.text().equals(item.anchor())) found.add(anchor);
            if (found.size() != 1) continue;
            Box origin = found.get(0).bounds();
            if (origin.width() != item.origin().width() || origin.height() != item.origin().height()) continue;
            Box previous = item.mask().bounds();
            int dx = origin.left() - item.origin().left(), dy = origin.top() - item.origin().top();
            Box moved = new Box(previous.left() + dx, previous.top() + dy, previous.right() + dx, previous.bottom() + dy);
            if (moved.intersect(clip).area() > 0) next.add(new Held(new Mask(moved, item.mask().theme()), item.anchor(), origin));
        }
        held = next;
    }
}
