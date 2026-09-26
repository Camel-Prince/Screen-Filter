package com.screenfilter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.screenfilter.app.core.KeywordMatcher;

public final class MainActivity extends Activity {
    private FilterSettings settings;
    private EditText keywords;
    private CheckBox[] apps;
    // Native Material theme; no AppCompat widget is needed.
    @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch ocr, expand;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            status.setText(!FilterRuntime.connected ? "● 尚未开启无障碍服务"
                    : settings.enabled() ? "● 已开启 · 返回目标 App 即可使用" : "● 已暂停");
            handler.postDelayed(this, 800);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new FilterSettings(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = Ui.column(this);
        int spacing = Ui.dp(this, 22);
        page.setPadding(spacing, spacing, spacing, spacing);
        scroll.addView(page);
        setContentView(scroll);
        Ui.insets(scroll);

        Ui.add(page, Ui.text(this, "SCREEN FILTER  /  早期体验版", 11, Ui.GREEN, true), Ui.dp(this, 12));
        Ui.add(page, Ui.text(this, "把屏幕，留给喜欢的。", 27, Ui.INK, true), Ui.dp(this, 10));
        Ui.add(page, Ui.text(this, "自定义关键词，遮住不想看的内容。\n"
                + (Build.VERSION.SDK_INT >= 34 ? "本地中文识别 + 界面文字匹配" : "兼容模式 · 界面文字匹配"),
                14, Ui.MUTED, false), Ui.dp(this, 22));

        LinearLayout stateCard = Ui.card(this, page);
        status = Ui.text(this, "", 16, Ui.GREEN, true);
        Ui.add(stateCard, status, Ui.dp(this, 8));
        Ui.add(stateCard, Ui.text(this, "切换到其他 App、锁屏或弹出输入法时暂停遮挡。", 13, Ui.MUTED, false), 0);

        LinearLayout wordsCard = Ui.card(this, page);
        Ui.add(wordsCard, Ui.text(this, "01  不想看到什么", 18, Ui.INK, true), Ui.dp(this, 8));
        Ui.add(wordsCard, Ui.text(this, "每行一个词，命中任意一个就遮挡。也支持逗号分隔，最多 100 个。", 13, Ui.MUTED, false), Ui.dp(this, 8));
        keywords = new EditText(this);
        keywords.setId(R.id.keywords);
        keywords.setText(settings.keywords());
        keywords.setHint("例如：\n剧透\n某个话题\n某个品牌");
        keywords.setTextSize(16);
        keywords.setTextColor(Ui.INK);
        keywords.setGravity(Gravity.TOP | Gravity.START);
        keywords.setMinLines(4);
        keywords.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        keywords.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});
        Ui.add(wordsCard, keywords, 0);

        LinearLayout appsCard = Ui.card(this, page);
        Ui.add(appsCard, Ui.text(this, "02  在这些 App 中过滤", 18, Ui.INK, true), Ui.dp(this, 8));
        apps = new CheckBox[FilterSettings.PACKAGES.length];
        int[] appIds = {R.id.target_zhihu, R.id.target_xiaohongshu, R.id.target_hupu};
        for (int i = 0; i < apps.length; i++) {
            apps[i] = new CheckBox(this);
            apps[i].setId(appIds[i]);
            apps[i].setText(FilterSettings.NAMES[i]);
            apps[i].setTextSize(16);
            apps[i].setTextColor(Ui.INK);
            apps[i].setChecked(settings.accepts(FilterSettings.PACKAGES[i]));
            appsCard.addView(apps[i]);
        }
        Ui.add(appsCard, Ui.text(this, "适用于上述 App 的标准包名；分身、极速版及不同版本页面需实机验证。", 12, Ui.MUTED, false), 0);

        LinearLayout options = Ui.card(this, page);
        Ui.add(options, Ui.text(this, "03  遮挡方式", 18, Ui.INK, true), Ui.dp(this, 12));
        ocr = toggle("识别图片中的文字", settings.ocr(), R.id.ocr_enabled);
        ocr.setEnabled(Build.VERSION.SDK_INT >= 34);
        if (Build.VERSION.SDK_INT < 34) {
            Ui.add(options, Ui.text(this, "这台设备使用界面文字模式，不截屏。图片内的文字和未提供界面文字的内容暂不识别。", 13, Ui.MUTED, false), Ui.dp(this, 10));
        }
        expand = toggle("找到卡片边界时遮住整卡", settings.expand(), R.id.expand_cards);
        Ui.add(options, ocr, Ui.dp(this, 12));
        Ui.add(options, expand, Ui.dp(this, 8));
        Ui.add(options, Ui.text(this, "识别不出卡片边界时，只遮挡命中文字。方块完全不透明，触摸由系统传给下方 App。", 12, Ui.MUTED, false), 0);

        Ui.add(page, Ui.button(this, "保存并开启过滤", true, v -> start()), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "立即暂停", false, v -> {
            settings.setEnabled(false);
            toast("已暂停过滤");
        }), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "查看遮挡演示", false, v -> {
            save();
            startActivity(new Intent(this, PreviewActivity.class));
        }), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "添加快捷暂停按钮", false, v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, FilterTileService.class), "屏幕过滤",
                    Icon.createWithResource(this, R.drawable.ic_filter), getMainExecutor(),
                    result -> toast("可在下拉快捷设置中切换过滤状态"));
            } else {
                new AlertDialog.Builder(this).setTitle("手动添加快捷开关")
                        .setMessage("下拉系统控制中心，进入快捷开关的编辑界面，查找并添加「屏幕过滤」。如果系统没有提供该开关，请回到本应用点击「立即暂停」。")
                        .setPositiveButton("知道了", null).show();
            }
        }), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "管理无障碍权限", false, v -> explainAndOpen(false)), Ui.dp(this, 20));
        Ui.add(page, Ui.text(this, "当前只按文字匹配，不会理解话题含义，也不会识别无文字的擦边图片。识别有延迟，滚动时可能短暂露出；此版本不保证零曝光。截图只用于本次识别，不落盘、不上传。", 12, Ui.MUTED, false), 0);
    }

    @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch toggle(String label, boolean checked, int id) {
        Switch view = new Switch(this);
        view.setId(id);
        view.setText(label);
        view.setTextColor(Ui.INK);
        view.setTextSize(14);
        view.setChecked(checked);
        view.setMinHeight(Ui.dp(this, 48));
        return view;
    }

    private void save() {
        android.content.SharedPreferences.Editor editor = settings.prefs.edit()
                .putString("keywords", keywords.getText().toString())
                .putBoolean("ocr", ocr.isChecked()).putBoolean("expand", expand.isChecked());
        for (int i = 0; i < apps.length; i++) editor.putBoolean("app_" + FilterSettings.PACKAGES[i], apps[i].isChecked());
        editor.apply();
    }

    private void start() {
        if (new KeywordMatcher(keywords.getText().toString()).isEmpty()) {
            keywords.setError("请至少输入一个关键词");
            keywords.requestFocus();
            return;
        }
        boolean selected = false;
        for (CheckBox app : apps) selected |= app.isChecked();
        if (!selected) { toast("请至少选择一个 App"); return; }
        save();
        if (!settings.consented() || !FilterRuntime.connected) { explainAndOpen(true); return; }
        settings.setEnabled(true);
        toast("已开启，请返回知乎、小红书或虎扑");
    }

    private void explainAndOpen(boolean enable) {
        new AlertDialog.Builder(this)
                .setTitle("允许读取并遮挡选定 App 的内容？")
                .setMessage("开启无障碍服务后，屏幕过滤会读取你选择的 App 的界面文字。"
                        + (Build.VERSION.SDK_INT >= 34 ? "开启图片文字识别时，还会截取当前窗口进行本地中文识别。" : "当前设备使用兼容模式，不截屏，不识别图片中的文字。")
                        + "命中关键词的区域会被不透明方块盖住。\n\n截图及读取的文字不保存、不上传；不会代替你点击或滑动。过滤设置保存在本机。\n\n你可以随时在本应用、快捷设置中暂停，或在系统设置中关闭服务。")
                .setNegativeButton("暂不开启", null)
                .setPositiveButton("同意并前往设置", (dialog, which) -> {
                    settings.prefs.edit().putBoolean("consent", true).apply();
                    if (enable) settings.setEnabled(true);
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                }).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    @Override protected void onResume() { super.onResume(); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
