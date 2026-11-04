package dev.sayit.server;

import dev.sayit.SayItMod;
import dev.sayit.config.SayItConfig;
import dev.sayit.config.WordOverrides;
import dev.sayit.core.WordMatcher;
import dev.sayit.net.SayItPayloads;
import dev.sayit.net.SayItPayloads.Outcome;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side state machine. The item goes into the inventory normally (as in the video);
 * a challenge runs; on failure every item of that type is deleted, on success the item type is
 * unlocked forever. Nothing is ever deleted unless the client confirmed its microphone is working.
 */
public final class ChallengeManager {
    private ChallengeManager() {}

    public enum Source { PICKUP, CRAFT, CONTAINER, COMMAND, REJOIN }

    enum Phase { AWAIT_READY, LISTENING }

    static final class Challenge {
        final int id;
        final Item item;
        final String itemId;
        final String language;
        int obtained;
        Phase phase = Phase.AWAIT_READY;
        String word = "";
        String scoreAs = "";
        long readyDeadline;
        long startTick;
        long deadline;
        double bestScore;
        int attempts;

        Challenge(int id, Item item, int obtained, String language) {
            this.id = id;
            this.item = item;
            this.itemId = Registries.ITEM.getId(item).toString();
            this.obtained = obtained;
            this.language = language;
        }
    }

    static final class PlayerState {
        Challenge active;
        final Deque<Challenge> queue = new ArrayDeque<>();
        boolean clientReady;
        String clientError = "";
        long cooldownUntil;
        long lastNotice;
        long rejoinAt = -1;
        Item lastCraftItem;
        long lastCraftTick = -1;
    }

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static int nextId = 1;
    private static long now;

    /** Waiting this long for the client to open its mic / load the model before giving up (no deletion). */
    private static final int READY_TIMEOUT_TICKS = 20 * 20;
    /** Gap between two queued challenges so the success card is readable. */
    private static final int COOLDOWN_TICKS = 20;

