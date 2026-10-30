package com.bingzheng.emotion;

import com.bingzheng.effect.MarkerEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 【神扰】。
 *
 * <p>与其它标记型效果唯一的区别：在<b>被成功施加的那一刻</b>（{@code onApplied}）
 * 强制清空玩家身上全部七种气机扰动。
 *
 * <p><b>为什么必须挂在效果自己的 onApplied 上，而不是写在合并判定的 if 里：</b>
 * 神扰有多个来源（合并判定、外部模组、管理员指令、未来的病证逻辑）。
 * 只在合并分支里清，等于「只有从那条路进来的神扰才清扰动」，
 * 其它来源进来的神扰会与残留扰动<b>同时存在</b>，
 * 等神扰结束后残留扰动立刻再次满足合并条件，形成连续两轮神扰（Bug-3）。
 * 挂在效果自身后，「只要神扰成功上身，不管从哪来，身上气机扰动一律归零」。
 *
 * <p><b>结束侧同理</b>：{@link #onRemoved} 是「神扰真正消失」的唯一可靠信号，
 * 它把高位情绪衰减、神扰冷却、心绪不宁三件事一次性启动（见 {@link DeliriumController#onDeliriumEnded}）。
 * 但本模组自己的 {@link DeliriumController#extend} / {@link DeliriumController#drain} 用的是
 * 「移除 + 重加」续期，会短暂触发 {@code onRemoved} —— 那种情况由 {@link #beginReissue()}
 * 重入标记挡掉，**不会被误判为神扰结束**（否则挨一次打就把情绪衰减一次）。
 *
 * <p>注意 {@code onApplied} 由原版 {@code LivingEntity#onStatusEffectApplied} 在服务端调用，
 * 且<b>只在新增效果时</b>触发；{@code StatusEffectInstance#update}（同效果续期）不会触发。
 * 因此 {@link DeliriumController#extend} / {@link DeliriumController#drain} 采用
 * 「移除 + 重加」的写法，续期时同样会走到这里。
 */
public class ShenRaoEffect extends MarkerEffect {

    public ShenRaoEffect() {
        super(StatusEffectCategory.HARMFUL, 0x9B1B1B);
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);
        if (entity instanceof ServerPlayerEntity player) {
            // 神扰与气机扰动是「合并态 / 分项态」的关系，不允许共存
            EmotionService.clearDisturbances(player);
        }
    }

    /**
     * 神扰被移除的那一刻 —— <b>神扰真正结束的权威回调</b>。
     *
     * <p>由原版三个路径共同触发，全部都会走到这里：
     * 自然到期（{@code tickStatusEffects} 里 {@code update} 返回 false 后移除）、
     * {@code removeStatusEffect}（指令 / 本模组逻辑）、
     * {@code clearStatusEffects}（不死图腾等）。
     *
     * <p>原版只在服务端调用它，且本方法还用 {@code instanceof ServerPlayerEntity} 再兜一层。
     */
    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);
        if (reissuing || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        DeliriumController.onDeliriumEnded(player);
    }

    // ---------------- 重入标记 ----------------

    /** 本模组正在「移除 + 重加」神扰做续期/削减，此期间的移除不算结束。 */
    private static boolean reissuing;

    public static void beginReissue() {
        reissuing = true;
    }

    public static void endReissue() {
        reissuing = false;
    }
}
