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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.screenfilter.app.core.KeywordMatcher;

public final class MainActivity extends Activity {
    private FilterSettings settings;
    private EditText keywords;
    private CheckBox[] apps;
    // Native Material theme; no AppCompat widget is needed.
    @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch ocr, expand, strict;
    private Spinner style;
    private TextView status, diagnostics;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            status.setText(!FilterRuntime.connected ? "● 尚未开启无障碍服务"
                    : settings.enabled() ? "● 已开启 · " + FilterRuntime.status : "● 已暂停");
            diagnostics.setText(getString(R.string.diagnostic_summary,
                    settings.ai() ? "已启用" : "未启用（仅填写 Key 不会自动开启）",
                    new KeywordMatcher(settings.keywords()).size(), FilterRuntime.diagnostics));
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

        Ui.add(page, Ui.text(this, "SCREEN FILTER  /  0.3.2 模型日志版", 11, Ui.GREEN, true), Ui.dp(this, 12));
        Ui.add(page, Ui.text(this, "把屏幕，留给喜欢的。", 27, Ui.INK, true), Ui.dp(this, 10));
        Ui.add(page, Ui.text(this, "本地关键词快速遮挡 · 通义千问图文判断\n留一小块安静，给自己。",
                14, Ui.MUTED, false), Ui.dp(this, 22));

