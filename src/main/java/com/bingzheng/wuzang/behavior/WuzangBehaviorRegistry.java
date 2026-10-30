package com.bingzheng.wuzang.behavior;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.Map;

/**
 * 行为驱动的<b>顶替注册表</b>（v0.7.0 起为强度点口径）。
 *
 * <p>核心规则只有一条：
 * <pre>
 * 同一项行为，同一时刻只有一个数据源。
 * 装了对应拓展 → 只用它的权威数据；没装 → 只用内置兜底。永远不相加。
 * </pre>
 *
 * <p>唯一的加法例外是「运动拓展·酸困 &gt; 50 → 肾气额外 −1」：它不是第二个数据源，
 * 而是<b>叠加修饰</b>（{@link OverworkModifier}），在档位表里作为额外一项相加，
 * 因此不破坏上面的规则。
 *
 * <p>安装点对任何模块开放：温湿 / 消化 / 运动拓展只要在自己初始化时调用对应的 install 方法，
 * 五脏这边一行都不用改。
 */
public final class WuzangBehaviorRegistry {

    /** 内置兜底（永远存在，不可卸载）。压力项没有点型兜底，见 {@link #PRESSURE_FALLBACK}。 */
    private static final Map<WuzangBehavior, BehaviorProvider> FALLBACKS =
            new EnumMap<>(WuzangBehavior.class);

    /** 跨拓展顶替源（装了才存在）。 */
    private static final Map<WuzangBehavior, BehaviorProvider> OVERRIDES =
            new EnumMap<>(WuzangBehavior.class);

    static {
        FALLBACKS.put(WuzangBehavior.STAY_UP, WuzangFallback.PROVIDER_STAY_UP);
        FALLBACKS.put(WuzangBehavior.COLD_DAMP, WuzangFallback.PROVIDER_COLD_DAMP);
        FALLBACKS.put(WuzangBehavior.EATING, WuzangFallback.PROVIDER_EATING);
        FALLBACKS.put(WuzangBehavior.OVERWORK, WuzangFallback.PROVIDER_OVERWORK);
    }

    /** 压力项的兜底（数值型，恒 0）。 */
    private static final PressureSource PRESSURE_FALLBACK = WuzangFallback.PRESSURE_FALLBACK;

    private static PressureSource pressureOverride;
    private static OverworkModifier sorenessOverride;

    private WuzangBehaviorRegistry() {
    }

    // ---------------- 点型：顶替安装点 ----------------

    /**
     * 由已装载的拓展安装权威数据源，<b>顶替</b>该项的内置兜底。
     *
     * @param provider 传 null 等同于 {@link #uninstall}
     */
    public static void install(WuzangBehavior behavior, BehaviorProvider provider) {
        if (provider == null) {
            OVERRIDES.remove(behavior);
        } else {
            OVERRIDES.put(behavior, provider);
        }
    }

    /** 撤销顶替，回到内置兜底（自检与热插拔用）。 */
    public static void uninstall(WuzangBehavior behavior) {
        OVERRIDES.remove(behavior);
    }

    /** 该点型行为项是否已被顶替。 */
    public static boolean isTakenOver(WuzangBehavior behavior) {
        return OVERRIDES.containsKey(behavior);
    }

    /** 当前生效的数据源（顶替优先）；没有兜底也没有顶替时为 null。 */
    public static BehaviorProvider active(WuzangBehavior behavior) {
        BehaviorProvider override = OVERRIDES.get(behavior);
        return override != null ? override : FALLBACKS.get(behavior);
    }

    /** 当前生效的数据源名称（调试输出与自检用）。 */
    public static String sourceName(WuzangBehavior behavior) {
        if (behavior == WuzangBehavior.MENTAL_PRESSURE) {
            return pressureSource().sourceName();
        }
        BehaviorProvider provider = active(behavior);
        return provider == null ? "无" : provider.sourceName();
    }

    /**
     * 行为强度点：<b>顶替源与内置兜底二选一，绝不相加。</b>
     *
     * @return 本 tick 的点增量；无数据源时返回 {@link BehaviorProvider.Accrual#NONE}
     */
    public static BehaviorProvider.Accrual accrual(ServerPlayerEntity player, WuzangBehavior behavior) {
        BehaviorProvider override = OVERRIDES.get(behavior);
        if (override != null) {
            // 有顶替源就【只】问顶替源 —— 内置兜底这一项彻底不参与计算
            return override.accrual(player);
        }
        BehaviorProvider fallback = FALLBACKS.get(behavior);
        return fallback == null ? BehaviorProvider.Accrual.NONE : fallback.accrual(player);
    }

    /** 只取内置兜底的点增量（自检用来证明「顶替时兜底没有参与计算」）。 */
    public static BehaviorProvider.Accrual fallbackAccrual(ServerPlayerEntity player, WuzangBehavior behavior) {
        BehaviorProvider fallback = FALLBACKS.get(behavior);
        return fallback == null ? BehaviorProvider.Accrual.NONE : fallback.accrual(player);
    }

    /** 该项是否登记了兜底数据源（点型 = 内置兜底；压力 = 恒 0 数值源）。 */
    public static boolean hasFallback(WuzangBehavior behavior) {
        if (behavior == WuzangBehavior.MENTAL_PRESSURE) {
            return PRESSURE_FALLBACK != null;
        }
        return FALLBACKS.containsKey(behavior);
    }

    // ---------------- 数值型：压力 ----------------

    /** 安装压力项的权威数值源；传 null 回到「恒 0」兜底。 */
    public static void installPressure(PressureSource source) {
        pressureOverride = source;
    }

    /** 压力项是否已被顶替。 */
    public static boolean pressureTakenOver() {
        return pressureOverride != null;
    }

    /** 当前生效的压力源。 */
    public static PressureSource pressureSource() {
        return pressureOverride != null ? pressureOverride : PRESSURE_FALLBACK;
    }

    /** 当前压力数值；读不到按 0 处理（分档表里 0 落在最低档，不驱动）。 */
    public static double pressureValue(ServerPlayerEntity player) {
        double value = pressureSource().value(player);
        return value < 0.0D ? 0.0D : value;
    }

    // ---------------- 叠加修饰：过劳·酸困 ----------------

    /** 安装过劳的叠加修饰源（运动拓展·酸困）；传 null 卸载。 */
    public static void installOverworkModifier(OverworkModifier modifier) {
        sorenessOverride = modifier;
    }

    /** 酸困叠加档当前是否成立；未安装时恒 false。 */
    public static boolean sorenessActive(ServerPlayerEntity player) {
        return sorenessOverride != null && sorenessOverride.active(player);
    }

    /** 叠加源名称（调试输出用）。 */
    public static String sorenessSourceName() {
        return sorenessOverride == null ? "未安装（酸困不参与）" : sorenessOverride.sourceName();
    }

    // ---------------- 装载 ----------------

    /**
     * 安装所有「已装载拓展」的权威数据源。
     *
     * <p>当前只有情志拓展可用（神耗 → 熬夜/压力、体耗 → 过劳）；
     * 温湿（核心体温 → 受凉）与消化（食积/膏脂 → 暴食）尚不存在，因此继续走内置兜底。
     */
    public static void installAvailable() {
        EmotionAuthority.installProviders();
    }
}
