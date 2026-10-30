package com.bingzheng.emotion;

/**
 * 七情行为驱动的<b>纯规则层</b>（v0.8.0：五志 + 忧惊联动）。
 *
 * <p>参与行为框架的只有「五志」：怒、喜、思、悲、恐。<b>忧、惊不入框架</b>
 * （延续「忧惊不补」），它们走联动扩展且不参与时辰相位。
 *
 * <p>本类不引用任何魔数：全部取自 {@link EmotionConfig}，因此「被测的就是在跑的」。
 */
public final class EmotionBehaviorRules {

    private EmotionBehaviorRules() {
    }

    /** 该情是否参与行为驱动（五志）。 */
    public static boolean isFiveZhi(EmotionType type) {
        return type != EmotionType.WORRY && type != EmotionType.FRIGHT;
    }

    /** 该项的结算阈值；不参与框架的情返回 {@link Float#MAX_VALUE}（永不结算）。 */
    public static float thresholdOf(EmotionType type) {
        return switch (type) {
            case ANGER -> EmotionConfig.ANGER_THRESHOLD;
            case JOY -> EmotionConfig.JOY_THRESHOLD;
            case PENSIVENESS -> EmotionConfig.PENSIVENESS_THRESHOLD;
            case GRIEF -> EmotionConfig.GRIEF_THRESHOLD;
            case FEAR -> EmotionConfig.FEAR_THRESHOLD;
            default -> Float.MAX_VALUE;
        };
    }

    /** 强度点走一步：累加并钳在 {@code 0..BEHAVIOR_POINT_MAX}。 */
    public static float pointsStep(float points, float delta) {
        float next = points + Math.max(0.0F, delta);
        return Math.max(0.0F, Math.min(EmotionConfig.BEHAVIOR_POINT_MAX, next));
    }

    /** 点是否已达结算线。 */
    public static boolean shouldSettle(EmotionType type, float points) {
        return isFiveZhi(type) && points >= thresholdOf(type);
    }

    /**
     * 结算后剩余的强度点。
     *
     * <p>任务单写「点清零」，因此这里是 0（不是扣掉阈值）—— 一次结算只兑现一次涨分。
     */
    public static float pointsAfterSettle(EmotionType type, float points) {
        return shouldSettle(type, points) ? 0.0F : points;
    }

    /** 恢复：距上次行为触发满一个时辰就回落一步。 */
    public static boolean recoveryDue(long ticksSinceLastTrigger) {
        return ticksSinceLastTrigger >= EmotionConfig.BEHAVIOR_RECOVERY_INTERVAL;
    }

    /** 恢复一步：向基线回落，<b>不越下限</b>（永不低于该情的基线）。 */
    public static float recoveryStep(float current, float baseline) {
        return Math.max(baseline, current - EmotionConfig.BEHAVIOR_RECOVERY_STEP);
    }

    // ---------------- 悲：一次物品结局该计多少悲点 ----------------

    /**
     * 物品结局。
     *
     * @param thrownByOwner       玩家主动扔出（Q）—— <b>排除</b>
     * @param deathDrop           死亡掉落物 —— <b>排除</b>（死亡本身已 +3）
     * @param destroyedByHazard   被岩浆 / 火 / 虚空 / 爆炸销毁
     * @param despawned           超时消失（掉落 5 分钟未捡）
     */
    public record ItemFate(boolean thrownByOwner, boolean deathDrop,
                           boolean destroyedByHazard, boolean despawned) {
    }

    /** 一次物品结局对应的悲点（0 表示不计）。 */
    public static float griefPointsForItem(ItemFate fate) {
        if (fate == null) {
            return 0.0F;
        }
        if (fate.thrownByOwner() || fate.deathDrop()) {
            return 0.0F;                 // 主动丢弃 / 死亡掉落：不计
        }
        if (fate.despawned()) {
            return EmotionConfig.GRIEF_POINTS_ITEM_DESPAWNED;
        }
        if (fate.destroyedByHazard()) {
            return EmotionConfig.GRIEF_POINTS_ITEM_DESTROYED;
        }
        return 0.0F;
    }

    // ---------------- 恐：锁定状态机 ----------------

    /**
     * 本次扫描是否该记一次「被锁定」：<b>只在「从无到有」的那一次记</b>，
     * 同一目标持续锁定不再重复计（这就是「锁定计一次」）。
     */
    public static boolean fearShouldAward(boolean locked, boolean targeted) {
        return targeted && !locked;
    }

    /** 锁定是否仍然有效：脱战（连续 {@link EmotionConfig#FEAR_REARM_TICKS} 无目标）后重新武装。 */
    public static boolean fearStillLocked(boolean locked, int ticksSinceTarget) {
        return locked && ticksSinceTarget < EmotionConfig.FEAR_REARM_TICKS;
    }

    /**
     * 恐<b>与怒的区分</b>：恐看「是否被锁定 / 追击」，怒看「是否真的挨打」。
     * 被追着打时两者都加 —— 这是刻意的，不是重复计算（两种心理不同源）。
     */
    public static boolean isHostileLockSource(boolean targetedByHostile) {
        return targetedByHostile;
    }
}
