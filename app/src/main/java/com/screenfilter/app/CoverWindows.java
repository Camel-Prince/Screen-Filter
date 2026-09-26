package com.screenfilter.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.MaskMemory;
import java.util.ArrayList;
import java.util.List;

final class CoverWindows {
    private record Entry(CoverView view, WindowManager.LayoutParams params) {}
    private final Context context;
    private final WindowManager manager;
    private final List<Entry> entries = new ArrayList<>();
    private List<Box> visible = List.of();
    CoverWindows(Context context) { this.context = context; manager = context.getSystemService(WindowManager.class); }
    List<Box> visible() { return new ArrayList<>(visible); }
    void render(List<MaskMemory.Mask> masks, String style, String pending, Runnable pause) {
        List<Box> next = new ArrayList<>();
        for (int i = 0; i < Math.min(32, masks.size()); i++) {
            MaskMemory.Mask mask = masks.get(i); Box b = mask.bounds();
            if (b.area() == 0) continue;
            int index = next.size(); next.add(b);
            if (index == entries.size()) {
                CoverView view = new CoverView(context);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(b.width(), b.height(),
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.OPAQUE);
                params.gravity = Gravity.TOP | Gravity.START; params.x = b.left(); params.y = b.top();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                params.setFitInsetsTypes(0); params.setTitle("ScreenFilter cover");
                view.configure(style, mask.theme(), pending, pause);
                manager.addView(view, params); entries.add(new Entry(view, params));
            } else {
                Entry entry = entries.get(index);
                entry.view().configure(style, mask.theme(), pending, pause);
                var p = entry.params();
                if (p.x != b.left() || p.y != b.top() || p.width != b.width() || p.height != b.height()) {
                    p.x = b.left(); p.y = b.top(); p.width = b.width(); p.height = b.height();
                    manager.updateViewLayout(entry.view(), p);
                }
            }
        }
        // New or moved covers are installed before obsolete ones are removed.
        while (entries.size() > next.size()) removeLast();
        visible = next;
    }
    private void removeLast() {
        Entry entry = entries.remove(entries.size() - 1);
        try { manager.removeViewImmediate(entry.view()); } catch (IllegalArgumentException gone) { /* Already detached. */ }
    }
    void clear() { while (!entries.isEmpty()) removeLast(); visible = List.of(); }
}
