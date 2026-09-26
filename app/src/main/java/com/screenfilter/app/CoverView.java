package com.screenfilter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.screenfilter.app.core.Comfort;

/** One opaque, touch-consuming window per blocked rectangle. */
final class CoverView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String style = "cloud", theme = "calm", message = "";
    private final float density;
    CoverView(Context context) {
        super(context); density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setOnTouchListener((v, e) -> { if (e.getAction() == android.view.MotionEvent.ACTION_UP) performClick(); return true; });
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    void configure(String style, String theme, String message, Runnable pause) {
        if (!this.style.equals(style) || !this.theme.equals(theme) || !this.message.equals(message)) {
            this.style = style; this.theme = theme; this.message = message; invalidate();
        }
        setOnClickListener(pause == null ? null : v -> pause.run());
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if ("mosaic".equals(style) && message.isEmpty()) {
            int tile = Math.max(12, Math.round(24 * density));
            int[] colors = {0xff445850, 0xff52675e, 0xff3c4e48, 0xff60746a};
            for (int y = 0, row = 0; y < h; y += tile, row++) for (int x = 0, col = 0; x < w; x += tile, col++) {
                paint.setColor(colors[(row * 3 + col) % colors.length]); canvas.drawRect(x, y, x + tile, y + tile, paint);
            }
            return;
        }
        canvas.drawColor("encourage".equals(theme) ? 0xfff7ecd8 : "rest".equals(theme) ? 0xffe8ecf6 : 0xffe3efe8);
        if (w < 90 * density || h < 34 * density) return;
        float center = w / 2f, baseline = h / 2f;
        if ("cloud".equals(style) && h > 145 * density) {
            float cy = h / 2f - 30 * density, r = 19 * density;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(center - r, cy, r, paint); canvas.drawCircle(center, cy - r * .5f, r * 1.25f, paint);
            canvas.drawCircle(center + r, cy, r, paint);
            canvas.drawRoundRect(center - 2 * r, cy, center + 2 * r, cy + r, r, r, paint);
            paint.setColor(0xff58786a);
            canvas.drawCircle(center - 6 * density, cy + 4 * density, 1.7f * density, paint);
            canvas.drawCircle(center + 6 * density, cy + 4 * density, 1.7f * density, paint);
            baseline = cy + 55 * density;
        }
        paint.setColor(0xff34594b); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(15 * density);
        String line = message.isEmpty() ? Comfort.line(theme) : message;
        float max = w - 20 * density;
        while (paint.measureText(line) > max && paint.getTextSize() > 10 * density) paint.setTextSize(paint.getTextSize() - density);
        int count = paint.breakText(line, true, max, null);
        canvas.drawText(line.substring(0, count), center, baseline, paint);
        if (h > 110 * density) {
            paint.setTextSize(11 * density); paint.setColor(0xff678174);
            canvas.drawText(message.isEmpty() ? "已为你遮住 · 从旁边继续滑动" : "轻点这里暂停过滤", center, baseline + 24 * density, paint);
        }
    }
}
