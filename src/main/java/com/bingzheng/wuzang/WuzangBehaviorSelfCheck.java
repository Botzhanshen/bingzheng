package com.bingzheng.wuzang;

import com.bingzheng.wuzang.behavior.BehaviorProvider;
import com.bingzheng.wuzang.behavior.WuzangBehavior;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRegistry;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRules;
import com.bingzheng.wuzang.behavior.WuzangFallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 五脏行为驱动的开发期自检（v0.7.0：量化规范 v2）。
 *
 * <p>断言的是 {@link WuzangBehaviorRules} 的<b>纯规则</b>与 {@link WuzangBehaviorRegistry}
 * 的<b>数据源规则</b>，两者都不需要世界、不需要玩家，模组启动时即可跑完。
 *
 * <p><b>为什么不测「点累积循环」本身</b>：那是每 tick 的引擎循环，需要一个真实玩家；
 * 这里改为直接对纯函数 {@code pointsStep} 做定步长积分，数学上等价（12000 次 1/12000 = 1.0）。
 */
public final class WuzangBehaviorSelfCheck {

    private static int passed;
    private static int failed;
    private static final List<String> FAILURES = new ArrayList<>();

    private WuzangBehaviorSelfCheck() {
    }

    public static void run() {
        if (!Boolean.getBoolean("fabric.development")) {
            return;
        }
        passed = 0;
        failed = 0;
        FAILURES.clear();
        Wuzang.LOGGER.info("【病证·行为驱动自检】开始，共 17 项。");

        checkFallbacksRegistered();
        checkPickOneNeverSums();
        checkTakeoverPluggable();
        checkSourceNames();
        checkDirectionTable();
        checkPerScanConversion();
        checkPointsAccumulate();
        checkPointsDecayAndClamp();
        checkColdSeverity();
        checkColdTiers();
        checkStayUpDailySettlement();
        checkStayUpTrend();
        checkOverworkTiers();
        checkEatingTiers();
        checkPressureTiers();
        checkSevenEmotionsExcluded();
        checkCrossModuleSlotsIdle();

        for (String f : FAILURES) {
            Wuzang.LOGGER.error("【病证·行为驱动自检】[FAIL] {}", f);
        }
        Wuzang.LOGGER.info("【病证·行为驱动自检】完成：通过 {} 项，失败 {} 项。", passed, failed);
        if (failed > 0) {
            Wuzang.LOGGER.error("【病证·行为驱动自检】存在 {} 项失败！", failed);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            Wuzang.LOGGER.info("【病证·行为驱动自检】[OK] {}", name);
        } else {
            failed++;
            FAILURES.add(name + " —— " + detail);
        }
    }

    /** 造一个只返回固定点增量的假数据源（不接触玩家）。 */
    private static BehaviorProvider fake(String name, double perBlock) {
        return new BehaviorProvider() {
            @Override
            public Accrual accrual(net.minecraft.server.network.ServerPlayerEntity player) {
                return Accrual.perBlock(perBlock);
            }

            @Override
            public String sourceName() {
                return name;
            }
        };
    }

    // 1 兜底登记
    private static void checkFallbacksRegistered() {
        boolean ok = WuzangBehavior.values().length == 5;
        for (WuzangBehavior behavior : WuzangBehavior.values()) {
            ok &= WuzangBehaviorRegistry.hasFallback(behavior);
        }
        check("五项行为全部登记了兜底数据源（四项点型 + 压力恒 0 数值源），行为项共 5 个",
                ok, "项数=" + WuzangBehavior.values().length);
    }

    // 2 二选一，绝不相加
    private static void checkPickOneNeverSums() {
        WuzangBehavior behavior = WuzangBehavior.COLD_DAMP;
        BehaviorProvider before = WuzangBehaviorRegistry.active(behavior);
        boolean wasOverridden = WuzangBehaviorRegistry.isTakenOver(behavior);
        BehaviorProvider plug = fake("假·顶替源（3.0/块）", 3.0D);
        WuzangBehaviorRegistry.install(behavior, plug);
        boolean plugged = WuzangBehaviorRegistry.active(behavior) == plug
                && WuzangBehaviorRegistry.isTakenOver(behavior)
                && WuzangBehaviorRegistry.active(behavior) != WuzangFallback.PROVIDER_COLD_DAMP;
        restore(behavior, before, wasOverridden);
        boolean back = WuzangBehaviorRegistry.active(behavior) == before
                && WuzangBehaviorRegistry.isTakenOver(behavior) == wasOverridden;
        check("行为数据源二选一：顶替在位时生效对象就是顶替源本体、内置兜底彻底不在路径上（结构上不可能相加）",
                plugged && back, "顶替生效=" + plugged + " 还原=" + back
                        + " 兜底=" + (before == null ? "无" : before.sourceName()));
    }

