package com.bingzheng.wuzang.behavior;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 「压力 / 情绪压抑」项的<b>数值型数据源</b>（v0.7.0）。
 *
 * <p>压力是五项行为里唯一<b>不使用强度点</b>的一项：文档明确「效果（按神耗值结算）」，
 * 即按当前数值直接分档（60–80 → 肝气 +1，&gt;80 → 肝气 +2），没有累积过程。
 * 因此它单独一个接口，不与 {@link BehaviorProvider} 的「点」语义混在一起。
 *
 * <p>内置兜底恒为 0（原版没有情绪系统，不凭空造情绪）；
 * 情志拓展在场时顶替为「读它的神耗」。
 */
@FunctionalInterface
public interface PressureSource {

    /** 当前压力数值（情志在场时就是神耗）；读不到返回负值表示「无数据」。 */
    double value(ServerPlayerEntity player);

    default String sourceName() {
        return "未命名压力源";
    }
}
