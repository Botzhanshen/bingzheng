package com.bingzheng.wuzang.behavior;

import com.bingzheng.wuzang.WuzangConfig;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 五脏的<b>内置兜底判定</b>（v0.7.0 起为「强度点」口径）：完全不依赖任何其它拓展，
 * 只用原版可观测的状态（时间、睡眠、天气、湿度、群系温度、疾跑、破坏方块、饥饿度）。
 *
 * <p>于是单开五脏时行为照样驱动气血升降。对应拓展一旦装好并
 * {@link WuzangBehaviorRegistry#install} 了权威数据源，这一整项立刻被顶替停用。
 *
 * <p><b>累加器是运行期数据（不落盘）</b>：它衡量的是「这一段时间的行为」，不是玩家属性；
 * 真正落盘的永远是五脏气血本身。
 */
public final class WuzangFallback {

    // ---------------- 运行期观测缓存 ----------------

    /** 进食时间戳（只在统计窗口内保留）。 */
    private static final Map<UUID, Deque<Long>> EAT_TIMES = new HashMap<>();
    /** 上一 tick 的饥饿度与饱和度，用于纯观察地识别「吃了一口」。 */
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    private static final Map<UUID, Float> LAST_SATURATION = new HashMap<>();
    /** 上一 tick 主手拿的东西：吃的那一刻物品可能已经被消耗，只能靠上一 tick 的快照判断食性。 */
    private static final Map<UUID, Item> LAST_HAND = new HashMap<>();
    /** 最近一次破坏方块的时刻（世界时间），用于「连续劳作」判定。 */
    private static final Map<UUID, Long> LAST_BLOCK_TICK = new HashMap<>();
    /** 待交付给引擎的一次性点数（暴食点 / 生冷点）。 */
    private static final Map<UUID, Double> PENDING_BINGE = new HashMap<>();
    private static final Map<UUID, Double> PENDING_COLD = new HashMap<>();

    /**
     * 「未烹饪 / 生食」白名单（原版可食用且通常是生冷的那些）。
     *
     * <p>这只是<b>兜底近似</b>：真正的食性由消化拓展提供，装上后整项被顶替。
     */
    private static final Set<Item> RAW_OR_COLD = Set.of(
            Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
            Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH,
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO);

    private WuzangFallback() {
    }

    // ---------------- 供事件回调使用 ----------------

    /** 破坏方块事件（后置追加，只记时刻）。 */
    public static void onBlockBreak(ServerPlayerEntity player) {
        LAST_BLOCK_TICK.put(player.getUuid(), player.getWorld().getTime());
    }

    // ---------------- 每 tick 观测 ----------------

    /**
     * 每 tick 观测：识别「吃了一口」、裁剪进食窗口、刷新手持快照。
     * <b>只读原版状态，不做判定、不施加任何效果。</b>
     *
     * <p>必须在 {@link WuzangBehaviorPoints#tick} <b>之前</b>调用：
     * 这样同一 tick 识别出来的暴食/生冷点当 tick 就被取走。
     */
    public static void tick(List<ServerPlayerEntity> players, long tick) {
        for (ServerPlayerEntity player : players) {
            UUID id = player.getUuid();

            int food = player.getHungerManager().getFoodLevel();
            float saturation = player.getHungerManager().getSaturationLevel();
            Integer lastFood = LAST_FOOD.get(id);
            Float lastSaturation = LAST_SATURATION.get(id);
            boolean ate = lastFood != null && (food > lastFood || saturation > lastSaturation + 0.01F);

            if (ate) {
                Deque<Long> eats = EAT_TIMES.computeIfAbsent(id, key -> new ArrayDeque<>());
                eats.addLast(tick);
                // ① 5 分钟内进食次数达到「超胃容量」→ 记一次暴食点
                if (eats.size() >= WuzangConfig.EATING_BINGE_COUNT) {
                    PENDING_BINGE.merge(id, WuzangConfig.EATING_BINGE_POINTS, Double::sum);
                }
                // ② 生冷 / 未烹饪（或「冷着吃」）→ 记一次生冷点
                if (isRawOrCold(LAST_HAND.get(id), player)) {
                    PENDING_COLD.merge(id, WuzangConfig.EATING_COLD_POINTS, Double::sum);
                }
            }

            Deque<Long> eats = EAT_TIMES.get(id);
            if (eats != null) {
                while (!eats.isEmpty() && tick - eats.peekFirst() > WuzangConfig.EATING_BINGE_WINDOW_TICKS) {
                    eats.pollFirst();
                }
            }

            LAST_FOOD.put(id, food);
            LAST_SATURATION.put(id, saturation);
            LAST_HAND.put(id, player.getMainHandStack().getItem());
        }
    }

    // ---------------- 原版可观测的判据 ----------------

    /** 是否处于「湿 / 冷」暴露中（任一条件成立即可）。 */
    public static boolean isExposedToCold(ServerPlayerEntity player) {
        if (player.isSubmergedInWater() || player.isTouchingWaterOrRain() || player.isWet()) {
            return true;
        }
        if (player.getWorld().hasRain(player.getBlockPos())) {
            return true;
        }
        return player.getWorld().getBiome(player.getBlockPos()).value().isCold(player.getBlockPos());
    }

    /** 原版寒冷度：把能被观测到的致冷因素数出来（湿、雨、冷群系、细雪、冷群系的夜）。 */
    public static int coldSeverity(ServerPlayerEntity player) {
        return WuzangBehaviorRules.coldSeverity(
                player.isTouchingWaterOrRain() || player.isWet(),
                player.getWorld().hasRain(player.getBlockPos()),
                player.getWorld().getBiome(player.getBlockPos()).value().isCold(player.getBlockPos()),
                player.getWorld().isNight(),
                player.inPowderSnow);
    }

    /** 是否处于「连续劳作」：疾跑，或刚刚还在破坏方块。 */
    public static boolean isLaboring(ServerPlayerEntity player) {
        if (player.isSprinting()) {
            return true;
        }
        Long last = LAST_BLOCK_TICK.get(player.getUuid());
        return last != null && player.getWorld().getTime() - last <= WuzangConfig.OVERWORK_LABOR_MEMORY_TICKS;
    }

    /** 一次进食是不是「生冷 / 未烹饪」（或冷着吃）。 */
    public static boolean isRawOrCold(Item item, ServerPlayerEntity player) {
        return (item != null && RAW_OR_COLD.contains(item)) || isExposedToCold(player);
    }

    /** 窗口内的进食次数（自检与调试用）。 */
    public static int recentEatCount(UUID id) {
        Deque<Long> eats = EAT_TIMES.get(id);
        return eats == null ? 0 : eats.size();
    }

    // ---------------- 五个内置数据源 ----------------

    /** 受凉：原版环境温度（群系温度 + 天气 + 昼夜）折算成体温档位。 */
    public static final BehaviorProvider PROVIDER_COLD_DAMP = new BehaviorProvider() {
        @Override
        public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
            return BehaviorProvider.Accrual.perBlock(
                    WuzangBehaviorRules.coldPointsPerBlock(coldSeverity(player)));
        }

        @Override
        public String sourceName() {
            return "内置兜底·原版环境温度（群系/天气/昼夜折算）";
        }
    };

    /** 熬夜：夜间清醒按块累积熬夜点（白天与睡到天亮由引擎清零）。 */
    public static final BehaviorProvider PROVIDER_STAY_UP = new BehaviorProvider() {
        @Override
        public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
            boolean awakeAtNight = player.getWorld().isNight() && !player.isSleeping();
            return BehaviorProvider.Accrual.perBlock(
                    awakeAtNight ? WuzangConfig.STAY_UP_POINTS_PER_BLOCK : 0.0D);
        }

        @Override
        public String sourceName() {
            return "内置兜底·MC 时间（夜间清醒计时）";
        }
    };

    /** 过劳：疾跑 / 破坏方块判定「连续劳作」，按块累积劳作点。 */
    public static final BehaviorProvider PROVIDER_OVERWORK = new BehaviorProvider() {
        @Override
        public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
            return BehaviorProvider.Accrual.perBlock(
                    isLaboring(player) ? WuzangConfig.OVERWORK_POINTS_PER_BLOCK : 0.0D);
        }

        @Override
        public String sourceName() {
            return "内置兜底·疾跑/破坏方块计时";
        }
    };

    /** 暴食 / 生冷：事件型，一次事件交付一次点数。 */
    public static final BehaviorProvider PROVIDER_EATING = new BehaviorProvider() {
        @Override
        public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
            UUID id = player.getUuid();
            double binge = PENDING_BINGE.getOrDefault(id, 0.0D);
            double cold = PENDING_COLD.getOrDefault(id, 0.0D);
            if (binge > 0.0D) {
                PENDING_BINGE.put(id, 0.0D);
            }
            if (cold > 0.0D) {
                PENDING_COLD.put(id, 0.0D);
            }
            return new BehaviorProvider.Accrual(binge, cold);
        }

        @Override
        public String sourceName() {
            return "内置兜底·进食事件（窗口计数 + 生冷近似）";
        }
    };

    /**
     * 压力 / 情绪压抑：<b>原版根本没有情绪系统，这一项的内置兜底恒为 0</b>。
     *
     * <p>它是五项里唯一「只有顶替、没有兜底」的一项 —— 单开五脏时这一项不驱动，
     * 这也正是顶替机制存在的理由：没有权威数据源，就不该凭空造情绪。
     */
    public static final PressureSource PRESSURE_FALLBACK = new PressureSource() {
        @Override
        public double value(ServerPlayerEntity player) {
            return 0.0D;
        }

        @Override
        public String sourceName() {
            return "内置兜底·原版无情绪系统（恒 0）";
        }
    };

    /** 清掉某玩家的全部观测缓存（死亡 / 复活 / 离线）。 */
    public static void forget(UUID id) {
        EAT_TIMES.remove(id);
        LAST_FOOD.remove(id);
        LAST_SATURATION.remove(id);
        LAST_HAND.remove(id);
        LAST_BLOCK_TICK.remove(id);
        PENDING_BINGE.remove(id);
        PENDING_COLD.remove(id);
    }

    /** 清理离线玩家。 */
    public static void prune(Set<UUID> online) {
        EAT_TIMES.keySet().retainAll(online);
        LAST_FOOD.keySet().retainAll(online);
        LAST_SATURATION.keySet().retainAll(online);
        LAST_HAND.keySet().retainAll(online);
        LAST_BLOCK_TICK.keySet().retainAll(online);
        PENDING_BINGE.keySet().retainAll(online);
        PENDING_COLD.keySet().retainAll(online);
    }
}