    // 3 顶替可插拔
    private static void checkTakeoverPluggable() {
        WuzangBehavior behavior = WuzangBehavior.OVERWORK;
        BehaviorProvider before = WuzangBehaviorRegistry.active(behavior);
        boolean wasOverridden = WuzangBehaviorRegistry.isTakenOver(behavior);
        BehaviorProvider plug = fake("假·体耗顶替", 2.0D);
        WuzangBehaviorRegistry.install(behavior, plug);
        boolean swapped = WuzangBehaviorRegistry.active(behavior) == plug
                && WuzangBehaviorRegistry.isTakenOver(behavior);
        restore(behavior, before, wasOverridden);
        boolean restored = WuzangBehaviorRegistry.active(behavior) == before
                && WuzangBehaviorRegistry.isTakenOver(behavior) == wasOverridden;
        check("顶替可插拔：装上是顶替对象本体、卸载立刻回内置兜底对象（或回到装载前的顶替源）",
                swapped && restored, "装上=" + swapped + " 还原=" + restored);
    }

    // 4 数据源名称随顶替切换
    private static void checkSourceNames() {
        WuzangBehavior behavior = WuzangBehavior.STAY_UP;
        BehaviorProvider before = WuzangBehaviorRegistry.active(behavior);
        boolean wasOverridden = WuzangBehaviorRegistry.isTakenOver(behavior);
        String fallbackName = WuzangBehaviorRegistry.sourceName(behavior);
        WuzangBehaviorRegistry.install(behavior, fake("假·神耗顶替", 1.0D));
        String overrideName = WuzangBehaviorRegistry.sourceName(behavior);
        restore(behavior, before, wasOverridden);
        boolean ok = fallbackName != null && !fallbackName.isBlank()
                && overrideName.contains("假·神耗顶替");
        check("调试输出能看到数据源随顶替切换（原数据源 → 顶替源），便于人工验收",
                ok, "原=" + fallbackName + " 顶替=" + overrideName);
    }

    /** 还原到装载前的状态：本来是顶替就装回原顶替源，本来没有顶替就卸载。 */
    private static void restore(WuzangBehavior behavior, BehaviorProvider before, boolean wasOverridden) {
        if (wasOverridden) {
            WuzangBehaviorRegistry.install(behavior, before);
        } else {
            WuzangBehaviorRegistry.uninstall(behavior);
        }
    }

    // 5 档位方向与文档一致
    private static void checkDirectionTable() {
        boolean cold = sign(WuzangBehaviorRules.coldDeltas(4.0D), WuzangType.LUNG, false) < 0
                && sign(WuzangBehaviorRules.coldDeltas(4.0D), WuzangType.SPLEEN, false) < 0;
        boolean work = sign(WuzangBehaviorRules.overworkDeltas(5.0D, false), WuzangType.KIDNEY, false) < 0;
        boolean eat = sign(WuzangBehaviorRules.eatingDeltas(2.0D, 3.0D), WuzangType.SPLEEN, false) < 0
                && sign(WuzangBehaviorRules.eatingDeltas(2.0D, 3.0D), WuzangType.SPLEEN, true) < 0;
        boolean pressure = sign(WuzangBehaviorRules.pressureDeltas(90.0D), WuzangType.LIVER, false) > 0;
        boolean stayUp = sign(WuzangBehaviorRules.stayUpDailySettlement(3.0D), WuzangType.LIVER, false) > 0
                && sign(WuzangBehaviorRules.stayUpDailySettlement(3.0D), WuzangType.HEART, false) > 0
                && sign(WuzangBehaviorRules.stayUpDailySettlement(3.0D), WuzangType.KIDNEY, false) < 0
                && sign(WuzangBehaviorRules.stayUpDailySettlement(3.0D), WuzangType.KIDNEY, true) < 0;
        check("档位方向与文档一致（受凉降肺脾、过劳降肾、暴食降脾气血、压力升肝、熬夜升肝心降肾）",
                cold && work && eat && pressure && stayUp,
                "受凉=" + cold + " 过劳=" + work + " 暴食=" + eat + " 压力=" + pressure + " 熬夜=" + stayUp);
    }

