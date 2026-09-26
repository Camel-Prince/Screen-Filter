package com.screenfilter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.screenfilter.app.core.Box;
import java.util.List;

/** Opaque decorative tiles, not a blur: underlying pixels never show through. */
public final class MosaicView extends View {
    private static final int[] COLORS = {0xff445850, 0xff52675e, 0xff3c4e48, 0xff60746a, 0xff495f55};
    private final Paint paint = new Paint();
    private final int tile;
    private final int[] origin = new int[2];
    private List<Box> regions = List.of();
    private final boolean screenCoordinates;

    public MosaicView(Context context) { this(context, false); }

    MosaicView(Context context, boolean screenCoordinates) {
        super(context);
        this.screenCoordinates = screenCoordinates;
        tile = Math.max(12, (int) (24 * getResources().getDisplayMetrics().density));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setBackgroundColor(Color.TRANSPARENT);
    }

    void setRegions(List<Box> regions) {
        if (this.regions.equals(regions)) return;
        this.regions = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(regions));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (screenCoordinates) getLocationOnScreen(origin);
        for (Box region : regions) {
            int left = region.left() - origin[0], top = region.top() - origin[1];
            int right = region.right() - origin[0], bottom = region.bottom() - origin[1];
            canvas.save();
            canvas.clipRect(left, top, right, bottom);
            int row = 0;
            for (int y = top; y < bottom; y += tile, row++) {
                int col = 0;
                for (int x = left; x < right; x += tile, col++) {
                    paint.setColor(COLORS[(row * 3 + col * 7) % COLORS.length]);
                    canvas.drawRect(x, y, x + tile, y + tile, paint);
                }
            }
            canvas.restore();
        }
    }
}
