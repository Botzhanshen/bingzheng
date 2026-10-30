package com.bingzheng.emotion;


import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LightType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 七情行为驱动的<b>运行期引擎</b>（v0.8.0：五志）。
 *
 * <p><b>数据源纪律</b>：全部来自 MC 原版可观测事件（受击 / 击杀 / 成就 / 专注方块 / 物品结局 /
 * 被锁定 / 黑暗 / 坠落 / 雷击），<b>绝不引用神耗、体耗或五脏数值</b> —— 那会构成自激环
 * （神耗 → 五脏行为 → 气血 → 又回头写七情）。
 *
 * <p><b>单写入者</b>：所有事件回调只往待处理队列里塞点，真正的写盘统一发生在
 * {@link #tick} 里（由 {@link EmotionService} 每 tick 调一次），避免「回调写一次、服务端又写一次」
 * 互相覆盖。
 *
 * <p>结算：点达阈值 → 该情稳定值 +1、点清零；稳定值一旦跨过 70，
 * 既有的阈值扫描会自动生成气机扰动、并经由气机扰动触发跨模块冲量（五脏监听），
 * 因此这里<b>不需要</b>另外发信号。
 */
public final class EmotionBehaviorPoints {

    private static final Map<UUID, PlayerState> STATE = new HashMap<>();
    /** 待累积的强度点（事件线程 → tick）。 */
    private static final Map<UUID, float[]> PENDING_POINTS = new HashMap<>();
    /** 待直接加到稳定值的量（只给不入框架的忧 / 惊用）。 */
    private static final Map<UUID, float[]> PENDING_DIRECT = new HashMap<>();
    /** 死亡掉落物：这些物品实体消失时不再单独计悲点（死亡本身已 +3）。 */
    private static final Set<UUID> DEATH_DROPS = new HashSet<>();
    private static final int DEATH_DROP_CAP = 512;
    /**
     * 待结算的物品结局。<b>事件回调只入队</b>：ENTITY_UNLOAD 位于原版实体迭代 / 区块卸载的栈内，
     * 在那里做世界查询会触发同步区块加载 → 并发修改 → ConcurrentModificationException（v0.8.1 的真实崩溃）。
     */
    private static final java.util.Deque<PendingItem> ITEM_FATES = new java.util.ArrayDeque<>();
    private static final int ITEM_FATE_CAP = 512;

    /** 一条待结算的物品结局：只含实体自身字段，<b>不含任何世界查询结果</b>。 */
    public record PendingItem(UUID id, boolean thrownByOwner, boolean deathDrop, int age,
                              double x, double y, double z, ServerWorld world) {
    }

    private EmotionBehaviorPoints() {
    }

    /** 每玩家状态。 */
    static final class PlayerState {
        private final float[] points = new float[EmotionType.COUNT];
        private final long[] lastTrigger = new long[EmotionType.COUNT];
        private boolean fearLocked;
        private int ticksSinceFearTarget = Integer.MAX_VALUE / 2;
        private int darknessTicks;
        private long focusUntil;
        private long nextFocusBlock;
        private int advancementDone = -1;
        private long deathDropUntil;
        private BlockPos deathDropOrigin;
    }

    // ---------------- 事件入口（只入队，不写盘） ----------------

    /** 累积某一情的强度点（仅五志有效）。 */
    public static void addPoint(ServerPlayerEntity player, EmotionType type, float amount) {
        if (amount <= 0.0F || !EmotionBehaviorRules.isFiveZhi(type)) {
            return;
        }
        PENDING_POINTS.computeIfAbsent(player.getUuid(), key -> new float[EmotionType.COUNT])[type.index()] += amount;
    }

    /** 直接改稳定值（<b>只给忧 / 惊</b>这种不入框架的联动项用）。 */
    public static void addDirect(ServerPlayerEntity player, EmotionType type, float amount) {
        if (amount == 0.0F || EmotionBehaviorRules.isFiveZhi(type)) {
            return;
        }
        PENDING_DIRECT.computeIfAbsent(player.getUuid(), key -> new float[EmotionType.COUNT])[type.index()] += amount;
    }

    /** 玩家死亡：悲点 +3，并开启「死亡掉落窗口」。 */
    public static void onPlayerDeath(ServerPlayerEntity player) {
        addPoint(player, EmotionType.GRIEF, EmotionConfig.GRIEF_POINTS_PLAYER_DEATH);
        PlayerState state = STATE.computeIfAbsent(player.getUuid(), key -> new PlayerState());
        state.deathDropUntil = player.getWorld().getTime() + EmotionConfig.GRIEF_DEATH_DROP_WINDOW;
        state.deathDropOrigin = player.getBlockPos().toImmutable();
    }

    /** 该物品实体是否属于「死亡掉落物」（属于则其消失不再单独计悲点）。 */
    public static boolean markDeathDropIfInWindow(UUID itemId, long now, double x, double y, double z) {
        if (DEATH_DROPS.size() >= DEATH_DROP_CAP) {
            return DEATH_DROPS.contains(itemId);
        }
        for (PlayerState state : STATE.values()) {
            if (state.deathDropOrigin == null || now > state.deathDropUntil) {
                continue;
            }
            if (state.deathDropOrigin.getSquaredDistance(x, y, z) <= 64.0D) {
                DEATH_DROPS.add(itemId);
                return true;
            }
        }
        return DEATH_DROPS.contains(itemId);
    }

    /** 该物品实体是否已被标记为死亡掉落。 */
    public static boolean isDeathDrop(UUID itemId) {
        return DEATH_DROPS.contains(itemId);
    }

    /** 专注方块交互（工作台 / 附魔台 / 酿造台等）：延续专注窗口。 */
    public static void onFocusBlock(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        PlayerState state = STATE.computeIfAbsent(player.getUuid(), key -> new PlayerState());
        state.focusUntil = now + EmotionConfig.FOCUS_MEMORY_TICKS;
        if (state.nextFocusBlock < now) {
            state.nextFocusBlock = now + EmotionConfig.PENSIVENESS_BLOCK_TICKS;
        }
    }

    /** 事件回调专用：<b>纯入队，不碰世界</b>。 */
    public static void queueItemFate(ItemEntity item, ServerWorld world) {
        if (ITEM_FATES.size() >= ITEM_FATE_CAP) {
            return;
        }
        ITEM_FATES.addLast(new PendingItem(item.getUuid(), item.getOwner() != null,
                isDeathDrop(item.getUuid()), item.getItemAge(),
                item.getX(), item.getY(), item.getZ(), world));
    }

    /**
     * 每 tick 由服务端在<b>实体迭代之外</b>调用：此时做世界查询是安全的。
     *
     * <p>区块未加载时**不查询**（否则又会同步加载区块），只按「未加载」处理 —— 那种消失不算悲点。
     */
    public static void drainItemFates(MinecraftServer server) {
        PendingItem pending;
        while ((pending = ITEM_FATES.poll()) != null) {
            ServerWorld world = pending.world();
            boolean hazard = false;
            BlockPos pos = BlockPos.ofFloored(pending.x(), pending.y(), pending.z());
            if (pos.getY() < world.getBottomY()) {
                hazard = true;                                   // 虚空
            } else if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                BlockState state = world.getBlockState(pos);
                hazard = world.getFluidState(pos).isIn(FluidTags.LAVA)
                        || state.isIn(BlockTags.FIRE)
                        || state.getBlock() instanceof CampfireBlock;
            }
            boolean despawned = pending.age() >= EmotionConfig.GRIEF_ITEM_DESPAWN_AGE;
            float points = EmotionBehaviorRules.griefPointsForItem(new EmotionBehaviorRules.ItemFate(
                    pending.thrownByOwner(), pending.deathDrop(), hazard, despawned));
            if (points <= 0.0F) {
                continue;
            }
            PlayerEntity nearest = world.getClosestPlayer(pending.x(), pending.y(), pending.z(),
                    EmotionConfig.GRIEF_ATTRIBUTION_RADIUS, false);
            if (nearest instanceof ServerPlayerEntity serverPlayer) {
                addPoint(serverPlayer, EmotionType.GRIEF, points);
            }
        }
    }

    // ---------------- 每 tick ----------------

    /**
     * 每 tick 调用（由 {@link EmotionService} 在拿到 EmotionState 后调用）。
     *
     * @return 是否改动了 EmotionState（由调用方决定要不要写盘）
     */
    public static boolean tick(ServerPlayerEntity player, EmotionState state, long tick) {
        PlayerState st = STATE.computeIfAbsent(player.getUuid(), key -> new PlayerState());
        boolean dirty = false;

        // ① 入队的点 / 直改量
        float[] pending = PENDING_POINTS.remove(player.getUuid());
        if (pending != null) {
            for (EmotionType type : EmotionType.values()) {
                if (pending[type.index()] > 0.0F) {
                    st.points[type.index()] = EmotionBehaviorRules.pointsStep(
                            st.points[type.index()], pending[type.index()]);
                }
            }
        }
        float[] direct = PENDING_DIRECT.remove(player.getUuid());
        if (direct != null) {
            for (EmotionType type : EmotionType.values()) {
                if (direct[type.index()] != 0.0F) {
                    state.setEmotion(type, state.emotion(type) + direct[type.index()]);
                    dirty = true;
                }
            }
        }

        // ② 扫描：恐（被锁定）/ 思（专注）/ 黑暗 / 喜（成就）
        dirty |= scanFear(player, st, tick);
        dirty |= scanFocus(player, st, tick);
        dirty |= scanDarkness(player, st);
        dirty |= scanAdvancements(player, st, tick);

        // ③ 结算 + 恢复（五志）
        for (EmotionType type : EmotionType.values()) {
            if (!EmotionBehaviorRules.isFiveZhi(type)) {
                continue;
            }
            int slot = type.index();
            if (EmotionBehaviorRules.shouldSettle(type, st.points[slot])) {
                st.points[slot] = EmotionBehaviorRules.pointsAfterSettle(type, st.points[slot]);
                state.setEmotion(type, state.emotion(type) + 1.0F);
                st.lastTrigger[slot] = tick;
                dirty = true;
            }
            if (EmotionBehaviorRules.recoveryDue(tick - st.lastTrigger[slot])) {
                float next = EmotionBehaviorRules.recoveryStep(state.emotion(type), state.baselineOf(type));
                if (next != state.emotion(type)) {
                    state.setEmotion(type, next);
                    dirty = true;
                }
                st.lastTrigger[slot] = tick;
            }
        }
        return dirty;
    }

    /** 恐：被敌对生物锁定为攻击目标（与距离无关；只看 mob 的 target 是不是你）。 */
    private static boolean scanFear(ServerPlayerEntity player, PlayerState st, long tick) {
        if (tick % EmotionConfig.FEAR_SCAN_TICKS != 0L) {
            return false;
        }
        Box box = player.getBoundingBox().expand(EmotionConfig.FEAR_SCAN_RADIUS);
        boolean targeted = !player.getWorld()
                .getEntitiesByClass(MobEntity.class, box, mob -> mob.getTarget() == player)
                .isEmpty();
        boolean awarded = EmotionBehaviorRules.fearShouldAward(st.fearLocked, targeted);
        if (awarded) {
            addPoint(player, EmotionType.FEAR, EmotionConfig.FEAR_POINTS_LOCKED);
            st.fearLocked = true;
        }
        if (targeted) {
            st.ticksSinceFearTarget = 0;
        } else {
            st.ticksSinceFearTarget += EmotionConfig.FEAR_SCAN_TICKS;
        }
        if (!EmotionBehaviorRules.fearStillLocked(st.fearLocked, st.ticksSinceFearTarget)) {
            st.fearLocked = false;          // 脱战 → 重新武装
        }
        return awarded;
    }

    /** 思：专注方块交互后，每持续一个结算块（10 分钟）累积一点。 */
    private static boolean scanFocus(ServerPlayerEntity player, PlayerState st, long tick) {
        if (tick < st.focusUntil && tick >= st.nextFocusBlock) {
            addPoint(player, EmotionType.PENSIVENESS, EmotionConfig.PENSIVENESS_POINTS_PER_BLOCK);
            st.nextFocusBlock = tick + EmotionConfig.PENSIVENESS_BLOCK_TICKS;
            return true;
        }
        return false;
    }

    /** 恐（保留项）：无光源黑暗停留，每 10 分钟 +1 点。 */
    private static boolean scanDarkness(ServerPlayerEntity player, PlayerState st) {
        int light = player.getWorld().getLightLevel(LightType.BLOCK, player.getBlockPos());
        if (light > 0) {
            st.darknessTicks = 0;
            return false;
        }
        st.darknessTicks++;
        if (st.darknessTicks >= EmotionConfig.FEAR_DARKNESS_BLOCK_TICKS) {
            st.darknessTicks = 0;
            addPoint(player, EmotionType.FEAR, EmotionConfig.FEAR_POINTS_DARKNESS);
            return true;
        }
        return false;
    }

    /** 喜：成就达成（只统计原版命名空间；首次扫描只记基线，避免登录瞬间把旧成就全兑现）。 */
    private static boolean scanAdvancements(ServerPlayerEntity player, PlayerState st, long tick) {
        if (tick % EmotionConfig.JOY_ADVANCEMENT_SCAN_TICKS != 0L) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        int done = 0;
        var tracker = player.getAdvancementTracker();
        for (var entry : server.getAdvancementLoader().getAdvancements()) {
            if (!EmotionConfig.JOY_ADVANCEMENT_NAMESPACE.equals(entry.getId().getNamespace())) {
                continue;
            }
            if (tracker.getProgress(entry).isDone()) {
                done++;
            }
        }
        int previous = st.advancementDone;
        st.advancementDone = done;
        if (previous < 0 || done <= previous) {
            return false;              // 首次扫描只记基线：登录时不会把旧成就一次性兑现
        }
        addPoint(player, EmotionType.JOY, (done - previous) * EmotionConfig.JOY_POINTS_ADVANCEMENT);
        return true;
    }

    // ---------------- 读取（调试与自检） ----------------

    /** 当前强度点。 */
    public static float points(ServerPlayerEntity player, EmotionType type) {
        PlayerState state = STATE.get(player.getUuid());
        return state == null ? 0.0F : state.points[type.index()];
    }

    /** 恐是否处于「已锁定」态（调试用）。 */
    public static boolean fearLocked(ServerPlayerEntity player) {
        PlayerState state = STATE.get(player.getUuid());
        return state != null && state.fearLocked;
    }

    /** 摘要，例如 {@code 怒3.0 喜0.0 思1.0 悲0.0 恐2.0}。 */
    public static String describe(ServerPlayerEntity player) {
        PlayerState state = STATE.get(player.getUuid());
        StringBuilder sb = new StringBuilder();
        for (EmotionType type : EmotionType.values()) {
            if (!EmotionBehaviorRules.isFiveZhi(type)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            float value = state == null ? 0.0F : state.points[type.index()];
            sb.append(type.zhName()).append(String.format(java.util.Locale.ROOT, "%.1f", value));
        }
        return sb.toString();
    }

    /** 清理某玩家（离线 / 死亡）。 */
    public static void forget(UUID id) {
        STATE.remove(id);
        PENDING_POINTS.remove(id);
        PENDING_DIRECT.remove(id);
    }

    /** 清理离线玩家（死亡掉落标记随时间自然失效，不在此清理）。 */
    public static void prune(Set<UUID> online) {
        STATE.keySet().retainAll(online);
        PENDING_POINTS.keySet().retainAll(online);
        PENDING_DIRECT.keySet().retainAll(online);
    }

    /** 服务器停机时清空全部运行期数据。 */
    public static void reset() {
        STATE.clear();
        PENDING_POINTS.clear();
        PENDING_DIRECT.clear();
        DEATH_DROPS.clear();
        ITEM_FATES.clear();
    }
}
