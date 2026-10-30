package com.bingzheng.wuzang;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
 * 五脏拓展的调试命令，统一挂在 {@code /bingzhengmod} 根下：
 * <pre>
 * /bingzhengmod wuzang                             查询五脏气血与虚实标记
 * /bingzhengmod wuzang set &lt;脏&gt; &lt;qi|blood&gt; &lt;值&gt;   直接设定（人工验收用）
 * /bingzhengmod wuzang add &lt;脏&gt; &lt;qi|blood&gt; &lt;增量&gt;
 * /bingzhengmod wuzang clear                       重置为平人基线
 * /bingzhengmod wuzang debug                       开关后台数值打印（OP）
 * </pre>
 *
 * <p>风格与情志拓展的 {@code /bingzhengmod emotion} 保持一致：
 * 查询与设定只发聊天栏文本、<b>不绘制任何 HUD</b>；
 * {@code wuzang debug} 额外要求 OP（v0.9.4 起去掉项目级调试开关，release 也能用）。
 *
 * <p><b>v1.08 指令重构</b>：原先独立的顶层 {@code wuzang_debug} 降级为 {@code wuzang debug} 子命令。
 *
 * <p>挂载方式同样是「先查再合并」，避免与内核、情志已注册的子命令冲突 —— 两边代码都不动。
 */
public final class WuzangCommands {

    private static final String PART_QI = "qi";
    private static final String PART_BLOOD = "blood";

    private WuzangCommands() {
    }

    /** 命令叶子节点的处理体。 */
    private interface CommandRunner {
        int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> wuzang = CommandManager.literal("wuzang")
                    .executes(WuzangCommands::query)
                    .then(CommandManager.literal("set")
                            .then(partArgument(false)))
                    .then(CommandManager.literal("add")
                            .then(partArgument(true)))
                    .then(CommandManager.literal("clear")
                            .executes(WuzangCommands::clear))
                    .then(CommandManager.literal("debug")
                            .requires(source -> source.hasPermissionLevel(2))   // v0.9.4: gate removed, OP only
                            .executes(WuzangCommands::toggleDebug))
                    .build();

            CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("bingzhengmod");
            try {
                if (root == null) {
                    dispatcher.register(CommandManager.literal("bingzhengmod").then(wuzang));
                } else {
                    root.addChild(wuzang);
                }
            } catch (Exception e) {
                Wuzang.LOGGER.warn("【病证·五脏】调试命令挂载失败（可能已有同名子命令）", e);
            }
        });
    }

    /** {@code <脏> <qi|blood> <值>} 三段参数。 */
    private static RequiredArgumentBuilder<ServerCommandSource, String> partArgument(boolean additive) {
        CommandRunner runner = additive ? WuzangCommands::add : WuzangCommands::set;
        return CommandManager.argument("zang", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(zangSuggestions(), builder))
                .then(CommandManager.argument("part", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                new String[] { PART_QI, PART_BLOOD }, builder))
                        .then(CommandManager.argument("value",
                                        FloatArgumentType.floatArg(-WuzangConfig.MAX, WuzangConfig.MAX))
                                .executes(ctx -> runner.run(ctx))));
    }

    private static String[] zangSuggestions() {
        String[] out = new String[WuzangType.COUNT];
        for (WuzangType type : WuzangType.values()) {
            out[type.index()] = type.key();
        }
        return out;
    }

    private static int query(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        WuzangState state = WuzangAttachments.get(player);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.header"), false);
        for (WuzangType type : WuzangType.values()) {
            context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.line",
                    type.zhName(), fmt(state.qiOf(type)), fmt(state.bloodOf(type))), false);
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.markers",
                state.markerSummary()), false);
        // 时辰行（v0.6.0）：这里显示的仍是**稳定值**，含波动的现值只在 wuzang debug 里看 ——
        // 4 条业务命令（set/add/clear/查询）的语义因此完全不变，波形不会干扰人工验收。
        long time = player.getWorld().getTimeOfDay();
        WuzangType sovereign = WuzangShichen.sovereignAt(time);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.shichen",
                WuzangShichen.name(time), sovereign == null ? "—" : sovereign.zhName()), false);
        // 参数对照表：告诉使用者合法输入值（中英语言文件里都有文案，不会显示裸 key）
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.usage"), false);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return apply(context, false);
    }

    private static int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return apply(context, true);
    }

    private static int apply(CommandContext<ServerCommandSource> context, boolean additive)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        WuzangType type = WuzangType.parse(StringArgumentType.getString(context, "zang"));
        if (type == null) {
            context.getSource().sendError(Text.translatable("command.bingzhengmod.wuzang.unknown"));
            return 0;
        }
        String part = StringArgumentType.getString(context, "part").toLowerCase(Locale.ROOT);
        boolean blood = PART_BLOOD.equals(part);
        if (!blood && !PART_QI.equals(part)) {
            context.getSource().sendError(Text.translatable("command.bingzhengmod.wuzang.unknown_part"));
            return 0;
        }
        float value = FloatArgumentType.getFloat(context, "value");
        WuzangState state = WuzangAttachments.get(player);
        float current = blood ? state.bloodOf(type) : state.qiOf(type);
        float next = additive ? current + value : value;
        if (blood) {
            state.setBlood(type, next);
        } else {
            state.setQi(type, next);
        }
        WuzangAttachments.set(player, state);
        float shown = blood ? state.bloodOf(type) : state.qiOf(type);
        context.getSource().sendFeedback(() -> Text.translatable(additive
                        ? "command.bingzhengmod.wuzang.added"
                        : "command.bingzhengmod.wuzang.set",
                type.zhName(), blood ? "血" : "气", fmt(shown)), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        WuzangState state = WuzangAttachments.get(player);
        state.resetToBaseline();
        WuzangAttachments.set(player, state);
        context.getSource().sendFeedback(() -> Text.translatable("command.bingzhengmod.wuzang.cleared"), false);
        return 1;
    }

    /**
     * 开关五脏调试打印（{@code wuzang debug}）。
     *
     * <p>v1.08：与 {@code emotion debug} 一样整段包 try/catch，
     * 把执行期异常转成中文回显而不是静默失败。
     */
    private static int toggleDebug(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            boolean on = WuzangDebugRecorder.toggle(player);
            context.getSource().sendFeedback(() -> Text.translatable(
                    on ? "command.bingzhengmod.wuzang_debug.on" : "command.bingzhengmod.wuzang_debug.off"), false);
            return on ? 1 : 0;
        } catch (RuntimeException e) {
            Wuzang.LOGGER.warn("【病证·五脏】wuzang debug 开关执行失败", e);
            String detail = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            context.getSource().sendError(
                    Text.translatable("command.bingzhengmod.wuzang_debug.failed", detail));
            return 0;
        }
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
