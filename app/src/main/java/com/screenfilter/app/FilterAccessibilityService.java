package com.screenfilter.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.KeywordMatcher;
import com.screenfilter.app.core.RegionPolicy;
import com.screenfilter.app.core.ResultGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FilterAccessibilityService extends AccessibilityService {
    private static final long OCR_INTERVAL_MS = 900;
    private static final long OCR_TTL_MS = 1600;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private FilterSettings settings;
    private KeywordMatcher matcher;
    private WindowManager windowsManager;
    private MosaicView overlay;
    private boolean attached, destroyed, inFlight;
    private TextRecognizer recognizer;
    private long epoch, fingerprint, nextOcrAt, ocrExpiresAt, requestStartedAt;
    private Target current;
    private List<Box> nodeMasks = List.of(), ocrMasks = List.of();
    private String captureStatus = "";

    private record Target(String packageName, int windowId, Box bounds) {}
    private record WindowTarget(Target target, AccessibilityNodeInfo root) {}

    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        matcher = new KeywordMatcher(settings.keywords());
        invalidateFrame();
        nextOcrAt = 0;
        schedule(0);
    };
    private final Runnable tick = () -> {
        if (destroyed) return;
        try { scan(); }
        catch (RuntimeException error) {
            clearTarget();
            FilterRuntime.status = "窗口暂不可读，稍后自动重试";
        } finally { if (!destroyed) schedule(current == null ? 450 : 120); }
    };

    @Override protected void onServiceConnected() {
        settings = new FilterSettings(this);
        matcher = new KeywordMatcher(settings.keywords());
        settings.prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        windowsManager = getSystemService(WindowManager.class);
        if (Build.VERSION.SDK_INT >= 34) {
            recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        }
        overlay = new MosaicView(this, true);
        FilterRuntime.connected = true;
        schedule(0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (settings == null || destroyed) return;
        String source = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (getPackageName().equals(source)) return;
        if (current != null && source.equals(current.packageName())) {
            // A late OCR response must never be painted onto a recycled/scrolling card.
            invalidateFrame();
        }
        schedule(30);
    }

    private void schedule(long delay) {
        main.removeCallbacks(tick);
        if (!destroyed) main.postDelayed(tick, delay);
    }

    private void scan() {
        if (!settings.enabled() || matcher.isEmpty()) {
            clearTarget();
            FilterRuntime.status = matcher.isEmpty() ? "请先添加屏蔽关键词" : "已暂停";
            return;
        }
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            clearTarget();
            FilterRuntime.status = "锁屏时暂停";
            return;
        }
        WindowTarget selected = findTarget();
        if (selected == null) {
            clearTarget();
            FilterRuntime.status = "就绪 · 等待选定 App（输入文字时暂停）";
            return;
        }
        Target target = selected.target();
        if (!target.equals(current)) {
            invalidateFrame();
            current = target;
            fingerprint = 0;
            nextOcrAt = 0;
            captureStatus = "";
        }
        NodeReader.Snapshot snapshot = new NodeReader().read(selected.root(), target.bounds());
        if (snapshot.fingerprint() != fingerprint) {
            invalidateFrame();
            fingerprint = snapshot.fingerprint();
        }
        int padding = Math.round(4 * getResources().getDisplayMetrics().density);
        nodeMasks = NodeReader.matches(snapshot, matcher, target.bounds(), settings.expand(), padding);
        long now = SystemClock.uptimeMillis();
        if (now >= ocrExpiresAt) ocrMasks = List.of();
        render();
        FilterRuntime.status = "过滤中 · " + (nodeMasks.size() + ocrMasks.size()) + " 个命中区域"
                + (captureStatus.isEmpty() ? "" : " · " + captureStatus);
        if (Build.VERSION.SDK_INT >= 34 && settings.ocr() && !inFlight && now >= nextOcrAt) {
            capture(target, snapshot, padding);
        }
    }

    private WindowTarget findTarget() {
        List<AccessibilityWindowInfo> windows = getWindows();
        // Avoid reading screenshots of typed messages or covering the keyboard.
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return null;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return null;
        String packageName = root.getPackageName().toString();
        if (!settings.accepts(packageName)) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getId() == root.getWindowId()
                    && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && (window.isActive() || window.isFocused())) {
                // A system dialog/notification shade in front suspends masking.
                for (AccessibilityWindowInfo other : windows) {
                    if (other.getType() == AccessibilityWindowInfo.TYPE_SYSTEM
                            && other.isFocused() && other.getLayer() > window.getLayer()) return null;
                }
                Rect rect = new Rect();
                window.getBoundsInScreen(rect);
                Box box = new Box(rect.left, rect.top, rect.right, rect.bottom);
                if (box.area() == 0) return null;
                return new WindowTarget(new Target(packageName, window.getId(), box), root);
            }
        }
        return null;
    }

    private void capture(Target target, NodeReader.Snapshot snapshot, int padding) {
        if (Build.VERSION.SDK_INT < 34) return;
        final long capturedEpoch = epoch;
        final KeywordMatcher capturedMatcher = matcher;
        final boolean expand = settings.expand();
        inFlight = true;
        requestStartedAt = SystemClock.uptimeMillis();
        nextOcrAt = SystemClock.uptimeMillis() + OCR_INTERVAL_MS;
        try {
            takeScreenshotOfWindow(target.windowId(), getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult screenshot) {
                    if (destroyed || capturedEpoch != epoch) {
                        screenshot.getHardwareBuffer().close();
                        inFlight = false;
                        return;
                    }
                    images.execute(() -> {
                        Bitmap bitmap = null;
                        Bitmap hardware = null;
                        try {
                            hardware = Bitmap.wrapHardwareBuffer(screenshot.getHardwareBuffer(), screenshot.getColorSpace());
                            if (hardware != null) bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        } catch (RuntimeException ignored) {
                            // Only an error category is reported; no screen text or image is logged.
                        } finally {
                            if (hardware != null) hardware.recycle();
                            screenshot.getHardwareBuffer().close();
                        }
                        Bitmap ready = bitmap;
                        main.post(() -> {
                            if (ready == null) { captureFailed("截图转换失败"); return; }
                            if (!isFresh(target, capturedEpoch)) { ready.recycle(); inFlight = false; return; }
                            runOcr(ready, target, capturedEpoch, snapshot, capturedMatcher, expand, padding);
                        });
                    });
                }
                @Override public void onFailure(int errorCode) {
                    if (capturedEpoch == epoch) {
                        captureFailed(errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                                ? "页面禁止截图，仅使用界面文字" : "截图不可用，仅使用界面文字");
                    } else inFlight = false;
                }
            });
        } catch (RuntimeException error) { captureFailed("截图不可用，仅使用界面文字"); }
    }

    private void runOcr(Bitmap bitmap, Target target, long capturedEpoch, NodeReader.Snapshot snapshot,
                        KeywordMatcher capturedMatcher, boolean expand, int padding) {
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(result -> {
                        if (!isFresh(target, capturedEpoch)) return;
                        List<Box> matches = new ArrayList<>();
                        for (Text.TextBlock block : result.getTextBlocks()) {
                            // Match a block to allow a keyword split across lines.
                            if (!capturedMatcher.matches(block.getText()) || block.getBoundingBox() == null) continue;
                            Rect rect = block.getBoundingBox();
                            Box box = RegionPolicy.mapImageBox(new Box(rect.left, rect.top, rect.right, rect.bottom),
                                    bitmap.getWidth(), bitmap.getHeight(), target.bounds());
                            matches.add(RegionPolicy.choose(box, snapshot.cards(), target.bounds(), expand, padding));
                        }
                        ocrMasks = RegionPolicy.compact(matches);
                        ocrExpiresAt = SystemClock.uptimeMillis() + OCR_TTL_MS;
                        captureStatus = "";
                        render();
                    })
                    .addOnFailureListener(error -> {
                        if (capturedEpoch == epoch) captureFailed("文字识别暂不可用");
                    })
                    .addOnCompleteListener(task -> { bitmap.recycle(); inFlight = false; });
        } catch (RuntimeException error) {
            bitmap.recycle();
            captureFailed("文字识别暂不可用");
        }
    }

    private boolean isFresh(Target target, long capturedEpoch) {
        if (destroyed || !ResultGuard.accepts(capturedEpoch, epoch, requestStartedAt,
                SystemClock.uptimeMillis(), settings.enabled()) || !target.equals(current)) return false;
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return false;
        try {
            WindowTarget actual = findTarget();
            return actual != null && target.equals(actual.target());
        } catch (RuntimeException unavailable) { return false; }
    }

    private void captureFailed(String message) {
        inFlight = false;
        captureStatus = message;
        ocrMasks = List.of();
        nextOcrAt = SystemClock.uptimeMillis() + 5000;
    }

    private void render() {
        if (destroyed || current == null) return;
        List<Box> combined = new ArrayList<>(nodeMasks);
        combined.addAll(ocrMasks);
        List<Box> masks = RegionPolicy.compact(combined);
        if (!attached && !masks.isEmpty()) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setFitInsetsTypes(0);
            params.setTitle("ScreenFilter mask");
            try {
                windowsManager.addView(overlay, params);
                attached = true;
            } catch (WindowManager.BadTokenException | IllegalStateException unavailable) {
                FilterRuntime.status = "遮罩暂不可用，请重新开启服务";
                return;
            }
        }
        overlay.setRegions(masks);
    }

    private void invalidateFrame() {
        epoch++;
        nodeMasks = List.of();
        ocrMasks = List.of();
        if (overlay != null) overlay.setRegions(List.of());
    }

    private void clearTarget() {
        if (current != null) invalidateFrame();
        current = null;
        if (attached) {
            try { windowsManager.removeViewImmediate(overlay); }
            catch (IllegalArgumentException alreadyRemoved) { /* System detached the window first. */ }
            attached = false;
        }
    }

    @Override public void onInterrupt() {
        if (settings != null) settings.setEnabled(false);
        clearTarget();
        FilterRuntime.status = "服务被中断，已暂停";
    }

    @Override public void onDestroy() {
        destroyed = true;
        epoch++;
        main.removeCallbacksAndMessages(null);
        clearTarget();
        if (settings != null) settings.prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        if (recognizer != null) recognizer.close();
        images.shutdown();
        FilterRuntime.connected = false;
        FilterRuntime.status = "无障碍服务已关闭";
        super.onDestroy();
    }
}
