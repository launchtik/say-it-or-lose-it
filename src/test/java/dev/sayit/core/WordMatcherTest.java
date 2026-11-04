package dev.sayit.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WordMatcherTest {
    static final String DE = "de_de";

    static int pct(String target, String heard) {
        return (int) Math.round(WordMatcher.score(target, heard, DE, true) * 100);
    }

    @Nested
    class Normalisation {
        @Test void lowercasesAndStripsPunctuation() {
            assertEquals("weizen", WordMatcher.compact("Weizen!", DE, true));
            assertEquals("rohesschweinefleisch", WordMatcher.compact("Rohes Schweinefleisch.", DE, true));
        }

        @Test void foldsAccentsAndUmlautsWhenEnabled() {
            assertEquals("weizenkorner", WordMatcher.compact("Weizenkörner", DE, true));
            assertEquals("weizenkörner", WordMatcher.compact("Weizenkörner", DE, false));
            assertEquals("cafe", WordMatcher.compact("Café", "fr_fr", true));
        }

        @Test void eszettBecomesDoubleS() {
            assertEquals("strasse", WordMatcher.compact("Straße", DE, false));
            assertTrue(WordMatcher.exactMatch("Straße", "strasse", DE, false));
        }

        @Test void stripsLeadingArticlePerLanguage() {
            assertEquals("brot", WordMatcher.compact("das Brot", DE, true));
            assertEquals("trigo", WordMatcher.compact("El trigo", "es_es", true));
            assertEquals("pain", WordMatcher.compact("le pain", "fr_fr", true));
            // only the leading article, and never the whole input
            assertEquals("das", WordMatcher.compact("das", DE, true));
            // a German article is not stripped when the language is Spanish
            assertEquals("dasbrot", WordMatcher.compact("das Brot", "es_es", true));
        }

        @Test void emptyAndNullAreEmpty() {
            assertEquals("", WordMatcher.compact("", DE, true));
            assertEquals("", WordMatcher.compact(null, DE, true));
            assertEquals("", WordMatcher.compact("?!  ...", DE, true));
        }

        @Test void langPrefix() {
            assertEquals("de", WordMatcher.langPrefix("de_de"));
            assertEquals("pt", WordMatcher.langPrefix("PT_BR"));
            assertEquals("en", WordMatcher.langPrefix("en"));
        }
    }

    @Nested
    class Scoring {
        @Test void exactPronunciationIs100() {
            assertEquals(100, pct("Weizen", "weizen"));
            assertEquals(100, pct("Brot", "Brot."));
            assertEquals(100, pct("Kartoffel", "kartoffel"));
            assertEquals(100, pct("Rohes Schweinefleisch", "rohes schweinefleisch"));
            assertEquals(100, pct("Weizenkörner", "weizen körner"));  // recogniser split the compound
        }

        @Test void matchesTheVideoAttempts() {
            // Percentages the HUD displayed in the reference video, within a few points.
            assertEquals(67, pct("Weizen", "Vai Zen!"));
            assertEquals(83, pct("Weizenkörner", "Weizenkönne!"));
            assertTrue(Math.abs(pct("Kartoffel", "Gott hoffel.") - 63) <= 5);
            assertTrue(pct("Weizenkörner", "Das war's.") <= 20);
            assertTrue(pct("Rohes Schweinefleisch", "Rohes Schweinflesch") >= 90);
        }

        @Test void usesBestWindowWhenExtraWordsAreSaid() {
            int withNoise = pct("Weizen", "Oh, no, no, no. Weissen!");
            int alone = pct("Weizen", "Weissen");
            assertEquals(alone, withNoise);
            assertEquals(100, pct("Brot", "ähm okay brot bitte"));
        }

        @Test void nothingHeardIsZero() {
            assertEquals(0, pct("Weizen", ""));
            assertEquals(0, pct("Weizen", "   "));
        }

        @Test void thresholdUsesDisplayedRounding() {
            assertFalse(WordMatcher.passes(0.83, 0.85));   // "83%" failed in the video
            assertTrue(WordMatcher.passes(0.93, 0.85));    // "93%" passed
            assertTrue(WordMatcher.passes(0.8496, 0.85));  // displays as 85%
            assertTrue(WordMatcher.passes(1.0, 1.0));
        }

        @Test void exactMatchIgnoresCaseAccentsAndArticles() {
            assertTrue(WordMatcher.exactMatch("Brot", "das brot", DE, true));
            assertTrue(WordMatcher.exactMatch("Weizenkörner", "weizenkorner", DE, true));
            assertFalse(WordMatcher.exactMatch("Weizenkörner", "weizenkorner", DE, false));
            assertFalse(WordMatcher.exactMatch("Weizen", "", DE, true));
        }

        @Test void levenshteinBasics() {
            assertEquals(0, WordMatcher.levenshtein("abc", "abc"));
            assertEquals(3, WordMatcher.levenshtein("", "abc"));
            assertEquals(1, WordMatcher.levenshtein("weizen", "weisen"));
            assertEquals(2, WordMatcher.levenshtein("weizenkorner", "weizenkonne"));
        }
    }

    @Nested
    class SyllablesAndSegments {
        @Test void splitsGermanWords() {
            assertEquals(List.of("Wei", "zen"), Syllables.split("Weizen"));
            assertEquals(List.of("Wei", "zen", "kör", "ner"), Syllables.split("Weizenkörner"));
            assertEquals(List.of("Kar", "tof", "fel"), Syllables.split("Kartoffel"));
            assertEquals(List.of("Brot"), Syllables.split("Brot"));
            assertEquals(List.of("Ro", "hes", " ", "Schwei", "ne", "fleisch"), Syllables.split("Rohes Schweinefleisch"));
            assertEquals(List.of("pa", "ta", "ta"), Syllables.split("patata"));
            assertEquals(List.of("tri", "go"), Syllables.split("trigo"));
        }

        @Test void spansCoverTheWholeString() {
            for (String w : List.of("Weizenkörner", "Rohes Schweinefleisch", "Kartoffel", "Oak Planks", "x")) {
                assertEquals(w, String.join("", Syllables.split(w)));
            }
        }

        static String render(List<WordMatcher.Segment> segs) {
            return segs.stream().map(s -> s.correct() ? s.text() : "[" + s.text() + "]").collect(Collectors.joining());
        }

        @Test void colouringMatchesTheVideo() {
            // "Weissen" -> Wei green, zen red
            assertEquals("Wei[zen]", render(WordMatcher.segments("Weizen", "Oh, no, no, no. Weissen!", DE, true)));
            // "Weizenkönne" -> Weizen green, körner red
            assertEquals("Weizen[kör][ner]", render(WordMatcher.segments("Weizenkörner", "Weizenkönne!", DE, true)));
        }

        @Test void perfectAttemptIsAllGreen() {
            assertEquals("Kartoffel", render(WordMatcher.segments("Kartoffel", "kartoffel", DE, true)));
            assertEquals("Rohes Schweinefleisch",
                    render(WordMatcher.segments("Rohes Schweinefleisch", "rohes schweinefleisch", DE, true)));
        }

        @Test void nothingHeardIsAllRed() {
            assertEquals("[Wei][zen]", render(WordMatcher.segments("Weizen", "", DE, true)));
        }
    }
}
