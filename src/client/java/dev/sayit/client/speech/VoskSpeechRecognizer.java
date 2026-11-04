package dev.sayit.client.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Pointer;
import dev.sayit.SayItMod;
import dev.sayit.config.SayItConfig;
import dev.sayit.core.GrammarBuilder;
import dev.sayit.core.LanguageNames;
import dev.sayit.core.WordMatcher;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Offline recognition with Vosk. One model per language, loaded lazily and kept. */
public final class VoskSpeechRecognizer implements SpeechRecognizer {
    public static final float SAMPLE_RATE = 16000f;

    private String loadedLanguage;
    private Pointer model;
    private boolean nativeLoaded;

    @Override public String id() { return "vosk"; }

    @Override
    public synchronized boolean isPrepared(String language) { return model != null && language.equals(loadedLanguage); }

    /** Downloads the model if it is missing and auto-download is on. Call before {@link #prepare}. */
    public void ensureModel(String language, java.util.function.IntConsumer progress) throws SpeechUnavailableException {
        if (findModel(language) != null) return;
        String name = LanguageNames.model(language);
        if (name == null || !SayItConfig.get().autoDownloadModels
                || SayItConfig.get().modelPaths.containsKey(language)) return; // prepare() explains what is missing
        ModelDownloader.download(name, modelsDir(), progress);
    }

    @Override
    public synchronized void prepare(String language) throws SpeechUnavailableException {
        if (isPrepared(language)) return;
        Path dir = findModel(language);
        if (dir == null) {
            String expected = java.util.Objects.requireNonNullElse(LanguageNames.model(language), "a vosk-model-*-" + WordMatcher.langPrefix(language) + "-* folder");
            throw new SpeechUnavailableException("No Vosk model for " + language + " (put " + expected + " in "
                    + modelsDir() + ")");
        }
        try {
            if (!nativeLoaded) {
                VoskNative.INSTANCE.vosk_set_log_level(-1); // warnings and errors only
                nativeLoaded = true;
            }
            if (model != null) VoskNative.INSTANCE.vosk_model_free(model);
            model = null;
            long t = System.currentTimeMillis();
            Pointer m = VoskNative.INSTANCE.vosk_model_new(dir.toString());
            if (m == null) throw new SpeechUnavailableException("Vosk could not read the model in " + dir);
            model = m;
            loadedLanguage = language;
            SayItMod.LOG.info("Loaded Vosk model {} in {} ms", dir.getFileName(), System.currentTimeMillis() - t);
        } catch (SpeechUnavailableException e) {
            throw e;
        } catch (Throwable e) {
            model = null;
            throw new SpeechUnavailableException("Could not load Vosk (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", e);
        }
    }

    public static Path modelsDir() {
        Path p = Path.of(SayItConfig.get().modelsDirectory);
        return p.isAbsolute() ? p : FabricLoader.getInstance().getGameDir().resolve(p).normalize();
    }

    /** Explicit config path, then the default model name, then any vosk-model folder for that language. */
    static Path findModel(String language) {
        String explicit = SayItConfig.get().modelPaths.get(language);
        if (explicit != null && !explicit.isBlank()) {
            Path p = Path.of(explicit);
            if (!p.isAbsolute()) p = FabricLoader.getInstance().getGameDir().resolve(p);
            return isModel(p) ? p : null;
        }
        Path root = modelsDir();
        String def = LanguageNames.model(language);
        if (def != null && isModel(root.resolve(def))) return root.resolve(def);
        if (!Files.isDirectory(root)) return null;
        String prefix = WordMatcher.langPrefix(language);
        String code = prefix.equals("zh") ? "cn" : prefix;
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(VoskSpeechRecognizer::isModel)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches("vosk-model(-small)?-" + code + "([-_].*)?"))
                    .sorted().findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isModel(Path p) {
        return Files.isDirectory(p) && (Files.isDirectory(p.resolve("am")) || Files.exists(p.resolve("final.mdl")));
    }

    private boolean inVocabulary(String word) {
        try {
            return VoskNative.INSTANCE.vosk_model_find_word(model, word) >= 0;
        } catch (Throwable e) {
            return false; // unknown -> no grammar, free recognition (safe)
        }
    }

    /**
     * Restricted grammar for the target (see {@link GrammarBuilder}). Null = free recognition, used when
     * grammars are disabled or no part of the target is in the model's vocabulary.
     */
    synchronized GrammarBuilder.Plan plan(String target, List<String> distractors) {
        if (model == null) return new GrammarBuilder.Plan(null, "", target);
        GrammarBuilder.Plan p = GrammarBuilder.build(target, distractors, this::inVocabulary,
                SayItConfig.get().nearMissDistractors);
        if (!SayItConfig.get().useGrammar) return new GrammarBuilder.Plan(null, p.spoken(), p.scoreAs());
        return p;
    }

    static String grammarJson(List<String> phrases) {
        JsonArray arr = new JsonArray();
        phrases.forEach(arr::add);
        arr.add("[unk]");
        return arr.toString();
    }

