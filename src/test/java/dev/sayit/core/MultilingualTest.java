package dev.sayit.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MultilingualTest {
    static int pct(String target, String heard, String lang) {
        return (int) Math.round(WordMatcher.score(target, heard, lang, true) * 100);
    }

    @Test void everyTableLanguageHasAModelAndName() {
        assertTrue(LanguageNames.all().size() >= 40, "locales: " + LanguageNames.all().size());
        long languages = LanguageNames.all().values().stream().map(LanguageNames.Entry::name).distinct().count();
        assertTrue(languages >= 25, "languages: " + languages);
        assertEquals("German", LanguageNames.english("de_de"));
        assertEquals("Japanese", LanguageNames.english("ja_jp"));
        assertEquals("vosk-model-small-cn-0.22", LanguageNames.model("zh_cn"));
        assertEquals("Finnish", LanguageNames.english("fi_fi")); // unsupported, falls back to Java's name
    }

    @Test void latinAccentsFoldButOtherScriptsKeepTheirMarks() {
        assertEquals("pan", WordMatcher.compact("Pán", "es_es", true));
        // Hindi vowel signs are combining marks: they must survive and must not split the word
        assertEquals("गेहूं", WordMatcher.compact("गेहूँ", "hi_in", true)); // candrabindu ~ anusvara
        assertEquals(100, pct("गेहूँ", "गेहूं", "hi_in"));
        assertEquals(1, WordMatcher.tokens("गेहूँ", "hi_in", true).size());
        // Cyrillic й folds to и only when folding is on
        assertEquals("пшеница", WordMatcher.compact("Пшеница", "ru_ru", true));
        assertEquals(100, pct("Картофель", "картофель", "ru_ru"));
    }

    @Test void arabicAndPersian() {
        // harakat are optional and ignored; the attached article is stripped
        assertEquals(100, pct("خُبْز", "الخبز", "ar_sa"));
        assertEquals(100, pct("قمح", "قمح", "ar_sa"));
        // Arabic vs Persian forms of yeh/kaf are the same letter
        assertEquals(100, pct("نان", "نان", "fa_ir"));
        assertEquals(WordMatcher.compact("كيك", "fa_ir", true), WordMatcher.compact("کیک", "fa_ir", true));
    }

    @Test void japaneseKatakanaMatchesHiragana() {
        assertEquals(100, pct("ジャガイモ", "じゃがいも", "ja_jp"));
        assertEquals(100, pct("パン", "ぱん", "ja_jp"));
        assertEquals(List.of("ジ", "ャ", "ガ", "イ", "モ"), Syllables.split("ジャガイモ"));
    }

    @Test void koreanAndChinese() {
        assertEquals(100, pct("감자", "감자", "ko_kr"));
        assertEquals(50, pct("감자", "감사", "ko_kr")); // one syllable off
        assertEquals(100, pct("马铃薯", "马铃 薯", "zh_cn")); // recogniser splits words with spaces
        assertEquals(List.of("马", "铃", "薯"), Syllables.split("马铃薯"));
    }

    @Test void vietnameseTonesAreNotFolded() {
        assertTrue(pct("Táo", "Tạo", "vi_vn") < 85, "different tone = different word");
        assertEquals(100, pct("Táo", "táo", "vi_vn"));
        assertEquals(100, pct("Pán", "pan", "es_es")); // other Latin languages still fold
    }

    @Test void turkishDotlessI() {
        assertEquals(100, pct("Işık", "ışık", "tr_tr"));
        assertEquals(100, pct("Ekmek", "ekmek", "tr_tr"));
    }

    @Test void catalanAndEsperantoArticles() {
        assertEquals("pa", WordMatcher.compact("el pa", "ca_es", true));
        assertEquals("pano", WordMatcher.compact("la pano", "eo_uy", true));
    }

    @Test void segmentsWorkInOtherScripts() {
        List<WordMatcher.Segment> s = WordMatcher.segments("Картофель", "картошка", "ru_ru", true);
        assertEquals("Картофель", String.join("", s.stream().map(WordMatcher.Segment::text).toList()));
        assertTrue(s.get(0).correct());
        assertFalse(s.get(s.size() - 1).correct());
        List<WordMatcher.Segment> j = WordMatcher.segments("ジャガイモ", "じゃがいも", "ja_jp", true);
        assertTrue(j.stream().allMatch(WordMatcher.Segment::correct));
    }

    @Test void grammarKeepsMarksAndSplitsCjk() {
        assertEquals("गेहूँ", GrammarBuilder.recogniserForm("गेहूँ!"));
        Set<String> vocab = Set.of("马铃", "薯", "картофель", "картофеля");
        GrammarBuilder.Plan p = GrammarBuilder.build("马铃薯", List.of(), vocab::contains, 4);
        assertEquals("马铃 薯", p.spoken());
        GrammarBuilder.Plan r = GrammarBuilder.build("Картофель", List.of("Картошка", "Яблоко"), vocab::contains, 4);
        assertTrue(r.phrases().contains("картофеля"), "Cyrillic near misses use the script's letters");
    }
}
