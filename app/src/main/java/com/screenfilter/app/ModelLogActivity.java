package com.screenfilter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.screenfilter.app.core.LogBook;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ModelLogActivity extends Activity {
    private ModelLog log;
    private FilterSettings settings;
    private TextView live, storage, history;
    private long shownRevision = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        log = ModelLog.get(this); settings = new FilterSettings(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this);
        int pad = Ui.dp(this, 20);
        page.setPadding(pad, pad, pad, pad); scroll.addView(page);
        setContentView(scroll); Ui.insets(scroll);
        Ui.add(page, Ui.button(this, "返回", false, v -> finish()), pad);
        Ui.add(page, Ui.text(this, "模型运行日志", 27, Ui.INK, true), pad);
        Ui.add(page, Ui.text(this, "打开目标 App，停在相关帖子约 10–15 秒，再回来查看。每次识别有独立编号，可查看截图是否成功、请求与返回、命中坐标、耗时、采用或作废原因。", 14, Ui.MUTED, false), pad);
        LinearLayout statusCard = Ui.card(this, page);
        live = Ui.text(this, "", 13, Ui.GREEN, false); live.setTextIsSelectable(true);
        Ui.add(statusCard, live, 0);
        LinearLayout options = Ui.card(this, page);
        Ui.add(options, Ui.text(this, "默认只记录诊断信息，不保存截图、API Key、过滤规则或页面文字。最多保留 400 条事件、256 KiB，超出后自动移除最早记录。日志不会自动上传。", 13, Ui.MUTED, false), pad);
        Switch raw = new Switch(this);
        raw.setText("记录服务原始文字回复（排查时开启）");
        raw.setTextColor(Ui.INK); raw.setChecked(log.rawEnabled());
        raw.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) { log.rawEnabled(false); return; }
            if (log.rawEnabled()) return;
            new AlertDialog.Builder(this).setTitle("记录后续请求的原始回复？")
                    .setMessage("服务返回的文字和报错可能含帖子、用户名等页面内容。日志保存在本机，复制时也会包含这些内容；请检查后再分享。每条最多保留约 6000 字符，并过滤密钥和图片数据。关闭此开关不会删除已有记录，可使用「清空日志」删除。")
                    .setNegativeButton("取消", (d, w) -> raw.setChecked(false))
                    .setOnCancelListener(d -> raw.setChecked(false))
                    .setPositiveButton("开启记录", (d, w) -> log.rawEnabled(true)).show();
        });
        Ui.add(options, raw, pad);
        storage = Ui.text(this, "", 12, Ui.MUTED, false); Ui.add(options, storage, 0);
        Ui.add(page, Ui.button(this, "复制最近一次识别", true, v -> copy(true)), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "复制全部日志与运行状态", false, v -> copy(false)), Ui.dp(this, 10));
        Ui.add(page, Ui.button(this, "清空日志", false, v -> new AlertDialog.Builder(this)
                .setTitle("清空本机日志？").setMessage("会删除已记录的诊断信息和原始回复。之后的新请求仍会记录。")
                .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> log.clear()).show()), pad);
        history = Ui.text(this, "", 13, Ui.INK, false); history.setTextIsSelectable(true);
        Ui.add(page, history, 0);
    }

    private String liveText() {
        return "ScreenFilter 0.3.2 / Android API " + Build.VERSION.SDK_INT
                + "\n无障碍服务：" + (FilterRuntime.connected ? "已连接" : "未连接")
                + " · 过滤：" + (settings.enabled() ? "已开启" : "已暂停")
                + "\n云端图文：" + (settings.ai() ? "已启用" : "未启用，请在模型设置中开启并保存")
                + "\n" + FilterRuntime.status + "\n" + FilterRuntime.diagnostics;
    }
    private void refresh() {
        live.setText(liveText());
        if (shownRevision == log.revision()) return;
        shownRevision = log.revision();
        List<LogBook.Entry> entries = log.entries();
        storage.setText(getString(R.string.model_log_storage_status, log.storageStatus(), entries.size()));
        history.setText(entries.isEmpty() ? "暂无日志。连接测试或目标 App 中的图文判断会自动记录；升级前的请求无法补录。"
                : "最近事件在上方\n\n" + format(entries, true));
    }
    private String format(List<LogBook.Entry> entries, boolean reverse) {
        SimpleDateFormat clock = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LogBook.Entry entry = entries.get(reverse ? entries.size() - 1 - i : i);
            text.append(clock.format(new Date(entry.time()))).append("  #").append(entry.request())
                    .append("  ").append(entry.level()).append(" / ").append(entry.stage())
                    .append('\n').append(entry.detail()).append("\n\n");
        }
        return text.toString();
    }
    private void copy(boolean latestOnly) {
        List<LogBook.Entry> entries = log.entries();
        List<LogBook.Entry> selected = new ArrayList<>();
        String latest = "";
        if (latestOnly) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                LogBook.Entry entry = entries.get(i);
                if (entry.request().startsWith("V-")) { latest = entry.request(); break; }
            }
            if (latest.isEmpty()) {
                Toast.makeText(this, "尚无识别记录，请复制全部日志与运行状态", Toast.LENGTH_LONG).show(); return;
            }
        }
        for (LogBook.Entry entry : entries) if (!latestOnly || entry.request().equals(latest)) selected.add(entry);
        String text = liveText() + "\n\n" + format(selected, false);
        Runnable action = () -> {
            ClipData clip = ClipData.newPlainText("屏幕过滤模型日志", text);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
            getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
        };
        if (selected.stream().anyMatch(e -> "RAW".equals(e.level()))) {
            new AlertDialog.Builder(this).setTitle("复制包含原始回复的日志？")
                    .setMessage("这些记录可能包含页面文字等个人信息，请检查后再分享。")
                    .setNegativeButton("取消", null).setPositiveButton("复制", (d, w) -> action.run()).show();
        } else action.run();
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
