package com.bingzheng.wuzang.behavior;

import com.bingzheng.wuzang.WuzangConfig;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 行为强度点的<b>数据源</b>（v0.7.0 任务单一：量化规范 v2）。
 *
 * <p>跨拓展顶替与内置兜底都实现这个接口，五脏只认「这一 tick 该累积多少点」，
 * 不关心它从哪儿来 —— 于是「顶替」只是把注册表里的实现换掉，业务代码一行不改。
 *
 * <p><b>返回值是「每 tick 的强度点增量」</b>，不是 0..1 的强度：
 * 连续型行为（受凉、熬夜、劳作）返回 {@link Accrual#perBlock} 折算后的每 tick 速率，
 * 事件型行为（暴食、生冷）在事件那一 tick 返回 {@link Accrual#burst} 的一次性点数。
 * 这样「按行为累积、按恢复递减」在引擎里就是一句加法，且对跳 tick 天然健壮。
 */
@FunctionalInterface
public interface BehaviorProvider {

    /**
     * 本 tick 应累积的强度点。
     *
     * @return 主点与次点的每 tick 增量；无数据/无行为时返回 {@link Accrual#NONE}
     */
    Accrual accrual(ServerPlayerEntity player);

    /** 数据源名称，用于调试输出与自检。 */
    default String sourceName() {
        return "未命名数据源";
    }

    /**
     * 强度点增量。
     *
     * @param primary   主点（受凉点 / 熬夜点 / 劳作点 / 暴食点）
     * @param secondary 次点（只有暴食用的「生冷点」）
     */
    record Accrual(double primary, double secondary) {

        /** 什么都不累积。 */
        public static final Accrual NONE = new Accrual(0.0D, 0.0D);

        /** 每「结算块」（{@link WuzangConfig#POINT_BLOCK_TICKS} = 10 分钟）累积这么多点，折算到每 tick。 */
        public static Accrual perBlock(double primaryPerBlock) {
            return perBlock(primaryPerBlock, 0.0D);
        }

        public static Accrual perBlock(double primaryPerBlock, double secondaryPerBlock) {
            double unit = 1.0D / WuzangConfig.POINT_BLOCK_TICKS;
            return new Accrual(primaryPerBlock * unit, secondaryPerBlock * unit);
        }

        /** 一次性点数（事件型行为，例如「5 分钟内吃超胃容量」的那一次）。 */
        public static Accrual burst(double primary, double secondary) {
            return new Accrual(primary, secondary);
        }

        /** 是否完全没有累积（引擎据此走「按恢复递减」）。 */
        public boolean isIdle() {
            return this.primary <= 0.0D && this.secondary <= 0.0D;
        }
    }
}
