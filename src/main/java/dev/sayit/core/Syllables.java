package dev.sayit.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small, language-agnostic syllable splitter good enough for HUD colouring
 * ("Weizenkörner" -> Wei|zen|kör|ner, "Kartoffel" -> Kar|tof|fel). Not a linguistic hyphenator.
 *
 * <p>Rule: consecutive vowels form one nucleus; the consonants between two nuclei are split so that
 * the next syllable starts with the longest valid onset (e.g. "fl", "sch", "tr"), at least one consonant.
 */
public final class Syllables {
    private Syllables() {}

    private static final String VOWELS = "aeiouyäöüáéíóúàèìòùâêîôûãõœæåëïÿăâêôơưıəөүұі"
            + "аеёиоуыэюяіїєө" + "αεηιουωάέήίόύώ" + "აეიოუ";
    private static final Set<String> ONSETS = Set.of(
            "sch", "schl", "schr", "schw", "schm", "schn", "spr", "str", "pfl", "pfr", "chr",
            "ch", "ph", "th", "pf", "qu", "sp", "st", "zw", "gn", "kn",
            "bl", "br", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "kl", "kr", "pl", "pr", "tr");

    static boolean isVowel(char c) { return VOWELS.indexOf(Character.toLowerCase(c)) >= 0; }

    /** Letter or combining mark: marks (Hindi vowel signs, dakuten) belong to the word. */
    static boolean isWordChar(char c) {
        int t = Character.getType(c);
        return Character.isLetter(c) || t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
                || t == Character.ENCLOSING_MARK;
    }

    /** Scripts where one character is roughly one syllable. */
    static boolean isSyllabic(char c) {
        Character.UnicodeScript s = Character.UnicodeScript.of(c);
        return s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA
                || s == Character.UnicodeScript.KATAKANA || s == Character.UnicodeScript.HANGUL;
    }

    /** Syllable spans [start, end) over the whole string; spaces and punctuation are their own spans. */
    public static List<int[]> spans(String s) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            if (!isWordChar(s.charAt(i))) {
                int j = i;
                while (j < s.length() && !isWordChar(s.charAt(j))) j++;
                out.add(new int[]{i, j});
                i = j;
                continue;
            }
            int j = i;
            while (j < s.length() && isWordChar(s.charAt(j))) j++;
            for (int[] w : wordSpans(s.substring(i, j))) out.add(new int[]{w[0] + i, w[1] + i});
            i = j;
        }
        return out;
    }

    public static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (int[] sp : spans(s)) out.add(s.substring(sp[0], sp[1]));
        return out;
    }

    private static List<int[]> wordSpans(String w) {
        if (isSyllabic(w.charAt(0))) {
            List<int[]> per = new ArrayList<>();
            for (int i = 0; i < w.length(); ) {
                int j = i + Character.charCount(w.codePointAt(i));
                while (j < w.length() && !Character.isLetter(w.charAt(j))) j++; // keep marks with their base
                per.add(new int[]{i, j});
                i = j;
            }
            return per;
        }
        // nuclei = [start, end) of vowel groups
        List<int[]> nuclei = new ArrayList<>();
        int i = 0;
        while (i < w.length()) {
            if (isVowel(w.charAt(i))) {
                int j = i;
                while (j < w.length() && isVowel(w.charAt(j))) j++;
                nuclei.add(new int[]{i, j});
                i = j;
            } else i++;
        }
        List<int[]> out = new ArrayList<>();
        if (nuclei.size() <= 1) { out.add(new int[]{0, w.length()}); return out; }
        int start = 0;
        for (int k = 0; k < nuclei.size() - 1; k++) {
            int cStart = nuclei.get(k)[1], cEnd = nuclei.get(k + 1)[0];
            String cluster = w.substring(cStart, cEnd).toLowerCase(Locale.ROOT);
            int onsetLen = 0;
            if (!cluster.isEmpty()) {
                onsetLen = 1;
                for (int len = cluster.length(); len >= 2; len--) {
                    if (ONSETS.contains(cluster.substring(cluster.length() - len))) { onsetLen = len; break; }
                }
            }
            int cut = cEnd - onsetLen;
            if (cut <= start) cut = cEnd; // never produce an empty syllable
            out.add(new int[]{start, cut});
            start = cut;
        }
        out.add(new int[]{start, w.length()});
        return out;
    }
}
