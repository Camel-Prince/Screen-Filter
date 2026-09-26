package com.screenfilter.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

final class AccessibilitySetup {
    static void open(Activity activity) {
        String component = new ComponentName(activity, FilterAccessibilityService.class).flattenToString();
        // AOSP/OEM optional deep link. Some systems protect or omit this action.
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        details.putExtra("android.intent.extra.COMPONENT_NAME", new ComponentName(activity, FilterAccessibilityService.class));
        if (launch(activity, details)) return;
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", component);
        settings.putExtra(":settings:fragment_args_key", component);
        settings.putExtra(":settings:show_fragment_args", args);
        if (launch(activity, settings)) {
            Toast.makeText(activity, "若未定位：向下滑到「已安装的服务」→「屏幕过滤」→开启", Toast.LENGTH_LONG).show();
        } else Toast.makeText(activity, "请手动打开：设置 → 辅助功能 → 无障碍 → 已安装的服务 → 屏幕过滤", Toast.LENGTH_LONG).show();
    }
    private static boolean launch(Activity activity, Intent intent) {
        try { activity.startActivity(intent); return true; }
        catch (android.content.ActivityNotFoundException | SecurityException unsupported) { return false; }
    }
}
