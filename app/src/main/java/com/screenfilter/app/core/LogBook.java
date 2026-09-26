package com.screenfilter.app.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded local journal. No screenshot, credential, or request-body field exists. */
public final class LogBook {
    public static final int MAX_EVENTS = 400, MAX_BYTES = 256 * 1024;
    public record Entry(long time, String request, String level, String stage, String detail) {
        JSONObject json() throws Exception {
            return new JSONObject().put("time", time).put("request", request).put("level", level)
                    .put("stage", stage).put("detail", detail);
        }
    }
    private final List<Entry> entries = new ArrayList<>();
    public List<Entry> entries() { return new ArrayList<>(entries); }
    public void clear() { entries.clear(); }
    public void add(Entry entry) throws Exception {
        entries.add(entry);
        while (entries.size() > MAX_EVENTS) entries.remove(0);
        while (encode().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES && !entries.isEmpty()) entries.remove(0);
    }
    public String encode() throws Exception {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) array.put(entry.json());
        return array.toString();
    }
    public void restore(String data) throws Exception {
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("日志文件过大");
        JSONArray array = new JSONArray(data);
        List<Entry> loaded = new ArrayList<>();
        for (int i = Math.max(0, array.length() - MAX_EVENTS); i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            loaded.add(new Entry(item.getLong("time"), item.getString("request"), item.getString("level"), item.getString("stage"), item.getString("detail")));
        }
        entries.clear(); entries.addAll(loaded);
    }
}