    /** 取一条档位量（找不到返回 0）。 */
    private static float first(List<WuzangBehaviorRules.Delta> deltas, WuzangType zang, boolean blood) {
        return sign(deltas, zang, blood);
    }

    /** 把同一脏同一属性的全部档位量<b>求和</b>（文档里的「额外」「相加」档专用）。 */
    private static float total(List<WuzangBehaviorRules.Delta> deltas, WuzangType zang, boolean blood) {
        float sum = 0.0F;
        for (WuzangBehaviorRules.Delta d : deltas) {
            if (d.zang() == zang && d.blood() == blood) {
                sum += d.perBlock();
            }
        }
        return sum;
    }

    private static float sign(List<WuzangBehaviorRules.Delta> deltas, WuzangType zang, boolean blood) {
        for (WuzangBehaviorRules.Delta d : deltas) {
            if (d.zang() == zang && d.blood() == blood) {
                return d.perBlock();
            }
        }
        return 0.0F;
    }

    // 6 折算
    private static void checkPerScanConversion() {
        float perScan = WuzangBehaviorRules.perScan(-2.0F);
        double expected = -2.0D * WuzangConfig.POINT_RATE_SCALE
                * WuzangConfig.SCAN_INTERVAL / (double) WuzangConfig.POINT_BLOCK_TICKS;
        boolean matches = Math.abs(perScan - expected) < 1.0E-6F;
        // 每块原值 × 折算系数 = 每 20 tick 的结算量；12000/20 = 600 次扫描正好还原一块
        boolean roundTrip = Math.abs(perScan * (WuzangConfig.POINT_BLOCK_TICKS / WuzangConfig.SCAN_INTERVAL)
                - (-2.0F * WuzangConfig.POINT_RATE_SCALE)) < 1.0E-3F;
        check("档位折算：文档「每 10 分钟」原值 → 每 20 tick 结算量 = 原值 × 折算系数 × 20/12000，"
                        + "600 次扫描正好还原一个结算块",
                matches && roundTrip, "perScan=" + perScan + " 期望=" + expected + " 往返=" + roundTrip);
    }

    // 7 点累积（定步长积分）
    private static void checkPointsAccumulate() {
        double accrue = WuzangConfig.COLD_POINTS_CHILL / WuzangConfig.POINT_BLOCK_TICKS;  // 微凉 = 1 点/块
        double points = 0.0D;
        int ticks = WuzangConfig.POINT_BLOCK_TICKS;
        for (int i = 0; i < ticks; i++) {
            points = WuzangBehaviorRules.pointsStep(points, accrue);
        }
        boolean oneBlock = Math.abs(points - 1.0D) < 1.0E-3D;
        // 失温档 4 点/块：半个块就到 2 点（一档线）
        double hypo = WuzangConfig.COLD_POINTS_HYPOTHERMIA / WuzangConfig.POINT_BLOCK_TICKS;
        double fast = 0.0D;
        // 多积 1 tick：浮点累加会差 ~1e-13 落在档位线正下方，多一步即稳稳越线
        for (int i = 0; i <= WuzangConfig.POINT_BLOCK_TICKS / 2; i++) {
            fast = WuzangBehaviorRules.pointsStep(fast, hypo);
        }
        boolean tierInHalfBlock = WuzangBehaviorRules.coldDeltas(fast).size() == 2;
        check("强度点按行为累积：1 点/块 积分 12000 tick 恰好 1.0 点；失温 4 点/块 半块即到一档线（≥2）",
                oneBlock && tierInHalfBlock, "一块后=" + points + " 半块后=" + fast);
    }

