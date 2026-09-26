package com.screenfilter.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.screenfilter.app.core.Box;
import com.screenfilter.app.core.FrameGate;
import com.screenfilter.app.core.ImageEvidence;
import com.screenfilter.app.core.VisionProtocol;
import com.screenfilter.app.core.KeywordMatcher;
import com.screenfilter.app.core.MaskMemory;
import com.screenfilter.app.core.RegionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FilterAccessibilityService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private final VisionClient client = new VisionClient();
    private final FrameGate gate = new FrameGate();
    private final MaskMemory memory = new MaskMemory();
    private FilterSettings settings;
    private KeywordMatcher matcher;
    private CoverWindows covers;
    private volatile boolean destroyed;
    private boolean inFlight, dirty, reviewFailed, legacyCaptureBlocked;
    private TextRecognizer recognizer;
    private long fingerprint, nextCaptureAt, stableSince, requestStarted;
    private Target current;
    private List<Box> nodeMasks = List.of();
    private String detail = "";
    private String selectionReason = "尚未进入选定 App", lastTarget = "无";
    private int captureCount, screenshotCount, sentCount, replyCount, appliedCount, discardCount, lastRegions, textCount;
    private long lastScreenshotAt;
    private record Target(String packageName, int windowId, Box bounds) {}
    private record WindowTarget(Target target, AccessibilityNodeInfo root) {}

    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        matcher = new KeywordMatcher(settings.keywords());
        clearTarget();
        nextCaptureAt = 0;
        schedule(0);
    };
    private final Runnable tick = () -> {
        if (destroyed) return;
        try { scan(); }
        catch (RuntimeException error) {
            clearTarget();
            detail = "运行异常：" + error.getClass().getSimpleName();
            FilterRuntime.status = "窗口暂不可读，请重试或重新开启服务";
        } finally { if (!destroyed) { publishDiagnostics(); schedule(current == null ? 200 : 60); } }
    };

    @Override protected void onServiceConnected() {
        settings = new FilterSettings(this);
        matcher = new KeywordMatcher(settings.keywords());
        settings.prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        covers = new CoverWindows(this);
        if (Build.VERSION.SDK_INT >= 34) recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        FilterRuntime.connected = true;
        schedule(0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (settings == null || destroyed) return;
        String source = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (getPackageName().equals(source)) return;
        if (current != null && source.equals(current.packageName())
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            dirty = true;
            gate.change(); // Invalidate asynchronous results, but do not clear the visible cover.
            stableSince = SystemClock.uptimeMillis();
            client.cancel();
        }
        // CONTENT_CHANGED is a notification, not proof of a new frame. scan() compares
        // actual node evidence; image-only changes are checked with a second screenshot.
        schedule(0);
    }

    private void schedule(long delay) {
        main.removeCallbacks(tick);
        if (!destroyed) main.postDelayed(tick, delay);
    }

    private boolean awake() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void scan() {
        if (!settings.enabled() || !settings.hasRules()) {
            clearTarget(); FilterRuntime.status = "已暂停或尚未配置过滤要求"; return;
        }
        if (!awake()) { clearTarget(); FilterRuntime.status = "锁屏时暂停"; return; }
        WindowTarget selected = findTarget();
        if (selected == null) {
            clearTarget();
            FilterRuntime.status = "等待选定 App（输入时暂停）" + (detail.isEmpty() ? "" : " · 上次：" + detail);
            return;
        }
        Target target = selected.target();
        for (int i = 0; i < FilterSettings.PACKAGES.length; i++)
            if (FilterSettings.PACKAGES[i].equals(target.packageName())) lastTarget = FilterSettings.NAMES[i];
        if (!target.equals(current)) {
            clearTarget(); current = target; fingerprint = 0;
            stableSince = SystemClock.uptimeMillis();
        }
        NodeReader.Snapshot snapshot = new NodeReader().read(selected.root(), target.bounds());
        textCount = snapshot.texts().size();
        boolean changedEvidence = gate.observe(snapshot.fingerprint());
        boolean changed = changedEvidence || dirty;
        if (changedEvidence) {
            client.cancel(); stableSince = SystemClock.uptimeMillis();
        }
        fingerprint = snapshot.fingerprint(); dirty = false;
        memory.reconcile(snapshot.anchors(), target.bounds(), changed);
        int padding = Math.round(4 * getResources().getDisplayMetrics().density);
        // Compute the complete replacement before painting. Never publish an empty intermediate frame.
        nodeMasks = NodeReader.matches(snapshot, matcher, target.bounds(), settings.expand(), padding);
        render();
        FilterRuntime.status = "本地 " + nodeMasks.size() + " 处 · 图文 " + memory.masks().size() + " 处"
                + (detail.isEmpty() ? "" : " · " + detail);
        if (settings.ai() && legacyCaptureBlocked) {
            if (inFlight) { gate.change(); client.cancel(); }
            detail = "兼容截图检测到多个应用窗口，云端暂停；本地词仍工作";
            return;
        }
        long now = SystemClock.uptimeMillis();
        if ((settings.ai() || settings.ocr()) && !inFlight && now >= nextCaptureAt && now - stableSince >= 350) {
            capture(target, snapshot);
        }
    }

    private WindowTarget findTarget() {
        legacyCaptureBlocked = false;
        selectionReason = "当前不在选定 App";
        List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect r = new Rect(); window.getBoundsInScreen(r);
                if (!r.isEmpty()) { selectionReason = "检测到输入法窗口，请收起键盘"; return null; }
            }
        }
        // Touching our non-focusable cover may make it the accessibility-active window.
        // Select the focused application window instead, so tapping a cover cannot dismiss it.
        AccessibilityWindowInfo focusedApp = null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                focusedApp = window; break;
            }
        }
        AccessibilityNodeInfo root = focusedApp == null ? getRootInActiveWindow() : focusedApp.getRoot();
        if (root == null || root.getPackageName() == null) { selectionReason = "系统未提供当前窗口文字树"; return null; }
        if (!settings.accepts(root.getPackageName().toString())) return null;
        selectionReason = "目标 App 未获得窗口焦点";
        for (AccessibilityWindowInfo window : windows) {
            if (window.getId() != root.getWindowId() || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || !(window.isActive() || window.isFocused())) continue;
            for (AccessibilityWindowInfo other : windows) {
                if (other.getType() == AccessibilityWindowInfo.TYPE_SYSTEM && other.getLayer() > window.getLayer()) {
                    // Exclude notification panels and system dialogs, including non-focused popups.
                    Rect r = new Rect(); other.getBoundsInScreen(r);
                    if (other.isFocused() || r.height() > 100 * getResources().getDisplayMetrics().density) {
                        selectionReason = "系统弹窗或通知层位于目标上方"; return null;
                    }
                }
                // Older display captures cannot isolate another app in split screen.
                if (Build.VERSION.SDK_INT < 34 && settings.ai() && other.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                        && other.getId() != window.getId()) {
                    Rect otherBounds = new Rect(); other.getBoundsInScreen(otherBounds);
                    if (!otherBounds.isEmpty()) legacyCaptureBlocked = true;
                }
            }
            Rect rect = new Rect(), rootRect = new Rect();
            window.getBoundsInScreen(rect); root.getBoundsInScreen(rootRect);
            Box box = new Box(rect.left, rect.top, rect.right, rect.bottom);
            Box rootBox = new Box(rootRect.left, rootRect.top, rootRect.right, rootRect.bottom);
            box = box.intersect(rootBox);
            if (box.area() == 0) { selectionReason = "目标窗口边界为空"; return null; }
            selectionReason = legacyCaptureBlocked ? "多个应用窗口，兼容截图暂停" : "已找到目标窗口";
            return new WindowTarget(new Target(root.getPackageName().toString(), window.getId(), box), root);
        }
        return null;
    }

    private void capture(Target target, NodeReader.Snapshot snapshot) {
        final long version = gate.version();
        final boolean ai = settings.ai();
        final Box capturedWindow = Build.VERSION.SDK_INT >= 34 ? screenshotWindowBounds(target) : null;
        final List<Box> redact = new ArrayList<>(snapshot.privateAreas());
        // Legacy capture includes overlays: preserve them and grey them out in the upload.
        if (Build.VERSION.SDK_INT < 34) redact.addAll(covers.visible());
        final KeywordMatcher capturedMatcher = matcher;
        captureCount++;
        inFlight = true; requestStarted = SystemClock.uptimeMillis();
        nextCaptureAt = requestStarted + (ai ? 3000 : 900);
        reviewFailed = false;
        detail = ai ? "图文判断中" : "本地文字识别中";
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult screenshot) {
                screenshotCount++; lastScreenshotAt = SystemClock.uptimeMillis();
                if (!fresh(target, version)) { screenshot.getHardwareBuffer().close(); discarded("截图期间页面变化"); return; }
                if (Build.VERSION.SDK_INT < 34) redact.addAll(covers.visible());
                final VisionClient.Config config;
                try {
                    config = ai ? new VisionClient.Config(settings.endpoint(), settings.model(), new SecretStore(FilterAccessibilityService.this).read(),
                            settings.rules() + "\n同时过滤这些明确关键词：\n" + settings.keywords()) : null;
                } catch (Exception keyError) {
                    screenshot.getHardwareBuffer().close(); failed(version, "密钥不可读取，请重新保存模型配置"); return;
                }
                images.execute(() -> {
                    Bitmap bitmap = null, hardware = null;
                    try {
                        hardware = Bitmap.wrapHardwareBuffer(screenshot.getHardwareBuffer(), screenshot.getColorSpace());
                        if (hardware == null) throw new IllegalStateException();
                        bitmap = hardware.copy(Bitmap.Config.ARGB_8888, true);
                    } catch (RuntimeException ignored) {
                        // No screen pixels, text, keys, or provider response are logged.
                    } finally {
                        if (hardware != null) hardware.recycle(); screenshot.getHardwareBuffer().close();
                    }
                    if (bitmap == null) { main.post(() -> failed(version, "截图转换失败")); return; }
                    Bitmap prepared = null;
                    try {
                        Box imageBounds;
                        if (Build.VERSION.SDK_INT < 34) {
                            imageBounds = new Box(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        } else {
                            // The window API uses window-local pixels; root bounds may exclude bars.
                            imageBounds = capturedWindow;
                        }
                        if (imageBounds == null || imageBounds.area() == 0) throw new IllegalStateException();
                        prepared = prepare(bitmap, imageBounds, target.bounds(), redact);
                        bitmap.recycle(); bitmap = null;
                        Bitmap ready = prepared;
                        if (ai) {
                            int[] evidence = sample(ready);
                            var regions = client.classify(ready, config, () -> !destroyed && gate.version() == version && settings.enabled() && settings.ai(),
                                    () -> main.post(() -> { sentCount++; publishDiagnostics(); }));
                            main.post(() -> {
                                replyCount++;
                                if (!fresh(target, version)) { discarded("模型返回时页面已变化"); return; }
                                detail = "模型已返回，正在复核截图";
                                verifyImage(target, snapshot, version, redact, evidence, regions);
                            });
                        } else {
                            prepared = null; // Ownership transfers to ML Kit until completion.
                            main.post(() -> runOcr(ready, target, version, snapshot, capturedMatcher));
                        }
                    } catch (Exception error) {
                        String message = ai ? VisionClient.error(error) : "图片识别不可用";
                        main.post(() -> failed(version, message));
                    } finally {
                        if (bitmap != null) bitmap.recycle();
                        if (prepared != null) prepared.recycle();
                    }
                });
            }
            @Override public void onFailure(int code) { failed(version, "截图不可用（" + code + "），本地关键词仍工作"); }
        };
        try {
            if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(target.windowId(), getMainExecutor(), callback);
            else takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), callback);
        } catch (RuntimeException unsupported) { failed(version, "系统不支持截图，本地关键词仍工作"); }
    }

    private void verifyImage(Target target, NodeReader.Snapshot snapshot, long version, List<Box> redact,
                             int[] before, List<VisionProtocol.Region> regions) {
        final boolean wholeFrameRequired = settings.strict() || regions.isEmpty();
        // Huawei/Android display capture is rate limited; leave time after the initial capture.
        main.postDelayed(() -> {
            if (!fresh(target, version)) { discarded("复核前页面变化"); return; }
            Box window = Build.VERSION.SDK_INT >= 34 ? screenshotWindowBounds(target) : null;
            TakeScreenshotCallback callback = new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult shot) {
                    lastScreenshotAt = SystemClock.uptimeMillis();
                    if (!fresh(target, version)) { shot.getHardwareBuffer().close(); discarded("复核时页面变化"); return; }
                    images.execute(() -> {
                        Bitmap hardware = null, bitmap = null, prepared = null;
                        boolean same = false;
                        try {
                            hardware = Bitmap.wrapHardwareBuffer(shot.getHardwareBuffer(), shot.getColorSpace());
                            if (hardware != null) bitmap = hardware.copy(Bitmap.Config.ARGB_8888, true);
                            if (bitmap != null) {
                                Box bounds = Build.VERSION.SDK_INT >= 34 ? window : new Box(0, 0, bitmap.getWidth(), bitmap.getHeight());
                                if (bounds != null) {
                                    prepared = prepare(bitmap, bounds, target.bounds(), redact);
                                    int[] after = sample(prepared);
                                    same = wholeFrameRequired ? ImageEvidence.comparable(before, after)
                                            : regions.stream().allMatch(r -> ImageEvidence.comparable(before, after, r.normalized()));
                                }
                            }
                        } catch (RuntimeException ignored) { /* Failed conversion cannot validate a frame. */ }
                        finally {
                            if (hardware != null) hardware.recycle();
                            if (bitmap != null) bitmap.recycle();
                            if (prepared != null) prepared.recycle();
                            shot.getHardwareBuffer().close();
                        }
                        boolean verified = same;
                        main.post(() -> {
                            if (!fresh(target, version)) { discarded("复核后页面变化"); return; }
                            if (!verified) {
                                gate.change();
                                discarded("截图内容变化，等待重新判断");
                                stableSince = SystemClock.uptimeMillis(); render(); return;
                            }
                            inFlight = false; appliedCount++; lastRegions = regions.size();
                            if (Build.VERSION.SDK_INT >= 34) memory.clear();
                            for (var region : regions) {
                                Box box = RegionPolicy.mapImageBox(region.normalized(), 1000, 1000, target.bounds());
                                if (box.area() > 0) memory.add(new MaskMemory.Mask(box, region.theme()), snapshot.anchors());
                            }
                            gate.reviewed(version);
                            detail = "模型完成：命中 " + regions.size() + " 处 · " + (SystemClock.uptimeMillis() - requestStarted) / 1000.0 + " 秒";
                            render(); publishDiagnostics();
                        });
                    });
                }
                @Override public void onFailure(int code) { failed(version, "截图复核失败（" + code + "），未采用模型结果"); }
            };
            try {
                if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(target.windowId(), getMainExecutor(), callback);
                else takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), callback);
            } catch (RuntimeException error) { failed(version, "系统不支持截图复核"); }
        }, Math.max(0, 400 - (SystemClock.uptimeMillis() - lastScreenshotAt)));
    }

    private static int[] sample(Bitmap bitmap) {
        Bitmap small = Bitmap.createScaledBitmap(bitmap, ImageEvidence.WIDTH, ImageEvidence.HEIGHT, true);
        int[] pixels = new int[ImageEvidence.WIDTH * ImageEvidence.HEIGHT];
        small.getPixels(pixels, 0, ImageEvidence.WIDTH, 0, 0, ImageEvidence.WIDTH, ImageEvidence.HEIGHT);
        if (small != bitmap) small.recycle();
        return pixels;
    }

    private void discarded(String reason) {
        inFlight = false; discardCount++;
        if (!destroyed) { detail = "结果作废：" + reason; publishDiagnostics(); }
    }

    private void publishDiagnostics() {
        if (destroyed) return;
        FilterRuntime.diagnostics = "最近目标：" + lastTarget + " · 可读文字 " + textCount + " 处"
                + "\n初始截图：成功 " + screenshotCount + " / 尝试 " + captureCount + " · 请求已发 " + sentCount
                + "\n模型返回 " + replyCount + " · 复核采用 " + appliedCount + " · 最近命中 " + lastRegions + " 处"
                + "\n作废 " + discardCount + " 次 · " + selectionReason
                + (detail.isEmpty() ? "" : "\n最近状态：" + detail);
    }

    private Box screenshotWindowBounds(Target target) {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getId() == target.windowId()) {
                Rect rect = new Rect(); window.getBoundsInScreen(rect);
                return new Box(rect.left, rect.top, rect.right, rect.bottom);
            }
        }
        return null;
    }

    private static Bitmap prepare(Bitmap source, Box sourceBounds, Box target, List<Box> redact) {
        if (!sourceBounds.contains(target)) throw new IllegalArgumentException("截图边界已变化，请稍后重试");
        Box crop = RegionPolicy.mapImageBox(new Box(target.left() - sourceBounds.left(), target.top() - sourceBounds.top(),
                target.right() - sourceBounds.left(), target.bottom() - sourceBounds.top()), sourceBounds.width(), sourceBounds.height(),
                new Box(0, 0, source.getWidth(), source.getHeight()));
        // Display captures use absolute pixels, so sourceBounds must represent the full display.
        Bitmap cropped = Bitmap.createBitmap(source, crop.left(), crop.top(), crop.width(), crop.height());
        Bitmap mutable = cropped.copy(Bitmap.Config.ARGB_8888, true);
        if (cropped != source) cropped.recycle();
        Canvas canvas = new Canvas(mutable); Paint paint = new Paint(); paint.setColor(0xff888888);
        for (Box area : redact) {
            Box relative = new Box(area.left() - target.left(), area.top() - target.top(), area.right() - target.left(), area.bottom() - target.top());
            Box mapped = RegionPolicy.mapImageBox(relative, target.width(), target.height(), new Box(0, 0, mutable.getWidth(), mutable.getHeight()));
            canvas.drawRect(mapped.left(), mapped.top(), mapped.right(), mapped.bottom(), paint);
        }
        float scale = Math.min(1f, 1280f / Math.max(mutable.getWidth(), mutable.getHeight()));
        if (scale == 1f) return mutable;
        Bitmap resized = Bitmap.createScaledBitmap(mutable, Math.max(1, Math.round(mutable.getWidth() * scale)), Math.max(1, Math.round(mutable.getHeight() * scale)), true);
        mutable.recycle(); return resized;
    }

    private void runOcr(Bitmap bitmap, Target target, long version, NodeReader.Snapshot snapshot, KeywordMatcher capturedMatcher) {
        if (!fresh(target, version)) { bitmap.recycle(); inFlight = false; return; }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener(result -> {
                if (!fresh(target, version)) return;
                memory.clear();
                int padding = Math.round(4 * getResources().getDisplayMetrics().density);
                for (var block : result.getTextBlocks()) {
                    Rect rect = block.getBoundingBox();
                    if (rect == null || !capturedMatcher.matches(block.getText())) continue;
                    Box box = RegionPolicy.mapImageBox(new Box(rect.left, rect.top, rect.right, rect.bottom), bitmap.getWidth(), bitmap.getHeight(), target.bounds());
                    box = RegionPolicy.choose(box, snapshot.cards(), target.bounds(), settings.expand(), padding);
                    memory.add(new MaskMemory.Mask(box, "calm"), snapshot.anchors());
                }
                detail = "本地文字识别完成"; render();
            }).addOnFailureListener(error -> failed(version, "本地图片文字识别失败"))
                    .addOnCompleteListener(task -> { bitmap.recycle(); inFlight = false; });
        } catch (RuntimeException error) { bitmap.recycle(); failed(version, "本地图片文字识别失败"); }
    }

    private boolean fresh(Target target, long version) {
        if (destroyed || !gate.accept(version, requestStarted, SystemClock.uptimeMillis(), settings.enabled())
                || !target.equals(current) || !awake()) return false;
        try {
            WindowTarget actual = findTarget();
            if (actual == null || !target.equals(actual.target())) return false;
            if (settings.ai() && legacyCaptureBlocked) return false;
            if (new NodeReader().read(actual.root(), target.bounds()).fingerprint() != fingerprint) {
                gate.change(); schedule(0); return false;
            }
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    private void failed(long version, String message) {
        inFlight = false;
        if (destroyed) return;
        if (gate.version() != version) { discarded("页面或设置变化，请求已取消"); return; }
        reviewFailed = true;
        detail = message; nextCaptureAt = SystemClock.uptimeMillis() + 15000;
        // A failed request never marks a frame safe; strict mode stays covered.
        render();
    }

    private void render() {
        if (destroyed || current == null || covers == null) return;
        if (settings.strict() && gate.pending()) {
            covers.render(List.of(new MaskMemory.Mask(current.bounds(), "rest")), settings.style(),
                    reviewFailed ? "暂未完成判断，继续为你遮挡" : "正在为你检查这一页", () -> settings.setEnabled(false));
            return;
        }
        List<MaskMemory.Mask> all = new ArrayList<>(memory.masks());
        for (Box box : nodeMasks) all.add(new MaskMemory.Mask(box, "calm"));
        all.sort(Comparator.comparingLong((MaskMemory.Mask m) -> m.bounds().area()).reversed());
        List<MaskMemory.Mask> compact = new ArrayList<>();
        for (MaskMemory.Mask mask : all) {
            if (compact.stream().noneMatch(m -> m.bounds().contains(mask.bounds()))) compact.add(mask);
            if (compact.size() >= 32) break;
        }
        covers.render(compact, settings.style(), "", null);
    }

    private void clearTarget() {
        if (current != null) { gate.resetEvidence(); client.cancel(); }
        current = null; nodeMasks = List.of(); memory.clear(); dirty = false;
        if (covers != null) covers.clear();
    }
    @Override public void onInterrupt() {
        if (settings != null) settings.setEnabled(false);
        clearTarget(); FilterRuntime.status = "服务被中断，已暂停";
    }
    @Override public void onDestroy() {
        destroyed = true; gate.change(); main.removeCallbacksAndMessages(null); clearTarget(); client.cancel();
        if (settings != null) settings.prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        if (recognizer != null) recognizer.close();
        images.shutdown(); FilterRuntime.connected = false; FilterRuntime.status = "无障碍服务已关闭";
        super.onDestroy();
    }
}
