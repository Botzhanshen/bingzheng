package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.bingzheng.effect.MarkerEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 情志拓展的状态效果注册表。全部使用 {@code bingzhengmod:} 命名空间。
 *
 * <p>其中<b>七种气机扰动仅作为标记存在</b>：不带任何移速/攻速/生命修饰，
 * 属性改动只发生在【神扰】激活之后。
 *
 * <p>全部效果都复用内核已有的 {@link MarkerEffect}（只读复用，未修改内核代码），
 * 保证不会直接覆写原版任何底层字段。
 */
public final class EmotionEffects {

    private static final StatusEffect[] DISTURBANCES = new StatusEffect[EmotionType.COUNT];

    /** 怒 → 肝气亢。 */
    public static final StatusEffect GAN_QI_KANG = disturbance(EmotionType.ANGER, 0x8B3A3A);
    /** 喜 → 心神扬。 */
    public static final StatusEffect XIN_SHEN_YANG = disturbance(EmotionType.JOY, 0xC24A5A);
    /** 忧 → 肺气郁。 */
    public static final StatusEffect FEI_QI_YU = disturbance(EmotionType.WORRY, 0x6E7B8B);
    /** 思 → 脾意缠。 */
    public static final StatusEffect PI_YI_CHAN = disturbance(EmotionType.PENSIVENESS, 0x9A7B4F);
    /** 悲 → 肺气消。 */
    public static final StatusEffect FEI_QI_XIAO = disturbance(EmotionType.GRIEF, 0x5C6B7A);
    /** 恐 → 肾气慑。 */
    public static final StatusEffect SHEN_QI_SHE = disturbance(EmotionType.FEAR, 0x3F3A55);
    /** 惊 → 神乍摇。 */
    public static final StatusEffect SHEN_ZHA_YAO = disturbance(EmotionType.FRIGHT, 0x7A4E8B);

    /**
     * 【神扰】——由 ≥2 个气机扰动合并而来，全部属性改动只在这里发生。
     *
     * <p>注册的是 {@link ShenRaoEffect}：它在<b>被成功施加的那一刻</b>强制清空全部气机扰动（Bug-3 修复）。
     * <b>不要退回普通 {@code MarkerEffect}</b> —— 那样 onApplied 钩子会丢失，自检会立刻失败。
     */
    public static final StatusEffect SHEN_RAO =
            register("shen_rao", new ShenRaoEffect());

    /**
     * 【静坐中】——<b>纯同步标记，玩家完全看不见</b>（showIcon=false）。
     *
     * <p>存在的唯一原因：把服务端裁决的静坐状态<b>精确同步</b>给客户端，
     * 让画面特效的开关和服务端严格一致，避免客户端自行推断造成「偶尔闪现」。
     * 它不参与任何玩法判定，也不在 HUD 上显示。
     */
    public static final StatusEffect MEDITATING =
            register("meditating", new MarkerEffect(StatusEffectCategory.NEUTRAL, 0x3F6B5A));

    /** 【神安】——无扰动、无神扰时自动挂上的稳态增益。与神扰强制互斥。 */
    public static final StatusEffect SHEN_AN =
            register("shen_an", new MarkerEffect(StatusEffectCategory.BENEFICIAL, 0x4E9B7A));

    private EmotionEffects() {
    }

    private static StatusEffect disturbance(EmotionType type, int color) {
        StatusEffect effect = register(type.disturbancePath(),
                new MarkerEffect(StatusEffectCategory.HARMFUL, color));
        DISTURBANCES[type.index()] = effect;
        return effect;
    }

    private static StatusEffect register(String path, StatusEffect effect) {
        return Registry.register(Registries.STATUS_EFFECT, BingzhengMod.id(path), effect);
    }

    /** 取某一情对应的气机扰动效果。 */
    public static StatusEffect disturbanceOf(EmotionType type) {
        return DISTURBANCES[type.index()];
    }

    /** 七种气机扰动的全部效果。 */
    public static StatusEffect[] allDisturbances() {
        return DISTURBANCES.clone();
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证·情志】状态效果注册完成（7 气机扰动 + 神扰(带 onApplied 清理钩子) + 神安 + 静坐标记）。");
    }
}
