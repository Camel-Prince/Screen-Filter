package com.screenfilter.app.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class VisionSafetyTest {
    private static String response(String content, String finish) throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", finish)
                .put("message", new JSONObject().put("content", content)))).toString();
    }
    @Test public void endpointAcceptsBaseAndCompleteRoute() {
        assertEquals("https://example.cn/v1/chat/completions", ModelEndpoint.normalize(" https://example.cn/v1/ "));
        assertEquals("https://example.cn/v1/chat/completions", ModelEndpoint.normalize("https://example.cn/v1/chat/completions"));
    }
    @Test public void endpointRejectsCredentialsQueriesAndPlaintext() {
        for (String value : List.of("http://example.cn/v1", "https://key@example.cn", "https://example.cn?key=secret", "https://example.cn#secret", "https://{WorkspaceId}.example.cn")) {
            assertThrows(IllegalArgumentException.class, () -> ModelEndpoint.normalize(value));
        }
    }
    @Test public void emptyResultIsValidOnlyWithExplicitRegions() throws Exception {
        assertTrue(VisionProtocol.parse(response("{\"regions\":[]}", "stop")).isEmpty());
        assertThrows(Exception.class, () -> VisionProtocol.parse(response("{}", "stop")));
    }
    @Test public void acceptsNormalizedCoordinatesAndFencedJson() throws Exception {
        var result = VisionProtocol.parse(response("```json\n{\"regions\":[{\"box\":[100,200,800,900],\"theme\":\"rest\"}]}\n```", "stop"));
        assertEquals(new Box(100, 200, 800, 900), result.get(0).normalized());
        assertEquals("rest", result.get(0).theme());
    }
    @Test public void refusesTruncatedOrRefusedModelResponses() throws Exception {
        for (String finish : List.of("length", "content_filter", "", "tool_calls"))
            assertThrows(Exception.class, () -> VisionProtocol.parse(response("{\"regions\":[]}", finish)));
    }
    @Test public void rejectsBadCoordinatesInsteadOfDeclaringFrameSafe() throws Exception {
        for (String box : List.of("[-1,0,100,100]", "[0,0,1001,100]", "[50,0,10,100]", "[0,0,0,100]", "[0.5,0,100,100]", "[\"0\",0,100,100]", "[0,0,100]")) {
            assertThrows(Exception.class, () -> VisionProtocol.parse(response("{\"regions\":[{\"box\":" + box + "}]}", "stop")));
        }
    }
    @Test public void arbitraryModelWordsNeverReachComfortCard() throws Exception {
        var result = VisionProtocol.parse(response("{\"regions\":[{\"box\":[0,0,500,500],\"theme\":\"repeat upsetting topic\"}]}", "stop"));
        assertEquals("calm", result.get(0).theme());
        assertFalse(Comfort.line(result.get(0).theme()).contains("upsetting"));
    }
    @Test public void requestHasImageAndInstructionsAreSeparated() throws Exception {
        JSONObject body = VisionProtocol.request("qwen3-vl-flash", "遮住剧透", "image-data");
        assertFalse(body.getBoolean("enable_thinking"));
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"));
        var parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        assertEquals("data:image/jpeg;base64,image-data", parts.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }
    @Test public void schemaRejectsExcessiveRegions() throws Exception {
        JSONArray regions = new JSONArray();
        for (int i = 0; i < 17; i++) regions.put(new JSONObject().put("box", new JSONArray("[0,0,100,100]")));
        assertThrows(Exception.class, () -> VisionProtocol.parse(response(new JSONObject().put("regions", regions).toString(), "stop")));
    }
    @Test public void frameChangesInvalidateApprovalAndSlowResults() {
        FrameGate gate = new FrameGate();
        long first = gate.version();
        assertTrue(gate.pending()); gate.reviewed(first); assertFalse(gate.pending());
        gate.change(); assertTrue(gate.pending()); gate.reviewed(first); assertTrue(gate.pending());
        assertFalse(gate.accept(first, 0, 500, true));
        assertFalse(gate.accept(gate.version(), 0, 25001, true));
        assertFalse(gate.accept(gate.version(), 100, 99, true));
        assertFalse(gate.accept(gate.version(), 0, 500, false));
        assertTrue(gate.accept(gate.version(), 0, 18000, true));
    }
    @Test public void maskFollowsUniqueTextAnchorOnScroll() {
        MaskMemory memory = new MaskMemory();
        memory.add(new MaskMemory.Mask(new Box(0, 100, 300, 300), "rest"), List.of(new MaskMemory.Anchor("unique title", new Box(10, 110, 100, 130))));
        memory.reconcile(List.of(new MaskMemory.Anchor("unique title", new Box(10, 60, 100, 80))), new Box(0, 0, 300, 700), true);
        assertEquals(new Box(0, 50, 300, 250), memory.masks().get(0).bounds());
    }
    @Test public void duplicateOrMissingAnchorNeverAttachesMaskToOtherCard() {
        for (var anchors : List.of(List.<MaskMemory.Anchor>of(), List.of(new MaskMemory.Anchor("same title", new Box(0, 0, 100, 20)), new MaskMemory.Anchor("same title", new Box(0, 50, 100, 70))))) {
            MaskMemory memory = new MaskMemory();
            memory.add(new MaskMemory.Mask(new Box(0, 0, 300, 300), "calm"), List.of(new MaskMemory.Anchor("same title", new Box(0, 0, 100, 20))));
            memory.reconcile(anchors, new Box(0, 0, 300, 700), true);
            assertTrue(memory.masks().isEmpty());
        }
    }
    @Test public void imageOnlyMaskStaysUntilFrameChanges() {
        MaskMemory memory = new MaskMemory();
        memory.add(new MaskMemory.Mask(new Box(0, 0, 300, 300), "calm"), List.of());
        memory.reconcile(List.of(), new Box(0, 0, 300, 700), false); assertEquals(1, memory.masks().size());
        memory.reconcile(List.of(), new Box(0, 0, 300, 700), true); assertTrue(memory.masks().isEmpty());
    }
    @Test public void repeatedSameMaskDoesNotAccumulate() {
        MaskMemory memory = new MaskMemory();
        for (int i = 0; i < 100; i++) memory.add(new MaskMemory.Mask(new Box(0, 0, 300, 300), "calm"), List.of());
        assertEquals(1, memory.masks().size()); memory.clear(); assertTrue(memory.masks().isEmpty());
    }
    @Test public void clippingDoesNotPermanentlyShrinkTrackedCard() {
        MaskMemory memory = new MaskMemory();
        var original = new MaskMemory.Anchor("unique title", new Box(10, 160, 100, 180));
        memory.add(new MaskMemory.Mask(new Box(0, 100, 300, 300), "rest"), List.of(original));
        Box clip = new Box(0, 0, 300, 700);
        memory.reconcile(List.of(new MaskMemory.Anchor("unique title", new Box(10, 10, 100, 30))), clip, true);
        assertEquals(new Box(0, 0, 300, 150), memory.masks().get(0).bounds());
        memory.reconcile(List.of(original), clip, true);
        assertEquals(new Box(0, 100, 300, 300), memory.masks().get(0).bounds());
    }
}
