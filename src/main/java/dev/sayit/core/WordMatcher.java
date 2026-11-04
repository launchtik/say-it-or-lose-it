package dev.sayit.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure-Java word matching used by both the server (pass/fail) and the client (HUD colouring).
 * No Minecraft classes here, so it can be unit tested in isolation.
 *
 * <p>Scoring follows what the reference video shows: a character-level similarity percentage
 * between what was heard and the target word, after normalisation (case, punctuation, spaces,
 * accents, leading articles). When the heard text contains extra words ("oh no no no weissen")
 * the best-matching contiguous window of words is used.
 */
public final class WordMatcher {
    private WordMatcher() {}

    /** Letters, combining marks (Hindi vowel signs, Japanese dakuten) and digits are kept. */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");

    /** Leading articles stripped per language (ISO 639-1 prefix of the Minecraft locale code). */
    private static final Map<String, Set<String>> ARTICLES = Map.of(
            "de", Set.of("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer"),
            "es", Set.of("el", "la", "los", "las", "un", "una", "unos", "unas"),
            "fr", Set.of("le", "la", "les", "l", "un", "une", "des", "du"),
            "it", Set.of("il", "lo", "la", "i", "gli", "le", "l", "un", "uno", "una"),
            "pt", Set.of("o", "a", "os", "as", "um", "uma", "uns", "umas"),
            "nl", Set.of("de", "het", "een"),
            "ca", Set.of("el", "la", "els", "les", "l", "un", "una", "uns", "unes"),
            "eo", Set.of("la"),
            "en", Set.of("the", "a", "an"));

    /** "de_de" -> "de". */
    public static String langPrefix(String langCode) {
        if (langCode == null) return "";
        String l = langCode.toLowerCase(Locale.ROOT);
        int i = l.indexOf('_');
        return i < 0 ? l : l.substring(0, i);
    }

