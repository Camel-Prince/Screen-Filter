package com.screenfilter.app.core;

/** No model-generated prose or filtered topic is displayed back to the user. */
public final class Comfort {
    private Comfort() {}
    public static String theme(String value) {
        return "encourage".equals(value) || "rest".equals(value) ? value : "calm";
    }
    public static String line(String theme) {
        return switch (theme(theme)) {
            case "encourage" -> "按自己的步调，慢慢来。";
            case "rest" -> "让眼睛歇一会，让心也松一松。";
            default -> "此刻，把目光留给喜欢的事。";
        };
    }
}
