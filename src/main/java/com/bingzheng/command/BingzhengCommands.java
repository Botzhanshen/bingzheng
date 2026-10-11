package com.bingzheng.command;

import com.bingzheng.attachment.BingzhengAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 开发期调试命令 {@code /bingzhengmod yingwei}。
 *
 * <p>存在的意义：让「喝桂枝汤 → 营卫上涨 → 重启仍在」这条链路可以被人工验证，
 * 无需依赖任何 HUD。
 */
public final class BingzhengCommands {

    private BingzhengCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("bingzhengmod")
                        .then(CommandManager.literal("yingwei")
                                .executes(BingzhengCommands::query)
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("value",
                                                        IntegerArgumentType.integer(
                                                                BingzhengAttachments.YINGWEI_MIN,
                                                                BingzhengAttachments.YINGWEI_MAX))
                                                .executes(BingzhengCommands::set))))));
    }

    private static int query(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int value = BingzhengAttachments.getYingwei(player);
        context.getSource().sendFeedback(() -> Text.translatable(
                "command.bingzhengmod.yingwei.query", value, BingzhengAttachments.YINGWEI_MAX), false);
        return value;
    }

    private static int set(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int value = IntegerArgumentType.getInteger(context, "value");
        BingzhengAttachments.setYingwei(player, value);
        context.getSource().sendFeedback(() -> Text.translatable(
                "command.bingzhengmod.yingwei.set", value), true);
        return value;
    }
}
