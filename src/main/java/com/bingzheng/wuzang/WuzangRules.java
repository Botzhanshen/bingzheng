package com.bingzheng.wuzang;

/**
 * 五脏拓展的<b>纯规则函数</b>。
 *
 * <p>把「钳位、迟滞判定、向基线恢复、时辰波动律法、伤血分摊、静坐/睡眠倍率」这些<b>决策</b>
 * 从需要真实玩家的代码里抽出来，于是启动自检可以直接断言 —— 无需世界、无需玩家。
 *
 * <p>业务代码统一调用本类，保证「被测的就是在跑的」。
 */
public final class WuzangRules {

    /**
     * 失血的脏腑优先次序：<b>先耗心、肝之血</b>，再轮到脾、肺、肾（文档原文：优先耗损心、肝之血）。
     */
    private static final WuzangType[] BLOOD_LOSS_PRIORITY = {
            WuzangType.HEART, WuzangType.LIVER, WuzangType.SPLEEN, WuzangType.LUNG, WuzangType.KIDNEY,
    };

    private WuzangRules() {
    }


    /** 气血取值钳位。 */
    public static float clamp(float v) {
        return Math.max(WuzangConfig.MIN, Math.min(WuzangConfig.MAX, v));
    }

    /** 是否判为「虚」：<b>严格小于</b>阈值。 */
    public static boolean deficient(float v) {
        return v < WuzangConfig.DEFICIENT_THRESHOLD;
    }

    /**
     * 是否判为「亢」：<b>严格大于</b>阈值。
     *
     * <p>只对气生效。血脉没有「血亢」，调用方不得把它用在血上。
     */
    public static boolean excess(float v) {
        return v > WuzangConfig.EXCESS_THRESHOLD;
    }

    /** 气血两虚：同一脏的气与血同时判虚。 */
    public static boolean bothDeficient(float qi, float blood) {
        return deficient(qi) && deficient(blood);
    }

    // ---------------- 阈值迟滞（v0.6.0：防波动边缘闪断） ----------------

    /**
     * 「虚」的<b>迟滞</b>判定：从正常态要跌破 {@link WuzangConfig#DEFICIENT_THRESHOLD} 才触发，
     * 已在虚态则要回升到 {@link WuzangConfig#DEFICIENT_RELEASE} 才解除。
     *
     * <p>迟滞必须<b>带记忆</b>（{@code wasDeficient}），否则它退化成单点判定 ——
     * 记忆存在 {@link WuzangState} 的标记数组里，随附件一起落盘。
     */
    public static boolean deficientHysteresis(float v, boolean wasDeficient) {
        return wasDeficient ? v < WuzangConfig.DEFICIENT_RELEASE : v < WuzangConfig.DEFICIENT_THRESHOLD;
    }

    /** 「亢」的迟滞判定：90 触发、回落 ≤ {@link WuzangConfig#EXCESS_RELEASE} 解除。只对气使用。 */
    public static boolean excessHysteresis(float v, boolean wasExcess) {
        return wasExcess ? v > WuzangConfig.EXCESS_RELEASE : v > WuzangConfig.EXCESS_THRESHOLD;
    }

    // ---------------- 时辰波动：变速到达 + 微观抖动 ----------------

    /**
     * 掷一个速度系数：以 {@code base} 为中心按 {@code jitter} 比例随机微调，
     * 再钳到 {@code [floor, RHYTHM_FACTOR_CAP]}。
     *
     * <p><b>纯函数</b>：随机源以 {@code sample ∈ [0,1)} 注入，自检因此可以固定样本、
     * 断言「位移方向永远朝着目标」这条不变量。
     */
    public static float approachFactor(float base, float jitter, double sample, float floor) {
        double factor = base * (1.0D + (sample * 2.0D - 1.0D) * jitter);
        factor = Math.max(floor, factor);
        return (float) Math.min(factor, WuzangConfig.RHYTHM_FACTOR_CAP);
    }

