package com.bingzheng.effect;

import com.bingzheng.attachment.BingzhengAttachments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * 「表气调和」（{@code bingzhengmod:harmonize_exterior}）。
 *
 * <p>文档依据：桂枝汤功效「解肌发表、调和营卫」。
 * 本效果的行为是<b>缓慢恢复本模组自定义的营卫值</b>，
 * 绝不触碰原版饥饿、生命或任何原版属性（约束 1）。
 */
public class HarmonizeExteriorEffect extends StatusEffect {

    /** 每多少 tick 恢复一次。//TODO 后续迁移配置文件 */
    private static final int RECOVER_EVERY_TICKS = 20;

    /** 每上涨多少点提示一次（避免刷屏）。//TODO 后续迁移配置文件 */
    private static final int NOTIFY_EVERY_POINTS = 10;

    public HarmonizeExteriorEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xD6A44C);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % RECOVER_EVERY_TICKS == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) {
            return; // 数值只由服务端裁决
        }
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        int before = BingzhengAttachments.getYingwei(player);
        if (before >= BingzhengAttachments.YINGWEI_MAX) {
            return;
        }
        int now = BingzhengAttachments.addYingwei(player, 1 + amplifier);

        boolean reachedMax = now >= BingzhengAttachments.YINGWEI_MAX;
        boolean crossedStep = before / NOTIFY_EVERY_POINTS != now / NOTIFY_EVERY_POINTS;
        if (reachedMax || crossedStep) {
            player.sendMessage(Text.translatable("message.bingzhengmod.yingwei_value",
                    now, BingzhengAttachments.YINGWEI_MAX), true);
        }
    }
}
