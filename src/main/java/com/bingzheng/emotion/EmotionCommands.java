package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * 情志拓展的<b>调试命令</b>。
 *
 * <p>存在的意义只有一个：让这套「后台隐形运行」的数值能够被人工验收。
 * <b>它不绘制任何 HUD 或进度条</b>，只在聊天栏输出文本，
 * 因此不违反「不给玩家看情绪数值 UI」的约束。
 *
 * <p><b>v1.08 指令重构</b>：顶层命令由 6 条（emotion / emotion_debug / fatigue /
 * fatigue_debug / wuzang / wuzang_debug）收为 3 组，调试开关降级为各组子命令
 * {@code debug}；神耗/体耗的写入挪到 {@code /bingzhengmod fatigue set spirit|body}。
 * <pre>
 * /bingzhengmod emotion                 查询七情与神耗/体耗（无参）
 * /bingzhengmod emotion set &lt;情&gt; &lt;值&gt;
 * /bingzhengmod emotion add &lt;情&gt; &lt;增量&gt;
 * /bingzhengmod emotion clear
 * /bingzhengmod emotion debug           开关后台数值打印（OP 2 级）
 * </pre>
 *
 * <p>命令挂在和内核相同的 {@code /bingzhengmod} 根下，但采用「先查再合并」的写法，
 * 避免与内核已注册的 yingwei 子命令冲突 —— 内核代码零改动。
 */
public final class EmotionCommands {

    private EmotionCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> emotion = CommandManager.literal("emotion")
                    .executes(EmotionCommands::query)
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("emotion", StringArgumentType.word())
                                    .suggests((ctx, builder) -> CommandSource.suggestMatching(suggestions(), builder))
                                    .then(CommandManager.argument("value",
                                                    FloatArgumentType.floatArg(EmotionConfig.EMOTION_MIN,
                                                            EmotionConfig.EMOTION_MAX))
                                            .executes(EmotionCommands::setEmotion))))
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("emotion", StringArgumentType.word())
                                    .suggests((ctx, builder) -> CommandSource.suggestMatching(suggestions(), builder))
                                    .then(CommandManager.argument("value",
                                                    FloatArgumentType.floatArg(-EmotionConfig.EMOTION_MAX,
                                                            EmotionConfig.EMOTION_MAX))
                                            .executes(EmotionCommands::addEmotion))))
                    .then(CommandManager.literal("clear")
                            .executes(EmotionCommands::clear))
                    .then(CommandManager.literal("debug")
                            .requires(source -> source.hasPermissionLevel(2))   // v0.9.4: debug gate removed, OP only
                            .executes(EmotionCommands::toggleDebug))
                    .build();

            CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("bingzhengmod");
            try {
                if (root == null) {
                    dispatcher.register(CommandManager.literal("bingzhengmod").then(emotion));
                } else {
                    root.addChild(emotion);
                }
            } catch (Exception e) {
                BingzhengMod.LOGGER.warn("【病证·情志】调试命令挂载失败（可能已有同名子命令）", e);
            }
            CommandNode<ServerCommandSource> mounted = dispatcher.getRoot().getChild("bingzhengmod");
            BingzhengMod.LOGGER.info("【病证·情志】命令挂载检查：root={} emotion={} emotion.debug={}",
                    mounted != null,
                    mounted != null && mounted.getChild("emotion") != null,
                    mounted != null && mounted.getChild("emotion") != null
                            && mounted.getChild("emotion").getChild("debug") != null);
        });
    }

    private static String[] suggestions() {
        String[] out = new String[EmotionType.COUNT];
        for (EmotionType type : EmotionType.values()) {
            out[type.index()] = type.name().toLowerCase(Locale.ROOT);
        }
        return out;
    }

    /**
     * 按英文标识符解析情志名（如 {@code anger}）。
     *
     * <p><b>为什么没有中文分支（v0.4.2 清理）：</b>命令参数用的是 Brigadier 的
     * {@code StringArgumentType.word()}，其字符白名单只有 {@code 0-9 A-Z a-z _ - . +}
     * （javap 实测）。中文名根本传递不到这里，原先的中文匹配分支是死代码。
     */
    private static EmotionType parse(String raw) {
        for (EmotionType type : EmotionType.values()) {
            if (type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }

    private static int query(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionState state = EmotionAttachments.get(player);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.header"), false);
        for (EmotionType type : EmotionType.values()) {
            context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.line",
                    type.zhName(), fmt(state.emotion(type)),
                    fmt(EmotionRhythm.display(state, type)), fmt(state.baselineOf(type))), false);
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.consumption",
                fmt(state.getSpiritConsumption()), fmt(state.getBodyConsumption())), false);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.misc",
                state.getCooldownDelirium(), state.getMeditationNightCounter(),
                state.getMeditationProgress(), state.isDeliriumActive() ? "on" : "off"), false);
        // 参数对照表：告诉使用者合法输入值（中英语言文件里都有文案，不会显示裸 key）
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.usage"), false);
        return 1;
    }

    private static int setEmotion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionType type = parse(StringArgumentType.getString(context, "emotion"));
        if (type == null) {
            context.getSource().sendError(Text.translatable("command.bingzhengmod.emotion.unknown"));
            return 0;
        }
        float value = FloatArgumentType.getFloat(context, "value");
        EmotionState state = EmotionAttachments.get(player);
        state.setEmotion(type, value);
        EmotionAttachments.set(player, state);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.set",
                type.zhName(), fmt(value)), false);
        return 1;
    }

    private static int addEmotion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionType type = parse(StringArgumentType.getString(context, "emotion"));
        if (type == null) {
            context.getSource().sendError(Text.translatable("command.bingzhengmod.emotion.unknown"));
            return 0;
        }
        float delta = FloatArgumentType.getFloat(context, "value");
        EmotionService.addEmotion(player, type, delta);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.added",
                type.zhName(), fmt(delta)), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionService.clearDisturbances(player);
        player.removeStatusEffect(EmotionEffects.SHEN_RAO);
        EmotionState state = EmotionAttachments.get(player);
        state.setCooldownDelirium(0);
        state.setDeliriumActive(false);
        for (EmotionType type : EmotionType.values()) {
            state.setEmotion(type, state.baselineOf(type));
        }
        EmotionAttachments.set(player, state);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.emotion.cleared"), false);
        return 1;
    }

    /**
     * 开关情志调试打印（{@code emotion debug}）。
     *
     * <p>v1.08：整段包 try/catch。节点已被证实注册成功（日志 mounted 行），
     * 但实机上仍偶发「执行期异常导致整条命令静默失败」，因此把异常转成中文回显，
     * 至少让玩家看到「为什么没生效」，而不是什么都没发生。
     */
    private static int toggleDebug(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            boolean on = EmotionDebugRecorder.toggle(player);
            context.getSource().sendFeedback(() -> Text.translatable(
                    on ? "command.bingzhengmod.emotion_debug.on" : "command.bingzhengmod.emotion_debug.off"), false);
            return on ? 1 : 0;
        } catch (RuntimeException e) {
            BingzhengMod.LOGGER.warn("【病证·情志】emotion debug 开关执行失败", e);
            String detail = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            context.getSource().sendError(
                    Text.translatable("command.bingzhengmod.emotion_debug.failed", detail));
            return 0;
        }
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
