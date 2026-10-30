package com.bingzheng.emotion;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 神耗 / 体耗的<b>运行期引擎 v2</b>（v0.9.2）。
 *
 * <p>体耗主来源是<b>饥饿值损失</b>（每损失 1 点 +2），辅以挖方块 / 疾跑 / 游泳距离；
 * 神耗主来源是<b>打开加工或容器 GUI</b>（附魔台双倍、10 秒防刷），次来源是夜间清醒（每现实分钟 +1）。
 * 静止时每 10 现实分钟 −5；睡到天亮归 0；死亡重生归 0。
 *
 * <p><b>数值持久化</b>在 {@link EmotionState} 的 Codec 里（本类只管变化），
 * 因此大退重进会保留；只有新世界从 0 开始。
 */
public final class EmotionFatigue {

    private static final Map<UUID, Track> STATE = new HashMap<>();

    private EmotionFatigue() {
    }

    static final class Track {
        private int blocksBroken;
        private double sprintMeters;
        private double swimMeters;
        private int nightTicks;
        private int idleTicks;
        private int hungerLevel = -1;
        private float sleptTicksTonight;
        private boolean wasNight;
        private Vec3d lastPos;
        /** 最近一次被计入神耗的 GUI 方块路径（v1.08：替代每 tick 读 currentScreenHandler）。 */
        private String lastGui = "—";
        /** v0.9.11 来源账本（只记账，不参与判定）。 */
        private final FatigueLedger ledger = new FatigueLedger();
        private final Map<String, Long> guiAwards = new java.util.HashMap<>();
    }