    private static PlayerState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), u -> new PlayerState());
    }

    // ------------------------------------------------------------------ triggers

    /** Called from the pickup / craft mixins with the item type and how many were obtained. */
    public static void onObtained(ServerPlayerEntity player, Item item, int count, Source source) {
        SayItConfig cfg = SayItConfig.get();
        if (!cfg.enabled || count <= 0 || item == null) return;
        if (source == Source.PICKUP && !cfg.triggerOnPickup) return;
        if (source == Source.CRAFT && !cfg.triggerOnCraft) return;
        if (source == Source.CONTAINER && !cfg.triggerOnContainer) return;
        if (player.isSpectator() || (player.isCreative() && !cfg.challengeInCreative)) return;
        String itemId = Registries.ITEM.getId(item).toString();
        if (!isChallenged(item)) return;
        if (PlayerWords.isUnlocked(player, cfg.language, itemId)) return;

        PlayerState st = state(player);
        // Taking a furnace/stonecutter result fires both the craft and the container hook: count it once.
        if (source == Source.CRAFT) { st.lastCraftItem = item; st.lastCraftTick = now; }
        else if (source == Source.CONTAINER && st.lastCraftItem == item && st.lastCraftTick == now) return;
        // Same item already being asked / queued: merge, one challenge per item type.
        if (st.active != null && st.active.item == item) { st.active.obtained += count; return; }
        for (Challenge c : st.queue) if (c.item == item) { c.obtained += count; return; }

        if (!ServerPlayNetworking.canSend(player, SayItPayloads.Start.ID)) return; // vanilla client: mod inactive
        if (!st.clientReady) {
            notice(player, st, "Speech unavailable (" + (st.clientError.isEmpty() ? "starting up" : st.clientError)
                    + ") - item kept");
            return;
        }
        st.queue.addLast(new Challenge(nextId++, item, count, cfg.language));
    }

    public static boolean isChallenged(Item item) {
        SayItConfig cfg = SayItConfig.get();
        if (item == null || new ItemStack(item).isEmpty()) return false;
        if (!cfg.whitelist.isEmpty() && cfg.whitelist.stream().noneMatch(s -> matches(item, s))) return false;
        return cfg.blacklist.stream().noneMatch(s -> matches(item, s));
    }

    private static boolean matches(Item item, String entry) {
        if (entry == null || entry.isBlank()) return false;
        entry = entry.trim();
        if (entry.startsWith("#")) {
            Identifier tag = Identifier.tryParse(entry.substring(1));
            return tag != null && Registries.ITEM.getEntry(item).isIn(TagKey.of(RegistryKeys.ITEM, tag));
        }
        Identifier id = Identifier.tryParse(entry);
        return id != null && Registries.ITEM.getId(item).equals(id);
    }

    /** /sayit challenge - force a challenge for the held item even if unlocked. */
    public static boolean forceChallenge(ServerPlayerEntity player, Item item) {
        PlayerState st = state(player);
        if (!ServerPlayNetworking.canSend(player, SayItPayloads.Start.ID) || !st.clientReady) return false;
        st.queue.addLast(new Challenge(nextId++, item, 0, SayItConfig.get().language));
        return true;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        now++;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerState st = STATES.get(p.getUuid());
            if (st == null) continue;
            if (st.rejoinAt >= 0 && now >= st.rejoinAt && st.clientReady) {
                st.rejoinAt = -1;
                for (String id : PlayerWords.takePending(p)) {
                    Item item = Registries.ITEM.get(Identifier.of(id));
                    if (!PlayerWords.isUnlocked(p, SayItConfig.get().language, id) && isChallenged(item))
                        st.queue.addLast(new Challenge(nextId++, item, 0, SayItConfig.get().language));
                }
            }
            Challenge c = st.active;
            if (c == null) {
                if (!st.queue.isEmpty() && now >= st.cooldownUntil && st.clientReady) start(p, st, st.queue.pollFirst());
                continue;
            }
            if (c.phase == Phase.AWAIT_READY && now > c.readyDeadline) {
                cancel(p, st, "Speech recognition did not start - item kept");
            } else if (c.phase == Phase.LISTENING && now > c.deadline) {
                fail(p, st);
            }
        }
    }

    private static void start(ServerPlayerEntity p, PlayerState st, Challenge c) {
        SayItConfig cfg = SayItConfig.get();
        // It may have been unlocked by a forced challenge in the meantime.
        if (c.obtained > 0 && PlayerWords.isUnlocked(p, c.language, c.itemId)) return;
        st.active = c;
        c.readyDeadline = now + READY_TIMEOUT_TICKS;
        SayItMod.LOG.info("Challenge #{} for {}: {} x{}", c.id, p.getName().getString(), c.itemId, c.obtained);
        ServerPlayNetworking.send(p, new SayItPayloads.Start(c.id, Identifier.of(c.itemId), c.item.getTranslationKey(),
                WordOverrides.get(c.language, c.itemId), c.language, cfg.timeLimitSeconds * 20, cfg.strictness.ordinal()));
    }

    // ------------------------------------------------------------------ client messages

    public static void onStatus(ServerPlayerEntity p, SayItPayloads.Status s) {
        PlayerState st = state(p);
        st.clientReady = s.ready();
        st.clientError = s.error() == null ? "" : s.error();
        if (!s.ready() && st.active != null) cancel(p, st, st.clientError + " - item kept");
    }

    public static void onReady(ServerPlayerEntity p, SayItPayloads.Ready r) {
        PlayerState st = state(p);
        Challenge c = st.active;
        if (c == null || c.id != r.challengeId() || c.phase != Phase.AWAIT_READY) return;
        String word = r.word() == null ? "" : r.word().trim();
        if (word.isEmpty() || word.length() > 128) { cancel(p, st, "No word for this item - item kept"); return; }
        String custom = WordOverrides.get(c.language, c.itemId);
        c.word = custom.isEmpty() ? word : custom; // the server's custom word always wins
        String scoreAs = r.scoreAs() == null ? "" : r.scoreAs().trim();
        c.scoreAs = scoreAs.isEmpty() || scoreAs.length() > 128 ? c.word : scoreAs;
        c.phase = Phase.LISTENING;
        c.startTick = now;
        c.deadline = now + SayItConfig.get().timeLimitSeconds * 20L + Math.round(SayItConfig.get().resultGraceSeconds * 20);
    }

    public static void onAttempt(ServerPlayerEntity p, SayItPayloads.Attempt a) {
        PlayerState st = state(p);
        Challenge c = st.active;
        if (c == null || c.id != a.challengeId() || c.phase != Phase.LISTENING) return;
        String heard = a.heard() == null ? "" : a.heard();
        if (heard.length() > 256) heard = heard.substring(0, 256);
        String open = a.openHeard() == null ? "" : a.openHeard();
        if (open.length() > 256) open = open.substring(0, 256);
        SayItConfig cfg = SayItConfig.get();
        dev.sayit.core.Strictness.Verdict v = cfg.strictness.judge(c.scoreAs, heard, a.confidence(), open,
                c.language, cfg.foldAccents, cfg.passThreshold, cfg.minConfidence);
        c.attempts++;
        c.bestScore = Math.max(c.bestScore, v.shownScore());
        if (v.pass()) {
            succeed(p, st, v.shownScore());
        } else {
            ServerPlayNetworking.send(p, new SayItPayloads.Feedback(c.id, v.shownHeard(), (float) v.shownScore(),
                    v.lowConfidence()));
        }
    }

    public static void onAbort(ServerPlayerEntity p, SayItPayloads.Abort a) {
        PlayerState st = state(p);
        if (st.active != null && st.active.id == a.challengeId()) cancel(p, st, a.reason() + " - item kept");
    }

    // ------------------------------------------------------------------ outcomes

    private static void succeed(ServerPlayerEntity p, PlayerState st, double score) {
        Challenge c = st.active;
        PlayerWords.unlock(p, c.language, c.itemId);
        int ticksLeft = (int) Math.max(0, c.startTick + SayItConfig.get().timeLimitSeconds * 20L - now);
        end(p, st, new SayItPayloads.End(c.id, Outcome.SUCCESS.ordinal(), (float) score, ticksLeft, 0, c.word));
        SayItMod.LOG.debug("{} unlocked {} ({}%)", p.getName().getString(), c.itemId, Math.round(score * 100));
    }

    private static void fail(ServerPlayerEntity p, PlayerState st) {
        Challenge c = st.active;
        int removed = InventoryRevoker.revoke(p, c.item,
                SayItConfig.get().failRemoves == SayItConfig.FailRemoves.ALL_OF_TYPE ? Integer.MAX_VALUE : c.obtained);
        end(p, st, new SayItPayloads.End(c.id, Outcome.FAIL.ordinal(), (float) c.bestScore, 0, removed, c.word));
        SayItMod.LOG.debug("{} failed {} - removed {}", p.getName().getString(), c.itemId, removed);
    }

    private static void cancel(ServerPlayerEntity p, PlayerState st, String message) {
        Challenge c = st.active;
        if (c == null) return;
        end(p, st, new SayItPayloads.End(c.id, Outcome.CANCELLED.ordinal(), 0, 0, 0, message));
    }

    private static void end(ServerPlayerEntity p, PlayerState st, SayItPayloads.End payload) {
        st.active = null;
        st.cooldownUntil = now + COOLDOWN_TICKS;
        ServerPlayNetworking.send(p, payload);
    }

    private static void notice(ServerPlayerEntity p, PlayerState st, String message) {
        if (now - st.lastNotice < 100) return;
        st.lastNotice = now;
        ServerPlayNetworking.send(p, new SayItPayloads.End(-1, Outcome.CANCELLED.ordinal(), 0, 0, 0, message));
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onJoin(ServerPlayerEntity p) {
        PlayerState st = state(p);
        st.clientReady = false;
        st.rejoinAt = now + 40;
        SayItConfig cfg = SayItConfig.get();
        if (ServerPlayNetworking.canSend(p, SayItPayloads.Session.ID))
            ServerPlayNetworking.send(p, new SayItPayloads.Session(cfg.language, cfg.timeLimitSeconds * 20, cfg.enabled));
    }

    /** Language or enable switch at runtime: every client reloads its speech model (no challenge until ready). */
    public static void broadcastSession(MinecraftServer server) {
        SayItConfig cfg = SayItConfig.get();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerState st = state(p);
            st.queue.clear();
            cancel(p, st, "Language changed to " + dev.sayit.core.LanguageNames.english(cfg.language));
            st.clientReady = false;
            if (ServerPlayNetworking.canSend(p, SayItPayloads.Session.ID))
                ServerPlayNetworking.send(p, new SayItPayloads.Session(cfg.language, cfg.timeLimitSeconds * 20, cfg.enabled));
        }
    }

    /** Disconnect mid-challenge: nothing is deleted, the item is asked again on the next join. */
    public static void onDisconnect(ServerPlayerEntity p) {
        PlayerState st = STATES.remove(p.getUuid());
        if (st == null) return;
        List<String> pending = new ArrayList<>(PlayerWords.of(p).pending());
        if (st.active != null && st.active.phase == Phase.LISTENING) pending.add(st.active.itemId);
        for (Challenge c : st.queue) pending.add(c.itemId);
        if (!pending.isEmpty()) PlayerWords.setPending(p, pending.stream().distinct().toList());
    }

    /** Death mid-challenge: items already dropped; cancel without deleting. Picking them up asks again. */
    public static void onDeath(ServerPlayerEntity p) {
        PlayerState st = STATES.get(p.getUuid());
        if (st == null) return;
        st.queue.clear();
        cancel(p, st, "You died - challenge cancelled");
    }

    public static void resetPlayer(ServerPlayerEntity p) {
        PlayerWords.reset(p);
        PlayerState st = STATES.get(p.getUuid());
        if (st != null) { st.queue.clear(); cancel(p, st, "Progress reset"); }
    }

    public static void onServerStopped() { STATES.clear(); }

    public static boolean isClientReady(ServerPlayerEntity p) { return state(p).clientReady; }
    public static String clientError(ServerPlayerEntity p) { return state(p).clientError; }
}