    // 8 点递减与钳位
    private static void checkPointsDecayAndClamp() {
        double decay = WuzangBehaviorRules.decayPerTick();
        double points = 3.0D;
        for (int i = 0; i < WuzangConfig.POINT_BLOCK_TICKS; i++) {
            points = WuzangBehaviorRules.pointsStep(points, 0.0D);   // 无行为 → 按恢复递减
        }
        boolean decaysOnePerBlock = Math.abs(points - 2.0D) < 1.0E-3D;
        double floor = 0.0D;
        for (int i = 0; i < WuzangConfig.POINT_BLOCK_TICKS * 3; i++) {
            floor = WuzangBehaviorRules.pointsStep(floor, 0.0D);
        }
        boolean neverNegative = floor == 0.0D;
        double capped = WuzangConfig.POINT_MAX;
        for (int i = 0; i < WuzangConfig.POINT_BLOCK_TICKS; i++) {
            capped = WuzangBehaviorRules.pointsStep(capped, 4.0D / WuzangConfig.POINT_BLOCK_TICKS);
        }
        boolean ceiling = capped <= WuzangConfig.POINT_MAX + 1.0E-9D;
        check("强度点按恢复递减：无行为时每块 −1 点、不会掉到负数；累积不会超过上限",
                decaysOnePerBlock && neverNegative && ceiling,
                "一块后=" + points + " 下限=" + floor + " 上限=" + capped + " decay=" + decay);
    }

    // 9 受凉环境档位折算（纯函数）
    private static void checkColdSeverity() {
        boolean warm = WuzangBehaviorRules.coldPointsPerBlock(
                WuzangBehaviorRules.coldSeverity(false, false, false, false, false)) == WuzangConfig.COLD_POINTS_NORMAL;
        boolean drizzle = WuzangBehaviorRules.coldPointsPerBlock(
                WuzangBehaviorRules.coldSeverity(false, true, false, false, false)) == WuzangConfig.COLD_POINTS_CHILL;
        boolean wetAndCold = WuzangBehaviorRules.coldPointsPerBlock(
                WuzangBehaviorRules.coldSeverity(true, true, false, false, false)) == WuzangConfig.COLD_POINTS_COLD;
        boolean hypothermia = WuzangBehaviorRules.coldPointsPerBlock(
                WuzangBehaviorRules.coldSeverity(true, true, true, false, true)) == WuzangConfig.COLD_POINTS_HYPOTHERMIA;
        boolean nightInColdBiome = WuzangBehaviorRules.coldSeverity(false, false, true, true, false) == 2;
        check("单开五脏的受凉兜底：原版环境温度折算 —— 干暖 0 点、单一致冷因素微凉、两因素冷档、四因素失温档",
                warm && drizzle && wetAndCold && hypothermia && nightInColdBiome,
                "暖=" + warm + " 微凉=" + drizzle + " 冷=" + wetAndCold + " 失温=" + hypothermia
                        + " 冷群系夜=" + nightInColdBiome);
    }

    // 10 受凉档位替换
    private static void checkColdTiers() {
        boolean none = WuzangBehaviorRules.coldDeltas(WuzangConfig.COLD_TIER1_AT - 0.1D).isEmpty();
        List<WuzangBehaviorRules.Delta> tier1 = WuzangBehaviorRules.coldDeltas(WuzangConfig.COLD_TIER1_AT);
        List<WuzangBehaviorRules.Delta> tier2 = WuzangBehaviorRules.coldDeltas(WuzangConfig.COLD_TIER2_AT);
        boolean replace = tier2.size() == 2
                && sign(tier2, WuzangType.LUNG, false) == WuzangConfig.COLD_TIER2_LUNG_QI
                && sign(tier2, WuzangType.LUNG, false) != WuzangConfig.COLD_TIER1_LUNG_QI + WuzangConfig.COLD_TIER2_LUNG_QI;
        check("受凉档位是替换而非累加：<2 不驱动、≥2 一档（肺气−2/脾气−1）、≥4 换成二档（肺气−3/脾气−2）",
                none && tier1.size() == 2 && replace,
                "一档=" + WuzangBehaviorRules.describe(tier1) + " 二档=" + WuzangBehaviorRules.describe(tier2));
    }

