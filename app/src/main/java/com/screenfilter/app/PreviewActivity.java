package com.screenfilter.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.KeywordMatcher;
import java.util.List;

/** In-app deterministic preview; not evidence of third-party app compatibility. */
public final class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this);
        int pad = Ui.dp(this, 22);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        Ui.insets(scroll);
        Ui.add(page, Ui.button(this, "返回设置", false, v -> finish()), Ui.dp(this, 20));
        Ui.add(page, Ui.text(this, "遮挡是什么样的？", 26, Ui.INK, true), Ui.dp(this, 10));
        Ui.add(page, Ui.text(this, "以下为应用内模拟卡片，示例词为「剧透」。用于预览方块和滑动效果，不代表三个 App 已完成适配测试。", 14, Ui.MUTED, false), Ui.dp(this, 18));
        KeywordMatcher demo = new KeywordMatcher("剧透");
        String[] titles = {"知乎 · 一个适合周末慢慢读的问题", "小红书 · 剧透：这部电影的结局", "虎扑 · 今天的比赛有什么精彩瞬间？", "知乎 · 剧透讨论：最后一集发生了什么", "小红书 · 公园里的绿色与阳光"};
        for (String title : titles) {
            FrameLayout frame = new FrameLayout(this);
            LinearLayout content = Ui.column(this);
            content.setPadding(pad, pad, pad, pad);
            content.setBackground(Ui.background(Color.WHITE, Ui.dp(this, 16)));
            Ui.add(content, Ui.text(this, title, 20, Ui.INK, true), Ui.dp(this, 18));
            Ui.add(content, Ui.text(this, "这是用于演示的推荐卡片。你可以继续上下滑动，已命中的卡片保持遮挡。", 15, Ui.MUTED, false), Ui.dp(this, 24));
            Ui.add(content, Ui.text(this, "示例内容  ·  本地匹配", 12, Ui.MUTED, false), 0);
            frame.addView(content, new FrameLayout.LayoutParams(-1, -2));
            if (demo.matches(title)) {
                MosaicView mask = new MosaicView(this, false);
                frame.addView(mask, new FrameLayout.LayoutParams(-1, -1));
                frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                        mask.setRegions(List.of(new Box(0, 0, r - l, b - t))));
            }
            Ui.add(page, frame, Ui.dp(this, 16));
        }
    }
}