    /**
     * 变速到达一步：朝目标移动「剩余距离 × 系数」，接近目标时位移自然变小（软着陆）。
     *
     * <p><b>为什么不会振荡 / 不会漂移</b>：只要系数落在 {@code (0,1)}，
     * 位移就永远小于剩余距离 —— 结果必然落在「当前值」与「目标」之间，一步跨不过去；
     * 剩余距离小于死区时直接归位，于是「始终收敛到目标值」。
     */
    public static float approach(float current, float target, float factor) {
        return approach(current, target, factor, WuzangConfig.RHYTHM_MARGIN);
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
        float capped = Math.min(factor, WuzangConfig.RHYTHM_FACTOR_CAP);
        return current + distance * capped;
    }

    /** 微观抖动取值：{@code sample ∈ [0,1)} → 均匀落在 ±amplitude。 */
    public static float jitter(double sample, float amplitude) {
        return (float) ((sample * 2.0D - 1.0D) * amplitude);
    }

    /**
     * 显示用「现值」= 稳定值 + 时辰波动 + 微观抖动（并钳到 0..100）。
     *
     * <p><b>只用于显示</b>：虚实判定与表现层 buff 一律只看稳定值，
     * 所以波动再大也不会让阈值闪断（文档 v1.05「四」的明确要求）。
     */
    public static float displayValue(float stable, float wave, float jitter) {
        return clamp(stable + wave + jitter);
    }

    /**
     * 向基线靠拢一步（高于 80 回落、低于 80 回升），<b>不会越过基线</b>。
     *
     * <p><b>步长按偏离距离成比例</b>（v1.05 文档：「偏离越远，回落/回升越明显」）：
     * 偏离等于 {@link WuzangConfig#RESTORE_DISTANCE_SCALE} 时步长就是传入的基准步长，
     * 偏离更大按比例放大（上限 {@link WuzangConfig#RESTORE_MAX_FACTOR} 倍），
     * 偏离更小则同比缩小 —— 越接近基线走得越慢。
     *
     * <p>传入的步长由调用方给出（已乘上静坐 / 睡眠倍率）。
     */
    public static float restoreStep(float current, float step) {
        return restoreStep(current, step, WuzangConfig.RESTORE_DISTANCE_SCALE,
                WuzangConfig.RESTORE_MAX_FACTOR);
    }

    /** 可注入参考距离与放大上限的版本，方便自检直接断言曲线形状。 */
    public static float restoreStep(float current, float step, float scale, float maxFactor) {
        float distance = WuzangConfig.BASELINE - current;
        if (Math.abs(distance) < WuzangConfig.RESTORE_MARGIN || step <= 0.0F || scale <= 0.0F) {
            return current;
        }
        float factor = Math.min(maxFactor, Math.abs(distance) / scale);
        float move = step * factor;
        if (move >= Math.abs(distance)) {
            return WuzangConfig.BASELINE;   // 不过冲：宁可正好归位
        }
        return current + Math.signum(distance) * move;
    }

    /**
     * 静坐 / 睡眠对恢复速率的倍率。
     *
     * <p>睡眠优先于静坐（两者不会同时成立，但规则里写死次序以免歧义）。
     */
    public static float restoreMultiplier(boolean meditating, boolean sleeping) {
        if (sleeping) {
            return WuzangConfig.SLEEPING_RESTORE_MULTIPLIER;
        }
        if (meditating) {
            return WuzangConfig.MEDITATING_RESTORE_MULTIPLIER;
        }
        return 1.0F;
    }

