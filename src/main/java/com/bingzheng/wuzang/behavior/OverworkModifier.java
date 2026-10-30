package com.bingzheng.wuzang.behavior;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 行为项的<b>叠加修饰源</b>（v0.7.0）。
 *
 * <p>这是「同一项行为只有一个数据源」的<b>唯一例外</b>，而且是文档明确要求的：
 * 过劳项的主源是情志体耗（或内置劳作计时），而「运动拓展·酸困 &gt; 50」是在主源之上
 * <b>额外</b>再降一点肾气 —— 它修饰的是「劳损程度」，不是「是否在劳作」，
 * 因此不构成同源双算。
 *
 * <p>未安装时恒为 false，过劳项照常按主源分档。
 */
@FunctionalInterface
public interface OverworkModifier {

    /** 酸困是否已达到额外扣减的门槛。 */
    boolean active(ServerPlayerEntity player);

    default String sourceName() {
        return "未命名叠加源";
    }
}
