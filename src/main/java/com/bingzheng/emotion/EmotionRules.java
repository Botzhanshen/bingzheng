package com.bingzheng.emotion;

import net.minecraft.entity.attribute.EntityAttributes;

/**
 * 情志拓展的<b>纯规则函数</b>。
 *
 * <p>把「衰减一步、阈值判定、合并条件、神安条件、时长封顶、静坐削减、攻速钳位、
 * 生命上限扣除」这些<b>决策</b>从需要真实玩家的代码里抽出来，
 * 于是它们可以被启动自检直接断言 —— 无需世界、无需玩家。
 *
 * <p>业务代码统一调用本类，保证「被测的就是在跑的」。
 */
public final class EmotionRules {

    private EmotionRules() {
    }

    /** 七情向基线插值一步；已足够接近时原样返回（避免浮点抖动）。 */
    public static float decayStep(float current, float baseline) {
        if (Math.abs(current - baseline) < 0.01F) {
            return current;
        }
        float next = current + (baseline - current) * EmotionConfig.EMOTION_DECAY_RATE;
        return Math.abs(next - current) < 0.001F ? current : next;
    }

    /** 是否冲破气机扰动阈值。 */
    public static boolean exceedsThreshold(float value) {
        return value >= EmotionConfig.DISTURBANCE_THRESHOLD;
    }

    /** 合并判定：冷却中一律不合并；否则 ≥2 个气机扰动即合并为神扰。 */
    public static boolean shouldMerge(int disturbanceCount, int cooldownTicks) {
        return cooldownTicks <= 0 && disturbanceCount >= EmotionConfig.DISTURBANCE_MAX;
    }

    /**
     * 本次扫描是否允许生成新的气机扰动。
     *
     * <p>神扰是「≥2 个气机扰动的<b>合并态</b>」，两者不应共存：
     * 神扰激活的几十秒里若继续按阈值生成扰动，这些扰动会活过神扰结束的那一刻，
     * 冷却一过就立刻二次合并 —— 这正是 Bug-3「连续跳两轮神扰」的根因。
     */
    public static boolean shouldRaiseDisturbance(boolean hasDelirium) {
        return !hasDelirium;
    }

    /**
     * 神扰<b>完全结束</b>时的单条情绪衰减。
     *
     * <p>仍然站在阈值之上的情绪乘系数打下去；**低于阈值的原样返回（绝不归零）**。
     * 保留残余是有意的：心绪不宁阶段的心悸抖动需要一个非零的起点，
     * 而「情绪被一次神扰彻底抹平」也不符合这个模块的设计。
     */
    public static float endOfDeliriumDecay(float current) {
        return exceedsThreshold(current) ? current * EmotionConfig.DELIRIUM_END_EMOTION_DECAY : current;
    }

    /**
     * 神安条件：身上既无神扰、也无任何气机扰动，<b>且不在心绪不宁阶段</b>。
     *
     * <p>最后一条是神扰结束后的缓冲：神扰刚消失时心绪未平，此时不该立刻回到神安。
     */
    public static boolean shouldHaveShenAn(boolean hasDelirium, int disturbanceCount, boolean restless) {
        return !hasDelirium && disturbanceCount == 0 && !restless;
    }

    /**
     * 心悸抖动的安全钳位：<b>绝不会把「本来没超标」的情绪推过扰动阈值</b>。
     * 本来就已经超标的不动，交给正常扫描处理。
     */
    public static float restlessJitterClamp(float current, float jittered) {
        if (current >= EmotionConfig.DISTURBANCE_THRESHOLD) {
            return jittered;
        }
        return Math.min(jittered, EmotionConfig.DISTURBANCE_THRESHOLD - EmotionConfig.RESTLESS_JITTER_MARGIN);
    }

    /** 追加神扰剩余时间，受全局硬封顶约束。 */
    public static int extendDelirium(int current, int delta) {
        return Math.min(current + Math.max(0, delta), EmotionConfig.DELIRIUM_HARD_CAP_TICKS);
    }

    /** 削减神扰剩余时间（唯一途径是静坐），不为负。 */
    public static int drainDelirium(int current, int delta) {
        return Math.max(0, current - Math.max(0, delta));
    }

    /** 神耗自然衰减速率；神安会加快它。 */
    public static float spiritDecayRate(boolean shenAn) {
        return EmotionConfig.SPIRIT_DECAY * (shenAn ? EmotionConfig.SHEN_AN_SPIRIT_DECAY_MULTIPLIER : 1.0F);
    }

