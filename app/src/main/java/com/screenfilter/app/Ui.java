package com.screenfilter.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(29, 47, 40);
    static final int MUTED = Color.rgb(93, 111, 102);
    static final int GREEN = Color.rgb(23, 107, 85);
    static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Activity activity) {
        LinearLayout result = new LinearLayout(activity);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    static GradientDrawable background(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(radius);
        return result;
    }
    static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(activity, 3), 1);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    static void add(LinearLayout parent, View child, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = bottomMargin;
        parent.addView(child, params);
    }
    static LinearLayout card(Activity activity, LinearLayout parent) {
        LinearLayout card = column(activity);
        card.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));
        card.setBackground(background(Color.WHITE, dp(activity, 18)));
        add(parent, card, dp(activity, 14));
        return card;
    }
    static Button button(Activity activity, String label, boolean primary, View.OnClickListener action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setMinHeight(dp(activity, 50));
        button.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        button.setBackground(background(primary ? GREEN : Color.rgb(231, 241, 233), dp(activity, 12)));
        button.setOnClickListener(action);
        return button;
    }
    static void insets(View view) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        view.requestApplyInsets();
    }
}