    // 11 熬夜当日结算
    private static void checkStayUpDailySettlement() {
        boolean penalize = WuzangBehaviorRules.stayUpDayPenalized(WuzangConfig.STAY_UP_TIER1_AT, false)
                && !WuzangBehaviorRules.stayUpDayPenalized(WuzangConfig.STAY_UP_TIER1_AT - 0.1D, false)
                && !WuzangBehaviorRules.stayUpDayPenalized(WuzangConfig.STAY_UP_TIER1_AT, true);
        boolean amounts = sign(WuzangBehaviorRules.stayUpDailySettlement(WuzangConfig.STAY_UP_TIER1_AT),
                WuzangType.LIVER, false) == WuzangConfig.STAY_UP_DAY_LIVER_QI
                && sign(WuzangBehaviorRules.stayUpDailySettlement(WuzangConfig.STAY_UP_TIER1_AT),
                WuzangType.HEART, false) == WuzangConfig.STAY_UP_DAY_HEART_QI
                && sign(WuzangBehaviorRules.stayUpDailySettlement(WuzangConfig.STAY_UP_TIER1_AT),
                WuzangType.KIDNEY, true) == WuzangConfig.STAY_UP_DAY_KIDNEY_BLOOD;
        boolean noneBelow = WuzangBehaviorRules.stayUpDailySettlement(WuzangConfig.STAY_UP_TIER1_AT - 0.1D).isEmpty();
        check("熬夜当日结算：点 ≥3 且没睡够才结算（肝气+2/心气+1/肾气−1/肾血−1）；睡够或点不足一律不结算",
                penalize && amounts && noneBelow,
                "判定=" + penalize + " 量=" + amounts + " 低点不结算=" + noneBelow);
    }