        LinearLayout stateCard = Ui.card(this, page);
        status = Ui.text(this, "", 16, Ui.GREEN, true);
        Ui.add(stateCard, status, Ui.dp(this, 8));
        diagnostics = Ui.text(this, "", 13, Ui.MUTED, false);
        Ui.add(stateCard, diagnostics, Ui.dp(this, 8));
        Ui.add(stateCard, Ui.button(this, "查看模型运行日志", true,
                v -> startActivity(new Intent(this, ModelLogActivity.class))), Ui.dp(this, 8));
        Ui.add(stateCard, Ui.button(this, "复制诊断信息", false, v -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("屏幕过滤诊断",
                    "ScreenFilter 0.3.2 / API " + Build.VERSION.SDK_INT + "\n" + status.getText() + "\n" + diagnostics.getText()));
            toast("已复制运行状态，不含截图、规则或密钥");
        }), Ui.dp(this, 8));
        Ui.add(stateCard, Ui.text(this, "切换到其他 App、锁屏或弹出输入法时暂停遮挡。", 13, Ui.MUTED, false), 0);
        Ui.add(stateCard, Ui.text(this, "需要的权限：无障碍服务 → 屏幕过滤。用于读取文字位置、截图及显示遮挡；无需开启屏幕朗读。系统若未直接定位，请向下滑到「已安装的服务」。", 13, Ui.MUTED, false), Ui.dp(this, 8));
        Ui.add(stateCard, Ui.button(this, "开启 / 管理屏幕过滤服务", false, v -> explainAndOpen(false)), 0);

        LinearLayout wordsCard = Ui.card(this, page);
        Ui.add(wordsCard, Ui.text(this, "01  不想看到什么", 18, Ui.INK, true), Ui.dp(this, 8));
        Ui.add(wordsCard, Ui.text(this, "本地快捷词：每行一个，命中就遮挡。模型的自然语言要求在下面单独设置。", 13, Ui.MUTED, false), Ui.dp(this, 8));
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
        Ui.add(wordsCard, Ui.button(this, "添加测试词：秋招、考研、论文辅导", false, v -> {
            if (keywords.length() > 0) keywords.append("\n");
            keywords.append(getString(R.string.topic_test_words));
            toast("已填入快捷词，请点保存并开启；这些词仅匹配文字");
        }), 0);
        Ui.add(page, Ui.button(this, "配置通义千问 · 地址、模型、API Key", true, v -> {
            save(); startActivity(new Intent(this, ModelSettingsActivity.class));
        }), Ui.dp(this, 18));

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
            Ui.add(options, Ui.text(this, "这台设备的本地 OCR 不可用。开启云端后会尝试兼容截图进行图文判断；已遮住的内容保持覆盖，不撤罩取图。", 13, Ui.MUTED, false), Ui.dp(this, 10));
        }
        expand = toggle("找到卡片边界时遮住整卡", settings.expand(), R.id.expand_cards);
        Ui.add(options, ocr, Ui.dp(this, 12));
        Ui.add(options, expand, Ui.dp(this, 8));
        strict = toggle("先遮住当前窗口，模型判断后再放行", settings.strict(), R.id.strict_enabled);
        strict.setEnabled(Build.VERSION.SDK_INT >= 34);
        Ui.add(options, strict, Ui.dp(this, 8));
        Ui.add(options, Ui.text(this, Build.VERSION.SDK_INT >= 34
                ? "需先启用云端。页面变化后暂时遮住窗口，判断失败继续遮挡，可轻点提示暂停。会增加等待时间；仍不能保证系统首帧零曝光。"
                : "本机不支持遮挡下方窗口的独立截图，暂不提供「先遮后审」。模型判断存在等待，无法保证首帧遮挡或逐帧过滤视频。", 12, Ui.MUTED, false), Ui.dp(this, 12));
        style = new Spinner(this); style.setId(R.id.cover_style);
        style.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"云朵与温柔短句", "只显示平静短句", "经典大马赛克"}));
        style.setSelection("text".equals(settings.style()) ? 1 : "mosaic".equals(settings.style()) ? 2 : 0);
        Ui.add(options, style, Ui.dp(this, 10));
        Ui.add(options, Ui.text(this, "遮挡区域阻止触摸，避免点进被过滤内容；请从未遮挡处滑动。短句只表达鼓励与放松，不复述触发内容。", 12, Ui.MUTED, false), 0);

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
        Ui.add(page, Ui.text(this, "云端默认关闭，只有在模型设置中明确同意后才上传截图。关键词过滤在本机完成。当前为实验版本，图文判断、定位和滚动跟随仍可能漏判或误判。", 12, Ui.MUTED, false), 0);
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
                .putBoolean("ocr", ocr.isChecked()).putBoolean("expand", expand.isChecked())
                .putBoolean("strict", strict.isChecked())
                .putString("cover_style", new String[]{"cloud", "text", "mosaic"}[style.getSelectedItemPosition()]);
        for (int i = 0; i < apps.length; i++) editor.putBoolean("app_" + FilterSettings.PACKAGES[i], apps[i].isChecked());
        editor.apply();
    }

    private void start() {
        if (new KeywordMatcher(keywords.getText().toString()).isEmpty() && !(settings.ai() && !settings.rules().trim().isEmpty())) {
            keywords.setError("请填写快捷词，或先配置云端过滤要求");
            keywords.requestFocus();
            return;
        }
        boolean selected = false;
        for (CheckBox app : apps) selected |= app.isChecked();
        if (!selected) { toast("请至少选择一个 App"); return; }
        save();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keywords.getWindowToken(), 0);
        if (!settings.consented() || !FilterRuntime.connected) { explainAndOpen(true); return; }
        settings.setEnabled(true);
        toast("已开启，请返回知乎、小红书或虎扑");
    }

    private void explainAndOpen(boolean enable) {
        new AlertDialog.Builder(this)
                .setTitle("开启「屏幕过滤」无障碍服务")
                .setMessage("需要此权限读取选定 App 的文字与位置、在启用识别时截图，并显示阻止触摸的遮挡卡片。不会代替你点击或滑动。\n\n接下来优先打开「屏幕过滤」详情；若系统只打开无障碍首页，请滑到最下方「已安装的服务」→「屏幕过滤」→开启。不要开启「屏幕朗读」。\n\n本地模式不上传。云端图文模式需在模型设置中另行同意，会向指定服务发送截图和过滤要求，可能含个人信息并产生费用。截图不在本机保存。\n\n可随时回到应用或快捷设置暂停。")
                .setNegativeButton("暂不开启", null)
                .setPositiveButton("同意并前往设置", (dialog, which) -> {
                    settings.prefs.edit().putBoolean("consent", true).apply();
                    if (enable) settings.setEnabled(true);
                    AccessibilitySetup.open(this);
                }).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    @Override protected void onResume() { super.onResume(); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
