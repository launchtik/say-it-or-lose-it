package dev.sayit.client.speech;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.Map;

/**
 * Minimal binding to libvosk (the native libraries come from the com.alphacephei:vosk jar).
 *
 * <p>We do not use org.vosk.LibVosk: it binds every function eagerly, and the macOS library shipped in
 * vosk 0.3.45 lacks vosk_recognizer_set_grm, so LibVosk fails to load on every Mac. Interface mapping
 * resolves functions lazily, and we only use ones present on all three platforms. Strings are forced to
 * UTF-8 (Windows would otherwise use the ANSI code page and break umlauts/accents).
 */
interface VoskNative extends Library {
    VoskNative INSTANCE = Native.load("vosk", VoskNative.class,
            Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));

    void vosk_set_log_level(int level);

    Pointer vosk_model_new(String path);
    void vosk_model_free(Pointer model);
    int vosk_model_find_word(Pointer model, String word);

    Pointer vosk_recognizer_new(Pointer model, float sampleRate);
    Pointer vosk_recognizer_new_grm(Pointer model, float sampleRate, String grammarJson);
    void vosk_recognizer_set_words(Pointer recognizer, int words);
    int vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int length);
    String vosk_recognizer_result(Pointer recognizer);
    String vosk_recognizer_final_result(Pointer recognizer);
    void vosk_recognizer_free(Pointer recognizer);
}