    /**
     * 把一次受伤折算成各脏的失血量，按 {@link #BLOOD_LOSS_PRIORITY} 顺序<b>优先</b>扣除。
     *
     * <p>纯函数：不修改传入数组，返回每个脏实际被扣掉的量（血不够时不会扣成负数）。
     *
     * @param blood 五个脏当前的血值（长度 5）
     * @param loss  本次要扣掉的总血量
     * @return 长度 5 的扣除量数组，下标与 {@link WuzangType#index()} 一致
     */
    public static float[] allocateBloodLoss(float[] blood, float loss) {
        float[] out = new float[WuzangType.COUNT];
        float remaining = Math.max(0.0F, loss);
        for (WuzangType type : BLOOD_LOSS_PRIORITY) {
            if (remaining <= 0.0F) {
                break;
            }
            float available = Math.max(0.0F, blood[type.index()]);
            float take = Math.min(available, remaining);
            out[type.index()] = take;
            remaining -= take;
        }
        return out;
    }

    /** 单点伤害折算的失血量。 */
    public static float bloodLossForDamage(float damage) {
        return Math.max(0.0F, damage) * WuzangConfig.INJURY_BLOOD_PER_DAMAGE;
    }

    /** 失血优先次序的副本（自检用）。 */
    public static WuzangType[] bloodLossPriority() {
        return BLOOD_LOSS_PRIORITY.clone();
    }

    /** 配置自洽性检查（自检用）。 */
    public static boolean configSane() {
        return WuzangConfig.MIN < WuzangConfig.DEFICIENT_THRESHOLD
                && WuzangConfig.DEFICIENT_THRESHOLD < WuzangConfig.BASELINE
                && WuzangConfig.BASELINE < WuzangConfig.EXCESS_THRESHOLD
                && WuzangConfig.EXCESS_THRESHOLD < WuzangConfig.MAX
                && WuzangConfig.START == WuzangConfig.BASELINE
                && WuzangConfig.MEDITATING_RESTORE_MULTIPLIER >= 1.0F
                && WuzangConfig.SLEEPING_RESTORE_MULTIPLIER >= WuzangConfig.MEDITATING_RESTORE_MULTIPLIER
                && WuzangConfig.SCAN_INTERVAL > 0
                && WuzangConfig.RESTORE_DISTANCE_SCALE > 0.0F
                && WuzangConfig.RESTORE_MAX_FACTOR >= 1.0F
                // 迟滞带必须落在「虚阈值..基线」与「基线..亢阈值」之间，否则会被死区吞掉
                && WuzangConfig.DEFICIENT_THRESHOLD < WuzangConfig.DEFICIENT_RELEASE
                && WuzangConfig.DEFICIENT_RELEASE < WuzangConfig.BASELINE
                && WuzangConfig.BASELINE < WuzangConfig.EXCESS_RELEASE
                && WuzangConfig.EXCESS_RELEASE < WuzangConfig.EXCESS_THRESHOLD
                // 时辰相位表必须正好铺满一天
                && WuzangConfig.TICKS_PER_DAY == WuzangConfig.SHICHEN_TICKS * WuzangConfig.SHICHEN_COUNT
                && WuzangConfig.SHICHEN_PHASE_OFFSET >= 0
                && WuzangConfig.SHICHEN_PHASE_OFFSET < WuzangConfig.TICKS_PER_DAY
                // 变速到达：系数必须严格落在 (0,1)，否则会振荡或卡死
                && WuzangConfig.RHYTHM_SPEED > 0.0F
                && WuzangConfig.RHYTHM_SPEED_MIN > 0.0F
                && WuzangConfig.RHYTHM_FACTOR_CAP > 0.0F
                && WuzangConfig.RHYTHM_FACTOR_CAP < 1.0F
                && WuzangConfig.RHYTHM_INTERVAL > 0
                && WuzangConfig.RHYTHM_JITTER_INTERVAL > 0
                // 波动本身绝不足以把平人推进「虚」带（波动不参与判定，但这条也顺带保证感知安全）
                && WuzangConfig.RHYTHM_AMPLITUDE > 0.0F
                && WuzangConfig.BASELINE - WuzangConfig.RHYTHM_AMPLITUDE > WuzangConfig.DEFICIENT_RELEASE;
    }
}
