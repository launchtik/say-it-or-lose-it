package dev.sayit.client;

import dev.sayit.SayItMod;
import dev.sayit.client.hud.ChallengeHud;
import dev.sayit.client.speech.SpeechController;
import dev.sayit.client.speech.SpeechUnavailableException;
import dev.sayit.config.SayItConfig;
import dev.sayit.core.LanguageNames;
import dev.sayit.core.WordMatcher;
import dev.sayit.net.SayItPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class SayItClient implements ClientModInitializer {
    private static final ChallengeHud HUD = new ChallengeHud();
    private static SpeechController speech;
    private static KeyBinding pushToTalk;
    private static boolean sessionActive;
    private static int retryCooldown;

    @Override
    public void onInitializeClient() {
        speech = new SpeechController(SpeechController.createRecognizer());
        pushToTalk = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sayit.push_to_talk",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyBinding.Category.create(Identifier.of(SayItMod.MOD_ID, "sayit"))));

        ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Session.ID, (p, ctx) -> onSession(p));
        ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Start.ID, (p, ctx) -> onStart(p));
        ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Feedback.ID, (p, ctx) -> onFeedback(p));
        ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.End.ID, (p, ctx) -> onEnd(p));

        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            speech.stop();
            HUD.clearActive();
            sessionActive = false;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> speech.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(SayItClient::tick);

        // In-world HUD layer; when a screen (inventory, crafting) is open we draw after the screen instead,
        // so the card stays on top exactly like in the video.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of(SayItMod.MOD_ID, "challenge"),
                (ctx, counter) -> {
                    if (sessionActive && MinecraftClient.getInstance().currentScreen == null)
                        HUD.render(ctx, counter.getTickProgress(false));
                });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                    if (sessionActive && client.world != null) HUD.render(ctx, delta);
                }));
    }

    // ------------------------------------------------------------------ packets

    private static void onSession(SayItPayloads.Session s) {
        sessionActive = s.enabled();
        if (!s.enabled()) return;
        ClientWords.clearCache();
        speech.prepare(s.language(), SayItClient::sendStatusLater);
    }

    private static void sendStatusLater() {
        MinecraftClient.getInstance().execute(() -> {
            if (!ClientPlayNetworking.canSend(SayItPayloads.Status.ID)) return;
            boolean ok = speech.state() == SpeechController.State.READY;
            ClientPlayNetworking.send(new SayItPayloads.Status(ok, ok ? "" : speech.error()));
            if (!ok) HUD.showError(ItemStack.EMPTY, speech.error());
        });
    }

    private static void onStart(SayItPayloads.Start p) {
        ItemStack icon = new ItemStack(Registries.ITEM.get(p.item()));
        String word = ClientWords.resolve(p.language(), p.translationKey(), p.customWord());
        ChallengeHud.Active a = new ChallengeHud.Active();
        a.id = p.challengeId();
        a.icon = icon;
        a.word = word;
        a.language = p.language();
        a.languageName = LanguageNames.english(p.language());
        a.limitTicks = p.timeLimitTicks();
        HUD.begin(a);
        String scoreAs;
        try {
            List<String> distractors = ClientWords.distractors(p.language(), word, SayItConfig.get().distractorCount);
            boolean openCheck = dev.sayit.core.Strictness.of(p.strictness()).wantsOpenCheck(p.language());
            scoreAs = speech.listen(word, distractors, openCheck, new SpeechController.Events() {
                @Override public void onUtterance(String text, float confidence, String openText) {
                    MinecraftClient.getInstance().execute(() -> sendAttempt(a, text, confidence, openText));
                }
                @Override public void onAbort(String reason) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (HUD.active() == a) ClientPlayNetworking.send(new SayItPayloads.Abort(a.id, reason));
                    });
                }
            });
        } catch (SpeechUnavailableException e) {
            ClientPlayNetworking.send(new SayItPayloads.Abort(a.id, e.getMessage()));
            return;
        }
        a.countingDown = true;
        ClientPlayNetworking.send(new SayItPayloads.Ready(a.id, word, scoreAs == null ? "" : scoreAs));
        a.scoreAs = scoreAs == null || scoreAs.isEmpty() ? word : scoreAs;
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.6f, 0.6f);
    }

    private static void sendAttempt(ChallengeHud.Active a, String text, float confidence, String openText) {
        if (HUD.active() != a) return;
        a.checking = true;
        ClientPlayNetworking.send(new SayItPayloads.Attempt(a.id, text, confidence, openText == null ? "" : openText));
    }

    private static void onFeedback(SayItPayloads.Feedback f) {
        ChallengeHud.Active a = HUD.active();
        if (a == null || a.id != f.challengeId()) return;
        a.checking = false;
        a.heard = f.heard();
        a.score = f.score();
        a.lowConfidence = f.lowConfidence();
        a.segments = WordMatcher.segments(a.word, f.heard(), a.language, SayItConfig.get().foldAccents);
        // words the recogniser cannot produce (dropped from scoring) must not be shown as mistakes
        if (!a.scoreAs.equals(a.word)) {
            java.util.Set<String> scored = new java.util.HashSet<>(java.util.Arrays.asList(a.scoreAs.split("\\s+")));
            java.util.List<WordMatcher.Segment> fixed = new java.util.ArrayList<>();
            int pos = 0;
            for (WordMatcher.Segment s : a.segments) {
                int start = pos; pos += s.text().length();
                String containing = wordAt(a.word, start);
                fixed.add(scored.contains(containing) ? s : new WordMatcher.Segment(s.text(), true));
            }
            a.segments = fixed;
        }
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
    }

    private static String wordAt(String text, int index) {
        int s = index, e = index;
        while (s > 0 && !Character.isWhitespace(text.charAt(s - 1))) s--;
        while (e < text.length() && !Character.isWhitespace(text.charAt(e))) e++;
        return text.substring(s, e);
    }

    private static void onEnd(SayItPayloads.End e) {
        ChallengeHud.Active a = HUD.active();
        boolean mine = a != null && a.id == e.challengeId();
        if (mine) speech.stop();
        ItemStack icon = mine ? a.icon : ItemStack.EMPTY;
        switch (e.outcomeEnum()) {
            case SUCCESS -> {
                if (!mine) return;
                HUD.showSuccess(icon, a.word, e.score(), e.ticksLeft());
                play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            }
            case FAIL -> {
                if (!mine) return;
                HUD.showFail(icon, a.word, e.removed());
                play(SoundEvents.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            }
            case CANCELLED -> {
                if (e.challengeId() != -1 && !mine) return;
                HUD.showError(icon, e.message());
                play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftClient mc) {
        if (!sessionActive || mc.world == null) return;
        int beep = HUD.tick(mc.isPaused());
        if (beep > 0) play(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1.4f + (3 - beep) * 0.2f, 0.7f);

        ChallengeHud.Active a = HUD.active();
        boolean ptt = SayItConfig.get().listenMode == SayItConfig.ListenMode.PUSH_TO_TALK;
        boolean held = ptt && isPushToTalkHeld(mc);
        speech.setPushToTalk(held);
        if (a != null && a.countingDown && !a.finishSent && a.elapsed >= a.limitTicks) {
            a.finishSent = true;
            speech.finishNow(); // last chance: whatever is mid-utterance gets scored
        }
        if (a != null) {
            a.status = a.checking ? "checking…" : ptt && !held ? "hold " + pushToTalk.getBoundKeyLocalizedText().getString() + " to speak" : "";
        }

        // mic indicator
        switch (speech.state()) {
            case LOADING -> {
                int dl = speech.downloadPercent();
                HUD.micText = dl >= 0 ? "model " + dl + "%" : "mic …";
                HUD.micColor = 0xFFA9ADB8;
            }
            case UNAVAILABLE -> {
                HUD.micText = speech.error().toLowerCase().contains("model") ? "no model" : "no mic";
                HUD.micColor = 0xFFFF5A6E;
                if (--retryCooldown <= 0) { retryCooldown = 200; speech.retry(SayItClient::sendStatusLater); }
            }
            case READY -> {
                boolean live = speech.isListening() && speech.isGateOpen();
                HUD.micText = ptt && speech.isListening() && !held ? "mic (hold " + pushToTalk.getBoundKeyLocalizedText().getString() + ")" : "mic on";
                HUD.micColor = live ? 0xFFFFFFFF : 0xFFA9ADB8;
            }
        }
        HUD.micLive = speech.isListening() && speech.isGateOpen();
        HUD.micLevel = speech.level();
    }

    /** Works with a screen open too (KeyBinding#isPressed does not update while the inventory is open). */
    private static boolean isPushToTalkHeld(MinecraftClient mc) {
        if (pushToTalk.isPressed()) return true;
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(pushToTalk);
        if (key.getCategory() != InputUtil.Type.KEYSYM || key.getCode() < 0) return false;
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    private static void play(net.minecraft.sound.SoundEvent e, float pitch, float volume) {
        if (SayItConfig.get().sounds) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(e, pitch, volume));
    }

    private static void play(net.minecraft.registry.entry.RegistryEntry<net.minecraft.sound.SoundEvent> e, float pitch, float volume) {
        play(e.value(), pitch, volume);
    }
}
