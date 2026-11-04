package dev.sayit.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sayit.SayItMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * config/sayit_words.json - custom words that override Minecraft's own translations.
 * <pre>
 * {
 *   "de_de": { "minecraft:wheat_seeds": "Weizenkörner", "minecraft:porkchop": "Schweinefleisch" },
 *   "*":     { "minecraft:diamond": "Diamant" }
 * }
 * </pre>
 * "*" applies to every language. Keys are item ids.
 */
public final class WordOverrides {
    private WordOverrides() {}

    private static volatile Map<String, Map<String, String>> words = Map.of();

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("sayit_words.json"); }

    public static void load() {
        Map<String, Map<String, String>> out = new HashMap<>();
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, """
                        {
                          "_comment": "Custom words per language (Minecraft locale code or *). Keys are item ids. Example below.",
                          "de_de": {
                            "minecraft:example_item": "Beispiel"
                          }
                        }
                        """, StandardCharsets.UTF_8);
            }
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                for (Map.Entry<String, JsonElement> lang : root.entrySet()) {
                    if (!lang.getValue().isJsonObject()) continue;
                    Map<String, String> m = new HashMap<>();
                    for (Map.Entry<String, JsonElement> e : lang.getValue().getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonPrimitive()) m.put(e.getKey(), e.getValue().getAsString());
                    }
                    out.put(lang.getKey().toLowerCase(java.util.Locale.ROOT), m);
                }
            }
        } catch (Exception e) {
            SayItMod.LOG.error("Could not read {}: {}", p, e.getMessage());
        }
        words = out;
    }

    /** Custom word for this item, or "" if Minecraft's translation should be used. */
    public static String get(String language, String itemId) {
        Map<String, String> m = words.get(language);
        if (m != null && m.containsKey(itemId)) return m.get(itemId);
        m = words.get("*");
        if (m != null && m.containsKey(itemId)) return m.get(itemId);
        return "";
    }
}
