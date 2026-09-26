package com.screenfilter.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

final class FilterSettings {
    static final String[] NAMES = {"知乎", "小红书", "虎扑"};
    static final String[] PACKAGES = {"com.zhihu.android", "com.xingin.xhs", "com.hupu.games"};
    private static final Set<String> ALLOWED = Set.of(PACKAGES);
    final SharedPreferences prefs;

    FilterSettings(Context context) { prefs = context.getSharedPreferences("filter_settings", Context.MODE_PRIVATE); }
    boolean enabled() { return prefs.getBoolean("enabled", false) && consented(); }
    boolean consented() { return prefs.getBoolean("consent", false); }
    void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    String keywords() { return prefs.getString("keywords", ""); }
    boolean ocr() { return android.os.Build.VERSION.SDK_INT >= 34 && prefs.getBoolean("ocr", true); }
    boolean expand() { return prefs.getBoolean("expand", true); }
    String endpoint() { return prefs.getString("model_url", "https://dashscope.aliyuncs.com/compatible-mode/v1"); }
    String model() { return prefs.getString("model_name", "qwen3-vl-flash"); }
    String rules() { return prefs.getString("model_rules", ""); }
    boolean ai() {
        return prefs.getBoolean("model_enabled", false)
                && (endpoint() + "\n" + model()).equals(prefs.getString("upload_consent", ""));
    }
    boolean strict() { return ai() && android.os.Build.VERSION.SDK_INT >= 34 && prefs.getBoolean("strict", false); }
    String style() { return prefs.getString("cover_style", "cloud"); }
    boolean hasRules() { return !new com.screenfilter.app.core.KeywordMatcher(keywords()).isEmpty() || (ai() && !rules().trim().isEmpty()); }
    boolean accepts(String packageName) {
        return ALLOWED.contains(packageName) && prefs.getBoolean("app_" + packageName, true);
    }
}