    /**
     * 攻速修饰器的数值。
     *
     * <p>先算出期望总量，再取保守上限，最后走原版
     * {@code EntityAttribute#clamp} —— 这就是「不超过 Minecraft 原版攻击速度硬上限」的实现。
     */
    public static double attackSpeedBonus(double baseValue, double rawBonus) {
        if (baseValue <= 0.0D) {
            return 0.0D;
        }
        double desired = baseValue * (1.0D + Math.max(0.0D, rawBonus));
        double ceiling = Math.min(desired, EmotionConfig.ATTACK_SPEED_CEILING);
        double clamped = EntityAttributes.GENERIC_ATTACK_SPEED.clamp(ceiling);
        return Math.max(0.0D, clamped / baseValue - 1.0D);
    }

    /** 神扰对最大生命值的扣除比例。硬上限：最多扣一半。 */
    public static double maxHealthPenalty() {
        return Math.max(EmotionConfig.DELIRIUM_MAX_HEALTH_PENALTY, -0.5D);
    }

    /** 病邪对神扰属性增益的缩放：火邪拉高、水寒压低、皆无则 1.0。 */
    public static double pathogenScale(boolean fireAfflicted, boolean coldWaterAfflicted) {
        if (fireAfflicted) {
            return EmotionConfig.PATHOGEN_FIRE_MULTIPLIER;
        }
        if (coldWaterAfflicted) {
            return EmotionConfig.PATHOGEN_COLD_MULTIPLIER;
        }
        return 1.0D;
    }

    /** 画面脉冲的间隔：火邪加快、水寒减慢。 */
    public static int pulseInterval(double pathogenScale) {
        int interval = EmotionConfig.OVERLAY_PULSE_INTERVAL;
        if (pathogenScale > 1.0D) {
            interval = (int) (interval * EmotionConfig.OVERLAY_PULSE_FIRE_HASTEN);
        } else if (pathogenScale < 1.0D) {
            interval = (int) (interval * EmotionConfig.OVERLAY_PULSE_COLD_SLOWDOWN);
        }
        return Math.max(10, interval);
    }

    // ---------------- 时辰波动：变速到达 + 微观抖动（v0.7.0） ----------------

    /** 迟滞解除线：触发阈值 − 迟滞带宽。 */
    public static float releaseThreshold() {
        return EmotionConfig.DISTURBANCE_THRESHOLD - EmotionConfig.DISTURBANCE_RELEASE_BAND;
    }

    /**
     * 气机扰动是否可以<b>新生成</b>：只在「未锁定」且稳定值达到触发阈值时允许。
     *
     * <p>锁定 = 这条情绪此前已经生成过一次扰动；它要在稳定值跌破解除线之后才解除锁定。
     * 判定输入永远是<b>稳定值</b>，时辰波动与抖动不参与。
     */
    public static boolean disturbanceArmed(float stableValue, boolean latched) {
        return !latched && stableValue >= EmotionConfig.DISTURBANCE_THRESHOLD;
    }

    /** 锁定是否仍然有效：稳定值仍在解除线之上就保持锁定，跌下去即解除（重新武装）。 */
    public static boolean latchSurvives(float stableValue, boolean latched) {
        return latched && stableValue >= releaseThreshold();
    }

    /** 变速到达一步：朝目标移动「剩余距离 × 系数」，接近目标时位移自然变小（软着陆）。 */
    public static float approach(float current, float target, float factor) {
        return approach(current, target, factor, EmotionConfig.RHYTHM_MARGIN);
    }

    /** 可注入死区的版本，方便自检直接断言。 */
    public static float approach(float current, float target, float factor, float margin) {
        float distance = target - current;
        if (Math.abs(distance) <= margin) {
            return target;                       // 软着陆：直接归位，不再无限逼近
        }
        if (factor <= 0.0F) {
            return current;
        }
        return current + distance * Math.min(factor, EmotionConfig.RHYTHM_FACTOR_CAP);
    }

    /**
     * 掷一个速度系数：以 {@code base} 为中心按 {@code jitter} 比例随机微调，
     * 再钳到 {@code [floor, RHYTHM_FACTOR_CAP]}（因此<b>恒在 (0,1) 内</b>，结构上不可能振荡）。
     */
    public static float approachFactor(float base, float jitter, double sample, float floor) {
        double factor = base * (1.0D + (sample * 2.0D - 1.0D) * jitter);
        factor = Math.max(floor, factor);
        return (float) Math.min(factor, EmotionConfig.RHYTHM_FACTOR_CAP);
    }

    /** 微观抖动取值：{@code sample ∈ [0,1)} → 均匀落在 ±amplitude（零均值、不累加）。 */
    public static float jitter(double sample, float amplitude) {
        return (float) ((sample * 2.0D - 1.0D) * amplitude);
    }

    /**
     * 显示用「现值」= 稳定值 + 时辰波动 + 微观抖动（并钳到 0..100）。
     *
     * <p><b>只用于显示</b>：气机扰动、神扰合并、心绪不宁全部只看稳定值。
     */
    public static float displayValue(float stable, float wave, float jitter) {
        return Math.max(EmotionConfig.EMOTION_MIN,
                Math.min(EmotionConfig.EMOTION_MAX, stable + wave + jitter));
    }

}