package com.screenfilter.app.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class KeywordMatcher {
    private final List<String> words;

    public KeywordMatcher(String input) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : input.split("[\\r\\n,，、;；]+")) {
            String value = normalize(line);
            if (!value.isEmpty()) unique.add(value);
            if (unique.size() == 100) break;
        }
        words = new ArrayList<>(unique);
    }

    public static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        normalized.codePoints().filter(c -> !Character.isWhitespace(c)
                && !Character.isSpaceChar(c) && Character.getType(c) != Character.FORMAT)
                .forEach(out::appendCodePoint);
        return out.toString();
    }

    public boolean matches(String text) {
        String normalized = normalize(text);
        for (String word : words) if (normalized.contains(word)) return true;
        return false;
    }

    public int size() { return words.size(); }
    public boolean isEmpty() { return words.isEmpty(); }
}
