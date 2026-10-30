package com.bingzheng.emotion;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 神耗 / 体耗的<b>服务端 → 客户端同步</b>（v0.9.1）。
 *
 * <p><b>不依赖受伤事件</b>：进世界（{@link ServerPlayConnectionEvents#JOIN}）、
 * 重生（{@link ServerPlayerEvents#AFTER_RESPAWN}）、切换游戏模式都会<b>主动推一次</b>；
 * 平时数值变化立即推，未变化每秒兜底 —— 因此切模式 / 重生后不会出现「条还在但数不刷新」。
 *
 * <p>只发给本人，多人在线不串台；包体只有两个 float。
 */
public final class EmotionHudSync {

    /** 通道名：{@code bingzhengmod:emotion_hud}。 */
    public static final Identifier CHANNEL = new Identifier("bingzhengmod", "emotion_hud");

    private static final Map<UUID, float[]> LAST_SENT = new HashMap<>();
    private static final Map<UUID, GameMode> LAST_MODE = new HashMap<>();

    private EmotionHudSync() {
    }

    /** 注册 JOIN / 重生 两条主动推送（由 {@link EmotionService#register} 调用）。 */
    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // 【v0.9.2】进世界【不再清零】：数值随附件持久化，大退重进保留。
            // 只有「新创建的世界」从 0 开始（EmotionState.fresh 的初始值就是 0）。
            // 这里只做一次主动推送，保证进世界第一帧就有数（不依赖受伤事件）。
            EmotionState state = EmotionAttachments.get(player);
            pushNow(player, state);
            LAST_MODE.put(player.getUuid(), player.interactionManager.getGameMode());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EmotionState state = EmotionAttachments.get(newPlayer);
            EmotionFatigue.reset(newPlayer, state);
            EmotionSleep.reset(newPlayer);
            EmotionAttachments.set(newPlayer, state);
            pushNow(newPlayer, state);
            LAST_MODE.put(newPlayer.getUuid(), newPlayer.interactionManager.getGameMode());
        });
    }

    /** 每 tick 调一次（由 {@link EmotionService}）；内部按间隔节流。 */
    public static void tick(MinecraftServer server, long tick) {
        // ① 游戏模式变化：立即推一次（同时让客户端重算「是否渲染」）
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            GameMode mode = player.interactionManager.getGameMode();
            if (LAST_MODE.get(player.getUuid()) != mode) {
                LAST_MODE.put(player.getUuid(), mode);
                pushNow(player, EmotionAttachments.get(player));
            }
        }
        if (tick % EmotionConfig.HUD_SYNC_INTERVAL != 0L) {
            return;
        }
        // ② 定时兜底 + 变化即推
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            EmotionState state = EmotionAttachments.get(player);
            float spirit = state.getSpiritConsumption();
            float body = state.getBodyConsumption();
            float[] last = LAST_SENT.get(player.getUuid());
            if (last != null && !EmotionHudRules.shouldSync(last[0], last[1], spirit, body)) {
                continue;
            }
            pushNow(player, state);
        }
    }

    /** 立即推送并记账。 */
    public static void pushNow(ServerPlayerEntity player, EmotionState state) {
        float spirit = state.getSpiritConsumption();
        float body = state.getBodyConsumption();
        send(player, spirit, body);
        LAST_SENT.put(player.getUuid(), new float[] { spirit, body });
    }

    /** 只发给本人。 */
    public static void send(ServerPlayerEntity player, float spirit, float body) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeFloat(spirit);
        buf.writeFloat(body);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /** 清理某玩家（离线）。 */
    public static void forget(UUID id) {
        LAST_SENT.remove(id);
        LAST_MODE.remove(id);
    }
}
