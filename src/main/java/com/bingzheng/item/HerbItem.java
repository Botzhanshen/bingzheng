package com.bingzheng.item;

import com.bingzheng.herb.HerbProfile;
import com.bingzheng.registry.BingzhengEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 药材物品。行为完全由自身的 {@link HerbProfile} 数据驱动，不涉及任何原版属性的改写。
 *
 * <p><b>所有药材都可以右键「入口尝药」</b>，这正是「生用有毒、炮制去毒」能被玩家直接感知的方式：
 * <ul>
 *   <li>带毒性标记的生药（如生附子）→ 施加本模组自定义的「心脉痹阻」</li>
 *   <li>无毒的药材与炮制产物（如制附子、炙甘草）→ 只提示一句「未觉异样」，不施加任何效果</li>
 * </ul>
 * 中医本来就有「单方」「食疗」的用法，所以药材可入口是设定上站得住的。
 */
public class HerbItem extends Item {

    /** 中毒效果持续时间（tick）。//TODO 后续迁移配置文件 */
    private static final int TOXIC_DURATION_TICKS = 10 * 20;

    private final HerbProfile profile;

    public HerbItem(HerbProfile profile, Settings settings) {
        super(settings);
        this.profile = profile;
    }

    public HerbProfile profile() {
        return this.profile;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            if (this.profile.toxic()) {
                // 「生用直投，立招中毒」——只给我们自己的玩家加我们自己的效果，
                // 没有取消或篡改任何原版事件。
                user.addStatusEffect(new StatusEffectInstance(
                        BingzhengEffects.XINMAI_BIZU, TOXIC_DURATION_TICKS, 0));
                user.sendMessage(Text.translatable(
                        "message.bingzhengmod.toxic_herb_used", this.getName()), true);
            } else {
                // 无毒：包括「生来无毒」与「炮制去毒」两种，入口皆无所苦。
                user.sendMessage(Text.translatable(
                        "message.bingzhengmod.herb_used_plain", this.getName()), true);
            }

            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack);
    }
}