    // 12 连续熬夜趋势
    private static void checkStayUpTrend() {
        int days = 0;
        List<Integer> history = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            days = WuzangBehaviorRules.nextTrendDays(days, true);   // 连续熬夜
            history.add(days);
        }
        boolean countsUp = history.get(0) == 1 && history.get(1) == 2 && history.get(2) == 3;
        boolean trendOn = WuzangBehaviorRules.stayUpTrendDeltas(3).size() == 2
                && WuzangBehaviorRules.describe(WuzangBehaviorRules.stayUpTrendDeltas(3)).contains("肾");
        boolean trendOff = WuzangBehaviorRules.stayUpTrendDeltas(2).isEmpty();
        int broken = WuzangBehaviorRules.nextTrendDays(3, false);
        check("连续 3 日熬夜点 ≥3 → 进入「肾精亏虚趋势」（持续压低肾气/肾血）；某日不熬夜立即断链清零",
                countsUp && trendOn && trendOff && broken == 0,
                "天数序列=" + history + " 趋势=" + trendOn + " 未达 3 日=" + trendOff + " 断链后=" + broken);
    }

    // 13 过劳与酸困叠加
    private static void checkOverworkTiers() {
        boolean below = WuzangBehaviorRules.overworkDeltas(WuzangConfig.OVERWORK_TIER1_AT - 0.1D, false).isEmpty();
        List<WuzangBehaviorRules.Delta> base = WuzangBehaviorRules.overworkDeltas(WuzangConfig.OVERWORK_TIER1_AT, false);
        List<WuzangBehaviorRules.Delta> withSoreness = WuzangBehaviorRules.overworkDeltas(WuzangConfig.OVERWORK_TIER1_AT, true);
        boolean baseOk = base.size() == 1 && sign(base, WuzangType.KIDNEY, false) == WuzangConfig.OVERWORK_TIER1_KIDNEY_QI;
        boolean stacked = withSoreness.size() == 2
                && Math.abs(total(withSoreness, WuzangType.KIDNEY, false)
                - (WuzangConfig.OVERWORK_TIER1_KIDNEY_QI + WuzangConfig.OVERWORK_SORENESS_KIDNEY_QI)) < 1.0E-6F;
        // 酸困只有运动拓展装了才有：默认未安装时叠加档恒 false
        boolean notInstalledByDefault = WuzangBehaviorRegistry.sorenessSourceName().contains("未安装");
        check("过劳：劳作点 <5 不驱动、≥5 → 肾气 −2；酸困 > 50 时肾气<b>额外</b> −1（文档明写的叠加档）",
                below && baseOk && stacked && notInstalledByDefault,
                "基础=" + WuzangBehaviorRules.describe(base) + " 叠加=" + WuzangBehaviorRules.describe(withSoreness)
                        + " 叠加源=" + WuzangBehaviorRegistry.sorenessSourceName());
    }

    // 14 暴食 / 生冷
    private static void checkEatingTiers() {
        boolean none = WuzangBehaviorRules.eatingDeltas(WuzangConfig.EATING_BINGE_TIER_AT - 0.1D,
                WuzangConfig.EATING_COLD_TIER_AT - 0.1D).isEmpty();
        List<WuzangBehaviorRules.Delta> binge = WuzangBehaviorRules.eatingDeltas(WuzangConfig.EATING_BINGE_TIER_AT, 0.0D);
        List<WuzangBehaviorRules.Delta> cold = WuzangBehaviorRules.eatingDeltas(0.0D, WuzangConfig.EATING_COLD_TIER_AT);
        List<WuzangBehaviorRules.Delta> both = WuzangBehaviorRules.eatingDeltas(
                WuzangConfig.EATING_BINGE_TIER_AT, WuzangConfig.EATING_COLD_TIER_AT);
        boolean sums = Math.abs(total(both, WuzangType.SPLEEN, false)
                - (WuzangConfig.EATING_BINGE_SPLEEN_QI + WuzangConfig.EATING_COLD_SPLEEN_QI)) < 1.0E-6F
                && Math.abs(total(both, WuzangType.SPLEEN, true)
                - (WuzangConfig.EATING_BINGE_SPLEEN_BLOOD + WuzangConfig.EATING_COLD_SPLEEN_BLOOD)) < 1.0E-6F;
        check("暴食 / 生冷是两个独立点：暴食点 ≥2 与生冷点 ≥3 各自分档，同时成立时效果相加（脾气−4/脾血−3）",
                none && binge.size() == 2 && cold.size() == 2 && sums,
                "暴食=" + WuzangBehaviorRules.describe(binge) + " 生冷=" + WuzangBehaviorRules.describe(cold)
                        + " 合计=" + WuzangBehaviorRules.describe(both));
    }

    // 15 压力分档
    private static void checkPressureTiers() {
        boolean fallbackZero = WuzangFallback.PRESSURE_FALLBACK.value(null) == 0.0D;
        boolean below = WuzangBehaviorRules.pressureDeltas(WuzangConfig.PRESSURE_SPIRIT_TIER1 - 1.0D).isEmpty();
        boolean tier1 = sign(WuzangBehaviorRules.pressureDeltas(WuzangConfig.PRESSURE_SPIRIT_TIER1),
                WuzangType.LIVER, false) == WuzangConfig.PRESSURE_TIER1_LIVER_QI;
        boolean tier2 = sign(WuzangBehaviorRules.pressureDeltas(WuzangConfig.PRESSURE_SPIRIT_TIER2 + 1.0D),
                WuzangType.LIVER, false) == WuzangConfig.PRESSURE_TIER2_LIVER_QI;
        boolean noData = WuzangBehaviorRules.pressureDeltas(-1.0D).isEmpty();
        check("压力按神耗数值分档：<60 不驱动、60–80 → 肝气 +1、>80 → 肝气 +2；无数据（负值）不驱动",
                fallbackZero && below && tier1 && tier2 && noData,
                "兜底=" + WuzangBehaviorRegistry.pressureSource().sourceName()
                        + " 一档=" + tier1 + " 二档=" + tier2);
    }

    // 16 七情不进强度点框架
    private static void checkSevenEmotionsExcluded() {
        boolean noEmotionProvider = true;
        for (WuzangBehavior behavior : WuzangBehavior.values()) {
            String name = WuzangBehaviorRegistry.sourceName(behavior);
            if (name != null && name.contains("七情")) {
                noEmotionProvider = false;
            }
        }
        boolean fiveBehaviors = WuzangBehavior.values().length == 5;
        check("七情不走强度点框架：行为 5 项的数据源里没有任何一条是七情信号（避免同源双算）",
                noEmotionProvider && fiveBehaviors, "行为项=" + WuzangBehavior.values().length);
    }

    // 17 跨拓展槽位默认空闲
    private static void checkCrossModuleSlotsIdle() {
        boolean sorenessIdle = WuzangBehaviorRegistry.sorenessSourceName().contains("未安装");
        boolean coldUsesFallback = WuzangBehaviorRegistry.active(WuzangBehavior.COLD_DAMP)
                == WuzangFallback.PROVIDER_COLD_DAMP;
        boolean eatingUsesFallback = WuzangBehaviorRegistry.active(WuzangBehavior.EATING)
                == WuzangFallback.PROVIDER_EATING;
        boolean hooks = WuzangHooks.allDefaults();
        check("全部跨拓展联动仍是「只留插槽」：酸困叠加未安装、受凉/暴食用内置兜底、病症钩子全为空实现",
                sorenessIdle && coldUsesFallback && eatingUsesFallback && hooks,
                "酸困=" + WuzangBehaviorRegistry.sorenessSourceName()
                        + " 受凉兜底=" + coldUsesFallback + " 暴食兜底=" + eatingUsesFallback + " 钩子空=" + hooks);
    }
}
