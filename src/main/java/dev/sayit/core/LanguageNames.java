package dev.sayit.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Supported languages, read from assets/sayit/languages.json (the single source of truth, also used by
 * the Gradle download task): Minecraft locale -> Vosk model folder + English name for the HUD header.
 */
public final class LanguageNames {
    private LanguageNames() {}

    public record Entry(String model, String name, boolean openCheck, boolean foldAccents) {}

    private static final Map<String, Entry> TABLE = load();

    private static Map<String, Entry> load() {
        Map<String, Entry> out = new TreeMap<>();
        try (InputStream in = LanguageNames.class.getResourceAsStream("/assets/sayit/languages.json")) {
            if (in == null) return out;
            JsonObject langs = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("languages");
            for (Map.Entry<String, JsonElement> e : langs.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                out.put(e.getKey(), new Entry(o.get("model").getAsString(), o.get("name").getAsString(),
                        !o.has("openCheck") || o.get("openCheck").getAsBoolean(),
                        !o.has("foldAccents") || o.get("foldAccents").getAsBoolean()));
            }
        } catch (Exception ignored) {
        }
        return Collections.unmodifiableMap(out);
    }

    /** All supported Minecraft locales. */
    public static Map<String, Entry> all() { return TABLE; }

    public static boolean isSupported(String langCode) { return TABLE.containsKey(langCode); }

    /** Default Vosk model folder for this locale, or null. */
    public static String model(String langCode) {
        Entry e = TABLE.get(langCode);
        return e == null ? null : e.model();
    }

    /** False where the open-vocabulary model is too weak for the accent check (Korean). */
    public static boolean openCheck(String langCode) {
        Entry e = TABLE.get(langCode);
        return e == null || e.openCheck();
    }

    /** False where accents are phonemic and must not be folded (Vietnamese tones). */
    public static boolean foldsAccents(String langCode) {
        Entry e = TABLE.get(langCode);
        return e == null || e.foldAccents();
    }

    /** "de_de" -> "German"; unknown codes fall back to Java's own language names. */
    public static String english(String langCode) {
        Entry e = TABLE.get(langCode);
        if (e != null) return e.name();
        String p = WordMatcher.langPrefix(langCode);
        String n = Locale.forLanguageTag(p).getDisplayLanguage(Locale.ENGLISH);
        return n == null || n.isEmpty() ? p.toUpperCase(Locale.ROOT) : n;
    }
}
