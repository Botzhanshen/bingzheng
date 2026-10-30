package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.bingzheng.attachment.BingzhengAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 情志拓展的服务端主循环。
 *
 * <p><b>全部数值后台隐形运行，玩家看不到任何进度条或数字。</b>
 * 反馈只通过状态效果、聊天消息与画面特效传达。
 *
 * <p>时序：先处理神扰起止与行为，再处理静坐，最后才是各类周期性衰减与阈值扫描。
 * 衰减与扫描都按固定间隔执行，<b>不是每 tick 都跑</b>，以控制开销。
 */
public final class EmotionService {

    /** 上一 tick 的血量，用于识别「受到掉血伤害」。不落盘，离线即清理。 */
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    /**
     * 气机扰动的<b>迟滞锁定</b>（v0.7.0）：某条情绪生成过一次扰动后即被锁定，
     * 要等它的稳定值跌到「触发阈值 − 迟滞带」以下才重新武装。
     * 运行期数据（不落盘）：丢锁最坏只是多生成一次扰动，不影响任何持久化数值。
     */
    private static final Map<UUID, boolean[]> DISTURBANCE_LATCH = new HashMap<>();

    private EmotionService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EmotionService::onServerTick);
        // v0.8.0 五志行为驱动的事件接线（全部为后置/只读监听，不取消任何原版行为）
        EmotionBehaviorEvents.register();
        EmotionHudSync.registerServer();
        EmotionFatigue.register();
        EmotionFatigueCommands.register();
        // 服务器停机时清掉波动状态：波动不落盘，下次开服从 0 平滑逼近当前时辰目标
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EmotionRhythm.reset();
            EmotionBehaviorPoints.reset();
        });
    }

    private static void onServerTick(MinecraftServer server) {
        long tick = server.getTicks();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // 七情时辰波动：每 tick 推进一次（纯表现层，只产出「现值」，绝不写回附件）
        EmotionRhythm.tick(server, tick);

        // v0.8.1：物品结局的结算挪到实体迭代之外（ENTITY_UNLOAD 里只入队，不查世界）
        EmotionBehaviorPoints.drainItemFates(server);

        // v0.9.0：神耗 / 体耗 HUD 同步（只发给本人；变化即推，未变化每秒兜底）
        EmotionHudSync.tick(server, tick);

        // 清理离线玩家的血量记录，避免长期驻留
        if (LAST_HEALTH.size() > players.size()) {
            Set<UUID> online = new HashSet<>();
            for (ServerPlayerEntity p : players) {
                online.add(p.getUuid());
            }
            LAST_HEALTH.keySet().retainAll(online);
            DISTURBANCE_LATCH.keySet().retainAll(online);
            EmotionBehaviorPoints.prune(online);
            EmotionFatigue.prune(online);
            EmotionSleep.prune(online);
            EmotionFatigueCommands.prune(online);
            for (UUID uuid : java.util.List.copyOf(LAST_HEALTH.keySet())) {
                // 占位：LAST_HEALTH 已在上面 retain；HUD 的发送缓存按需清理
                EmotionHudSync.forget(uuid);
            }
            MeditationService.prune(online);
            EmotionDebugRecorder.prune(online);
        }

        for (ServerPlayerEntity player : players) {
            try {
                tickPlayer(server, player, tick);
            } catch (Exception e) {
                // 单个玩家出错不能拖垮整个 tick 循环
                BingzhengMod.LOGGER.error("【病证·情志】玩家 {} 的情志 tick 出错", player.getName().getString(), e);
            }
        }
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity player, long tick) {
        EmotionState state = EmotionAttachments.get(player);
        boolean dirty = false;

        // 受伤害检测：纯观察血量变化，不拦截、不取消任何伤害事件（符合后置监听原则）
        Float last = LAST_HEALTH.get(player.getUuid());
        float health = player.getHealth();
        boolean damaged = last != null && health < last - 0.01F;
        LAST_HEALTH.put(player.getUuid(), health);

        // 神扰（起止迁移 + 行为 + 属性）
        dirty |= DeliriumController.tick(player, state, tick, damaged);

        // 静坐 / 夜间冥想
        dirty |= MeditationService.tick(player, state, tick, damaged);

        // 五志行为驱动（点数累积 / 结算 / 恢复；忧惊的联动项也在这里落地）
        dirty |= EmotionBehaviorPoints.tick(player, state, tick);

        // v0.9.1 神耗 / 体耗量化 v1（六个来源累积 + 每时辰回落 + 睡到天亮归 0）
        dirty |= EmotionFatigue.tick(player, state, tick);

        // v0.9.5 睡眠：恢复梯度 / 睡眠债 / 日照计数（只认本人上床且睡到天亮）
        dirty |= EmotionSleep.tick(player, state);

        // 疲劳调试命令的周期打印（仅对开了 fatigue_debug 的玩家）
        EmotionFatigueCommands.tick(player, tick);

        // 冷却递减
        if (state.getCooldownDelirium() > 0) {
            state.setCooldownDelirium(state.getCooldownDelirium() - 1);
            dirty = true;
        }

        // 心绪不宁：递减 + 微弱心悸抖动（不会生成气机扰动）
        if (state.getRestlessTicks() > 0) {
            state.setRestlessTicks(state.getRestlessTicks() - 1);
            dirty = true;
            if (tick % EmotionConfig.RESTLESS_JITTER_INTERVAL == 0) {
                restlessJitter(player, state);
            }
        }

        // 调试打印（默认关闭，见 EmotionDebugRecorder）
        EmotionDebugRecorder.tick(player, state, tick);

        // 七情向各自基线缓慢回落
        if (tick % EmotionConfig.EMOTION_DECAY_INTERVAL == 0) {
            dirty |= decayEmotions(state);
        }

        // 神耗 / 体耗自然衰减 + 营卫自动恢复
        if (tick % EmotionConfig.CONSUMPTION_DECAY_INTERVAL == 0) {
            dirty |= decayConsumption(player, state, tick);
        }

        // 阈值扫描 + 合并判定 + 神安管理
        if (tick % EmotionConfig.MERGE_SCAN_INTERVAL == 0) {
            dirty |= scan(player, state);
        }

        if (dirty) {
            EmotionAttachments.set(player, state);
        }
    }

    // ---------------- 对外增减 API（「增减事件钩子」的落点）----------------

    /**
     * 由外部事件推高/压低某一情。未来的熬夜、恐惧、受伤、剧痛、汤药、病证等模块都调用这里。
     *
     * <p>//TODO 后续迁移外部配置：各事件的具体增减幅度一律走配置。
     */
    public static void addEmotion(ServerPlayerEntity player, EmotionType type, float delta) {
        EmotionState state = EmotionAttachments.get(player);
        state.addEmotion(type, delta);
        EmotionAttachments.set(player, state);
    }

    /** 外部事件改动神耗（正数上涨，负数缓解）。 */
    public static void addSpiritConsumption(ServerPlayerEntity player, float delta) {
        EmotionState state = EmotionAttachments.get(player);
        state.addSpiritConsumption(delta);
        EmotionAttachments.set(player, state);
    }

    /** 外部事件改动体耗。 */
    public static void addBodyConsumption(ServerPlayerEntity player, float delta) {
        EmotionState state = EmotionAttachments.get(player);
        state.addBodyConsumption(delta);
        EmotionAttachments.set(player, state);
    }

    // ---------------- 衰减 ----------------

    private static boolean decayEmotions(EmotionState state) {
        boolean changed = false;
        for (EmotionType type : EmotionType.values()) {
            // v0.8.0：五志的稳定值改由「行为驱动 + 每时辰 −1」管理，
            // 旧的 2%/40t 插值衰减会把行为推高的 1 点在 1 秒内抹平，故默认跳过。
            if (!EmotionConfig.LEGACY_DECAY_FOR_BEHAVIOR_EMOTIONS
                    && EmotionBehaviorRules.isFiveZhi(type)) {
                continue;
            }
            float current = state.emotion(type);
            float baseline = state.baselineOf(type);
            if (Math.abs(current - baseline) < 0.01F) {
                continue;
            }
            float next = EmotionRules.decayStep(current, baseline);
            if (next == current) {
                continue;
            }
            state.setEmotion(type, next);
            changed = true;
        }
        return changed;
    }

    private static boolean decayConsumption(ServerPlayerEntity player, EmotionState state, long tick) {
        boolean changed = false;
        boolean shenAn = player.hasStatusEffect(EmotionEffects.SHEN_AN);

        // 【v0.9.1】自然回落下沉到 EmotionFatigue（每时辰 −1，神安加速）；
        // 这里不再叠加旧的 0.25/0.35-per-40t 衰减 —— 那会让「每 10 分钟 +1」的累积永远涨不上去。
        // 神安对回落速度的影响由 EmotionFatigueRules.SHEN_AN_DECAY_MULTIPLIER 保留。

        // 营卫的「自动恢复」由本模块提供（内核只有汤药驱动的恢复）。
        // 神扰期间暂停；神安期间加速。仅通过内核的公开 API 读写，未修改内核任何代码。
        if (EmotionConfig.EMOTION_PROVIDES_YINGWEI_AUTO_RECOVERY
                && tick % EmotionConfig.YINGWEI_RECOVERY_INTERVAL == 0
                && !player.hasStatusEffect(EmotionEffects.SHEN_RAO)) {
            int amount = EmotionConfig.YINGWEI_AUTO_RECOVERY
                    + (shenAn ? EmotionConfig.SHEN_AN_YINGWEI_RECOVERY : 0);
            if (BingzhengAttachments.getYingwei(player) < BingzhengAttachments.YINGWEI_MAX) {
                BingzhengAttachments.addYingwei(player, amount);
            }
        }
        return changed;
    }

    /**
     * 心绪不宁期间的心悸抖动：对若干条情绪做小幅度随机扰动。
     *
     * <p>通过 EmotionRules.restlessJitterClamp 保证<b>绝不会自己生成气机扰动</b>。
     */
    private static void restlessJitter(ServerPlayerEntity player, EmotionState state) {
        net.minecraft.util.math.random.Random random = player.getRandom();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            EmotionType type = EmotionType.byIndex(random.nextInt(EmotionType.COUNT));
            float current = state.emotion(type);
            float delta = (random.nextFloat() * 2.0F - 1.0F) * EmotionConfig.RESTLESS_JITTER;
            state.setEmotion(type, EmotionRules.restlessJitterClamp(current, current + delta));
        }
    }

    // ---------------- 阈值扫描 / 合并 / 神安 ----------------

    private static boolean scan(ServerPlayerEntity player, EmotionState state) {
        boolean dirty = false;
        boolean hasDelirium = player.hasStatusEffect(EmotionEffects.SHEN_RAO);

        // 【Bug-3 修复】神扰期间绝不允许存在气机扰动。
        // 旧写法把清理写在合并分支的 if 里，只覆盖「合并的那一瞬间」；
        // 而神扰激活的几十秒内扫描仍在按阈值生成扰动，它们会活过神扰结束的那一刻，
        // 冷却一过立刻二次合并 —— 表现为「连续跳两轮神扰」。
        if (!EmotionRules.shouldRaiseDisturbance(hasDelirium)) {
            // 防御性清扫：覆盖任何外部来源（其它模组 / 指令 / 旧存档）残留的扰动
            if (countDisturbances(player) > 0) {
                clearDisturbances(player);
            }
        } else {
            // a) 单项冲破阈值 → 生成对应气机扰动
            // 【v0.7.0 迟滞】判定只认**稳定值**（波动与抖动的瞬时值不参与）；同一条情绪生成过
            // 一次扰动后即被锁定，要等稳定值跌破「触发阈值 − 迟滞带」才重新武装 ——
            // 于是「贴着阈值反复生成扰动 → 反复合并神扰」的闪断不再可能。
            boolean[] latch = DISTURBANCE_LATCH.computeIfAbsent(player.getUuid(),
                    key -> new boolean[EmotionType.COUNT]);
            for (EmotionType type : EmotionType.values()) {
                float stable = state.emotion(type);
                int slot = type.index();
                if (latch[slot] && !EmotionRules.latchSurvives(stable, true)) {
                    latch[slot] = false;                 // 稳定值已跌破解除线 → 重新武装
                }
                if (!EmotionRules.disturbanceArmed(stable, latch[slot])) {
                    continue;                            // 未达阈值，或仍处于迟滞锁定中
                }
                if (player.hasStatusEffect(EmotionEffects.disturbanceOf(type))) {
                    continue;
                }
                // 已满额则舍弃强度最低的那一个（强度按对应七情数值衡量）
                if (countDisturbances(player) >= EmotionConfig.DISTURBANCE_MAX) {
                    discardWeakest(player, state);
                }
                player.addStatusEffect(new StatusEffectInstance(
                        EmotionEffects.disturbanceOf(type), EmotionConfig.DISTURBANCE_DURATION, 0));
                player.sendMessage(Text.translatable("message.bingzhengmod.disturbance_arise",
                        type.zhName(), type.disturbanceZhName()), true);
                latch[slot] = true;                      // 触发后锁定，直到跌回解除线
            }

            // b) 合并判定：≥2 个任意气机扰动 → 神扰（冷却期间跳过）
            // 冷却只拦「神扰生成」，不阻止新的气机扰动产生 —— 这是刻意的设计。
            if (EmotionRules.shouldMerge(countDisturbances(player), state.getCooldownDelirium())) {
                // 清理动作已移到 ShenRaoEffect.onApplied：只要神扰成功上身，不管什么来源，扰动一律归零
                player.addStatusEffect(new StatusEffectInstance(
                        EmotionEffects.SHEN_RAO, EmotionConfig.DELIRIUM_BASE_TICKS, 0));
                clearDisturbances(player);   // 双保险：即便 onApplied 因任何原因未触发，也不留残留
                // 神扰与神安强制互斥
                player.removeStatusEffect(EmotionEffects.SHEN_AN);
                player.sendMessage(Text.translatable("message.bingzhengmod.delirium_merge"), false);
            }
        }

        // c) 神安：无神扰且 0 个气机扰动时自动挂上
        dirty |= manageShenAn(player, state);

        return dirty;
    }

    /** 统计玩家身上存活的气机扰动数量。 */
    public static int countDisturbances(ServerPlayerEntity player) {
        int count = 0;
        for (EmotionType type : EmotionType.values()) {
            if (player.hasStatusEffect(EmotionEffects.disturbanceOf(type))) {
                count++;
            }
        }
        return count;
    }

    /** 删除玩家身上全部气机扰动。 */
    public static void clearDisturbances(ServerPlayerEntity player) {
        for (EmotionType type : EmotionType.values()) {
            player.removeStatusEffect(EmotionEffects.disturbanceOf(type));
        }
    }

    /** 舍弃强度最低的那个气机扰动（强度按对应七情数值衡量）。 */
    private static void discardWeakest(ServerPlayerEntity player, EmotionState state) {
        EmotionType weakest = null;
        float min = Float.MAX_VALUE;
        for (EmotionType type : EmotionType.values()) {
            if (!player.hasStatusEffect(EmotionEffects.disturbanceOf(type))) {
                continue;
            }
            float v = state.emotion(type);
            if (v < min) {
                min = v;
                weakest = type;
            }
        }
        if (weakest != null) {
            player.removeStatusEffect(EmotionEffects.disturbanceOf(weakest));
            player.sendMessage(Text.translatable("message.bingzhengmod.disturbance_discarded",
                    weakest.disturbanceZhName()), true);
        }
    }

    /** 神安：无神扰且无气机扰动时自动挂上；一旦出现扰动立即移除。 */
    private static boolean manageShenAn(ServerPlayerEntity player, EmotionState state) {
        boolean hasDelirium = player.hasStatusEffect(EmotionEffects.SHEN_RAO);
        boolean shouldHave = EmotionRules.shouldHaveShenAn(
                hasDelirium, countDisturbances(player), state.getRestlessTicks() > 0);
        boolean has = player.hasStatusEffect(EmotionEffects.SHEN_AN);

        if (shouldHave && !has) {
            // 无限时长：-1 为原版约定的无限
            player.addStatusEffect(new StatusEffectInstance(EmotionEffects.SHEN_AN, -1, 0, false, true));
            player.sendMessage(Text.translatable("message.bingzhengmod.shen_an_gained"), true);
            return true;
        }
        if (!shouldHave && has) {
            player.removeStatusEffect(EmotionEffects.SHEN_AN);
            return true;
        }
        return false;
    }
}
