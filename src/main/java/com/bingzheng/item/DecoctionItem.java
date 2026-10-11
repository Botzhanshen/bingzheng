package com.bingzheng.item;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 汤剂成品（如桂枝汤药剂）。服用后施加对应的模组自定义状态效果。
 *
 * <p>只使用本模组自己的效果 Key，绝不复用原版效果 ID，也不改动原版饥饿/生命。
 */
public class DecoctionItem extends Item {

    private final StatusEffect effect;
    private final int durationTicks;
    private final int amplifier;

    public DecoctionItem(StatusEffect effect, int durationTicks, int amplifier, Settings settings) {
        super(settings);
        this.effect = effect;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            user.addStatusEffect(new StatusEffectInstance(this.effect, this.durationTicks, this.amplifier));
            user.sendMessage(Text.translatable("message.bingzhengmod.decoction_drunk", this.getName()), true);

            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack);
    }
}