    /**
     * Lower-cases, maps ß to ss, optionally removes accents, replaces punctuation with spaces.
     * Script-aware: accents are folded only on Latin/Greek/Cyrillic letters (never Hindi vowel signs or
     * Japanese dakuten); Arabic harakat are always dropped and Arabic/Persian letter variants unified;
     * katakana is mapped to hiragana; Turkish dotless ı counts as i.
     */
    public static String normalizeChars(String s, boolean foldAccents) {
        if (s == null) return "";
        String r = s.toLowerCase(Locale.ROOT).replace("ß", "ss").replace("’", "'").replace('ı', 'i');
        String d = Normalizer.normalize(r, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(d.length());
        Character.UnicodeScript prev = Character.UnicodeScript.COMMON;
        for (int i = 0; i < d.length(); ) {
            int cp = d.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            if (mark) {
                if (isArabicHaraka(cp)) continue;
                if (cp == 0x0901) cp = 0x0902; // Devanagari candrabindu ~ anusvara (गेहूँ = गेहूं)
                if (foldAccents && (prev == Character.UnicodeScript.LATIN || prev == Character.UnicodeScript.GREEK
                        || prev == Character.UnicodeScript.CYRILLIC)) continue;
                sb.appendCodePoint(cp);
                continue;
            }
            sb.appendCodePoint(unifyLetter(cp));
            prev = Character.UnicodeScript.of(cp);
        }
        r = Normalizer.normalize(sb, Normalizer.Form.NFC);
        return NON_WORD.matcher(r).replaceAll(" ").trim();
    }

    private static boolean isArabicHaraka(int cp) {
        return (cp >= 0x064B && cp <= 0x065F) || cp == 0x0670 || (cp >= 0x06D6 && cp <= 0x06ED);
    }

    private static int unifyLetter(int cp) {
        if (cp >= 0x30A1 && cp <= 0x30F6) return cp - 0x60;          // katakana -> hiragana
        return switch (cp) {
            case 0x0623, 0x0625, 0x0622, 0x0671 -> 0x0627;             // alef variants -> ا
            case 0x064A, 0x0649 -> 0x06CC;                             // Arabic yeh / alef maksura -> Persian ی
            case 0x0643 -> 0x06A9;                                     // Arabic kaf -> Persian ک
            case 0x0629 -> 0x0647;                                     // teh marbuta -> heh
            default -> cp;
        };
    }

    /** Normalised word list with a leading article removed (only if something remains). */
    public static List<String> tokens(String s, String langCode, boolean foldAccents) {
        String n = normalizeChars(s, foldAccents && LanguageNames.foldsAccents(langCode));
        List<String> out = new ArrayList<>();
        if (n.isEmpty()) return out;
        for (String t : n.split(" ")) if (!t.isEmpty()) out.add(t);
        String prefix = langPrefix(langCode);
        Set<String> arts = ARTICLES.getOrDefault(prefix, Set.of());
        if (out.size() > 1 && arts.contains(out.get(0))) out.remove(0);
        if (prefix.equals("ar")) out.replaceAll(t -> t.startsWith("ال") && t.length() > 3 ? t.substring(2) : t);
        return out;
    }

    /** Normalised form with spaces removed: "Das Brot!" -> "brot". */
    public static String compact(String s, String langCode, boolean foldAccents) {
        return String.join("", tokens(s, langCode, foldAccents));
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** 1 - distance / longer length, in [0,1]. Two empty strings are 0 (nothing was said). */
    public static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 0;
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    /** Best contiguous window of heard words, compacted. Empty if nothing was heard. */
    public static String bestWindow(String target, String heard, String langCode, boolean foldAccents) {
        String t = compact(target, langCode, foldAccents);
        List<String> h = tokens(heard, langCode, foldAccents);
        String best = "";
        double bestScore = -1;
        for (int i = 0; i < h.size(); i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < h.size(); j++) {
                sb.append(h.get(j));
                if (sb.length() > t.length() * 2 + 4) break;
                double s = similarity(t, sb.toString());
                if (s > bestScore) { bestScore = s; best = sb.toString(); }
            }
        }
        return best;
    }

    /** Similarity score in [0,1] between target and the best window of what was heard. */
    public static double score(String target, String heard, String langCode, boolean foldAccents) {
        String t = compact(target, langCode, foldAccents);
        if (t.isEmpty()) return 0;
        return similarity(t, bestWindow(target, heard, langCode, foldAccents));
    }

    /** True if normalised heard text equals the target exactly (the 100% case). */
    public static boolean exactMatch(String target, String heard, String langCode, boolean foldAccents) {
        String t = compact(target, langCode, foldAccents);
        return !t.isEmpty() && t.equals(compact(heard, langCode, foldAccents));
    }

    public static boolean passes(double score, double threshold) {
        // Round like the HUD does so "85%" on screen never fails an 0.85 threshold.
        return Math.round(score * 100) >= Math.round(threshold * 100);
    }

    /** A piece of the displayed target word and whether it was pronounced correctly. */
    public record Segment(String text, boolean correct) {}

    /**
     * Splits the displayed target into syllables and marks each one correct/incorrect by aligning
     * the target against the best window of the heard text. Used for the green/red word colouring.
     */
    public static List<Segment> segments(String display, String heard, String langCode, boolean foldAccentsSetting) {
        boolean foldAccents = foldAccentsSetting && LanguageNames.foldsAccents(langCode);
        // Per display character: its normalised contribution ("ß" -> "ss", " " -> "", "Ö" -> "o").
        int n = display.length();
        String[] contrib = new String[n];
        StringBuilder target = new StringBuilder();
        int[] owner = new int[n * 3 + 4];
        for (int i = 0; i < n; i++) {
            String c = normalizeChars(String.valueOf(display.charAt(i)), foldAccents).replace(" ", "");
            contrib[i] = c;
            for (int k = 0; k < c.length(); k++) owner[target.length() + k] = i;
            target.append(c);
        }
        // A stripped leading article would shift alignment, so align against the full compacted target.
        String t = target.toString();
        String h = bestWindowRaw(t, heard, foldAccents);
        boolean[] ok = alignCorrect(t, h);

        boolean[] charOk = new boolean[n];
        java.util.Arrays.fill(charOk, true);
        for (int ti = 0; ti < t.length(); ti++) if (!ok[ti]) charOk[owner[ti]] = false;

        List<Segment> out = new ArrayList<>();
        for (int[] span : Syllables.spans(display)) {
            boolean good = true;
            for (int i = span[0]; i < span[1]; i++) good &= charOk[i];
            out.add(new Segment(display.substring(span[0], span[1]), good));
        }
        return out;
    }

    private static String bestWindowRaw(String t, String heard, boolean foldAccents) {
        String norm = normalizeChars(heard, foldAccents);
        if (norm.isEmpty()) return "";
        String[] h = norm.split(" ");
        String best = "";
        double bestScore = -1;
        for (int i = 0; i < h.length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < h.length; j++) {
                sb.append(h[j]);
                if (sb.length() > t.length() * 2 + 4) break;
                double s = similarity(t, sb.toString());
                if (s > bestScore) { bestScore = s; best = sb.toString(); }
            }
        }
        return best;
    }

    /** For each char of t: true if the optimal alignment matches it exactly with no insertion next to it. */
    static boolean[] alignCorrect(String t, String h) {
        int a = t.length(), b = h.length();
        int[][] d = new int[a + 1][b + 1];
        for (int i = 0; i <= a; i++) d[i][0] = i;
        for (int j = 0; j <= b; j++) d[0][j] = j;
        for (int i = 1; i <= a; i++)
            for (int j = 1; j <= b; j++) {
                int cost = t.charAt(i - 1) == h.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        boolean[] ok = new boolean[a];
        boolean[] inserted = new boolean[a];
        int i = a, j = b;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && t.charAt(i - 1) == h.charAt(j - 1) && d[i][j] == d[i - 1][j - 1]) {
                ok[i - 1] = true; i--; j--;           // match
            } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
                i--; j--;                             // substitution
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                i--;                                  // target char missing from what was heard
            } else {
                // extra heard char: blame the target char it was inserted before (or the last one)
                if (a > 0) inserted[Math.min(i, a - 1)] = true;
                j--;
            }
        }
        for (int k = 0; k < a; k++) ok[k] &= !inserted[k];
        return ok;
    }
}
