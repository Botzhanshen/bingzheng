package com.bingzheng.registry;

import com.bingzheng.BingzhengMod;
import com.bingzheng.effect.HarmonizeExteriorEffect;
import com.bingzheng.effect.MarkerEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 本模组的状态效果注册表。全部使用 {@code bingzhengmod:} 命名空间，绝不复用原版 ID。 */
public final class BingzhengEffects {

    /**
     * 「心脉痹阻」——模组自定义中毒标记的具体表现。
     *
     * <p>文档依据：生附子「生用大毒，直接食用/入药 100% 触发『心脉痹阻』debuff」。
     * 当前阶段只做可见的标记效果，不含任何原版属性改动。
     *
     * <p>//TODO 后续迁移配置文件并补全玩法效果（掉血曲线等），数值一律走配置。
     */
    public static final StatusEffect XINMAI_BIZU =
            register("xinmai_bizu", new MarkerEffect(StatusEffectCategory.HARMFUL, 0x6B2B3A));

    /** 「表气调和」——桂枝汤的功效。 */
    public static final StatusEffect HARMONIZE_EXTERIOR =
            register("harmonize_exterior", new HarmonizeExteriorEffect());

    private BingzhengEffects() {
    }

    private static StatusEffect register(String path, StatusEffect effect) {
        return Registry.register(Registries.STATUS_EFFECT, BingzhengMod.id(path), effect);
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】状态效果注册完成。");
    }
}
