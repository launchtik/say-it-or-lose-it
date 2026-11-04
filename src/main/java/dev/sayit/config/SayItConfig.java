package dev.sayit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.sayit.SayItMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * config/sayit.json. One file for both sides: the server reads the gameplay section,
 * the client reads the speech section. Unknown/missing fields fall back to defaults.
 */
public final class SayItConfig {
    // ---------------- gameplay (server) ----------------
    /** Master switch. */
    public boolean enabled = true;
    /** Minecraft locale code of the language you must speak, e.g. de_de, es_es, fr_fr. */
    public String language = "de_de";
    /** Seconds the player has to say the word (the video uses 10). */
    public int timeLimitSeconds = 10;
    /** Extra seconds the server waits for a late recognition result after the client timer hits 0. */
    public double resultGraceSeconds = 2.0;
    /**
     * How strictly pronunciation is judged. RELAXED: the restricted grammar alone (accents mostly pass).
     * NORMAL: a second open-vocabulary recogniser must hear roughly the same word (accents mostly fail).
     * HARD: both recognisers must reach passThreshold.
     */
    public dev.sayit.core.Strictness strictness = dev.sayit.core.Strictness.NORMAL;
    /** Minimum similarity (0-1) between what was heard and the word. Video: 83% failed, 93% passed. */
    public double passThreshold = 0.85;
    /** Minimum average recogniser confidence (0-1). 0 disables the check. */
    public double minConfidence = 0.5;
    /** Remove accents/umlauts before comparing ("körner" == "korner"). */
    public boolean foldAccents = true;
    /** If non-empty, ONLY these items are challenged. Item ids or #tags, e.g. "minecraft:wheat", "#minecraft:logs". */
    public List<String> whitelist = new ArrayList<>();
    /** Items never challenged. Item ids or #tags. */
    public List<String> blacklist = new ArrayList<>();
    /** Challenge players in creative mode too (default: creative players are never challenged). */
    public boolean challengeInCreative = false;
    /** What a failed challenge deletes: ALL_OF_TYPE ("lost all my wheat", the video) or OBTAINED_AMOUNT. */
    public FailRemoves failRemoves = FailRemoves.ALL_OF_TYPE;
    /** Trigger when picking up item entities. */
    public boolean triggerOnPickup = true;
    /** Trigger when taking a crafting / smelting / stonecutting result. */
    public boolean triggerOnCraft = true;
    /** Trigger when taking items out of a chest, barrel, shulker box, furnace, villager trade or any other container. */
    public boolean triggerOnContainer = true;

    // ---------------- speech (client) ----------------
    /** AUTOMATIC: always listening during a challenge. PUSH_TO_TALK: only while the key (default V) is held. */
    public ListenMode listenMode = ListenMode.AUTOMATIC;
    /** Recogniser implementation. Only "vosk" ships; the interface allows a cloud scorer later. */
    public String recognizer = "vosk";
    /** Folder holding Vosk models. Relative paths are resolved against the game directory. */
    public String modelsDirectory = "config/sayit/models";
    /** Download a missing Vosk model automatically from alphacephei.com the first time a language is used. */
    public boolean autoDownloadModels = true;
    /** Optional explicit model folder per language, e.g. {"de_de": "C:/models/vosk-model-de-0.21"}. */
    public java.util.Map<String, String> modelPaths = new java.util.HashMap<>();
    /** Restrict Vosk to a grammar of the target word + similar-sounding distractors (fewer false positives). */
    public boolean useGrammar = true;
    /** Number of distractor item names added to the grammar. */
    public int distractorCount = 8;
    /** Vocabulary words one letter away from each target word ("weisen" for "weizen") added to the grammar. */
    public int nearMissDistractors = 12;
    /** Substring of the input device name to use; empty = system default microphone. */
    public String microphone = "";
    /** Show the "mic on" label + level meter left of the hotbar. */
    public boolean showMicIndicator = true;
    /** Play the countdown / success / fail sounds. */
    public boolean sounds = true;

    public enum FailRemoves { ALL_OF_TYPE, OBTAINED_AMOUNT }
    public enum ListenMode { AUTOMATIC, PUSH_TO_TALK }

    // ---------------- io ----------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile SayItConfig instance = new SayItConfig();

    public static SayItConfig get() { return instance; }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("sayit.json"); }

    public static synchronized SayItConfig load() {
        Path p = path();
        SayItConfig cfg = new SayItConfig();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                SayItConfig read = GSON.fromJson(r, SayItConfig.class);
                if (read != null) cfg = read;
            } catch (IOException | JsonParseException e) {
                SayItMod.LOG.error("Could not read {}, using defaults: {}", p, e.getMessage());
            }
        }
        cfg.sanitize();
        instance = cfg;
        save(cfg); // writes any new fields with defaults
        return cfg;
    }

    public static synchronized void save(SayItConfig cfg) {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) { GSON.toJson(cfg, w); }
        } catch (IOException e) {
            SayItMod.LOG.error("Could not write {}: {}", path(), e.getMessage());
        }
    }

    private void sanitize() {
        if (language == null || language.isBlank()) language = "de_de";
        language = language.toLowerCase(java.util.Locale.ROOT);
        timeLimitSeconds = Math.max(2, Math.min(120, timeLimitSeconds));
        resultGraceSeconds = Math.max(0, Math.min(10, resultGraceSeconds));
        passThreshold = Math.max(0, Math.min(1, passThreshold));
        minConfidence = Math.max(0, Math.min(1, minConfidence));
        distractorCount = Math.max(0, Math.min(50, distractorCount));
        nearMissDistractors = Math.max(0, Math.min(50, nearMissDistractors));
        if (whitelist == null) whitelist = new ArrayList<>();
        if (blacklist == null) blacklist = new ArrayList<>();
        if (modelPaths == null) modelPaths = new java.util.HashMap<>();
        if (failRemoves == null) failRemoves = FailRemoves.ALL_OF_TYPE;
        if (strictness == null) strictness = dev.sayit.core.Strictness.NORMAL;
        if (listenMode == null) listenMode = ListenMode.AUTOMATIC;
        if (recognizer == null) recognizer = "vosk";
        if (modelsDirectory == null) modelsDirectory = "config/sayit/models";
        if (microphone == null) microphone = "";
    }
}
