package dev.sayit.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class GrammarBuilderTest {
    // A tiny fake vocabulary standing in for the Vosk model.
    static final Set<String> VOCAB = Set.of("weizen", "weisen", "heizen", "reizen", "brot", "boot", "rot",
            "roh", "es", "schweinefleisch", "kürbis", "kartoffel", "körner");
    static final Predicate<String> IN = VOCAB::contains;

    @Test void knownWordIsUsedAsIs() {
        GrammarBuilder.Plan p = GrammarBuilder.build("Weizen", List.of(), IN, 12);
        assertEquals("weizen", p.spoken());
        assertEquals("weizen", p.phrases().get(0));
        assertEquals("Weizen", p.scoreAs());
    }

    @Test void addsNearMissesFromVocabulary() {
        GrammarBuilder.Plan p = GrammarBuilder.build("Weizen", List.of(), IN, 12);
        assertTrue(p.phrases().containsAll(List.of("weisen", "heizen", "reizen")), p.phrases().toString());
        assertFalse(p.phrases().contains("brot"));
        GrammarBuilder.Plan capped = GrammarBuilder.build("Weizen", List.of(), IN, 1);
        assertEquals(2, capped.phrases().size());
    }

    @Test void decomposesOutOfVocabularyWords() {
        // "rohes" is not in the vocabulary (as in vosk-model-small-de) but "roh" + "es" are
        GrammarBuilder.Plan p = GrammarBuilder.build("Rohes Schweinefleisch", List.of(), IN, 0);
        assertEquals("roh es schweinefleisch", p.spoken());
        assertEquals("Rohes Schweinefleisch", p.scoreAs());
        // and the decomposed answer still scores 100% against the real word
        assertEquals(1.0, WordMatcher.score(p.scoreAs(), p.spoken(), "de_de", true));
        assertTrue(p.phrases().contains("schweinefleisch"), "partial answer is recognisable");
    }

    @Test void decomposesCompounds() {
        assertEquals("weizen körner", GrammarBuilder.decompose("weizenkörner", IN, 3));
        assertNull(GrammarBuilder.decompose("xyzzy", IN, 3));
    }

    @Test void undecomposableWordIsDroppedFromScoringNotMadeImpossible() {
        GrammarBuilder.Plan p = GrammarBuilder.build("Qwrtz Kartoffel", List.of(), IN, 0);
        assertEquals("kartoffel", p.spoken());
        assertEquals("Kartoffel", p.scoreAs());
    }

    @Test void nothingKnownMeansFreeRecognition() {
        GrammarBuilder.Plan p = GrammarBuilder.build("Qwrtz", List.of(), IN, 12);
        assertNull(p.phrases());
        assertEquals("Qwrtz", p.scoreAs());
    }

    @Test void distractorsMustBeInVocabulary() {
        GrammarBuilder.Plan p = GrammarBuilder.build("Brot", List.of("Kürbis", "Rote Bete", "Brot"), IN, 0);
        assertTrue(p.phrases().contains("kürbis"));
        assertFalse(p.phrases().contains("rote bete"));
        assertEquals(1, p.phrases().stream().filter("brot"::equals).count());
    }
}
