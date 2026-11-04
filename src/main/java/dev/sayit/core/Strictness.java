package dev.sayit.core;

/**
 * How hard pronunciation is judged. Measured on 180 native and 109 English-accented text-to-speech
 * samples across 18 languages (see README):
 * <pre>
 *            native passes   accented passes   wrong word passes
 * RELAXED        87%              40%                 3%
 * NORMAL         78%               7%                 0%
 * HARD           65%               6%                 0%
 * </pre>
 * RELAXED trusts the restricted-grammar recogniser alone; it has to pick one of a few phrases, so an
 * accent usually still lands on the right word. NORMAL and HARD additionally need an open-vocabulary
 * recogniser (which can output any word it knows) to hear roughly the same word in the same audio.
 */
public enum Strictness {
    RELAXED, NORMAL, HARD;

    public static Strictness of(int ordinal) { return values()[Math.max(0, Math.min(values().length - 1, ordinal))]; }

    /** Whether the client should run the second, open-vocabulary recogniser for this language. */
    public boolean wantsOpenCheck(String lang) { return this != RELAXED && LanguageNames.openCheck(lang); }

    /**
     * @param heard     what the restricted-grammar recogniser heard
     * @param openHeard what the open-vocabulary recogniser heard on the same audio ("" if not run)
     * @return pass/fail plus the transcript and score worth showing the player
     */
    public Verdict judge(String target, String heard, float confidence, String openHeard, String lang,
                         boolean foldAccents, double passThreshold, double minConfidence) {
        double score = WordMatcher.score(target, heard, lang, foldAccents);
        if (!WordMatcher.passes(score, passThreshold)) return new Verdict(false, heard, score, false);
        boolean confident = confidence >= minConfidence;
        if (this == RELAXED) return new Verdict(confident, heard, score, !confident);

        if (!LanguageNames.openCheck(lang)) {
            // no usable open-vocabulary model: demand a much more confident grammar match instead
            boolean ok = confidence >= Math.max(minConfidence, this == HARD ? 0.9 : 0.8);
            return new Verdict(ok, heard, score, !ok);
        }
        if (!confident) return new Verdict(false, heard, score, true);
        String open = openHeard == null ? "" : openHeard;
        double openScore = WordMatcher.score(target, open, lang, foldAccents);
        double needed = this == HARD ? passThreshold
                : WordMatcher.compact(target, lang, foldAccents).length() <= 4 ? 0.60 : 0.75;
        boolean ok = WordMatcher.passes(openScore, needed);
        // On failure show what it really sounded like; on success report the weaker of the two scores.
        return ok ? new Verdict(true, heard, Math.min(score, openScore), false)
                  : new Verdict(false, open.isEmpty() ? "…" : open, openScore, false);
    }

    /** @param lowConfidence the words matched but the recogniser was unsure (HUD says "heard (unsure)") */
    public record Verdict(boolean pass, String shownHeard, double shownScore, boolean lowConfidence) {}
}
