package dev.sayit.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sayit.SayItMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-player, saved with the world (Fabric data attachment, survives death).
 *
 * @param unlocked item ids the player has said correctly; never challenged again (per language)
 * @param pending  item ids whose challenge was interrupted by a disconnect; re-asked on next join
 */
public record PlayerWords(List<String> unlocked, List<String> pending) {
    public static final Codec<PlayerWords> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("unlocked").forGetter(PlayerWords::unlocked),
            Codec.STRING.listOf().optionalFieldOf("pending", List.of()).forGetter(PlayerWords::pending)
    ).apply(i, PlayerWords::new));

    public static final PlayerWords EMPTY = new PlayerWords(List.of(), List.of());

    public static final AttachmentType<PlayerWords> TYPE = AttachmentRegistry.create(
            Identifier.of(SayItMod.MOD_ID, "words"),
            b -> b.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY));

    public static void init() { /* class load registers the attachment */ }

    /** Unlocks are stored as "lang|item" so switching language starts fresh. */
    static String key(String language, String itemId) { return language + "|" + itemId; }

    public static PlayerWords of(ServerPlayerEntity p) { return p.getAttachedOrElse(TYPE, EMPTY); }

    public static boolean isUnlocked(ServerPlayerEntity p, String language, String itemId) {
        return of(p).unlocked.contains(key(language, itemId));
    }

    public static void unlock(ServerPlayerEntity p, String language, String itemId) {
        PlayerWords d = of(p);
        Set<String> u = new LinkedHashSet<>(d.unlocked);
        if (u.add(key(language, itemId))) p.setAttached(TYPE, new PlayerWords(List.copyOf(u), d.pending));
    }

    public static int countUnlocked(ServerPlayerEntity p, String language) {
        String prefix = language + "|";
        return (int) of(p).unlocked.stream().filter(s -> s.startsWith(prefix)).count();
    }

    public static void reset(ServerPlayerEntity p) { p.setAttached(TYPE, EMPTY); }

    public static void setPending(ServerPlayerEntity p, List<String> itemIds) {
        PlayerWords d = of(p);
        p.setAttached(TYPE, new PlayerWords(d.unlocked, List.copyOf(itemIds)));
    }

    public static List<String> takePending(ServerPlayerEntity p) {
        PlayerWords d = of(p);
        if (d.pending.isEmpty()) return List.of();
        p.setAttached(TYPE, new PlayerWords(d.unlocked, List.of()));
        return new ArrayList<>(d.pending);
    }
}
