package com.bingzheng.wuzang.behavior;

import com.bingzheng.wuzang.WuzangConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 行为强度点的<b>运行期引擎</b>（v0.7.0 任务单一）。
 *
 * <p>职责只有两条：
 * <ol>
 *   <li><b>每 tick</b>：向当前生效的数据源问「这一 tick 累积多少点」，
 *       累加（或按恢复递减）到该行为的强度点上；天亮时做熬夜的当日结算；</li>
 *   <li><b>被扫描取用</b>：把强度点换算成档位效果交给服务端结算。</li>
 * </ol>
 *
 * <p><b>为什么不落盘</b>：强度点衡量的是「这一段时间的行为」，属于运行期数据；
 * 玩家退出重进会从零开始累计 —— 真正落盘的永远是五脏气血本身（附件）。
 * 这与 v0.5.0 以来的口径一致，且<b>不改动存档格式</b>。
 *
 * <p>累积/递减的数学、档位表、折算全部在 {@link WuzangBehaviorRules}（纯函数），
 * 本类只持有状态与搬运，因此自检断言的是同一批纯函数。
 */
public final class WuzangBehaviorPoints {

    private static final Map<UUID, PlayerPoints> STATE = new HashMap<>();

    private WuzangBehaviorPoints() {
    }

    /** 每个玩家一份：五项行为各两个点槽（主点 / 次点） + 熬夜的日账。 */
    static final class PlayerPoints {
        private final double[][] points = new double[WuzangBehavior.values().length][2];
        private final Deque<WuzangBehaviorRules.Delta> pending = new ArrayDeque<>();
        private int trendDays;
        private float sleptTicksTonight;
        private boolean wasNight;
    }

    // ---------------- 每 tick ----------------

    /**
     * 每 tick 调用：累积 / 递减强度点，并在「天亮」这一刻结算熬夜的当日账。
     *
     * <p>纯观察式实现：只读玩家状态，不修改任何原版数据、不施加任何效果。
     */
    public static void tick(List<ServerPlayerEntity> players, long tick) {
        for (ServerPlayerEntity player : players) {
            PlayerPoints state = STATE.computeIfAbsent(player.getUuid(), key -> new PlayerPoints());

            // ① 逐项累积 / 递减
            for (WuzangBehavior behavior : WuzangBehavior.values()) {
                BehaviorProvider.Accrual accrual = WuzangBehaviorRegistry.accrual(player, behavior);
                double[] slot = state.points[behavior.ordinal()];
                slot[0] = WuzangBehaviorRules.pointsStep(slot[0], accrual.primary());
                slot[1] = WuzangBehaviorRules.pointsStep(slot[1], accrual.secondary());
            }

            // ② 熬夜的日账：夜间睡眠计时 + 天亮结算 + 清零
            boolean night = player.getWorld().isNight();
            if (night && player.isSleeping()) {
                state.sleptTicksTonight += 1.0F;
            }
            if (state.wasNight && !night) {
                settleStayUpDay(state);
            }
            state.wasNight = night;
        }
    }

    /**
     * 天亮 / 睡醒时的熬夜结算。
     *
     * <p>文档：「当日睡醒/天亮结算」+「白天或睡到天亮清零」。
     * 睡够（{@link WuzangConfig#STAY_UP_SLEEP_ADEQUATE_TICKS}）则不罚、并断开连续熬夜链。
     */
    private static void settleStayUpDay(PlayerPoints state) {
        double points = state.points[WuzangBehavior.STAY_UP.ordinal()][0];
        boolean sleptEnough = state.sleptTicksTonight >= WuzangConfig.STAY_UP_SLEEP_ADEQUATE_TICKS;
        boolean penalized = WuzangBehaviorRules.stayUpDayPenalized(points, sleptEnough);
        if (penalized) {
            // 一次性结算：文档原值直接施加（不是速率，不参与折算），交给服务端下一次扫描取走
            state.pending.addAll(WuzangBehaviorRules.stayUpDailySettlement(points));
        }
        state.trendDays = WuzangBehaviorRules.nextTrendDays(state.trendDays, penalized);
        state.points[WuzangBehavior.STAY_UP.ordinal()][0] = 0.0D;
        state.sleptTicksTonight = 0.0F;
    }

    // ---------------- 读取 ----------------

    /** 主强度点（受凉点 / 熬夜点 / 劳作点 / 暴食点）；无记录时为 0。 */
    public static double points(ServerPlayerEntity player, WuzangBehavior behavior) {
        PlayerPoints state = STATE.get(player.getUuid());
        return state == null ? 0.0D : Math.max(0.0D, state.points[behavior.ordinal()][0]);
    }

    /** 次强度点（只有暴食用的「生冷点」）。 */
    public static double secondary(ServerPlayerEntity player, WuzangBehavior behavior) {
        PlayerPoints state = STATE.get(player.getUuid());
        return state == null ? 0.0D : Math.max(0.0D, state.points[behavior.ordinal()][1]);
    }

    /** 连续熬夜天数（用于肾精亏虚趋势）。 */
    public static int trendDays(ServerPlayerEntity player) {
        PlayerPoints state = STATE.get(player.getUuid());
        return state == null ? 0 : state.trendDays;
    }

    /** 取走一次性结算（熬夜当日结算）；取走即清空。 */
    public static List<WuzangBehaviorRules.Delta> takePending(ServerPlayerEntity player) {
        PlayerPoints state = STATE.get(player.getUuid());
        if (state == null || state.pending.isEmpty()) {
            return List.of();
        }
        List<WuzangBehaviorRules.Delta> out = List.copyOf(state.pending);
        state.pending.clear();
        return out;
    }

    /** 强度点摘要，例如 {@code 受凉2.4 熬夜0.0 劳作5.1 暴食0.0/生冷3.0 压力0}。 */
    public static String describe(ServerPlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        for (WuzangBehavior behavior : WuzangBehavior.values()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(behavior.zhName()).append(String.format(Locale.ROOT, "%.1f", points(player, behavior)));
            double second = secondary(player, behavior);
            if (second > 0.0D) {
                sb.append("/").append(String.format(Locale.ROOT, "%.1f", second));
            }
        }
        return sb.toString();
    }

    /** 清理某玩家（死亡 / 复活 / 离线）。 */
    public static void forget(UUID id) {
        STATE.remove(id);
    }

    /** 清理离线玩家。 */
    public static void prune(Set<UUID> online) {
        STATE.keySet().retainAll(online);
    }
}
