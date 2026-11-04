package dev.sayit.client.speech;

import java.util.List;

/**
 * Pluggable recogniser. Vosk (offline) ships; a cloud scorer such as Azure Pronunciation Assessment
 * can implement this interface and be selected with "recognizer" in config/sayit.json.
 * Audio is always 16 kHz, 16-bit, mono, little-endian PCM.
 */
public interface SpeechRecognizer extends AutoCloseable {
    /** Identifier used in the config ("vosk"). */
    String id();

    /** Loads whatever is needed for this language. May block for seconds; never call on the render thread. */
    void prepare(String language) throws SpeechUnavailableException;

    boolean isPrepared(String language);

    /**
     * @param target      the word the player must say, as displayed ("Weizenkörner")
     * @param distractors similar words, used to build a restricted grammar
     */
    /**
     * @param openCheck also transcribe the same audio without a grammar, so the server can tell a real
     *                  pronunciation from an accent that the grammar merely snapped onto the right word
     */
    RecognitionSession startSession(String language, String target, List<String> distractors,
                                    boolean openCheck, Listener listener) throws SpeechUnavailableException;

    @Override void close();

    interface Listener {
        /**
         * One finished utterance. {@code confidence} in [0,1]; 1 if the engine does not report it.
         * {@code openText}: open-vocabulary transcript of the same utterance, "" if not requested.
         */
        void onResult(String text, float confidence, String openText);
    }

    interface RecognitionSession extends AutoCloseable {
        /** Feed audio. Emits {@link Listener#onResult} when an utterance ends (automatic endpointing). */
        void accept(byte[] pcm, int length);

        /** Force the current utterance to finish (push-to-talk release, timer ran out). */
        void flush();

        /**
         * What the server should score against. Usually the target itself; a recogniser that cannot
         * produce some word of the target (out of vocabulary) may drop it so the item stays winnable.
         */
        default String scoreAs() { return ""; }

        @Override void close();
    }
}
