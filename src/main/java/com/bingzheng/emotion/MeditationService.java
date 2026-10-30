package com.bingzheng.emotion;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 静坐 / 夜间冥想。
 *
 * <p>静坐状态由服务端裁决：
 * <ol>
 *   <li><b>入场</b>：站在坐垫上、无动作、水平速度足够小，<b>连续</b>满足若干 tick 才成立；</li>
 *   <li><b>维持</b>：入场时记录位置锚点，只要不离开坐垫、不偏离锚点、不受伤害就一直保持；</li>
 *   <li><b>退出</b>：离开坐垫 / 有动作 / 偏离锚点 / 受伤。退出是<b>瞬时</b>的。</li>
 * </ol>
 *
 * <p>用「位置锚点」而不是「瞬时速度」做维持条件，是因为神扰会注入随机横向速度，
 * 瞬时速度会被污染从而造成判定抖动（旧版豁免失效的第二个原因）。
 *
 * <p>静坐期间三件事并行：消耗神扰剩余时间、夜间冥想计数、降低神耗；对体耗无效果。
 * 被打断时已经完成的数据<b>不会回滚</b>。
 */
public final class MeditationService {

    private static final Map<UUID, SitTracker> TRACKERS = new HashMap<>();

    private static final class SitTracker {
        private double anchorX;
        private double anchorZ;
        private int stillTicks;
        private boolean sitting;
    }

    private MeditationService() {
    }

    /** 服务端裁决：该玩家当前是否处于静坐状态。 */
    public static boolean isSitting(ServerPlayerEntity player) {
        SitTracker tracker = TRACKERS.get(player.getUuid());
        return tracker != null && tracker.sitting;
    }

    /** 清理离线玩家的追踪状态。 */
    public static void prune(Set<UUID> online) {
        TRACKERS.keySet().retainAll(online);
    }

    public static boolean tick(ServerPlayerEntity player, EmotionState state, long tick, boolean damaged) {
        boolean sitting = updateSitState(player, damaged);
        syncMarker(player, sitting);

        if (!sitting) {
            // 打断：本轮冥想进度清零（已完成的轮次与已扣减的神扰时间保持不变）
            if (state.getMeditationProgress() != 0) {
                state.setMeditationProgress(0);
                return true;
            }
            return false;
        }

        boolean dirty = false;

        // 1) 消耗神扰剩余时间（唯一允许主动缩短神扰的途径）
        if (player.hasStatusEffect(EmotionEffects.SHEN_RAO)
                && tick % EmotionConfig.MEDITATION_DRAIN_INTERVAL == 0) {
            DeliriumController.drain(player, EmotionConfig.MEDITATION_DELIRIUM_DRAIN);
        }

        // 2) 夜间冥想累计（白天静坐不计入）
        if (player.getWorld().isNight()) {
            state.setMeditationProgress(state.getMeditationProgress() + 1);
            dirty = true;
            if (state.getMeditationProgress() >= EmotionConfig.MEDITATION_NIGHT_SESSION_TICKS) {
                state.setMeditationProgress(0);
                state.setMeditationNightCounter(state.getMeditationNightCounter() + 1);
                player.sendMessage(Text.translatable("message.bingzhengmod.meditation_session_done",
                        state.getMeditationNightCounter()), true);

                if (state.getMeditationNightCounter() >= EmotionConfig.MEDITATION_SESSIONS_TO_CLEAR) {
                    int nights = EmotionConfig.MEDITATION_SESSIONS_TO_CLEAR;
                    state.setMeditationNightCounter(0);
                    EmotionHooks.crossModule().consumeInsomniaNights(player, nights);
                    player.sendMessage(Text.translatable(
                            "message.bingzhengmod.meditation_insomnia_cleared", nights), false);
                }
            }
        }

        // 3) 降低神耗（对体耗无效果）
        if (tick % EmotionConfig.MEDITATION_RELIEF_INTERVAL == 0) {
            state.addSpiritConsumption(-EmotionConfig.MEDITATION_SPIRIT_RELIEF);
            dirty = true;
        }

        return dirty;
    }

    private static boolean updateSitState(ServerPlayerEntity player, boolean damaged) {
        SitTracker tracker = TRACKERS.computeIfAbsent(player.getUuid(), k -> new SitTracker());
        boolean onCushion = MeditationRules.isOnCushion(player);
        boolean busy = MeditationRules.isBusy(player);

        if (tracker.sitting) {
            double dx = player.getX() - tracker.anchorX;
            double dz = player.getZ() - tracker.anchorZ;
            boolean displaced = Math.sqrt(dx * dx + dz * dz) > EmotionConfig.MEDITATION_LEAVE_RADIUS;
            if (!onCushion || busy || displaced || damaged) {
                tracker.sitting = false;
                tracker.stillTicks = 0;
            }
            return tracker.sitting;
        }

        if (!onCushion || busy) {
            tracker.stillTicks = 0;
            return false;
        }
        if (MeditationRules.isStill(player)) {
            tracker.stillTicks++;
            if (tracker.stillTicks >= EmotionConfig.MEDITATION_ENTER_TICKS) {
                tracker.sitting = true;
                tracker.anchorX = player.getX();
                tracker.anchorZ = player.getZ();
            }
        } else {
            tracker.stillTicks = 0;
        }
        return tracker.sitting;
    }

    /** 把静坐状态通过隐藏标记效果同步给客户端；玩家完全看不见它。 */
    private static void syncMarker(ServerPlayerEntity player, boolean sitting) {
        boolean has = player.hasStatusEffect(EmotionEffects.MEDITATING);
        if (sitting && !has) {
            // 无限时长、ambient、不显示粒子、不显示图标 —— 纯同步用
            player.addStatusEffect(new StatusEffectInstance(
                    EmotionEffects.MEDITATING, -1, 0, true, false, false));
        } else if (!sitting && has) {
            player.removeStatusEffect(EmotionEffects.MEDITATING);
        }
    }
}