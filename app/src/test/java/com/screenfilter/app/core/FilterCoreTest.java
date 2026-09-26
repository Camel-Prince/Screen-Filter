package com.screenfilter.app.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class FilterCoreTest {
    @Test public void rejectsLateOcrAfterScrollOrAppChange() {
        assertFalse(ResultGuard.accepts(4, 5, 1000, 1200, true));
    }
    @Test public void rejectsLateOcrAfterPause() {
        assertFalse(ResultGuard.accepts(4, 4, 1000, 1200, false));
    }
    @Test public void dropsSlowFramesEvenIfPageDidNotEmitEvent() {
        assertFalse(ResultGuard.accepts(4, 4, 1000, 2600, true));
        assertTrue(ResultGuard.accepts(4, 4, 1000, 1300, true));
    }
    @Test public void matchesChineseWithOcrSpacesAndLineBreaks() {
        assertTrue(new KeywordMatcher("剧透").matches("剧情有剧 \n透内容"));
    }
    @Test public void normalizesFullWidthAndCase() {
        assertTrue(new KeywordMatcher("NBA").matches("今日ＮｂＡ赛况"));
    }
    @Test public void supportsSeparatorsAndDeduplicates() {
        KeywordMatcher matcher = new KeywordMatcher("剧透，广告;NBA\n nba\n；");
        assertEquals(3, matcher.size());
        assertTrue(matcher.matches("这是一则广告"));
        assertFalse(matcher.matches("周末散步"));
    }
    @Test public void blankRulesNeverMaskEverything() {
        KeywordMatcher matcher = new KeywordMatcher(" \n,；\u200B");
        assertTrue(matcher.isEmpty());
        assertFalse(matcher.matches("任意内容"));
    }
    @Test public void literalKeywordIsNotRegex() {
        KeywordMatcher matcher = new KeywordMatcher("a.b");
        assertFalse(matcher.matches("axb"));
        assertTrue(matcher.matches("a.b"));
    }
    @Test public void capsRuleCount() {
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < 150; i++) words.append("词").append(i).append('\n');
        assertEquals(100, new KeywordMatcher(words.toString()).size());
    }
    @Test public void picksOnlyContainingCardAndKeepsOtherColumnVisible() {
        Box window = new Box(0, 0, 1080, 2400);
        Box left = new Box(20, 200, 520, 900), right = new Box(560, 200, 1060, 1000);
        Box text = new Box(600, 850, 1010, 900);
        assertEquals(right, RegionPolicy.choose(text, List.of(left, right), window, true, 0));
    }
    @Test public void refusesWholeScreenAncestor() {
        Box window = new Box(0, 0, 1080, 2400), text = new Box(40, 300, 900, 350);
        assertEquals(text, RegionPolicy.choose(text, List.of(window), window, true, 0));
    }
    @Test public void disabledExpansionKeepsOnlyText() {
        Box window = new Box(0, 0, 1080, 2400), card = new Box(0, 200, 1080, 800);
        Box text = new Box(40, 300, 900, 350);
        assertEquals(text, RegionPolicy.choose(text, List.of(card), window, false, 0));
    }
    @Test public void clipsPaddingToWindowInSplitScreen() {
        Box window = new Box(500, 80, 1000, 1200), text = new Box(502, 82, 700, 120);
        assertEquals(new Box(500, 80, 710, 130), text.padded(10, window));
    }
    @Test public void mapsOcrPixelsToOffsetWindow() {
        Box window = new Box(50, 100, 1050, 2100);
        assertEquals(new Box(150, 300, 450, 500),
                RegionPolicy.mapImageBox(new Box(50, 100, 200, 200), 500, 1000, window));
    }
    @Test public void deduplicatesContainedMasksWithoutBridgingGap() {
        Box card = new Box(0, 100, 400, 600), child = new Box(20, 200, 300, 240);
        Box neighbor = new Box(500, 100, 900, 600);
        List<Box> result = RegionPolicy.compact(List.of(child, card, neighbor, card));
        assertEquals(2, result.size());
        assertTrue(result.contains(card));
        assertTrue(result.contains(neighbor));
    }
}
