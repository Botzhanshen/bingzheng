package com.bingzheng.emotion;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 静坐相关的<b>无状态</b>判定辅助，客户端与服务端共用。
 *
 * <p><b>为什么不在这里判断 isSprinting：</b>神扰会每 tick 强制 setSprinting(true)，
 * 若静坐判定依赖疾跑标记就会形成循环依赖 —— 静坐永远不会成立（这是之前豁免失效的根因）。
 * 静坐的最终裁决放在服务端 MeditationService，判定结果通过
 * EmotionEffects.MEDITATING 这个隐藏标记效果精确同步给客户端。
 */
public final class MeditationRules {

    private MeditationRules() {
    }

    /** 玩家是否正站在静坐坐垫正上方。 */
    public static boolean isOnCushion(PlayerEntity player) {
        if (player == null || !player.isOnGround()) {
            return false;
        }
        BlockPos below = player.getBlockPos().down();
        return player.getWorld().getBlockState(below).isOf(EmotionBlocks.MEDITATION_CUSHION);
    }

    /** 是否正在做会打断静坐的事：使用物品、挥手攻击、睡觉。 */
    public static boolean isBusy(PlayerEntity player) {
        return player == null || player.isUsingItem() || player.handSwinging || player.isSleeping();
    }

    /** 水平速度是否足够小（仅作为服务端「入场」判定的辅助条件）。 */
    public static boolean isStill(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        double vx = player.getVelocity().x;
        double vz = player.getVelocity().z;
        return Math.sqrt(vx * vx + vz * vz) <= EmotionConfig.MEDITATION_STILL_SPEED;
    }

    /**
     * 读取服务端同步过来的静坐标记 —— 客户端画面特效只看这个。
     *
     * <p>这样画面特效与服务端裁决严格一致，不会出现「偶尔闪现」。
     */
    public static boolean isSittingByEffect(PlayerEntity player) {
        return player != null && player.hasStatusEffect(EmotionEffects.MEDITATING);
    }
}