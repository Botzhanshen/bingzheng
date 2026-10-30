package com.bingzheng.emotion;

/**
 * 睡眠恢复梯度 + 睡眠债 + 日光调摄的<b>纯规则层</b>（v0.9.5）。
 *
 * <p>时间口径：MC 0 刻 = 06:00，真实小时 = (刻/1000 + 6) % 24；"可睡时长" = 06:00 − 入睡时刻。
 * 全部可断言，不含任何世界对象。
 */
public final class EmotionSleepRules {

    /** 起床时刻（游戏小时）。 */
    public static final int WAKE_HOUR = 6;
    /** 熬夜窗口起点（含）与终点（不含），亥时 22–23 不算熬夜。 */
    public static final int NIGHT_START_HOUR = 23;
    public static final int NIGHT_END_HOUR = 6;

    /** 睡眠债上限。 */
    public static final int DEBT_MAX = 6;
    /** 每股债对神耗速率的加成。 */
    public static final float DEBT_RATE_STEP = 0.25F;
    /** 神耗速率倍率上限（债 6）。 */
    public static final float DEBT_RATE_CAP = 2.5F;
    /** 通宵次日体耗倍率。 */
    public static final float OVERNIGHT_BODY_RATE = 1.5F;

    /** 晴天日照对神耗速率的倍率。 */
    public static final float SUN_CLEAR = 0.7F;
    /** 阴雨天日照倍率。 */
    public static final float SUN_RAIN = 0.85F;
    /** 日照累计多少 tick 之后才开始压低神耗涨速（1 游戏小时 = 1000 tick）。 */
    public static final int SUN_SLOW_START_TICKS = 1000;
    /** 日照累计多少 tick 抵 1 点债（3 游戏小时 = 3000 tick ≈ 2.5 现实分钟）。 */
    public static final int SUN_DEBT_RELIEF_TICKS = 3000;

    private EmotionSleepRules() {
    }

    /** 游戏时刻 → 真实小时（0–23）。 */
    public static int hourOf(long timeOfDay) {
        return (int) ((Math.floorMod(timeOfDay, 24000L) / 1000L + 6L) % 24L);
    }

    /** 是否熬夜窗口（23:00–06:00，不含 22–23 的亥时）。 */
    public static boolean isNightWindow(long timeOfDay) {
        int hour = hourOf(timeOfDay);
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    /** 可睡时长（小时）：06:00 − 入睡时刻；入睡在 00:00 之后也算到 06:00。 */
    public static double sleepHours(long sleepStartTod) {
        double startHour = hourOf(sleepStartTod) + (Math.floorMod(sleepStartTod, 24000L) % 1000L) / 1000.0;
        double wake = WAKE_HOUR;
        double usable = wake - startHour;
        if (usable <= 0) {
            usable += 24.0;          // 入睡在 06:00 之后 → 跨到第二天早上
        }
        return Math.min(usable, 12.0);
    }

    /**
     * 恢复率：≥7h 全清 100%；6h 75%；6–7h 每半小时 +12.5%；<6h 封顶 75%。
     */
    public static double recoveryRate(double hours) {
        if (hours >= 7.0) {
            return 1.0;
        }
        if (hours <= 6.0) {
            return 0.75;
        }
        return 0.75 + (hours - 6.0) * 0.25;   // 6h→0.75，7h→1.0
    }

    /** 应用恢复：新值 = 旧值 ×(1−恢复率)，向下取整、不为负。 */
    public static float applyRecovery(float oldValue, double rate) {
        double next = Math.floor(oldValue * (1.0 - rate));
        return (float) Math.max(0.0, next);
    }

    /** 整夜在场的判定线：7 游戏小时 = 7000 tick（正好是 23:00–06:00 整段）。 */
    public static final int OVERNIGHT_MIN_NIGHT_TICKS = 7000;
    /** 熬夜一段的判定线：4 游戏小时 = 4000 tick。 */
    public static final int LATE_NIGHT_MIN_NIGHT_TICKS = 4000;

    /** 睡了的这一晚的债变化：≥8h −2；≥7h −1；6–7h 不变；<6h +1。 */
    public static int sleptDebtDelta(double hours) {
        if (hours >= 8.0) {
            return -2;
        }
        if (hours >= 7.0) {
            return -1;
        }
        if (hours >= 6.0) {
            return 0;
        }
        return 1;
    }

    /**
     * 没睡的这一晚的债变化（v0.9.10 分档，<b>替换原先「未睡一律 +3」</b>）。
     *
     * <p>按「本夜在线且未睡」累计的 tick 分档：
     * <ul>
     *   <li>≥7 游戏小时（7000 tick）→ 通宵 <b>+3</b>；</li>
     *   <li>4–7 游戏小时 → 熬夜一段 <b>+1</b>；</li>
     *   <li>&lt;4 游戏小时 → <b>不计债</b>（中途加入没有经历整夜，不该背通宵的账）。</li>
     * </ul>
     *
     * <p><b>挂机整夜照样算通宵</b>：口径是「角色整夜没睡」，不看人是否在键盘前 ——
     * 否则站在田里挂一夜反而比挂着聊天更「干净」，说不通。
     */
    public static int unsleptDebtDelta(int nightTicksAwake) {
        if (nightTicksAwake >= OVERNIGHT_MIN_NIGHT_TICKS) {
            return 3;
        }
        if (nightTicksAwake >= LATE_NIGHT_MIN_NIGHT_TICKS) {
            return 1;
        }
        return 0;
    }

    /** 债钳到 0..6。 */
    public static int clampDebt(int debt) {
        return Math.max(0, Math.min(DEBT_MAX, debt));
    }

    /** 睡眠债对神耗速率的倍率（债 0 → 1.0；债 6 → 2.5 封顶）。 */
    public static float spiritRate(int debt) {
        return Math.min(DEBT_RATE_CAP, 1.0F + DEBT_RATE_STEP * clampDebt(debt));
    }

    /** 通宵次日的体耗倍率。 */
    public static float bodyRate(boolean overnight) {
        return overnight ? OVERNIGHT_BODY_RATE : 1.0F;
    }

    /** 日照是否生效：白天 + 头顶可见天空。 */
    public static boolean sunlightApplies(boolean day, int skyLight) {
        return day && skyLight > 0;
    }

    /** 日照对神耗速率的倍率：不生效 1.0；晴天 0.7；阴雨 0.85。 */
    public static float sunRate(boolean applies, boolean raining) {
        if (!applies) {
            return 1.0F;
        }
        return raining ? SUN_RAIN : SUN_CLEAR;
    }
}
