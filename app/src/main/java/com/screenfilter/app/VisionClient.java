package com.screenfilter.app;

import android.graphics.Bitmap;
import android.util.Base64;
import com.screenfilter.app.core.ModelEndpoint;
import com.screenfilter.app.core.LogRedactor;
import com.screenfilter.app.core.VisionProtocol;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

final class VisionClient {
    record Config(String url, String model, String key, String rules) {}
    private static final class Failure extends Exception {
        Failure(String message) { super(message); }
    }
    private volatile HttpURLConnection connection;
    void cancel() { HttpURLConnection active = connection; if (active != null) active.disconnect(); }
    List<VisionProtocol.Region> classify(Bitmap image, Config config, BooleanSupplier active) throws Exception {
        return classify(image, config, active, () -> {});
    }
    List<VisionProtocol.Region> classify(Bitmap image, Config config, BooleanSupplier active, Runnable sent) throws Exception {
        return classify(image, config, active, sent, null);
    }
    List<VisionProtocol.Region> classify(Bitmap image, Config config, BooleanSupplier active, Runnable sent, ModelLog.Trace trace) throws Exception {
        if (config.key().trim().isEmpty()) throw new Failure("请先填写 API Key");
        URL endpoint = new URL(ModelEndpoint.normalize(config.url()));
        if (trace != null) trace.info("准备请求", LogRedactor.clean("模型：" + config.model() + " · 服务主机：" + endpoint.getHost()
                + "\n图片尺寸：" + image.getWidth() + " × " + image.getHeight() + " · 过滤要求长度：" + config.rules().length() + " 字符", config.key()));
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        if (!image.compress(Bitmap.CompressFormat.JPEG, 78, jpeg)) throw new Failure("图片转换失败");
        byte[] request = VisionProtocol.request(config.model(), config.rules(),
                Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP)).toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection http = (HttpURLConnection) endpoint.openConnection();
        connection = http;
        try {
            http.setInstanceFollowRedirects(false); // Never forward a key to a redirected host.
            http.setConnectTimeout(6000);
            http.setReadTimeout(15000);
            http.setRequestMethod("POST");
            http.setRequestProperty("Authorization", "Bearer " + config.key());
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            http.setDoOutput(true);
            http.setFixedLengthStreamingMode(request.length);
            if (!active.getAsBoolean()) throw new InterruptedException();
            try (java.io.OutputStream out = http.getOutputStream()) {
                if (!active.getAsBoolean()) throw new InterruptedException();
                out.write(request);
            }
            sent.run();
            if (trace != null) trace.info("请求已发送", "图文请求已写出，等待模型返回。");
            int code = http.getResponseCode();
            if (trace != null) trace.info("HTTP 返回", "状态码 " + code);
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            long deadline = System.nanoTime() + 20_000_000_000L;
            try (InputStream in = code == 200 ? http.getInputStream() : http.getErrorStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while (in != null && (n = in.read(buffer)) != -1) {
                    if (!active.getAsBoolean()) throw new InterruptedException();
                    if (response.size() + n > 65536 || System.nanoTime() > deadline) throw new Failure("模型响应过长");
                    response.write(buffer, 0, n);
                }
            }
            String body = response.toString(StandardCharsets.UTF_8.name());
            if (trace != null) trace.raw(body, config.key());
            if (code != 200) throw new Failure(switch (code) {
                case 401, 403 -> "鉴权失败：检查 API Key、地域和模型权限";
                case 404 -> "地址或模型不存在，请检查配置";
                case 429 -> "服务限流或额度不足，稍后重试";
                default -> "模型服务返回 HTTP " + code;
            });
            if (!active.getAsBoolean()) throw new InterruptedException();
            List<VisionProtocol.Region> regions;
            try { regions = VisionProtocol.parse(body); }
            catch (Exception invalid) {
                if (trace != null) trace.problem("解析失败", "返回内容不满足区域 JSON 要求。可开启原始回复记录后重试。");
                throw invalid;
            }
            if (trace != null) {
                StringBuilder summary = new StringBuilder("模型命中 " + regions.size() + " 处；坐标相对于上传图片，范围 0–1000。");
                for (var region : regions) {
                    var b = region.normalized();
                    summary.append("\n[").append(b.left()).append(',').append(b.top()).append(',')
                            .append(b.right()).append(',').append(b.bottom()).append("] · ").append(region.theme());
                }
                trace.info("解析完成", summary.toString());
            }
            return regions;
        } finally { http.disconnect(); if (connection == http) connection = null; }
    }
    static String error(Exception error) {
        if (error instanceof InterruptedException) return "请求已取消";
        if (error instanceof java.net.SocketTimeoutException) return "模型连接超时，请检查网络后重试";
        if (error instanceof java.io.IOException) return "无法连接模型，请检查网络与 HTTPS 地址";
        // Platform exceptions may echo headers or response bodies. Only our fixed messages are exposed.
        if (error instanceof Failure) return error.getMessage();
        return "模型返回格式不符合要求，请检查模型是否支持图文输入";
    }
}
