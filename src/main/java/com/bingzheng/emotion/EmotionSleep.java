package com.bingzheng.emotion;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 睡眠恢复梯度 + 睡眠债 + 日光调摄的<b>运行期状态</b>（v0.9.5）。纯规则见 {@link EmotionSleepRules}。
 *
 * <p>只认「本人上床且睡到天亮」：没上床、半夜被吵醒、中途下床一律不恢复、也不结算。
 */
public final class EmotionSleep {

    private static final Map<UUID, Track> STATE = new HashMap<>();

    private EmotionSleep() {
    }

    static final class Track {
        private boolean wasSleeping;
        private long sleepStartTod = -1L;
        private boolean wasNight;
        private boolean settledTonight;
        private int debt;
        private boolean overnight;
        private int sunTicks;
        private boolean sunReliefDone;
        private boolean wasDay;
        /** v0.9.10：本夜「在线且未睡」累计 tick，天亮结算时用于分档计债，结算后清零。 */
        private int nightTicksAwake;
    }

    /** 当前睡眠债。 */
    public static int debt(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        return track == null ? 0 : EmotionSleepRules.clampDebt(track.debt);
    }

    /** 神耗上涨速率倍率（睡眠债驱动）。 */
    public static float spiritMultiplier(ServerPlayerEntity player) {
        return EmotionSleepRules.spiritRate(debt(player));
    }

    /** 体耗上涨速率倍率（通宵次日 ×1.5）。 */
    public static float bodyMultiplier(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        return EmotionSleepRules.bodyRate(track != null && track.overnight);
    }

    /** 日照期间的神耗速率倍率（白天 + 头顶可见天空才生效）。 */
    public static float sunMultiplier(ServerPlayerEntity player) {
        Track track = state(player);
        if (track.sunTicks < EmotionSleepRules.SUN_SLOW_START_TICKS) {
            return 1.0F;   // 晒不够 1 游戏小时：不减速
        }
        boolean applies = EmotionSleepRules.sunlightApplies(
                player.getWorld().isDay(),
                player.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, player.getBlockPos()));
        return EmotionSleepRules.sunRate(applies, player.getWorld().isRaining()
                || player.getWorld().isThundering());
    }

    /** 当日日照累计 tick（白天 + 头顶可见天空才累计；调试用）。 */
    public static int sunTicks(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        return track == null ? 0 : track.sunTicks;
    }

    /** 昨夜是否通宵（调试用：通宵次日体耗 ×1.5）。 */
    public static boolean overnight(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        return track != null && track.overnight;
    }

    /** 每 tick 调一次：记录入睡时刻、结算恢复与债、累计日照。 */
    public static boolean tick(ServerPlayerEntity player, EmotionState state) {
        Track track = state(player);
        boolean changed = false;
        long tod = player.getWorld().getTimeOfDay();
        boolean sleeping = player.isSleeping();

        // ① 入睡瞬间记时刻（只认本人上床）
        if (sleeping && !track.wasSleeping) {
            track.sleepStartTod = tod;
            track.settledTonight = false;
        }
        // ② 中途下床只在天黑时作废；若是自然睡醒（已天亮），保留入睡时刻交给 ③ 结算
        if (!sleeping && track.wasSleeping && !player.getWorld().isDay()) {
            track.sleepStartTod = -1L;
        }
        track.wasSleeping = sleeping;

        // ③ 天亮结算（每人每晚一次）：睡了的按梯度恢复；没睡的记通宵
        boolean night = !player.getWorld().isDay();
        if (track.wasNight && !night && !track.settledTonight) {
            track.settledTonight = true;
            boolean slept = track.sleepStartTod >= 0L;
            double hours = slept ? EmotionSleepRules.sleepHours(track.sleepStartTod) : 0.0;
            if (slept) {
                double rate = EmotionSleepRules.recoveryRate(hours);
                state.setSpiritConsumption(EmotionSleepRules.applyRecovery(state.getSpiritConsumption(), rate));
                state.setBodyConsumption(EmotionSleepRules.applyRecovery(state.getBodyConsumption(), rate));
            }
            // v0.9.10：睡了按小时档；没睡按「本夜在线且未睡」的累计时长分档（通宵 +3 / 熬夜 +1 / 中途加入 0）
            int delta = slept
                    ? EmotionSleepRules.sleptDebtDelta(hours)
                    : EmotionSleepRules.unsleptDebtDelta(track.nightTicksAwake);
            track.debt = EmotionSleepRules.clampDebt(track.debt + delta);
            // 体耗 ×1.5 只给真通宵（整夜在场且没睡），熬夜一段不享受这笔「通宵账」
            track.overnight = !slept
                    && track.nightTicksAwake >= EmotionSleepRules.OVERNIGHT_MIN_NIGHT_TICKS;
            track.sunReliefDone = false;
            track.sunTicks = 0;
            track.sleepStartTod = -1L;
            track.nightTicksAwake = 0;
            changed = true;
        }
        track.wasNight = night;

        // ③b v0.9.10：累计「本夜在线且未睡」的 tick —— 天亮结算时按它分档计债
        if (night && !sleeping) {
            track.nightTicksAwake++;
        }

        // ④ 日照累计：白天 + 可见天空 → 满 10 现实分钟抵 1 点债（每天最多 1 点）
        boolean sunApplies = EmotionSleepRules.sunlightApplies(player.getWorld().isDay(),
                player.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, player.getBlockPos()));
        if (sunApplies) {
            track.sunTicks++;
            if (!track.sunReliefDone && track.sunTicks >= EmotionSleepRules.SUN_DEBT_RELIEF_TICKS) {
                track.sunReliefDone = true;
                if (track.debt > 0) {
                    track.debt = EmotionSleepRules.clampDebt(track.debt - 1);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Track state(ServerPlayerEntity player) {
        return STATE.computeIfAbsent(player.getUuid(), key -> new Track());
    }

    /** 死亡重生：债与通宵标记保留（债是「身体记账」），日照进度重置。 */
    public static void reset(ServerPlayerEntity player) {
        Track track = state(player);
        track.sunTicks = 0;
        track.sleepStartTod = -1L;
        track.overnight = false;
        track.wasSleeping = false;
    }

    public static void forget(UUID id) {
        STATE.remove(id);
    }

    public static void prune(java.util.Set<UUID> online) {
        STATE.keySet().retainAll(online);
    }

    /** 调试摘要。 */
    public static String describe(ServerPlayerEntity player) {
        Track track = STATE.get(player.getUuid());
        if (track == null) {
            return "债0";
        }
        return "债" + track.debt + (track.overnight ? " 通宵" : "")
                + " 本夜未睡" + track.nightTicksAwake + "t 日照" + track.sunTicks + "t";
    }
}
