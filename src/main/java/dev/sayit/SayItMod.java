package dev.sayit;

import dev.sayit.command.SayItCommands;
import dev.sayit.config.SayItConfig;
import dev.sayit.config.WordOverrides;
import dev.sayit.net.SayItPayloads;
import dev.sayit.server.ChallengeManager;
import dev.sayit.server.PlayerWords;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SayItMod implements ModInitializer {
    public static final String MOD_ID = "sayit";
    public static final Logger LOG = LoggerFactory.getLogger("SayIt");

    @Override
    public void onInitialize() {
        SayItConfig.load();
        WordOverrides.load();
        PlayerWords.init();
        SayItPayloads.register();

        ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Status.ID, (p, ctx) -> ChallengeManager.onStatus(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Ready.ID, (p, ctx) -> ChallengeManager.onReady(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Attempt.ID, (p, ctx) -> ChallengeManager.onAttempt(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Abort.ID, (p, ctx) -> ChallengeManager.onAbort(ctx.player(), p));

        ServerTickEvents.END_SERVER_TICK.register(ChallengeManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ChallengeManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ChallengeManager.onDisconnect(handler.getPlayer()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity sp) ChallengeManager.onDeath(sp);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> ChallengeManager.onServerStopped());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> SayItCommands.register(dispatcher));

        LOG.info("Say It loaded - language {}, {}s per word", SayItConfig.get().language, SayItConfig.get().timeLimitSeconds);
    }
}
