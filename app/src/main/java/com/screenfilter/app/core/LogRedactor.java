package com.screenfilter.app.core;

import org.json.JSONObject;

public final class LogRedactor {
    private LogRedactor() {}
    public static String clean(String input, String key) {
        String value = input == null ? "" : input.replace("\\/", "/");
        if (key != null && !key.isEmpty()) {
            value = value.replace(key, "[密钥已隐藏]");
            String quoted = JSONObject.quote(key).replace("\\/", "/");
            value = value.replace(quoted.substring(1, quoted.length() - 1), "[密钥已隐藏]");
        }
        value = value.replaceAll("(?i)Bearer\\s+[^\\s\",}]+", "Bearer [已隐藏]")
                .replaceAll("(?i)sk[-_][a-z0-9_-]{8,}", "[密钥已隐藏]")
                .replaceAll("(?i)data:image/[^;\\s]+;base64,[a-zA-Z0-9+/=]+", "[图片数据已省略]");
        return value.length() <= 6000 ? value : value.substring(0, 6000) + "\n[回复已截断]";
    }
}
