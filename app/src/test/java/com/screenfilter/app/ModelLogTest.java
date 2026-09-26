package com.screenfilter.app;

import com.screenfilter.app.core.LogBook;
import com.screenfilter.app.core.LogRedactor;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelLogTest {
    private LogBook.Entry entry(int i, String detail) {
        return new LogBook.Entry(123456000L + i, "V-test" + i, "INFO", "解析完成", detail);
    }

    @Test public void logSurvivesRoundTripWithChineseAndMultilineReplies() throws Exception {
        LogBook book = new LogBook();
        book.add(entry(1, "命中 2 处\n[10,20,500,800]"));
        book.add(new LogBook.Entry(123456002L, "V-test1", "RAW", "原始文字回复", "{\"regions\":[]}"));
        LogBook restored = new LogBook(); restored.restore(book.encode());
        assertEquals(book.entries(), restored.entries());
    }
    @Test public void eventLimitEvictsOldestWhilePreservingRecentFailures() throws Exception {
        LogBook book = new LogBook();
        for (int i = 0; i < 430; i++) book.add(entry(i, "截图复核失败"));
        assertEquals(400, book.entries().size());
        assertEquals("V-test30", book.entries().get(0).request());
        assertEquals("V-test429", book.entries().get(399).request());
    }
    @Test public void chineseRepliesRemainWithinByteLimit() throws Exception {
        LogBook book = new LogBook();
        for (int i = 0; i < 40; i++) book.add(entry(i, "页面文字".repeat(1500)));
        assertTrue(book.entries().size() < 40);
        assertFalse(book.entries().isEmpty());
        assertTrue(book.encode().getBytes(StandardCharsets.UTF_8).length <= LogBook.MAX_BYTES);
        assertEquals("V-test39", book.entries().get(book.entries().size() - 1).request());
    }
    @Test public void damagedRestoreDoesNotPartiallyReplaceHistory() throws Exception {
        LogBook book = new LogBook(); book.add(entry(7, "已采用"));
        try { book.restore("[{},]"); fail(); } catch (Exception expected) { /* Invalid storage is rejected. */ }
        assertEquals(entry(7, "已采用"), book.entries().get(0));
        try { book.restore(" ".repeat(LogBook.MAX_BYTES + 1)); fail(); } catch (IllegalArgumentException expected) { /* Bounded read. */ }
        book.clear(); assertEquals("[]", book.encode());
    }
    @Test public void customAndJsonEscapedKeysAreRemovedBeforeStorage() {
        String key = "custom-secret-\"quoted\\value";
        String text = key + " " + JSONObject.quote(key);
        String safe = LogRedactor.clean(text, key);
        assertFalse(safe.contains("custom-secret"));
        assertTrue(safe.contains("[密钥已隐藏]"));
        assertFalse(LogRedactor.clean("{\"error\":\"custom\\/credential\"}", "custom/credential").contains("credential"));
    }
    @Test public void bearerTokensAndCommonKeysAreRedactedWithoutConfiguredKey() {
        String safe = LogRedactor.clean("Authorization: Bearer unusual-token-123\nerror sk-abcdefghij987654321", "");
        assertFalse(safe.contains("unusual-token")); assertFalse(safe.contains("abcdefghij"));
    }
    @Test public void imageDataIsOmittedIncludingJsonEscapedSlashes() {
        String text = "{\"url\":\"data:image\\/jpeg;base64,ab12+CD/ef==\"}";
        String safe = LogRedactor.clean(text, "");
        assertFalse(safe.contains("ab12")); assertTrue(safe.contains("[图片数据已省略]"));
    }
    @Test public void secretCrossingTruncationBoundaryCannotLeakPartialKey() {
        String key = "custom-sensitive-credential";
        String safe = LogRedactor.clean("x".repeat(5995) + key + "end", key);
        assertFalse(safe.contains("custom")); assertTrue(safe.endsWith("[回复已截断]"));
        assertTrue(safe.length() < 6100);
    }
    @Test public void ordinaryCoordinatesAndErrorInformationRemainReadable() {
        String text = "HTTP 429\n{\"regions\":[{\"box\":[10,20,400,800],\"theme\":\"calm\"}]}";
        assertEquals(text, LogRedactor.clean(text, "separate-secret"));
        assertEquals("", LogRedactor.clean(null, null));
    }
}
