package com.bingzheng.wuzang;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 五脏表现层的纯标记型状态效果。
 *
 * <p>刻意<b>不复用内核的 {@code MarkerEffect}</b>：五脏要对独立拆包友好，本包不引用
 * {@code com.bingzheng} 下的既有类。行为上与内核的标记效果完全一致 ——
 * 只让玩家在 HUD 上看到「这一脏出问题了」，不做任何 tick 行为、
 * <b>不带任何属性修饰</b>，绝不碰原版生命/移速/饥饿属性。
 *
 * <p>血脉只有「血虚」一途，不存在「血亢」效果（文档没有这个设定）。
 */
public class WuzangMarkerEffect extends StatusEffect {

    public WuzangMarkerEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
