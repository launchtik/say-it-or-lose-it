package dev.sayit.net;

import dev.sayit.SayItMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Protocol (server-authoritative):
 * <pre>
 * S2C Session  on join: language + time limit, lets the client preload its speech model
 * C2S Status   client speech readiness (mic + model). Server never challenges a client that is not ready.
 * S2C Start    a challenge for an item (id, translation key, optional custom word)
 * C2S Ready    client resolved the word and opened the mic -> server starts the countdown
 * C2S Attempt  one recognised utterance (text + confidence); server scores it
 * S2C Feedback score for a failed attempt (HUD shows heard: "..." NN%)
 * C2S Abort    mic broke / produced only silence -> server cancels WITHOUT deleting anything
 * S2C End      SUCCESS / FAIL (items deleted) / CANCELLED (nothing deleted) + message
 * </pre>
 */
public final class SayItPayloads {
    private SayItPayloads() {}

    static Identifier id(String path) { return Identifier.of(SayItMod.MOD_ID, path); }

    public record Session(String language, int timeLimitTicks, boolean enabled) implements CustomPayload {
        public static final Id<Session> ID = new Id<>(id("session"));
        public static final PacketCodec<RegistryByteBuf, Session> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Session::language,
                PacketCodecs.VAR_INT, Session::timeLimitTicks,
                PacketCodecs.BOOLEAN, Session::enabled,
                Session::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Status(boolean ready, String error) implements CustomPayload {
        public static final Id<Status> ID = new Id<>(id("status"));
        public static final PacketCodec<RegistryByteBuf, Status> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOLEAN, Status::ready,
                PacketCodecs.STRING, Status::error,
                Status::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** {@code strictness}: {@link dev.sayit.core.Strictness} ordinal, tells the client which recognisers to run. */
    public record Start(int challengeId, Identifier item, String translationKey, String customWord,
                        String language, int timeLimitTicks, int strictness) implements CustomPayload {
        public static final Id<Start> ID = new Id<>(id("start"));
        public static final PacketCodec<RegistryByteBuf, Start> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Start::challengeId,
                Identifier.PACKET_CODEC, Start::item,
                PacketCodecs.STRING, Start::translationKey,
                PacketCodecs.STRING, Start::customWord,
                PacketCodecs.STRING, Start::language,
                PacketCodecs.VAR_INT, Start::timeLimitTicks,
                PacketCodecs.VAR_INT, Start::strictness,
                Start::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** {@code scoreAs}: the target minus words the client's recogniser cannot produce ("" = word). */
    public record Ready(int challengeId, String word, String scoreAs) implements CustomPayload {
        public static final Id<Ready> ID = new Id<>(id("ready"));
        public static final PacketCodec<RegistryByteBuf, Ready> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Ready::challengeId,
                PacketCodecs.STRING, Ready::word,
                PacketCodecs.STRING, Ready::scoreAs,
                Ready::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** {@code openHeard}: the open-vocabulary recogniser's transcript of the same audio ("" if not run). */
    public record Attempt(int challengeId, String heard, float confidence, String openHeard) implements CustomPayload {
        public static final Id<Attempt> ID = new Id<>(id("attempt"));
        public static final PacketCodec<RegistryByteBuf, Attempt> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Attempt::challengeId,
                PacketCodecs.STRING, Attempt::heard,
                PacketCodecs.FLOAT, Attempt::confidence,
                PacketCodecs.STRING, Attempt::openHeard,
                Attempt::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Feedback(int challengeId, String heard, float score, boolean lowConfidence) implements CustomPayload {
        public static final Id<Feedback> ID = new Id<>(id("feedback"));
        public static final PacketCodec<RegistryByteBuf, Feedback> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Feedback::challengeId,
                PacketCodecs.STRING, Feedback::heard,
                PacketCodecs.FLOAT, Feedback::score,
                PacketCodecs.BOOLEAN, Feedback::lowConfidence,
                Feedback::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Abort(int challengeId, String reason) implements CustomPayload {
        public static final Id<Abort> ID = new Id<>(id("abort"));
        public static final PacketCodec<RegistryByteBuf, Abort> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Abort::challengeId,
                PacketCodecs.STRING, Abort::reason,
                Abort::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public enum Outcome { SUCCESS, FAIL, CANCELLED }

    /** challengeId -1 = a notice not tied to a running challenge (e.g. "no mic, item kept"). */
    public record End(int challengeId, int outcome, float score, int ticksLeft, int removed, String message)
            implements CustomPayload {
        public static final Id<End> ID = new Id<>(id("end"));
        public static final PacketCodec<RegistryByteBuf, End> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, End::challengeId,
                PacketCodecs.VAR_INT, End::outcome,
                PacketCodecs.FLOAT, End::score,
                PacketCodecs.VAR_INT, End::ticksLeft,
                PacketCodecs.VAR_INT, End::removed,
                PacketCodecs.STRING, End::message,
                End::new);
        public Outcome outcomeEnum() { return Outcome.values()[Math.max(0, Math.min(2, outcome))]; }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Session.ID, Session.CODEC);
        PayloadTypeRegistry.playS2C().register(Start.ID, Start.CODEC);
        PayloadTypeRegistry.playS2C().register(Feedback.ID, Feedback.CODEC);
        PayloadTypeRegistry.playS2C().register(End.ID, End.CODEC);
        PayloadTypeRegistry.playC2S().register(Status.ID, Status.CODEC);
        PayloadTypeRegistry.playC2S().register(Ready.ID, Ready.CODEC);
        PayloadTypeRegistry.playC2S().register(Attempt.ID, Attempt.CODEC);
        PayloadTypeRegistry.playC2S().register(Abort.ID, Abort.CODEC);
    }
}
