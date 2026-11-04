package dev.sayit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sayit.config.SayItConfig;
import dev.sayit.config.WordOverrides;
import dev.sayit.server.ChallengeManager;
import dev.sayit.server.PlayerWords;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /sayit status                 - your progress and speech status (everyone)
 * /sayit challenge              - ask the held item now, even if unlocked (op)
 * /sayit unlock                 - unlock the held item without saying it (op)
 * /sayit reset [players]        - forget unlocked words (op)
 * /sayit reload                 - reload config/sayit.json and sayit_words.json (op)
 * /sayit language [code]        - show or switch the language for everyone, e.g. es_es (op to switch)
 * /sayit languages              - list supported languages
 */
public final class SayItCommands {
    private SayItCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        java.util.function.Predicate<ServerCommandSource> op = CommandManager.<ServerCommandSource>requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);
        d.register(literal("sayit")
                .then(literal("status").executes(SayItCommands::status))
                .then(literal("challenge").requires(op).executes(SayItCommands::challenge))
                .then(literal("unlock").requires(op).executes(SayItCommands::unlock))
                .then(literal("reset").requires(op)
                        .executes(c -> reset(c, java.util.List.of(c.getSource().getPlayerOrThrow())))
                        .then(argument("targets", EntityArgumentType.players())
                                .executes(c -> reset(c, EntityArgumentType.getPlayers(c, "targets")))))
                .then(literal("reload").requires(op).executes(SayItCommands::reload))
                .then(literal("languages").executes(SayItCommands::languages))
                .then(literal("language").executes(SayItCommands::showLanguage)
                        .then(argument("code", com.mojang.brigadier.arguments.StringArgumentType.word()).requires(op)
                                .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                        dev.sayit.core.LanguageNames.all().keySet(), b))
                                .executes(SayItCommands::setLanguage))));
    }

    private static int status(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        SayItConfig cfg = SayItConfig.get();
        String speech = ChallengeManager.isClientReady(p) ? "ready"
                : "not ready" + (ChallengeManager.clientError(p).isEmpty() ? "" : " (" + ChallengeManager.clientError(p) + ")");
        c.getSource().sendFeedback(() -> Text.literal("[Say It] " + (cfg.enabled ? "enabled" : "disabled")
                + ", language " + cfg.language + ", " + cfg.timeLimitSeconds + "s, pass " + Math.round(cfg.passThreshold * 100)
                + "%. Unlocked words: " + PlayerWords.countUnlocked(p, cfg.language) + ". Speech: " + speech), false);
        return 1;
    }

    private static ItemStack held(ServerCommandSource src) throws CommandSyntaxException {
        ItemStack s = src.getPlayerOrThrow().getMainHandStack();
        if (s.isEmpty()) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Text.literal("Hold an item first")).create();
        return s;
    }

    private static int challenge(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ItemStack s = held(c.getSource());
        if (!ChallengeManager.forceChallenge(c.getSource().getPlayerOrThrow(), s.getItem())) {
            c.getSource().sendError(Text.literal("Speech is not ready on your client (see /sayit status)"));
            return 0;
        }
        return 1;
    }

    private static int unlock(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ItemStack s = held(c.getSource());
        String id = Registries.ITEM.getId(s.getItem()).toString();
        PlayerWords.unlock(c.getSource().getPlayerOrThrow(), SayItConfig.get().language, id);
        c.getSource().sendFeedback(() -> Text.literal("[Say It] Unlocked " + id), false);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> c, java.util.Collection<ServerPlayerEntity> players) {
        players.forEach(ChallengeManager::resetPlayer);
        c.getSource().sendFeedback(() -> Text.literal("[Say It] Reset " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int reload(CommandContext<ServerCommandSource> c) {
        String before = SayItConfig.get().language;
        boolean wasEnabled = SayItConfig.get().enabled;
        SayItConfig.load();
        WordOverrides.load();
        if (!before.equals(SayItConfig.get().language) || wasEnabled != SayItConfig.get().enabled)
            ChallengeManager.broadcastSession(c.getSource().getServer());
        c.getSource().sendFeedback(() -> Text.literal("[Say It] Config reloaded."), true);
        return 1;
    }

    private static int languages(CommandContext<ServerCommandSource> c) {
        java.util.Map<String, java.util.List<String>> byName = new java.util.TreeMap<>();
        dev.sayit.core.LanguageNames.all().forEach((code, e) -> byName.computeIfAbsent(e.name(), k -> new java.util.ArrayList<>()).add(code));
        StringBuilder sb = new StringBuilder("[Say It] " + byName.size() + " languages: ");
        byName.forEach((name, codes) -> sb.append(name).append(" (").append(String.join(", ", codes)).append("), "));
        c.getSource().sendFeedback(() -> Text.literal(sb.substring(0, sb.length() - 2)), false);
        return byName.size();
    }

    private static int showLanguage(CommandContext<ServerCommandSource> c) {
        String code = SayItConfig.get().language;
        c.getSource().sendFeedback(() -> Text.literal("[Say It] Language: " + dev.sayit.core.LanguageNames.english(code)
                + " (" + code + "). Change with /sayit language <code>"), false);
        return 1;
    }

    private static int setLanguage(CommandContext<ServerCommandSource> c) {
        String code = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "code").toLowerCase(java.util.Locale.ROOT);
        if (!dev.sayit.core.LanguageNames.isSupported(code)) {
            c.getSource().sendError(Text.literal("Unknown language '" + code + "'. See /sayit languages"));
            return 0;
        }
        SayItConfig cfg = SayItConfig.get();
        cfg.language = code;
        SayItConfig.save(cfg);
        ChallengeManager.broadcastSession(c.getSource().getServer());
        c.getSource().sendFeedback(() -> Text.literal("[Say It] Language set to " + dev.sayit.core.LanguageNames.english(code)
                + " (" + code + "). Clients are loading the speech model" + (cfg.autoDownloadModels ? " (downloading if needed)." : ".")), true);
        return 1;
    }
}
