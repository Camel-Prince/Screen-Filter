package com.screenfilter.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.AtomicFile;
import com.screenfilter.app.core.LogBook;
import com.screenfilter.app.core.LogRedactor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class ModelLog {
    private static ModelLog instance;
    static synchronized ModelLog get(Context context) {
        if (instance == null) instance = new ModelLog(context.getApplicationContext());
        return instance;
    }
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicLong generation = new AtomicLong();
    private final LogBook book = new LogBook();
    private final AtomicFile file;
    private final SharedPreferences prefs;
    private volatile List<LogBook.Entry> entries = List.of();
    private volatile long revision;
    private volatile String storageStatus = "正在读取日志…";

    private ModelLog(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "model-log.json"));
        prefs = context.getSharedPreferences("model_log_settings", Context.MODE_PRIVATE);
        io.execute(() -> {
            try {
                if (file.getBaseFile().length() > LogBook.MAX_BYTES) throw new IllegalStateException();
                try { book.restore(new String(file.readFully(), StandardCharsets.UTF_8)); }
                catch (java.io.FileNotFoundException empty) { /* First run has no journal yet. */ }
                storageStatus = "日志保存在本机，重启后仍可查看。";
            } catch (Exception bad) { storageStatus = "旧日志无法读取；新记录仍可保存。"; }
            publish();
        });
    }
    boolean rawEnabled() { return prefs.getBoolean("raw_reply", false); }
    void rawEnabled(boolean value) { prefs.edit().putBoolean("raw_reply", value).apply(); }
    List<LogBook.Entry> entries() { return entries; }
    long revision() { return revision; }
    String storageStatus() { return storageStatus; }
    Trace begin(String context) {
        return begin("S-", context);
    }
    Trace beginRecognition(String context) { return begin("V-", "图文识别 · " + context); }
    private Trace begin(String prefix, String context) {
        Trace trace = new Trace(this, prefix + UUID.randomUUID().toString().substring(0, 8), generation.get());
        trace.info("开始", context); return trace;
    }
    static final class Trace {
        private final ModelLog owner;
        private final String id;
        private final long generation, started = SystemClock.uptimeMillis();
        private final boolean rawAtStart;
        Trace(ModelLog owner, String id, long generation) {
            this.owner = owner; this.id = id; this.generation = generation; rawAtStart = owner.rawEnabled();
        }
        void info(String stage, String detail) { add("INFO", stage, detail); }
        void problem(String stage, String detail) { add("WARN", stage, detail); }
        private void add(String level, String stage, String detail) {
            owner.append(generation, new LogBook.Entry(System.currentTimeMillis(), id, level, stage,
                    "+" + (SystemClock.uptimeMillis() - started) + " ms · " + LogRedactor.clean(detail, "")));
        }
        void raw(String text, String key) {
            if (!rawAtStart || !owner.rawEnabled()) return;
            // Sanitize before queuing; the key is never retained by the journal or its writer.
            String safe = LogRedactor.clean(text, key);
            owner.appendRaw(generation, new LogBook.Entry(System.currentTimeMillis(), id, "RAW", "原始文字回复", safe));
        }
    }
    private void appendRaw(long expected, LogBook.Entry entry) { append(expected, entry); }
    private void append(long expected, LogBook.Entry entry) {
        io.execute(() -> {
            if (expected != generation.get() || ("RAW".equals(entry.level()) && !rawEnabled())) return;
            try { book.add(entry); persist(); }
            catch (Exception error) { storageStatus = "日志写入失败，当前记录仅保留在内存。"; }
            publish();
        });
    }
    void clear() {
        generation.incrementAndGet(); // Late callbacks from cleared requests cannot repopulate old logs.
        io.execute(() -> {
            book.clear();
            try {
                file.delete();
                storageStatus = file.getBaseFile().exists() ? "日志文件删除失败，请重试。" : "日志已清空。";
            }
            catch (RuntimeException error) { storageStatus = "日志删除失败，请重试。"; }
            publish();
        });
    }
    private void persist() throws Exception {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite(); stream.write(book.encode().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream); storageStatus = "日志保存在本机，重启后仍可查看。";
        } catch (Exception error) { if (stream != null) file.failWrite(stream); throw error; }
    }
    private void publish() {
        entries = java.util.Collections.unmodifiableList(book.entries()); revision++;
    }
}
