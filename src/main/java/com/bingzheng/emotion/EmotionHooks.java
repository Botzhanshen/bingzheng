package com.bingzheng.emotion;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 情志拓展对外/对内的<b>空钩子接口</b>。
 *
 * <p>五脏、机能、瘟疫、天癸等跨模块联动<b>本次一律不做实际数值变化</b>，
 * 只在这里预留接口。后续模块在自己的初始化里调用 {@link #setPathogen} /
 * {@link #setCrossModule} 注册真实实现即可，情志模块本身无需改动。
 *
 * <p>注意：{@link Pathogen} 会被客户端画面特效读取（用于调整脉冲频率），
 * 所以未来的实现<b>必须在客户端与服务端都注册</b>。
 */
public final class EmotionHooks {

    /** 病邪类型钩子：火邪 / 水寒。用于动态缩放神扰的属性增益与画面脉冲频率。 */
    public interface Pathogen {
        /** 玩家身上是否存在火类病证。 */
        boolean isFireAfflicted(PlayerEntity player);

        /** 玩家身上是否存在水-寒（阴、水饮）类病证。 */
        boolean isColdWaterAfflicted(PlayerEntity player);
    }

    /** 跨模块联动钩子：脏腑恢复、失眠抵消、神扰起止通知。 */
    public interface CrossModule {
        /** 脏腑/营卫的自动恢复是否需要因神扰而暂停。 */
        void onRecoveryPaused(PlayerEntity player, boolean paused);

        /** 冥想抵消失眠（失眠本体不在本次范围，仅记账）。 */
        void consumeInsomniaNights(ServerPlayerEntity player, int nights);

        void onDeliriumStart(ServerPlayerEntity player);

        void onDeliriumEnd(ServerPlayerEntity player);
    }

    /** 默认：无病邪。 */
    private static final Pathogen NO_PATHOGEN = new Pathogen() {
        @Override
        public boolean isFireAfflicted(PlayerEntity player) {
            return false;
        }

        @Override
        public boolean isColdWaterAfflicted(PlayerEntity player) {
            return false;
        }
    };

    /** 默认：什么都不做。 */
    private static final CrossModule NO_CROSS_MODULE = new CrossModule() {
        @Override
        public void onRecoveryPaused(PlayerEntity player, boolean paused) {
            // 预留：未来由脏腑/营卫模块实现
        }

        @Override
        public void consumeInsomniaNights(ServerPlayerEntity player, int nights) {
            // 预留：失眠 debuff 本体尚未实现
        }

        @Override
        public void onDeliriumStart(ServerPlayerEntity player) {
            // 预留
        }

        @Override
        public void onDeliriumEnd(ServerPlayerEntity player) {
            // 预留
        }
    };

    private static Pathogen pathogen = NO_PATHOGEN;
    private static CrossModule crossModule = NO_CROSS_MODULE;

    private EmotionHooks() {
    }

    public static void setPathogen(Pathogen impl) {
        pathogen = impl == null ? NO_PATHOGEN : impl;
    }

    public static void setCrossModule(CrossModule impl) {
        crossModule = impl == null ? NO_CROSS_MODULE : impl;
    }

    public static Pathogen pathogen() {
        return pathogen;
    }

    public static CrossModule crossModule() {
        return crossModule;
    }

    /**
     * 神扰属性增益的病邪缩放系数：
     * 火邪拉高、水寒压低、都没有则为 1.0。
     */
    public static double attributeMultiplier(PlayerEntity player) {
        if (player == null) {
            return 1.0D;
        }
        return EmotionRules.pathogenScale(
                pathogen.isFireAfflicted(player), pathogen.isColdWaterAfflicted(player));
    }
}
