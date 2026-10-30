package com.bingzheng.wuzang;

/**
 * 时辰（子午流注）相位 —— <b>纯函数</b>：无状态、不引用世界对象、可被启动自检直接断言。
 *
 * <p>五脏各有当令时辰（肺寅、脾巳、心午、肾酉、肝丑），当令时气血偏高、对冲时辰偏低。
 * 本类只负责「世界时间 → 相位 → 目标偏移」这一层数学；
 * 偏移<b>怎样</b>逼近过去（变速到达 + 微观抖动）交给 {@link WuzangRules} 与 {@link WuzangRhythm}。
 *
 * <p><b>与游戏时间的关系</b>：MC 一天 24000 tick（20 分钟），一个时辰 = 2 游戏小时 = 2000 tick，
 * 与文档「以 2 小时（一个时辰）为段」一致。MC 的 0 刻是清晨 06:00（卯时），
 * 而时辰以 23:00 起子时，故整条时间轴前移 {@link WuzangConfig#SHICHEN_PHASE_OFFSET} 刻。
 */
public final class WuzangShichen {

    /**
     * 十二时辰名，下标即时辰序号（0 = 子时）。
     *
     * <p><b>刻意恒为中文</b>：与 {@link WuzangType#zhName()} 在英文语言文件里同样保持中文是同一做法，
     * 领域名不逐字翻译，避免出现「脏名中文、时辰英文」的半吊子界面。
     * //TODO 后续迁移外部配置
     */
    private static final String[] NAMES = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥",
    };

    /**
     * 各脏的<b>当令时辰</b>序号（子午流注）：肺寅(2)、脾巳(5)、心午(6)、肾酉(9)、肝丑(1)。
     *
     * <p>下标与 {@link WuzangType#index()} 一一对应（肝、心、脾、肺、肾），
     * 因此字面量读作：肝丑、心午、脾巳、肺寅、肾酉。
     */
    private static final int[] SOVEREIGN = { 1, 6, 5, 2, 9 };

    private WuzangShichen() {
    }

    /** 把任意世界时间回绕到一天内刻度 0..23999（负值也正确）。 */
    public static long timeOfDay(long worldTime) {
        return Math.floorMod(worldTime, (long) WuzangConfig.TICKS_PER_DAY);
    }

    /** 以「子时起点」为零点的时间轴刻度。 */
    public static long shifted(long worldTime) {
        return Math.floorMod(timeOfDay(worldTime) + WuzangConfig.SHICHEN_PHASE_OFFSET,
                (long) WuzangConfig.TICKS_PER_DAY);
    }

    /** 时辰序号 0..11（0 = 子时，23:00-01:00）。 */
    public static int index(long worldTime) {
        return (int) (shifted(worldTime) / WuzangConfig.SHICHEN_TICKS);
    }

    /** 时辰名，如「午」。 */
    public static String name(long worldTime) {
        return NAMES[index(worldTime)];
    }

    /** 某脏的当令时辰序号。 */
    public static int sovereignShichen(WuzangType type) {
        return SOVEREIGN[type.index()];
    }

    /** 该脏此刻是否当令。 */
    public static boolean isSovereign(WuzangType type, long worldTime) {
        return index(worldTime) == SOVEREIGN[type.index()];
    }

    /** 此刻当令的脏；十二时辰里只有五个有脏当令，其余时辰返回 {@code null}。 */
    public static WuzangType sovereignAt(long worldTime) {
        int i = index(worldTime);
        for (WuzangType type : WuzangType.values()) {
            if (SOVEREIGN[type.index()] == i) {
                return type;
            }
        }
        return null;
    }

    /**
     * 连续相位：当令时辰中点为 +1、对冲时辰中点为 −1（余弦）。
     *
     * @param unit 「时辰相位刻 ÷ 24000」，取值 0..1
     */
    public static float phaseAt(WuzangType type, double unit) {
        double center = (SOVEREIGN[type.index()] + 0.5D) / WuzangConfig.SHICHEN_COUNT;
        return (float) Math.cos(2.0D * Math.PI * (unit - center));
    }

    /**
     * 某脏在<b>指定时辰段</b>的目标波动偏移 = 该段中点的相位 × 振幅。
     *
     * <p>于是「当令时辰 = +振幅、对冲时辰 = −振幅」；一天 12 段、<b>段内目标恒定</b>，
     * 段与段之间目标切换，由 {@link WuzangRhythm} 用变速到达逼近（软着陆、不过冲、不漂移）。
     */
    public static float targetOffsetForSegment(WuzangType type, int segment) {
        double unit = (segment + 0.5D) / WuzangConfig.SHICHEN_COUNT;
        return phaseAt(type, unit) * WuzangConfig.RHYTHM_AMPLITUDE;
    }

    /** 当前时辰段的目标波动偏移。 */
    public static float targetOffset(WuzangType type, long worldTime) {
        return targetOffsetForSegment(type, index(worldTime));
    }

    /** 十二时辰名的只读副本（自检与调试用）。 */
    public static String[] names() {
        return NAMES.clone();
    }

    /** 对冲时辰序号（相隔六个时辰 = 半天）。 */
    public static int oppositeShichen(WuzangType type) {
        return (SOVEREIGN[type.index()] + WuzangConfig.SHICHEN_COUNT / 2) % WuzangConfig.SHICHEN_COUNT;
    }
}
