package com.bingzheng.emotion;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 神耗 / 体耗的<b>专用调试命令组</b>（v0.9.3 建组，v1.08 指令重构并入顶层三组之一）。
 *
 * <pre>
 * /bingzhengmod fatigue                     打印一次详细快照 + 来源说明
 * /bingzhengmod fatigue set spirit &lt;值&gt;     直接设定神耗
 * /bingzhengmod fatigue set body &lt;值&gt;       直接设定体耗
 * /bingzhengmod fatigue reset               神耗与体耗清零
 * /bingzhengmod fatigue debug               开关「每 20 tick 打印一次」
 * </pre>
 *
 * <p>为什么单独成组：{@code emotion debug} 与五脏的 {@code wuzang debug} 都带
 * 「正式版本默认关闭」的属性开关（{@code -Dbingzhengmod.debug.console=true}），
 * 在 PCL2 里不加 JVM 参数时那些节点<b>直接不可用</b>（表现为「未知命令」）。
 * 验收疲劳体系需要一条不依赖该开关的轻量路径，因此本组：
 * <ul>
 *   <li>只要 <b>OP 2 级</b>就能用（不做属性门禁）；</li>
 *   <li>无参形式打印一次完整快照；</li>
 *   <li>{@code debug} 子命令开关周期打印。</li>
 * </ul>
 *
 * <p><b>v0.9.9 权限收紧</b>：{@code set} / {@code reset} 这些<b>写操作</b>也显式挂
 * {@code hasPermissionLevel(2)}（与 {@code debug} 一致）。虽然父节点 {@code fatigue}
 * 已带 OP 门槛、Brigadier 解析时会连带拦住子节点，但显式声明是为了防「日后有人为了只读查询
 * 放开父节点、顺手把写操作一起放开」——多人服里数值被人随手改掉，比命令少一条更难查。
 * 若日后要按正式版纪律隐藏它，把各处 {@code requires} 改成
 * {@code source.hasPermissionLevel(2) && EmotionDebugRecorder.isAvailable()} 即可。
 */
public final class EmotionFatigueCommands {

    /** 已开启周期打印的玩家。 */
    private static final Set<UUID> ENABLED = new HashSet<>();

    /** 周期打印间隔（tick）。 */
    private static final int PRINT_INTERVAL = 60;

