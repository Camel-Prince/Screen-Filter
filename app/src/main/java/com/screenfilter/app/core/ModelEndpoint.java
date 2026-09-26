package com.screenfilter.app.core;

import java.net.URI;

/** Only an explicitly configured HTTPS origin can receive images or credentials. */
public final class ModelEndpoint {
    private ModelEndpoint() {}
    public static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return value.endsWith("/chat/completions") ? value : value + "/chat/completions";
        } catch (Exception bad) {
            throw new IllegalArgumentException("请填写完整 HTTPS 地址，不要包含密钥、参数或占位符");
        }
    }
}
