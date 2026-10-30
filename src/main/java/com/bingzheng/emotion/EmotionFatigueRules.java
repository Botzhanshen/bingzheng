package com.bingzheng.emotion;

/**
 * 神耗 / 体耗的<b>量化规范 v2</b>（纯函数，v0.9.2）。
 *
 * <p><b>时间基准</b>：MC 1 游戏日 = 24000 tick = 20 现实分钟 ⇒
 * <b>1 现实分钟 = 1200 tick</b>；1 时辰 ≈ 1.67 现实分钟；夜晚 23:00–06:00 ≈ 7 现实分钟。
 * 本类所有速率都按现实分钟标注，tick 换算在此集中。
 */
public final class EmotionFatigueRules {

    /** 1 现实分钟 = 1200 tick。 */
    public static final int TICKS_PER_REAL_MINUTE = 1200;

    // ---------------- 体耗 v2 ----------------

    /** 主来源：饥饿值每损失 1 点（半格）→ 体耗 +2（满 20→0 一轮 = +40）。 */
    public static final float BODY_PER_HUNGER_POINT = 2.0F;

    /** 辅助：每挖 10 个方块 +1。 */
    public static final int BLOCKS_PER_POINT = 10;
    /** 辅助：每 100 格（疾跑 / 游泳各自累计）+1。 */
    public static final double DISTANCE_PER_POINT = 100.0D;

    // ---------------- 神耗 v2 ----------------

    /** 打开加工 / 容器类 GUI 计 1 次 → +1；附魔台 +2。 */
    public static final float GUI_POINT = 1.0F;
    public static final float GUI_ENCHANT_POINT = 2.0F;
    /** 同一个 GUI 关闭重开后，这么久（10 秒）内不重复计。 */
    public static final int GUI_ANTI_SPAM_TICKS = 200;

    /** 熬夜：夜间清醒每现实分钟 +1（一晚 ≈ +7）。 */
    public static final float NIGHT_POINT_PER_MINUTE = 1.0F;

    // ---------------- 自然回落 v2 ----------------

    /** 静止（无任何行为触发）时每 10 现实分钟 −5。 */
    public static final int IDLE_DECAY_TICKS = 10 * TICKS_PER_REAL_MINUTE;
    public static final float IDLE_DECAY_STEP = 5.0F;

    /** 视为「睡到天亮」的夜间睡眠时长（tick）。 */
    public static final float SLEEP_ADEQUATE_TICKS = 200.0F;

    public static final float MIN = 0.0F;
    public static final float MAX = 100.0F;

    private EmotionFatigueRules() {
    }

    public static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** 加值（钳 0..100）。 */
    public static float add(float value, float delta) {
        return clamp(value + delta);
    }

    /** 饥饿值损失 {@code hungerLost} 点折算的体耗。 */
    public static float hungerLossPoints(int hungerLost) {
        return Math.max(0, hungerLost) * BODY_PER_HUNGER_POINT;
    }

    /** 挖了这么多方块折几个点。 */
    public static int blockPoints(int blocks) {
        return Math.max(0, blocks) / BLOCKS_PER_POINT;
    }

    /** 走了这么多格折几个点。 */
    public static int distancePoints(double meters) {
        if (meters <= 0.0D) {
            return 0;
        }
        return (int) (meters / DISTANCE_PER_POINT);
    }

    /** 一次 GUI 交互折几个神耗点：附魔台 2、其它加工/容器 1、自己的背包 0。 */
    public static float guiPoints(boolean enchantingTable, boolean playerInventory) {
        if (playerInventory) {
            return 0.0F;
        }
        return enchantingTable ? GUI_ENCHANT_POINT : GUI_POINT;
    }

    /** 防刷：距上次同一 GUI 计点是否已过 10 秒。 */
    public static boolean canAwardGui(long now, long lastAward) {
        // 「从未计过」必须单独处理：引擎用 Long.MIN_VALUE 表示没记录，
        // 直接相减会 long 溢出成负数 → 第一次交互永远不计点（自检抓到的真 bug）。
        if (lastAward <= 0L) {
            return true;
        }
        return now - lastAward >= GUI_ANTI_SPAM_TICKS;
    }

    /** 夜间清醒累计 tick 折几个神耗点。 */
    public static int nightPoints(int nightTicks) {
        return Math.max(0, nightTicks) / TICKS_PER_REAL_MINUTE;
    }

    /** 静止回落一步（−5，不为负）。 */
    public static float idleDecay(float value) {
        return Math.max(MIN, value - IDLE_DECAY_STEP);
    }

    /** 夜间睡眠是否足够（睡到天亮）。 */
    public static boolean sleptEnough(float sleptTicks) {
        return sleptTicks >= SLEEP_ADEQUATE_TICKS;
    }

    /** 是否处于「熬夜时段」（23:00–06:00）；MC 0 刻 = 06:00。 */
    public static boolean isNightWindow(long timeOfDay) {
        long tod = Math.floorMod(timeOfDay, 24000L);
        int hour = (int) (tod / 1000L + 6L) % 24;
        return hour >= 23 || hour < 6;
    }
}
