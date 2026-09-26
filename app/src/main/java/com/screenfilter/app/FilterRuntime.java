package com.screenfilter.app;

final class FilterRuntime {
    static volatile boolean connected;
    static volatile String status = "等待开启无障碍服务";
    static volatile String diagnostics = "还没有读取目标 App。";
    private FilterRuntime() {}
}
