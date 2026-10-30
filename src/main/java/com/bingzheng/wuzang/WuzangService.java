package com.bingzheng.wuzang;

import com.bingzheng.wuzang.behavior.EmotionAuthority;
import com.bingzheng.wuzang.behavior.WuzangBehavior;
import com.bingzheng.wuzang.behavior.WuzangBehaviorPoints;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRules;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRegistry;
import com.bingzheng.wuzang.behavior.WuzangFallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 五脏拓展的服务端主循环。
 *
 * <p><b>全部数值后台隐形运行，玩家看不到任何进度条或数字。</b>
 * 反馈只通过状态效果与聊天消息传达。
 *
 * <p>时序（每 {@link WuzangConfig#SCAN_INTERVAL} tick 一轮，节流非每 tick）：
 * <ol>
 *   <li>受伤失血（优先心、肝之血）</li>
 *   <li>自然向基线恢复（静坐 / 睡眠加速）</li>
 *   <li>情志信号 → 对应脏腑气血增减</li>
 *   <li>重算虚实标记 → 同步表现层 buff → 触发预留钩子</li>
 * </ol>
 */
public final class WuzangService {

    private static final String KEY_QI = "qi";
    private static final String KEY_BLOOD = "blood";

    /** 上一 tick 的血量，用于识别实际受伤。纯观察，不拦截不取消任何伤害。 */
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
    /** 两次扫描之间累积的受伤量。 */
    private static final Map<UUID, Float> PENDING_DAMAGE = new HashMap<>();
    /** 上一轮扫描的虚实标记位图，用于识别「新出现」的标记并触发钩子。 */
    private static final Map<UUID, Integer> MARKER_BITS = new HashMap<>();
    /** 上一轮扫描时身上已有的情志信号，用于边界触发。 */
    private static final Map<UUID, Set<String>> ACTIVE_SIGNALS = new HashMap<>();

    private WuzangService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(WuzangService::onServerTick);
        // 破坏方块：AFTER 是纯后置回调，只累加「劳作点数」，不取消、不修改任何掉落
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WuzangFallback.onBlockBreak(serverPlayer);
            }
        });
        // 死亡这一刻先把「尚未结算的实际掉血」补结掉，再清运行期缓存。
        // 不能只靠复活时清缓存：否则「死前最后一击到底算不算」会取决于
        // 20 tick 扫描有没有恰好落在死亡与复活之间 —— 必须让它确定。
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                flushPendingInjury(player);
                forget(player.getUuid());
            }
        });
        // 复活后清运行期缓存（UUID 不变，但血量基准要重新取）。
        // 注意：五脏气血本身【不】重置 —— 附件是 copyOnDeath 的，数值跨死亡保留。
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> forget(newPlayer.getUuid()));
        // 服务器停机时清掉时辰波动状态：波动不落盘，下次开服从 0 平滑逼近当前时辰目标。
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WuzangRhythm.reset());
    }

    /** 把累积的受伤立刻结算到五脏气血上（死亡时补结，保证口径确定）。 */
    private static void flushPendingInjury(ServerPlayerEntity player) {
        Float pending = PENDING_DAMAGE.get(player.getUuid());
        if (pending == null || pending <= 0.0F) {
            return;
        }
        PENDING_DAMAGE.put(player.getUuid(), 0.0F);
        WuzangState state = WuzangAttachments.get(player);
        applyInjury(player, state, pending);
        WuzangAttachments.set(player, state);
    }

    /** 清掉某玩家的全部运行期缓存。 */
    public static void forget(UUID id) {
        WuzangFallback.forget(id);
        WuzangBehaviorPoints.forget(id);
        LAST_HEALTH.remove(id);
        PENDING_DAMAGE.remove(id);
        MARKER_BITS.remove(id);
        ACTIVE_SIGNALS.remove(id);
    }

    private static void onServerTick(MinecraftServer server) {
        long tick = server.getTicks();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // 受伤检测：纯观察血量变化。
        // 【v0.4.1 决策：维持现实现，不切换为 ALLOW_DAMAGE 事件】
        // javap 实测 Fabric API 0.92.12 的 ServerLivingEntityEvents 只有
        // ALLOW_DAMAGE / ALLOW_DEATH / AFTER_DEATH，没有 AFTER_DAMAGE；
        // 而 ALLOW_DAMAGE 是「是否允许受伤」的闸门，用它计损会对
        // 被盾牌格挡、实际没有掉血的攻击也扣除心肝血 —— 与本设计不符。
        // 因此维持「统计实际血量差值」：只有真掉血才消耗五脏之血。
        for (ServerPlayerEntity player : players) {
            UUID id = player.getUuid();
            Float last = LAST_HEALTH.get(id);
            float health = player.getHealth();
            if (last != null && health < last - 0.01F) {
                PENDING_DAMAGE.merge(id, last - health, Float::sum);
            }
            LAST_HEALTH.put(id, health);
        }

        if (LAST_HEALTH.size() > players.size()) {
            Set<UUID> online = new HashSet<>();
            for (ServerPlayerEntity player : players) {
                online.add(player.getUuid());
            }
            LAST_HEALTH.keySet().retainAll(online);
            PENDING_DAMAGE.keySet().retainAll(online);
            MARKER_BITS.keySet().retainAll(online);
            ACTIVE_SIGNALS.keySet().retainAll(online);
            WuzangFallback.prune(online);
            WuzangBehaviorPoints.prune(online);
            WuzangDebugRecorder.prune(online);
        }

        // 行为观测每 tick 更新（纯观察）：先识别进食事件，再让强度点引擎累积/递减
        WuzangFallback.tick(players, tick);
        WuzangBehaviorPoints.tick(players, tick);

        // 时辰波动每 tick 推进（纯表现层：只产生「现值」，绝不写回附件、不参与判定）
        WuzangRhythm.tick(server, tick);

        if (tick % WuzangConfig.SCAN_INTERVAL != 0) {
            return;
        }
        for (ServerPlayerEntity player : players) {
            try {
                scan(player, tick);
            } catch (Exception e) {
                Wuzang.LOGGER.error("【病证·五脏】玩家 {} 的五脏扫描出错", player.getName().getString(), e);
            }
        }
    }

    private static void scan(ServerPlayerEntity player, long tick) {
        WuzangState state = WuzangAttachments.get(player);

        // ① 受伤失血
        Float pending = PENDING_DAMAGE.get(player.getUuid());
        if (pending != null && pending > 0.0F) {
            PENDING_DAMAGE.put(player.getUuid(), 0.0F);
            applyInjury(player, state, pending);
        }

        // ② 自然向基线恢复（静坐 / 睡眠加速）
        if (tick % WuzangConfig.RESTORE_INTERVAL == 0) {
            boolean meditating = WuzangLinks.hasEffect(player, WuzangLinks.MEDITATING_PATH);
            boolean sleeping = player.isSleeping();
            float step = WuzangConfig.RESTORE_STEP
                    * WuzangRules.restoreMultiplier(meditating, sleeping);
            restoreToBaseline(state, step);
        }

        // ③ 情志信号（边界触发：信号新出现时结算一次）
        applyEmotionSignals(player, state);

        // ③.5 行为驱动（持续式：每轮扫描按强度折算气血升降）
        applyBehaviors(player, state);

        // ④ 标记重算（数值是唯一权威）
        state.refreshMarkers();

        // ⑤ 表现层同步 + 预留钩子
        syncEffects(player, state);

        // 每轮都写回，保证落盘（数值较小，20 tick 一次的开销可忽略）
        WuzangAttachments.set(player, state);
        WuzangDebugRecorder.tick(player, state, tick);
    }

    // ---------------- 受伤 ----------------

    /** 受伤失血：按「优先心、肝之血」的次序扣除。 */
    private static void applyInjury(ServerPlayerEntity player, WuzangState state, float damage) {
        float loss = WuzangRules.bloodLossForDamage(damage);
        if (loss <= 0.0F) {
            return;
        }
        float[] alloc = WuzangRules.allocateBloodLoss(state.blood(), loss);
        for (WuzangType type : WuzangType.values()) {
            if (alloc[type.index()] > 0.0F) {
                state.addBlood(type, -alloc[type.index()]);
            }
        }
    }

    // ---------------- 自然恢复 ----------------

    /** 五脏气血同步向基线靠拢（高于回落、低于回升），步长已含静坐 / 睡眠倍率。 */
    private static void restoreToBaseline(WuzangState state, float step) {
        for (WuzangType type : WuzangType.values()) {
            state.setQi(type, WuzangRules.restoreStep(state.qiOf(type), step));
            state.setBlood(type, WuzangRules.restoreStep(state.bloodOf(type), step));
        }
    }

    // ---------------- 情志联动 ----------------

    /**
     * 把情志模块的气机扰动当成「剧烈情绪变化」的信号：信号<b>新出现</b>时结算一次
     * 对应脏腑的气血增减。
     *
     * <p>五脏<b>不调用</b>情志模块的任何方法 —— 只是按字符串 ID 查公开注册表，
     * 然后自己改自己的数值。情志模块不存在时整段静默跳过。
     */
    private static void applyEmotionSignals(ServerPlayerEntity player, WuzangState state) {
        Set<String> now = new HashSet<>();
        Set<String> before = ACTIVE_SIGNALS.get(player.getUuid());
        for (WuzangLinks.EmotionSignal signal : WuzangLinks.emotionSignals()) {
            if (!WuzangLinks.hasEffect(player, signal.effectPath())) {
                continue;
            }
            now.add(signal.effectPath());
            if (before != null && before.contains(signal.effectPath())) {
                continue;
            }
            state.addQi(signal.zang(), signal.qiDelta());
            if (signal.bloodDelta() != 0.0F) {
                state.addBlood(signal.zang(), signal.bloodDelta());
            }
            if (signal.spiritDelta() != 0.0F) {
                // 神耗属于情志拓展；经反射软链接写入，情志不在场时静默忽略
                EmotionAuthority.addSpirit(player, signal.spiritDelta());
            }
            player.sendMessage(Text.translatable("message.bingzhengmod.wuzang_emotion_impact",
                    signal.emotionLabel(), describeImpact(signal)), true);
        }
        ACTIVE_SIGNALS.put(player.getUuid(), now);
    }

    /** 把一条情志冲量拼成提示明细，例如「肝气+8.0、肝血-6.0」。 */
    private static String describeImpact(WuzangLinks.EmotionSignal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append(signal.zang().zhName()).append("气").append(signed(signal.qiDelta()));
        if (signal.bloodDelta() != 0.0F) {
            sb.append('、').append(signal.zang().zhName()).append("血").append(signed(signal.bloodDelta()));
        }
        if (signal.spiritDelta() != 0.0F) {
            sb.append("、神耗").append(signed(signal.spiritDelta()));
        }
        return sb.toString();
    }

    // ---------------- 行为驱动 ----------------

    /**
     * 把「行为强度点档位」折算成五脏气血升降（v0.7.0 量化规范 v2）。
     *
     * <p>两类结算，口径不同、互不混淆：
     * <ol>
     *   <li><b>一次性结算</b>（熬夜的天亮/睡醒结算）：文档原值<b>直接</b>施加 —— 它是事件，不是速率；
     *       睡够或点数不足则没有这一笔。</li>
     *   <li><b>档位结算</b>（受凉 / 熬夜趋势 / 过劳 / 暴食 / 压力）：按当前强度点（压力按神耗数值）
     *       取档，再把文档「每 10 分钟」的原值折算成「每 {@link WuzangConfig#SCAN_INTERVAL} tick」的速率。</li>
     * </ol>
     *
     * <p>每一项行为在 {@link WuzangBehaviorRegistry} 里<b>只有一个生效数据源</b>
     * （装了对应拓展就是顶替源，没装就是内置兜底），所以结构上不可能重复计算；
     * 唯一的加法例外是过劳的「酸困 &gt; 50 额外 −1」，那是文档明写的叠加档。
     *
     * <p>本方法<b>不发任何聊天消息</b>：行为是持续状态，发消息会刷屏；
     * 想观察强度点与档位请用 {@code /bingzhengmod wuzang_debug}。
     */
    private static void applyBehaviors(ServerPlayerEntity player, WuzangState state) {
        // ① 熬夜的当日一次性结算（引擎在天亮时记账，这里取走立刻施加）
        for (WuzangBehaviorRules.Delta delta : WuzangBehaviorPoints.takePending(player)) {
            applyDelta(state, delta, delta.perBlock());
        }

        // ② 档位结算
        boolean soreness = WuzangBehaviorRegistry.sorenessActive(player);
        int trendDays = WuzangBehaviorPoints.trendDays(player);
        double pressure = WuzangBehaviorRegistry.pressureValue(player);
        for (WuzangBehavior behavior : WuzangBehavior.values()) {
            List<WuzangBehaviorRules.Delta> deltas;
            if (behavior == WuzangBehavior.MENTAL_PRESSURE) {
                deltas = WuzangBehaviorRules.pressureDeltas(pressure);
            } else {
                deltas = WuzangBehaviorRules.deltas(behavior,
                        WuzangBehaviorPoints.points(player, behavior),
                        WuzangBehaviorPoints.secondary(player, behavior),
                        soreness, trendDays);
            }
            for (WuzangBehaviorRules.Delta delta : deltas) {
                applyDelta(state, delta, WuzangBehaviorRules.perScan(delta.perBlock()));
            }
        }
    }

    /** 施加一条档位效果（正为升、负为降）。 */
    private static void applyDelta(WuzangState state, WuzangBehaviorRules.Delta delta, float amount) {
        if (amount == 0.0F) {
            return;
        }
        if (delta.blood()) {
            state.addBlood(delta.zang(), amount);
        } else {
            state.addQi(delta.zang(), amount);
        }
    }

    // ---------------- 表现层同步 ----------------

    /** 让表现层 buff 与虚实标记严格一致：缺则挂上（无限时长），多则移除。 */
    private static void syncEffects(ServerPlayerEntity player, WuzangState state) {
        int bits = 0;
        for (WuzangType type : WuzangType.values()) {
            bits |= bit(type, 0, state.isQiDeficient(type));
            bits |= bit(type, 1, state.isBloodDeficient(type));
            bits |= bit(type, 2, state.isBothDeficient(type));
            bits |= bit(type, 3, state.isQiExcess(type));
        }
        int prev = MARKER_BITS.getOrDefault(player.getUuid(), 0);

        for (WuzangType type : WuzangType.values()) {
            sync(player, state.isQiDeficient(type), WuzangEffects.qiDeficient(type));
            sync(player, state.isBloodDeficient(type), WuzangEffects.bloodDeficient(type));
            sync(player, state.isBothDeficient(type), WuzangEffects.bothDeficient(type));
            sync(player, state.isQiExcess(type), WuzangEffects.qiExcess(type));
        }

        if (bits != prev) {
            fireHooks(player, state, bits, prev);
        }
        MARKER_BITS.put(player.getUuid(), bits);
    }

    private static void sync(ServerPlayerEntity player, boolean should, StatusEffect effect) {
        boolean has = player.hasStatusEffect(effect);
        if (should && !has) {
            player.addStatusEffect(new StatusEffectInstance(effect, -1, 0, false, true));
        } else if (!should && has) {
            player.removeStatusEffect(effect);
        }
    }

    private static int bit(WuzangType type, int kind, boolean value) {
        return value ? (1 << (type.index() * 4 + kind)) : 0;
    }

    private static boolean newlySet(int bits, int prev, WuzangType type, int kind) {
        int mask = 1 << (type.index() * 4 + kind);
        return (bits & mask) != 0 && (prev & mask) == 0;
    }

    /**
     * 新出现虚实标记时触发预留钩子。
     *
     * <p><b>当前全部实现都是空方法体</b>，因此这里不会产生任何实际效果 ——
     * 只是把接线口留好：将来对应拓展 install 了自己的实现就自动生效。
     */
    private static void fireHooks(ServerPlayerEntity player, WuzangState state, int bits, int prev) {
        if (newlySet(bits, prev, WuzangType.LUNG, 0)) {
            WuzangHooks.core().onLungQiDeficient(player);
            WuzangHooks.skinPores().onLungQiDeficient(player);
        }
        if (newlySet(bits, prev, WuzangType.SPLEEN, 0)) {
            WuzangHooks.core().onSpleenQiDeficient(player);
        }
        if (newlySet(bits, prev, WuzangType.KIDNEY, 0)) {
            WuzangHooks.tiangui().onKidneyEssenceDeficient(player);
            WuzangHooks.boneJoint().onKidneyEssenceDeficient(player);
        }
        if (newlySet(bits, prev, WuzangType.LIVER, 1)) {
            WuzangHooks.eyeOrifice().onLiverBloodDeficient(player);
        }
        if (newlySet(bits, prev, WuzangType.HEART, 3)) {
            WuzangHooks.emotionFeedback().onHeartFireExcess(player);
        }
        if (Integer.bitCount(bits) > Integer.bitCount(prev) && state.imbalancedCount() >= 2) {
            WuzangHooks.functions().onMultiZangImbalance(player, state.imbalancedCount());
        }
        // 说明：文档里的「肝郁气滞使郁证加重」「神耗过高反噬肝心」在本阶段没有对应标记
        //（肝郁气滞、神耗反噬都属于尚未实现的病症），因此只声明钩子、不设触发点。
    }

    private static String signed(float v) {
        return String.format(Locale.ROOT, "%+.1f", v);
    }
}