    @Override
    public RecognitionSession startSession(String language, String target, List<String> distractors, boolean openCheck,
                                           Listener listener) throws SpeechUnavailableException {
        prepare(language);
        GrammarBuilder.Plan plan = plan(target, distractors);
        String grammar = plan.phrases() == null ? null : grammarJson(plan.phrases());
        Pointer rec;
        synchronized (this) {
            rec = grammar != null ? VoskNative.INSTANCE.vosk_recognizer_new_grm(model, SAMPLE_RATE, grammar)
                    : VoskNative.INSTANCE.vosk_recognizer_new(model, SAMPLE_RATE);
        }
        if (rec == null) throw new SpeechUnavailableException("Could not start the Vosk recogniser");
        VoskNative.INSTANCE.vosk_recognizer_set_words(rec, 1);
        Pointer open = null;
        if (openCheck && grammar != null) { // without a grammar the main recogniser already is open-vocabulary
            synchronized (this) { open = VoskNative.INSTANCE.vosk_recognizer_new(model, SAMPLE_RATE); }
            if (open != null) VoskNative.INSTANCE.vosk_recognizer_set_words(open, 1);
        }
        SayItMod.LOG.debug("Vosk session for '{}' scoreAs='{}' grammar={}", target, plan.scoreAs(), grammar);
        return new VoskSession(rec, open, openCheck && grammar == null, listener, plan.scoreAs());
    }

    @Override
    public synchronized void close() {
        if (model != null) VoskNative.INSTANCE.vosk_model_free(model);
        model = null;
        loadedLanguage = null;
    }

    private static final class VoskSession implements RecognitionSession {
        private final Pointer rec;
        private final Pointer open;           // second, open-vocabulary recogniser (null if not needed)
        private final boolean mainIsOpen;     // no grammar: the main transcript is the open one
        private final Listener listener;
        private final String scoreAs;
        private final StringBuilder openSoFar = new StringBuilder();
        private boolean closed;

        VoskSession(Pointer rec, Pointer open, boolean mainIsOpen, Listener listener, String scoreAs) {
            this.rec = rec;
            this.open = open;
            this.mainIsOpen = mainIsOpen;
            this.listener = listener;
            this.scoreAs = scoreAs;
        }

        @Override public String scoreAs() { return scoreAs; }

        @Override
        public synchronized void accept(byte[] pcm, int length) {
            if (closed) return;
            // The open recogniser may end its own segments earlier; keep what it heard until the main
            // recogniser (which decides where an utterance ends) finishes.
            if (open != null && VoskNative.INSTANCE.vosk_recognizer_accept_waveform(open, pcm, length) == 1)
                appendOpen(VoskNative.INSTANCE.vosk_recognizer_result(open));
            if (VoskNative.INSTANCE.vosk_recognizer_accept_waveform(rec, pcm, length) == 1)
                emit(VoskNative.INSTANCE.vosk_recognizer_result(rec));
        }

        @Override
        public synchronized void flush() { if (!closed) emit(VoskNative.INSTANCE.vosk_recognizer_final_result(rec)); }

        private void appendOpen(String json) {
            Parsed p = parse(json);
            if (p != null && !p.text.equals("…")) openSoFar.append(openSoFar.length() == 0 ? "" : " ").append(p.text);
        }

        private void emit(String json) {
            Parsed p = parse(json);
            if (open != null) appendOpen(VoskNative.INSTANCE.vosk_recognizer_final_result(open));
            String openText = mainIsOpen ? (p == null ? "" : p.text) : openSoFar.toString();
            openSoFar.setLength(0);
            if (p == null) return;
            listener.onResult(p.text, p.confidence, openText);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            VoskNative.INSTANCE.vosk_recognizer_free(rec);
            if (open != null) VoskNative.INSTANCE.vosk_recognizer_free(open);
        }
    }

    record Parsed(String text, float confidence) {}

    /** Parses a Vosk result. "[unk]"-only speech becomes "…" (scores 0%); silence returns null. */
    static Parsed parse(String json) {
        if (json == null || json.isBlank()) return null;
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String text = o.has("text") ? o.get("text").getAsString().trim() : "";
        List<String> words = new ArrayList<>();
        double confSum = 0;
        int n = 0;
        boolean unk = text.contains("[unk]");
        if (o.has("result")) {
            for (JsonElement e : o.getAsJsonArray("result")) {
                JsonObject w = e.getAsJsonObject();
                String word = w.get("word").getAsString();
                if (word.equals("[unk]")) { unk = true; continue; }
                words.add(word);
                if (w.has("conf")) { confSum += w.get("conf").getAsDouble(); n++; }
            }
        } else if (!text.isEmpty()) {
            for (String w : text.split(" ")) if (!w.equals("[unk]")) words.add(w);
        }
        if (words.isEmpty()) return unk ? new Parsed("…", 0f) : null;
        String joined = String.join(" ", words);
        String pretty = Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
        return new Parsed(pretty, n == 0 ? 1f : (float) (confSum / n));
    }

    /** For README / error messages. */
    public static String expectedModelPath(String language) {
        String def = LanguageNames.model(language);
        return modelsDir() + File.separator + (def == null ? "vosk-model-<lang>" : def);
    }
}
