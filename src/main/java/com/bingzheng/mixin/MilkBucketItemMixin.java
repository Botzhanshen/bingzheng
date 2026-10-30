package com.bingzheng.mixin;

import com.bingzheng.protect.MilkSafeEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MilkBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>全项目唯一一处 Mixin（用户明确授权的定点拦截）。</b>
 *
 * <p>目标：原版 {@code MilkBucketItem#finishUsing} 里那一句
 * {@code LivingEntity#clearStatusEffects()}。译文如下（javap 实测，该调用在全方法内只出现一次）：
 * <pre>
 * 66: aload_3
 * 67: invokevirtual #83  // Method net/minecraft/entity/LivingEntity.clearStatusEffects:()Z
 * 70: pop
 * </pre>
 *
 * <p><b>为什么必须是 {@code @Redirect} 而不是 {@code @Inject}：</b>
 * 约束 5 要求优先用 {@code @Inject}，但 {@code @Inject} 只能在调用<i>前后</i>插入代码，
 * 无法「不让那句调用发生」；而在 INVOKE 处 cancel 会让整个 {@code finishUsing} 提前返回，
 * 破坏原版喝水后的物品结算（桶变回空桶、物品扣减）。
 * 只有 {@code @Redirect} 能在<b>不改变原版任何其它行为</b>的前提下替换掉这一句调用。
 *
 * <p><b>失效策略（v0.4.1 决策）：{@code defaultRequire = 0}。</b>
 * 万一别的模组也 {@code @Redirect} 了同一句调用而触发字节码冲突，Mixin 会
 * <b>跳过本注入、打一条 warn 日志、游戏照常启动</b>，而不是让整合包起不来。
 * 代价是「牛奶保护」会在这种极端情况下静默失效（牛奶恢复原版行为）。
 * 这个代价可以承受，因为<b>真正的病根不在状态效果上</b>：
 * 五脏气血（{@code bingzhengmod:wuzang}）、七情与神耗体耗（{@code bingzhengmod:emotion}）、
 * 营卫（{@code bingzhengmod:yingwei}）全部存放在 Fabric Attachment 里，
 * <b>饮用牛奶永远碰不到它们</b>；表现层 buff 就算被清掉，下一轮扫描也会依据底层数值重新挂回。
 *
 * <p><b>本 Mixin 不做的事</b>：不改 {@code MilkBucketItem} 的属性 / 配方 / 消耗行为，
 * 不改原版 {@code clearStatusEffects} 本身，因此 {@code /effect clear}、死亡复活、
 * 不死图腾走的仍然是原版代码路径，清除权限完好无损。
 */
@Mixin(MilkBucketItem.class)
public class MilkBucketItemMixin {

    /** 替换原版「一口牛奶清空全部效果」为「过滤式清空」。 */
    @Redirect(
            method = "finishUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;clearStatusEffects()Z"))
    private boolean bingzhengmod$filteredClearStatusEffects(LivingEntity entity) {
        return MilkSafeEffects.clearForMilk(entity);
    }
}
