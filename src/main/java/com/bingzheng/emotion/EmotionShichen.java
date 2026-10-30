package com.bingzheng.emotion;

/**
 * 七情的时辰相位 —— <b>纯函数</b>：无状态、不引用世界对象、可被启动自检直接断言。
 *
 * <p>相位沿用五脏 v0.6.0 的同一张子午流注表（当令时辰 → 对应脏 → 对应情）：
 * <pre>
 * 丑时（肝）→ 怒   午时（心）→ 喜   巳时（脾）→ 思   寅时（肺）→ 悲   酉时（肾）→ 恐
 * </pre>
 * <b>忧、惊不参与</b> —— 延续「忧惊不补」的既有裁定（文档里这两情没有脏腑归属）。
 *
 * <p><b>为什么这里另写一份、不复用五脏的类</b>：情志拓展要求「单开情志也能跑」，
 * 与五脏之间必须是**零编译期依赖**（五脏反过来也一样）。两边的相位数学各自独立，
 * 是刻意的重复而不是疏漏 —— 拆包时谁都不需要带上对方。
 */
public final class EmotionShichen {

    /** 十二时辰名（下标 = 时辰序号，0 为子时）。 //TODO 后续迁移外部配置 */
    private static final String[] NAMES = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥",
    };

    /**
     * 各情的当令时辰序号，下标与 {@link EmotionType#index()} 一一对应
     * （怒、喜、忧、思、悲、恐、惊）：怒丑(1)、喜午(6)、忧无、思巳(5)、悲寅(2)、恐酉(9)、惊无。
     */
    private static final int[] SOVEREIGN = { 1, 6, -1, 5, 2, 9, -1 };

    private EmotionShichen() {
    }

    /** 该情是否参与时辰波动（忧、惊不参与）。 */
    public static boolean hasPhase(EmotionType type) {
        return SOVEREIGN[type.index()] >= 0;
    }

    /** 该情的当令时辰序号；不参与者返回 -1。 */
    public static int sovereignShichen(EmotionType type) {
        return SOVEREIGN[type.index()];
    }

    /** 把任意世界时间回绕到一天内刻度 0..23999（负值也正确）。 */
    public static long timeOfDay(long worldTime) {
        return Math.floorMod(worldTime, (long) EmotionConfig.TICKS_PER_DAY);
    }

    /** 以「子时起点」为零点的时间轴刻度。 */
    public static long shifted(long worldTime) {
        return Math.floorMod(timeOfDay(worldTime) + EmotionConfig.SHICHEN_PHASE_OFFSET,
                (long) EmotionConfig.TICKS_PER_DAY);
    }

    /** 时辰序号 0..11（0 = 子时，23:00-01:00）。 */
    public static int index(long worldTime) {
        return (int) (shifted(worldTime) / EmotionConfig.SHICHEN_TICKS);
    }

    /** 时辰名，如「午」。 */
    public static String name(long worldTime) {
        return NAMES[index(worldTime)];
    }

    /** 此刻当令的情；无当令情时返回 {@code null}。 */
    public static EmotionType sovereignAt(long worldTime) {
        int i = index(worldTime);
        for (EmotionType type : EmotionType.values()) {
            if (SOVEREIGN[type.index()] == i) {
                return type;
            }
        }
        return null;
    }

    /** 连续相位：当令时辰中点 +1、对冲时辰中点 −1（余弦）。 */
    public static float phaseAt(EmotionType type, double unit) {
        int sovereign = SOVEREIGN[type.index()];
        if (sovereign < 0) {
            return 0.0F;
        }
        double center = (sovereign + 0.5D) / EmotionConfig.SHICHEN_COUNT;
        return (float) Math.cos(2.0D * Math.PI * (unit - center));
    }

    /** 某情在指定时辰段的目标波动偏移 = 该段中点的相位 × 振幅；不参与的情恒为 0。 */
    public static float targetOffsetForSegment(EmotionType type, int segment) {
        if (!hasPhase(type)) {
            return 0.0F;
        }
        double unit = (segment + 0.5D) / EmotionConfig.SHICHEN_COUNT;
        return phaseAt(type, unit) * EmotionConfig.RHYTHM_AMPLITUDE;
    }

    /** 当前时辰段的目标波动偏移。 */
    public static float targetOffset(EmotionType type, long worldTime) {
        return targetOffsetForSegment(type, index(worldTime));
    }

    /** 对冲时辰序号（相隔六个时辰）。 */
    public static int oppositeShichen(EmotionType type) {
        int sovereign = SOVEREIGN[type.index()];
        return sovereign < 0 ? -1 : (sovereign + EmotionConfig.SHICHEN_COUNT / 2) % EmotionConfig.SHICHEN_COUNT;
    }

    /** 十二时辰名的只读副本（自检与调试用）。 */
    public static String[] names() {
        return NAMES.clone();
    }
}
