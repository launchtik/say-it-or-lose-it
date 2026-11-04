package dev.sayit.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds a restricted recognition grammar for a target word, given the recogniser's vocabulary.
 *
 * <ul>
 *   <li><b>Out-of-vocabulary words are decomposed</b> into in-vocabulary pieces ("rohes" -> "roh es").
 *       Because scoring compacts spaces away, "roh es schweinefleisch" still scores 100% against
 *       "Rohes Schweinefleisch". A word that cannot be decomposed is dropped from scoring
 *       ({@link Plan#scoreAs}) so the item is never impossible to unlock.</li>
 *   <li><b>Near-miss distractors</b>: vocabulary words one edit away from each target word
 *       ("weizen" -> "weisen", "heizen", "reizen"). The recogniser then has to discriminate, so a sloppy
 *       "Weissen" lands on "weisen" (83%, fail) instead of being snapped to "weizen" (100%).</li>
 *   <li>Similar item names (passed in) and "[unk]" catch everything else.</li>
 * </ul>
 */
public final class GrammarBuilder {
    private GrammarBuilder() {}

    /**
     * @param phrases  grammar phrases (first = the correct answer as the recogniser would spell it), or
     *                 null if no grammar should be used
     * @param spoken   the correct answer in recogniser words, e.g. "roh es schweinefleisch"
     * @param scoreAs  the text the server should score against (the display word, minus undecomposable words)
     */
    public record Plan(List<String> phrases, String spoken, String scoreAs) {}

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzäöüßéèàáíóúñç";

    /** "Rohes Schweinefleisch!" -> "rohes schweinefleisch" (accents kept: vocabularies use them). */
    public static String recogniserForm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}' ]+", " ").replaceAll("\\s+", " ").trim();
    }

    public static Plan build(String target, List<String> similarNames, Predicate<String> inVocab, int maxNearMissPerWord) {
        String form = recogniserForm(target);
        if (form.isEmpty()) return new Plan(null, "", target);

        List<String> spokenParts = new ArrayList<>();
        List<String> displayKept = new ArrayList<>();
        String[] displayWords = target.trim().split("\\s+");
        String[] words = form.split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String dec = decompose(w, inVocab, 3);
            if (dec == null && w.contains("ß")) dec = decompose(w.replace("ß", "ss"), inVocab, 3);
            if (dec != null) {
                spokenParts.add(dec);
                displayKept.add(i < displayWords.length ? displayWords[i] : w);
            }
        }
        if (spokenParts.isEmpty()) return new Plan(null, "", target); // nothing recognisable: free recognition
        String spoken = String.join(" ", spokenParts);
        String scoreAs = displayKept.size() == words.length ? target : String.join(" ", displayKept);

        // near-miss letters: the Latin set plus every character of this script seen in the item names
        StringBuilder alphabet = new StringBuilder(ALPHABET);
        Set<Integer> seen = new java.util.HashSet<>();
        ALPHABET.codePoints().forEach(seen::add);
        for (String src : similarNames) recogniserForm(src).codePoints().filter(c -> c != ' ' && seen.add(c)).forEach(alphabet::appendCodePoint);
        form.codePoints().filter(c -> c != ' ' && seen.add(c)).forEach(alphabet::appendCodePoint);
        String letters = alphabet.toString();

        Set<String> phrases = new LinkedHashSet<>();
        phrases.add(spoken);
        // near misses: replace one target word by a vocabulary word one edit away
        for (int i = 0; i < spokenParts.size(); i++) {
            String part = spokenParts.get(i);
            if (part.contains(" ") || part.length() < 3) continue;
            for (String alt : nearMisses(part, inVocab, maxNearMissPerWord, letters)) {
                List<String> copy = new ArrayList<>(spokenParts);
                copy.set(i, alt);
                phrases.add(String.join(" ", copy));
            }
        }
        for (String d : similarNames) {
            String g = recogniserForm(d);
            if (g.isEmpty() || g.equals(spoken)) continue;
            boolean ok = true;
            for (String w : g.split(" ")) ok &= inVocab.test(w);
            if (ok) phrases.add(g);
        }
        // partial answers ("schweinefleisch" alone) are recognisable and score as partial
        if (spokenParts.size() > 1) phrases.addAll(spokenParts);
        return new Plan(new ArrayList<>(phrases), spoken, scoreAs);
    }

    /** The word itself if known, else up to {@code maxParts} known pieces (longest first piece wins). */
    static String decompose(String w, Predicate<String> inVocab, int maxParts) {
        if (inVocab.test(w)) return w;
        // CJK words have no spaces and single-character words are common
        int minPart = !w.isEmpty() && Syllables.isSyllabic(w.charAt(0)) ? 1 : 2;
        if (maxParts <= 1 || w.length() < 2 * minPart) return null;
        for (int cut = w.length() - minPart; cut >= minPart; cut--) {
            String head = w.substring(0, cut);
            if (!inVocab.test(head)) continue;
            String rest = decompose(w.substring(cut), inVocab, maxParts - 1);
            if (rest != null) return head + " " + rest;
        }
        return null;
    }

    /** Vocabulary words at edit distance 1 (substitution, deletion, insertion, transposition). */
    static List<String> nearMisses(String w, Predicate<String> inVocab, int max) {
        return nearMisses(w, inVocab, max, ALPHABET);
    }

    static List<String> nearMisses(String w, Predicate<String> inVocab, int max, String alphabet) {
        if (max <= 0) return List.of();
        int[] letters = alphabet.codePoints().distinct().toArray();
        Set<String> cands = new LinkedHashSet<>();
        for (int i = 0; i < w.length(); i++) {
            for (int c : letters) {
                if (c != w.charAt(i)) cands.add(w.substring(0, i) + Character.toString(c) + w.substring(i + 1));
            }
            if (w.length() > 3) cands.add(w.substring(0, i) + w.substring(i + 1));
            if (i + 1 < w.length()) cands.add(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2));
        }
        for (int i = 0; i <= w.length(); i++)
            for (int c : letters) cands.add(w.substring(0, i) + Character.toString(c) + w.substring(i));
        cands.remove(w);
        List<String> out = new ArrayList<>();
        for (String c : cands) {
            if (out.size() >= max) break;
            if (inVocab.test(c)) out.add(c);
        }
        return out;
    }
}
