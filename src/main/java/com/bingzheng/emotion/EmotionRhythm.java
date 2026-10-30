package com.bingzheng.emotion;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Arrays;
import java.util.Random;

/**
 * 七情时辰波动的<b>运行期驱动</b>：全服共用一份波动偏移。
 *
 * <p>与五脏 v0.6.0 的机制<b>完全对齐</b>（时辰相位 + 变速到达 + 微观抖动 + 迟滞区间），
 * 但实现是独立的一份 —— 情志与五脏之间零编译期依赖，任一侧都可以单独拆包。
 *
 * <p><b>为什么是全服一份、不落盘</b>：波动只取决于世界时间与随机数，与玩家无关；
 * 它是显示层的起伏，不是玩家属性，所以不新增任何持久化字段（存档格式零变更）。
 *
 * <p><b>产出永远不参与判定</b>：气机扰动阈值、神扰合并、心绪不宁抖动一律只读
 * {@link EmotionState} 里的稳定值（文档 v0.7.0 任务单二第四条）。
 */
public final class EmotionRhythm {

    private static final float[] WAVE = new float[EmotionType.COUNT];
    private static final float[] JITTER = new float[EmotionType.COUNT];
    private static final Random RANDOM = new Random();

    private EmotionRhythm() {
    }

    /** 每 tick 调用一次；内部按 {@link EmotionConfig#RHYTHM_INTERVAL} 节流。 */
    public static void tick(MinecraftServer server, long tick) {
        if (EmotionConfig.RHYTHM_INTERVAL <= 0 || tick % EmotionConfig.RHYTHM_INTERVAL != 0L) {
            return;
        }
        ServerWorld world = server.getOverworld();
        long time = world == null ? 0L : world.getTimeOfDay();
        if (tick % EmotionConfig.RHYTHM_JITTER_INTERVAL == 0L) {
            refreshJitter();
        }
        for (EmotionType type : EmotionType.values()) {
            int i = type.index();
            float target = EmotionShichen.targetOffset(type, time);
            WAVE[i] = EmotionRules.approach(WAVE[i], target, nextFactor());
        }
    }

    /** 掷一个本 tick 的速度系数（距目标距离 × 本系数 = 本 tick 位移）。 */
    private static float nextFactor() {
        return EmotionRules.approachFactor(EmotionConfig.RHYTHM_SPEED,
                EmotionConfig.RHYTHM_SPEED_JITTER, RANDOM.nextDouble(), EmotionConfig.RHYTHM_SPEED_MIN);
    }

    /** 重掷微观抖动（零均值、每次重掷而不是累加 → 不会漂移）。 */
    private static void refreshJitter() {
        for (EmotionType type : EmotionType.values()) {
            JITTER[type.index()] = EmotionRules.jitter(RANDOM.nextDouble(),
                    EmotionConfig.RHYTHM_JITTER_AMPLITUDE);
        }
    }

    /** 当前波动偏移。 */
    public static float wave(EmotionType type) {
        return WAVE[type.index()];
    }

    /** 当前微观抖动。 */
    public static float jitter(EmotionType type) {
        return JITTER[type.index()];
    }

    /** 含波动的「现值」：<b>只用于显示</b>，绝不写回附件、绝不参与判定。 */
    public static float display(EmotionState state, EmotionType type) {
        int i = type.index();
        return EmotionRules.displayValue(state.emotion(type), WAVE[i], JITTER[i]);
    }

    /** 清空波动状态（服务器停机时调用）。 */
    public static void reset() {
        Arrays.fill(WAVE, 0.0F);
        Arrays.fill(JITTER, 0.0F);
    }
}