    private EmotionFatigueCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> fatigue = CommandManager.literal("fatigue")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(EmotionFatigueCommands::printOnce)
                    .then(CommandManager.literal("set")
                            .requires(source -> source.hasPermissionLevel(2))   // v0.9.9: 写操作显式 OP
                            .then(CommandManager.literal("spirit")
                                    .then(CommandManager.argument("value",
                                                    FloatArgumentType.floatArg(EmotionConfig.CONSUMPTION_MIN,
                                                            EmotionConfig.CONSUMPTION_MAX))
                                            .executes(ctx -> setConsumption(ctx, true))))
                            .then(CommandManager.literal("body")
                                    .then(CommandManager.argument("value",
                                                    FloatArgumentType.floatArg(EmotionConfig.CONSUMPTION_MIN,
                                                            EmotionConfig.CONSUMPTION_MAX))
                                            .executes(ctx -> setConsumption(ctx, false)))))
                    .then(CommandManager.literal("reset")
                            .requires(source -> source.hasPermissionLevel(2))   // v0.9.9: 写操作显式 OP
                            .executes(EmotionFatigueCommands::reset))
                    .then(CommandManager.literal("debug")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(EmotionFatigueCommands::toggle))
                    .build();
            CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("bingzhengmod");
            try {
                if (root == null) {
                    dispatcher.register(CommandManager.literal("bingzhengmod").then(fatigue));
                } else {
                    root.addChild(fatigue);
                }
            } catch (Exception e) {
                LoggerFactory.getLogger("bingzhengmod")
                        .warn("【病证·情志】疲劳调试命令挂载失败（可能已有同名子命令）", e);
            }
        });
        // v0.9.4：周期打印改走**独立的 END_SERVER_TICK**，不再依赖 tickPlayer ——
        // 否则 tickPlayer 里任何一处先抛异常，都会被外层 catch 吞掉并跳过打印（表现为「只显示一次」）。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % PRINT_INTERVAL != 0L) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!ENABLED.contains(player.getUuid())) {
                    continue;
                }
                try {
                    send(player);
                } catch (Throwable t) {
                    LoggerFactory.getLogger("bingzhengmod").error("【病证·情志】疲劳调试打印失败，已自动关闭 fatigue debug", t);
                    ENABLED.remove(player.getUuid());
                }
            }
        });
    }

    /** 开关周期打印（{@code fatigue debug}）。 */
    private static int toggle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        boolean on;
        if (ENABLED.remove(player.getUuid())) {
            on = false;
        } else {
            ENABLED.add(player.getUuid());
            on = true;
        }
        context.getSource().sendFeedback(() -> Text.translatable(
                on ? "command.bingzhengmod.fatigue_debug.on" : "command.bingzhengmod.fatigue_debug.off"),
                false);
        if (on) {
            send(player);
        }
        return on ? 1 : 0;
    }

    /** 打印一次（{@code fatigue} 无参形式）。 */
    private static int printOnce(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        send(player);
        context.getSource().sendFeedback(
                () -> Text.translatable("command.bingzhengmod.fatigue.note"), false);
        context.getSource().sendFeedback(
                () -> Text.translatable("command.bingzhengmod.fatigue.usage"), false);
        return 1;
    }

    /**
     * 直接设定神耗/体耗（{@code fatigue set spirit|body}）。
     *
     * <p>v1.08 由 {@code /bingzhengmod emotion spirit|body} 迁来：神耗/体耗属于疲劳体系，
     * 挂在 fatigue 组下语义才一致，emotion 组只留七情本身。
     */
    private static int setConsumption(CommandContext<ServerCommandSource> context, boolean spirit)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        float value = FloatArgumentType.getFloat(context, "value");
        EmotionState state = EmotionAttachments.get(player);
        if (spirit) {
            state.setSpiritConsumption(value);
        } else {
            state.setBodyConsumption(value);
        }
        EmotionAttachments.set(player, state);
        context.getSource().sendFeedback(() -> Text.translatable(
                spirit ? "command.bingzhengmod.emotion.spirit_set" : "command.bingzhengmod.emotion.body_set",
                fmt(value)), false);
        return 1;
    }

    /** 神耗与体耗清零（{@code fatigue reset}）。 */
    private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionState state = EmotionAttachments.get(player);
        state.setSpiritConsumption(0.0F);
        state.setBodyConsumption(0.0F);
        EmotionAttachments.set(player, state);
        context.getSource().sendFeedback(
                () -> Text.translatable("command.bingzhengmod.fatigue.reset"), false);
        return 1;
    }

    /** 供服务端每 tick 调用：开着的玩家按间隔打印。 */
    public static void tick(ServerPlayerEntity player, long tick) {
        if (tick % PRINT_INTERVAL == 0L && ENABLED.contains(player.getUuid())) {
            send(player);
        }
    }

    private static void send(ServerPlayerEntity player) {
        EmotionFatigue.Snapshot snap = EmotionFatigue.snapshot(player);
        for (String line : FatigueText.lines(snap)) {
            player.sendMessage(Text.literal(line), false);
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 清理离线玩家。 */
    public static void prune(Set<UUID> online) {
        ENABLED.retainAll(online);
    }

    /** 周期打印间隔（自检用）。 */
    public static int printInterval() {
        return PRINT_INTERVAL;
    }

    /** 是否开着周期打印（自检/调试用）。 */
    public static boolean isEnabled(ServerPlayerEntity player) {
        return ENABLED.contains(player.getUuid());
    }
}