    /** 破坏方块（后置监听，只计数）。 */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Track track = STATE.computeIfAbsent(serverPlayer.getUuid(), key -> new Track());
                track.blocksBroken++;
            }
        });
    }

    /** 兼容旧调用点：专注方块的 GUI 由 currentScreenHandler 统一检测，这里不再单独计。 */
    public static void onFocusBlock(ServerPlayerEntity player) {
        // v0.9.2：神耗改为「打开 GUI 计点」，由 tick 里的 ScreenHandler 变化检测统一处理
    }

    /** 每 tick 调一次（服务端 tick，实体生命周期回调之外）。 */
    public static boolean tick(ServerPlayerEntity player, EmotionState state, long tick) {
        Track track = STATE.computeIfAbsent(player.getUuid(), key -> new Track());
        long now = player.getWorld().getTime();
        boolean sleeping = player.isSleeping();
        boolean active = false;
        boolean changed = false;
        float spiritMul = EmotionSleep.spiritMultiplier(player) * EmotionSleep.sunMultiplier(player);
        float bodyMul = EmotionSleep.bodyMultiplier(player);

        // ① 体耗主来源：饥饿值下降
        int food = player.getHungerManager().getFoodLevel();
        if (track.hungerLevel >= 0 && food < track.hungerLevel) {
            float points = EmotionFatigueRules.hungerLossPoints(track.hungerLevel - food);
            if (points > 0.0F) {
                float gained = points * bodyMul;
                state.addBodyConsumption(gained);
                track.ledger.addBody(FatigueLedger.Body.HUNGER, gained);
                changed = true;
                active = true;
            }
        }
        track.hungerLevel = food;

        // ② 体耗辅助：挖方块（每 10 个 +1）
        int blockPoints = EmotionFatigueRules.blockPoints(track.blocksBroken);
        if (blockPoints > 0) {
            track.blocksBroken -= blockPoints * EmotionFatigueRules.BLOCKS_PER_POINT;
            float gained = blockPoints * bodyMul;
            state.addBodyConsumption(gained);
            track.ledger.addBody(FatigueLedger.Body.BLOCKS, gained);
            changed = true;
            active = true;
        }

        // ③ 体耗辅助：疾跑 / 游泳距离（v0.9.5：只取 x/z 水平位移，排除浮沉；游泳只认「在水中」）
        Vec3d pos = player.getPos();
        if (track.lastPos != null) {
            double dx = pos.x - track.lastPos.x;
            double dz = pos.z - track.lastPos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.0D && distance < 10.0D) {
                if (player.isSprinting()) {
                    track.sprintMeters += distance;
                    active = true;
                }
                if (isInWater(player)) {
                    track.swimMeters += distance;
                    active = true;
                }
            }
        }
        track.lastPos = pos;
        int sprintPoints = EmotionFatigueRules.distancePoints(track.sprintMeters);
        if (sprintPoints > 0) {
            track.sprintMeters -= (double) sprintPoints * EmotionFatigueRules.DISTANCE_PER_POINT;
            float gained = sprintPoints * bodyMul;
            state.addBodyConsumption(gained);
            track.ledger.addBody(FatigueLedger.Body.SPRINT, gained);
            changed = true;
            active = true;
        }
        int swimPoints = EmotionFatigueRules.distancePoints(track.swimMeters);
        if (swimPoints > 0) {
            track.swimMeters -= (double) swimPoints * EmotionFatigueRules.DISTANCE_PER_POINT;
            float gained = swimPoints * bodyMul;
            state.addBodyConsumption(gained);
            track.ledger.addBody(FatigueLedger.Body.SWIM, gained);
            changed = true;
            active = true;
        }

        // ④ 神耗次来源：夜间清醒，每现实分钟 +1
        if (!sleeping && EmotionFatigueRules.isNightWindow(player.getWorld().getTimeOfDay())) {
            track.nightTicks++;
            active = true;
            int nightPoints = EmotionFatigueRules.nightPoints(track.nightTicks);
            if (nightPoints > 0) {
                track.nightTicks -= nightPoints * EmotionFatigueRules.TICKS_PER_REAL_MINUTE;
                float gained = nightPoints * EmotionFatigueRules.NIGHT_POINT_PER_MINUTE * spiritMul;
                state.addSpiritConsumption(gained);
                track.ledger.addSpirit(FatigueLedger.Spirit.NIGHT, gained);
                changed = true;
            }
        }

        // ⑤ 神耗主来源：由 UseBlockCallback -> onUseBlock 处理（v0.9.7）

        // ⑥ 自然回落：完全静止每 10 现实分钟 −5
        if (active) {
            track.idleTicks = 0;
        } else {
            track.idleTicks++;
            if (track.idleTicks >= EmotionFatigueRules.IDLE_DECAY_TICKS) {
                track.idleTicks = 0;
                state.setSpiritConsumption(EmotionFatigueRules.idleDecay(state.getSpiritConsumption()));
                state.setBodyConsumption(EmotionFatigueRules.idleDecay(state.getBodyConsumption()));
                changed = true;
            }
        }

        // ⑦ 睡眠结算（v0.9.5）交给 EmotionSleep：恢复梯度 + 睡眠债 + 日照
        return changed;
    }

    private static final java.util.Set<String> GUI_BLOCKS = java.util.Set.of(
            "crafting_table", "furnace", "blast_furnace", "smoker", "stonecutter",
            "smithing_table", "loom", "cartography_table", "fletching_table",
            "grindstone", "enchanting_table");

    /** 打开加工 / 容器类方块时计神耗（v0.9.7，替代每 tick 读 currentScreenHandler）。 */
    public static void onUseBlock(ServerPlayerEntity player, net.minecraft.block.BlockState blockState) {
        String path = net.minecraft.registry.Registries.BLOCK.getId(blockState.getBlock()).getPath();
        if (!GUI_BLOCKS.contains(path)) {
            return;
        }
        Track track = STATE.computeIfAbsent(player.getUuid(), key -> new Track());
        long now = player.getWorld().getTime();
        if (!EmotionFatigueRules.canAwardGui(now, track.guiAwards.getOrDefault(path, Long.MIN_VALUE))) {
            return;
        }
        track.guiAwards.put(path, now);
        track.lastGui = path;
        float gained = EmotionFatigueRules.guiPoints("enchanting_table".equals(path), false)
                * EmotionSleep.spiritMultiplier(player);
        EmotionAttachments.get(player).addSpiritConsumption(gained);
        track.ledger.addSpirit(FatigueLedger.Spirit.GUI, gained);
    }

    /** v0.9.11：外部来源（神扰、五脏回写）记一笔账，只影响调试输出。 */
    public static void noteSpirit(ServerPlayerEntity player, FatigueLedger.Spirit source, double amount) {
        STATE.computeIfAbsent(player.getUuid(), key -> new Track()).ledger.addSpirit(source, amount);
    }

    /** v0.9.11：取该玩家当前账本（只读用途）。 */
    public static FatigueLedger ledgerOf(UUID id) {
        Track track = STATE.get(id);
        return track == null ? new FatigueLedger() : track.ledger;
    }

    private static boolean isInWater(ServerPlayerEntity player) {
        // v0.9.5：不再依赖 isSwimming()（1.20.1 只有冲刺入水瞬间为真）与 isOnGround()（浅水踩底会误判）
        return player.isTouchingWater() || player.isSwimming();
    }

    /** 死亡重生：归 0（任务单：重生归 0；进世界不再重置，改由 Codec 持久化）。 */
    public static boolean reset(ServerPlayerEntity player, EmotionState state) {
        boolean changed = state.getSpiritConsumption() != 0.0F || state.getBodyConsumption() != 0.0F;
        state.setSpiritConsumption(0.0F);
        state.setBodyConsumption(0.0F);
        Track track = STATE.computeIfAbsent(player.getUuid(), key -> new Track());
        track.blocksBroken = 0;
        track.sprintMeters = 0.0D;
        track.swimMeters = 0.0D;
        track.nightTicks = 0;
        track.idleTicks = 0;
        track.sleptTicksTonight = 0.0F;
        track.guiAwards.clear();
        return changed;
    }

    public static void forget(UUID id) {
        STATE.remove(id);
    }

    public static void prune(java.util.Set<UUID> online) {
        STATE.keySet().retainAll(online);
    }

    /**
     * 调试快照：把「每个来源累计到哪了」一次性摊开，供 fatigue 调试命令打印。
     *
     * <p>v1.08 起补三项：睡眠债、当日日照累计 tick、昨夜是否通宵。
     * <b>不再读取 currentScreenHandler</b> —— 那条读取会在服务端触发
     * {@code ScreenHandlerType.create} 抛 {@code UnsupportedOperationException}（0.9.5 实机崩溃根因），
     * GUI 来源改为在 {@link #onUseBlock} 里留痕。
     */
    public record Snapshot(int hunger, int blocksBroken, int sprintMeters, int swimMeters,
                           int nightTicks, int idleTicks, boolean sleeping, String gui,
                           int debt, int sunTicks, boolean overnight,
                           float spirit, float body, FatigueLedger ledger) {
    }

    /** 取当前快照（只读，不改变任何状态）。 */
    public static Snapshot snapshot(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        int debt = EmotionSleep.debt(player);
        int sunTicks = EmotionSleep.sunTicks(player);
        boolean overnight = EmotionSleep.overnight(player);
        EmotionState state = EmotionAttachments.get(player);
        float spirit = state.getSpiritConsumption();
        float body = state.getBodyConsumption();
        if (track == null) {
            return new Snapshot(-1, 0, 0, 0, 0, 0, player.isSleeping(), "—",
                    debt, sunTicks, overnight, spirit, body, new FatigueLedger());
        }
        return new Snapshot(track.hungerLevel, track.blocksBroken,
                (int) Math.round(track.sprintMeters), (int) Math.round(track.swimMeters),
                track.nightTicks, track.idleTicks, player.isSleeping(), track.lastGui,
                debt, sunTicks, overnight, spirit, body, track.ledger);
    }

    /** 调试摘要。 */
    public static String describe(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        if (track == null) {
            return "—";
        }
        return "块" + track.blocksBroken + " 跑" + Math.round(track.sprintMeters) + "m 泳"
                + Math.round(track.swimMeters) + "m 夜" + track.nightTicks + "t 静" + track.idleTicks + "t";
    }
}
