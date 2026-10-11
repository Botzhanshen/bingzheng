package com.bingzheng.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 纯标记型状态效果：只负责让玩家在 HUD 上看到「中了这个证」，不做任何 tick 行为。
 *
 * <p>刻意保持无副作用，是为了严格遵守约束 1：不改原版生命/饥饿/体温属性。
 * 具体玩法效果待后续阶段按需实现。
 */
public class MarkerEffect extends StatusEffect {

    public MarkerEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
