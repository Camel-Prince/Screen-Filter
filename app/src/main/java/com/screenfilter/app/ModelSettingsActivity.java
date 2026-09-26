package com.screenfilter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.ModelEndpoint;
import com.screenfilter.app.core.RegionPolicy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModelSettingsActivity extends Activity {
    private FilterSettings settings;
    private SecretStore secrets;
    private EditText url, model, key, rules;
    @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch enabled;
    private TextView result;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final VisionClient client = new VisionClient();
    private volatile boolean closed;
    private boolean testing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        settings = new FilterSettings(this);
        secrets = new SecretStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this);
        int pad = Ui.dp(this, 22);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        Ui.insets(scroll);
        Ui.add(page, Ui.button(this, "返回过滤设置", false, v -> finish()), pad);
        Ui.add(page, Ui.text(this, "让模型读懂图文", 27, Ui.INK, true), pad);
        Ui.add(page, Ui.text(this, "通义千问 · 支持图文的兼容接口\n关键词仍在本机快速匹配，模型补充图片与语义判断。", 14, Ui.MUTED, false), pad);
        LinearLayout card = Ui.card(this, page);
        enabled = new Switch(this);
        enabled.setText("开启云端图文过滤");
        enabled.setId(R.id.model_enabled);
        enabled.setChecked(settings.ai());
        Ui.add(card, enabled, pad);
        url = field(card, "服务地址（Base URL 或完整接口）", settings.endpoint(), R.id.model_url, false);
        model = field(card, "模型名称", settings.model(), R.id.model_name, false);
        key = field(card, "API Key（在手机本地加密保存）", "", R.id.model_key, false);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);
        key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setHint(secrets.exists() ? "已保存；留空保留原密钥" : "填写阿里云百炼 API Key");
        rules = field(card, "你希望过滤什么（用自然语言描述）", settings.rules(), R.id.model_rules, true);
        rules.setHint("例如：遮住带有性暗示的营销图文；遮住引发外貌比较的推荐。");
        Ui.add(page, Ui.text(this, "默认 qwen3-vl-flash。地址、模型与密钥的地域需匹配；若百炼提供业务空间专属地址，请粘贴该地址。密钥不要发到聊天或 GitHub。", 13, Ui.MUTED, false), pad);
        Ui.add(page, Ui.text(this, "开启后会把选定 App 当前窗口的截图和过滤要求发送到你填写的服务。截图可能包含用户名、私信或其他个人信息；输入法打开时暂停，识别到的输入框会被涂灰，但不能保证识别出所有敏感内容。图片不在本机落盘，服务商的数据处理规则另行适用。每次请求可能计费，至少间隔 3 秒，只保留一个在途请求；不适用于逐帧视频过滤。", 13, Ui.MUTED, false), pad);
        Ui.add(page, Ui.button(this, "保存模型设置", true, v -> saveWithConsent(false)), 12);
        Ui.add(page, Ui.button(this, "保存并测试图文连接", false, v -> saveWithConsent(true)), 12);
        Ui.add(page, Ui.button(this, "关闭云端并删除密钥", false, v -> {
            settings.prefs.edit().putBoolean("model_enabled", false).remove("upload_consent").apply();
            try { secrets.write(""); } catch (Exception ignored) { /* Removing the preference does not use Keystore. */ }
            client.cancel(); enabled.setChecked(false); key.setText(""); key.setHint("密钥已删除"); result.setText("云端已关闭，已发出的请求无法撤回。");
        }), 12);
        result = Ui.text(this, "连接测试只发送内置彩色图，不读取其他 App。测试会核验模型是否正确定位红色方块。", 14, Ui.GREEN, false);
        Ui.add(page, result, pad);
    }

    private EditText field(LinearLayout card, String label, String value, int id, boolean multi) {
        Ui.add(card, Ui.text(this, label, 14, Ui.INK, true), 4);
        EditText field = new EditText(this);
        field.setId(id); field.setText(value); field.setTextSize(15); field.setTextColor(Ui.INK);
        field.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(multi ? 2000 : 1000)});
        if (multi) field.setMinLines(3); else field.setSingleLine(true);
        Ui.add(card, field, Ui.dp(this, 16));
        return field;
    }

    private void saveWithConsent(boolean test) {
        if (testing) { result.setText("正在测试，请稍候。"); return; }
        final String endpoint;
        try { endpoint = ModelEndpoint.normalize(url.getText().toString()); }
        catch (IllegalArgumentException bad) { url.setError(bad.getMessage()); return; }
        String name = model.getText().toString().trim();
        if (name.isEmpty()) { model.setError("请填写支持图文的模型名称"); return; }
        if ((enabled.isChecked() || test) && key.getText().toString().trim().isEmpty() && !secrets.exists()) {
            key.setError("请填写 API Key"); return;
        }
        if (enabled.isChecked() && rules.getText().toString().trim().isEmpty()) { rules.setError("请描述要过滤的内容"); return; }
        Runnable save = () -> {
            try {
                String entered = key.getText().toString().trim();
                if (!entered.isEmpty()) secrets.write(entered);
                settings.prefs.edit().putString("model_url", endpoint).putString("model_name", name)
                        .putString("model_rules", rules.getText().toString().trim())
                        .putBoolean("model_enabled", enabled.isChecked())
                        .putString("upload_consent", endpoint + "\n" + name).apply();
                key.setText(""); key.setHint(secrets.exists() ? "已保存；留空保留原密钥" : "填写 API Key");
                result.setText("设置已保存。返回首页，点击「保存并开启过滤」。");
                if (test) testConnection();
            } catch (Exception error) { result.setText("无法读取或加密保存密钥，请重新填写。配置未启用。"); }
        };
        if (!enabled.isChecked() && !test) { save.run(); return; }
        new AlertDialog.Builder(this).setTitle(test ? "允许发送内置图测试？" : "允许向此服务发送图文？")
                .setMessage("接收地址：\n" + endpoint + "\n模型：" + name
                        + (enabled.isChecked() ? "\n\n开启过滤后，会自动上传所选 App 当前窗口截图和过滤要求。识别到的输入框会涂灰，仍可能含个人信息。" : "\n\n本次仅上传内置彩色测试图。")
                        + "\n\nAPI Key 将用于向此地址鉴权，调用可能计费。你可随时关闭云端过滤。")
                .setNegativeButton("取消", null).setPositiveButton("同意并继续", (d, w) -> save.run()).show();
    }

    private void testConnection() throws Exception {
        VisionClient.Config config = new VisionClient.Config(settings.endpoint(), settings.model(), secrets.read(), "只遮挡红色方块，不遮挡蓝色方块或白色背景。");
        testing = true; result.setText("正在用内置图验证图文输入和坐标返回…");
        worker.execute(() -> {
            Bitmap bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
            boolean left = new java.security.SecureRandom().nextBoolean();
            Box red = left ? new Box(30, 60, 210, 250) : new Box(270, 60, 450, 250);
            Paint paint = new Paint(); paint.setColor(Color.RED);
            canvas.drawRect(red.left(), red.top(), red.right(), red.bottom(), paint);
            paint.setColor(Color.BLUE); canvas.drawRect(left ? 270 : 30, 60, left ? 450 : 210, 250, paint);
            String message;
            long began = android.os.SystemClock.uptimeMillis();
            try {
                var regions = client.classify(bitmap, config, () -> !closed);
                boolean valid = regions.size() == 1 && regions.stream().anyMatch(r -> {
                    Box mapped = RegionPolicy.mapImageBox(r.normalized(), 1000, 1000, new Box(0, 0, 480, 320));
                    long overlap = mapped.intersect(red).area();
                    return overlap > 0.6 * red.area() && overlap > 0.6 * mapped.area();
                });
                message = valid ? "图文连接通过：正确定位红色方块。耗时 " + (android.os.SystemClock.uptimeMillis() - began) / 1000.0 + " 秒。\n这不代表实际内容过滤准确率，接下来请用目标 App 实测。"
                        : "接口可访问，但模型未正确定位测试方块。请检查是否为支持图文的模型，再测试。";
            } catch (Exception error) { message = VisionClient.error(error); }
            finally { bitmap.recycle(); }
            String finalMessage = message;
            runOnUiThread(() -> { testing = false; if (!closed) result.setText(finalMessage); });
        });
    }
    @Override protected void onDestroy() { closed = true; client.cancel(); worker.shutdownNow(); super.onDestroy(); }
}
