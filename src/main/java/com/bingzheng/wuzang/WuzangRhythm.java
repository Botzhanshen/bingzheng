package com.bingzheng.wuzang;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Arrays;
import java.util.Random;

/**
 * 时辰波动的<b>运行期驱动</b>：全服共用一份「波动偏移」。
 *
 * <p><b>为什么是全服一份、而不是每玩家一份</b>：波动只取决于世界时间与随机数，
 * 与玩家无关；共用一份既省内存，也不会出现「同一世界里两个人看到不同时辰」的怪事。
 *
 * <p><b>为什么不落盘</b>：波动是显示层的起伏，不是玩家的体质数据。
 * 服务器重启后偏移从 0 平滑逼近到当前时辰目标即可，因此
 * <b>存档格式一个字节都没动</b>（{@link WuzangState} 的 Codec 保持原样）。
 *
 * <p><b>三层各司其职</b>：
 * <ul>
 *   <li>{@link WuzangShichen} —— 世界时间 → 目标偏移（纯数学）；</li>
 *   <li>{@link WuzangRules#approach} —— 变速到达一步（纯数学，绝不越过目标）；</li>
 *   <li>本类 —— 持有状态、每 tick 推进、给显示层提供「现值」。</li>
 * </ul>
 *
 * <p><b>本类产出的任何数字都不参与虚实判定、不写回附件</b>：判定只看
 * {@link WuzangState} 里的稳定值（文档「四」：波动与抖动造成的瞬时值不参与触发判定）。
 */
public final class WuzangRhythm {

    /** 当前波动偏移（气 / 血各五个脏）。 */
    private static final float[] WAVE_QI = new float[WuzangType.COUNT];
    private static final float[] WAVE_BLOOD = new float[WuzangType.COUNT];
    /** 当前微观抖动（每 {@link WuzangConfig#RHYTHM_JITTER_INTERVAL} tick 重掷一次）。 */
    private static final float[] JITTER_QI = new float[WuzangType.COUNT];
    private static final float[] JITTER_BLOOD = new float[WuzangType.COUNT];

    /** 只用「观察」，不影响任何判定；用固定种子的自检走的是纯函数版本。 */
    private static final Random RANDOM = new Random();

    private WuzangRhythm() {
    }

    /** 每 tick 调用一次；内部按 {@link WuzangConfig#RHYTHM_INTERVAL} 节流。 */
    public static void tick(MinecraftServer server, long tick) {
        if (WuzangConfig.RHYTHM_INTERVAL <= 0 || tick % WuzangConfig.RHYTHM_INTERVAL != 0L) {
            return;
        }
        ServerWorld world = server.getOverworld();
        long time = world == null ? 0L : world.getTimeOfDay();
        if (tick % WuzangConfig.RHYTHM_JITTER_INTERVAL == 0L) {
            refreshJitter();
        }
        for (WuzangType type : WuzangType.values()) {
            int i = type.index();
            float target = WuzangShichen.targetOffset(type, time);
            WAVE_QI[i] = WuzangRules.approach(WAVE_QI[i], target, nextFactor());
            WAVE_BLOOD[i] = WuzangRules.approach(WAVE_BLOOD[i], target, nextFactor());
        }
    }

    /** 掷一个本 tick 的速度系数（距目标距离 × 本系数 = 本 tick 位移）。 */
    private static float nextFactor() {
        return WuzangRules.approachFactor(WuzangConfig.RHYTHM_SPEED,
                WuzangConfig.RHYTHM_SPEED_JITTER, RANDOM.nextDouble(), WuzangConfig.RHYTHM_SPEED_MIN);
    }

    /** 重掷微观抖动（零均值、每次重掷而不是累加 → 不会漂移）。 */
    private static void refreshJitter() {
        for (WuzangType type : WuzangType.values()) {
            int i = type.index();
            JITTER_QI[i] = WuzangRules.jitter(RANDOM.nextDouble(), WuzangConfig.RHYTHM_JITTER_AMPLITUDE);
            JITTER_BLOOD[i] = WuzangRules.jitter(RANDOM.nextDouble(), WuzangConfig.RHYTHM_JITTER_AMPLITUDE);
        }
    }

    /** 当前波动偏移（气）。 */
    public static float waveQi(WuzangType type) {
        return WAVE_QI[type.index()];
    }

    /** 当前波动偏移（血）。 */
    public static float waveBlood(WuzangType type) {
        return WAVE_BLOOD[type.index()];
    }

    /** 当前微观抖动（气）。 */
    public static float jitterQi(WuzangType type) {
        return JITTER_QI[type.index()];
    }

    /** 当前微观抖动（血）。 */
    public static float jitterBlood(WuzangType type) {
        return JITTER_BLOOD[type.index()];
    }

    /** 含波动的「现值」（气）：<b>只用于显示</b>，绝不写回附件。 */
    public static float displayQi(WuzangState state, WuzangType type) {
        int i = type.index();
        return WuzangRules.displayValue(state.qiOf(type), WAVE_QI[i], JITTER_QI[i]);
    }

    /** 含波动的「现值」（血）：同上。 */
    public static float displayBlood(WuzangState state, WuzangType type) {
        int i = type.index();
        return WuzangRules.displayValue(state.bloodOf(type), WAVE_BLOOD[i], JITTER_BLOOD[i]);
    }

    /** 清空波动状态（服务器停机时调用；下次开服从 0 平滑逼近）。 */
    public static void reset() {
        Arrays.fill(WAVE_QI, 0.0F);
        Arrays.fill(WAVE_BLOOD, 0.0F);
        Arrays.fill(JITTER_QI, 0.0F);
        Arrays.fill(JITTER_BLOOD, 0.0F);
    }
}
