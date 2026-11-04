package dev.sayit.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sayit.SayItMod;
import dev.sayit.core.WordMatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Words come from Minecraft's own language files (assets/minecraft/lang/de_de.json and every mod's
 * lang file), whatever language the game UI itself is set to. The server's sayit_words.json wins.
 */
public final class ClientWords {
    private ClientWords() {}

    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    public static synchronized Map<String, String> table(String language) {
        return CACHE.computeIfAbsent(language, ClientWords::load);
    }

    public static synchronized void clearCache() { CACHE.clear(); }

    private static Map<String, String> load(String language) {
        Map<String, String> out = new HashMap<>();
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        for (String ns : rm.getAllNamespaces()) {
            for (Resource res : rm.getAllResources(Identifier.of(ns, "lang/" + language + ".json"))) {
                try (Reader r = res.getReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet())
                        if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
                } catch (Exception e) {
                    SayItMod.LOG.warn("Bad lang file {}:{} - {}", ns, language, e.getMessage());
                }
            }
        }
        SayItMod.LOG.info("Loaded {} {} translations", out.size(), language);
        return out;
    }

    /** custom word > target-language translation > English name. */
    public static String resolve(String language, String translationKey, String customWord) {
        if (customWord != null && !customWord.isBlank()) return customWord.trim();
        String w = table(language).get(translationKey);
        if (w != null && !w.isBlank() && !w.contains("%")) return w;
        return I18n.translate(translationKey);
    }

    /** The {@code count} item/block names in this language that look most like the target. */
    public static List<String> distractors(String language, String target, int count) {
        if (count <= 0) return List.of();
        String t = WordMatcher.compact(target, language, true);
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : table(language).entrySet()) {
            String k = e.getKey();
            if (!(k.startsWith("item.") || k.startsWith("block.")) || k.split("\\.").length != 3) continue;
            String v = e.getValue();
            if (v.contains("%") || v.length() > 40) continue;
            if (!WordMatcher.compact(v, language, true).equals(t)) seen.add(v);
        }
        Map<String, Double> score = new HashMap<>();
        for (String v : seen) score.put(v, WordMatcher.similarity(t, WordMatcher.compact(v, language, true)));
        List<String> all = new ArrayList<>(seen);
        all.sort(Comparator.comparingDouble((String v) -> -score.get(v)));
        return new ArrayList<>(all.subList(0, Math.min(count, all.size())));
    }
}
