package dev.sayit.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrictnessTest {
    static Strictness.Verdict judge(Strictness s, String target, String heard, float conf, String open, String lang) {
        return s.judge(target, heard, conf, open, lang, true, 0.85, 0.5);
    }

    @Test void relaxedTrustsTheGrammarAlone() {
        // an accent snapped onto the right word by the grammar, while the open recogniser heard nonsense
        assertTrue(judge(Strictness.RELAXED, "Kartoffel", "Kartoffel", 0.9f, "Car to fell", "de_de").pass());
    }

    @Test void normalRejectsAnAccentTheOpenRecogniserDidNotUnderstand() {
        Strictness.Verdict v = judge(Strictness.NORMAL, "Kartoffel", "Kartoffel", 0.9f, "Car to fell", "de_de");
        assertFalse(v.pass());
        assertEquals("Car to fell", v.shownHeard(), "the HUD shows what it really sounded like");
        assertTrue(v.shownScore() < 0.75);
    }

    @Test void normalAcceptsGoodPronunciation() {
        assertTrue(judge(Strictness.NORMAL, "Kartoffel", "Kartoffel", 0.9f, "Kartoffel", "de_de").pass());
        // the open recogniser may inflect or split it slightly
        assertTrue(judge(Strictness.NORMAL, "Kartoffel", "Kartoffel", 0.9f, "Kartoffeln", "de_de").pass());
        assertTrue(judge(Strictness.NORMAL, "Weizenkörner", "Weizenkörner", 0.9f, "Weizen Körner", "de_de").pass());
    }

    @Test void shortWordsGetAGentlerOpenCheck() {
        assertTrue(judge(Strictness.NORMAL, "Brot", "Brot", 0.9f, "Boot", "de_de").pass());     // 75%
        assertFalse(judge(Strictness.HARD, "Brot", "Brot", 0.9f, "Boot", "de_de").pass());
        assertFalse(judge(Strictness.NORMAL, "Brot", "Brot", 0.9f, "Rad", "de_de").pass());
    }

    @Test void grammarMissAlwaysFails() {
        assertFalse(judge(Strictness.RELAXED, "Weizen", "Weisen", 1f, "Weizen", "de_de").pass());
        assertFalse(judge(Strictness.NORMAL, "Weizen", "Weisen", 1f, "Weizen", "de_de").pass());
    }

    @Test void lowConfidenceFailsAndIsFlagged() {
        Strictness.Verdict v = judge(Strictness.RELAXED, "Brot", "Brot", 0.3f, "", "de_de");
        assertFalse(v.pass());
        assertTrue(v.lowConfidence());
    }

    @Test void koreanUsesAConfidenceBarInsteadOfTheOpenCheck() {
        assertFalse(Strictness.NORMAL.wantsOpenCheck("ko_kr"));
        assertTrue(Strictness.NORMAL.wantsOpenCheck("de_de"));
        assertFalse(Strictness.RELAXED.wantsOpenCheck("de_de"));
        assertTrue(judge(Strictness.NORMAL, "감자", "감자", 0.95f, "", "ko_kr").pass());
        assertFalse(judge(Strictness.NORMAL, "감자", "감자", 0.6f, "", "ko_kr").pass());
    }

    @Test void missingOpenTranscriptFails() {
        // a client that skipped the open check cannot pass NORMAL
        assertFalse(judge(Strictness.NORMAL, "Kartoffel", "Kartoffel", 0.9f, "", "de_de").pass());
    }
}
