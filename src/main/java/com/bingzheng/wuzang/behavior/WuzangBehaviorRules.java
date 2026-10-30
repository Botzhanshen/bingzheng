package com.bingzheng.wuzang.behavior;

import com.bingzheng.wuzang.WuzangConfig;
import com.bingzheng.wuzang.WuzangType;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为驱动量化规范 v2 的<b>纯规则层</b>（v0.7.0 任务单一）。
 *
 * <p>本类只做三件事，全部是纯函数，启动自检可以直接断言：
 * <ol>
 *   <li><b>累积 / 递减</b>：强度点的一步加法（{@link #pointsStep}）；</li>
 *   <li><b>档位表</b>：当前强度点（或压力数值）落在哪一档，对应文档给出的每块位移量；</li>
 *   <li><b>折算</b>：把「每 10 分钟」的文档原值折算成「每 20 tick 扫描」的实际结算量。</li>
 * </ol>
 *
 * <p><b>档位是替换而不是累加</b>：文档写「受凉点 ≥2 → 肺气 −2…；≥4 → 肺气 −3…」，
 * 后者是更重的一档，不是两档相加。唯一的加法例外是文档明写的「酸困 &gt; 50 → 肾气<b>额外</b> −1」。
 */
public final class WuzangBehaviorRules {

    /**
     * 一条档位效果。
     *
     * @param zang     受影响的脏
     * @param blood    true = 血，false = 气
     * @param perBlock 文档原值：每 {@link WuzangConfig#POINT_BLOCK_TICKS}（10 分钟）的位移量
     */
    public record Delta(WuzangType zang, boolean blood, float perBlock) {
    }

    private WuzangBehaviorRules() {
    }

    // ---------------- 折算 ----------------

    /** 文档「每 10 分钟」的原值 → 每 {@link WuzangConfig#SCAN_INTERVAL} tick 的结算量。 */
    public static float perScan(float perBlock) {
        return (float) (perBlock * WuzangConfig.POINT_RATE_SCALE
                * WuzangConfig.SCAN_INTERVAL / (double) WuzangConfig.POINT_BLOCK_TICKS);
    }

    /** 无累积时每 tick 递减的强度点（「按恢复递减」）。 */
    public static double decayPerTick() {
        return WuzangConfig.POINT_DECAY_PER_BLOCK / (double) WuzangConfig.POINT_BLOCK_TICKS;
    }

    /**
     * 强度点走一步：<b>有累积就加、没有就按恢复递减</b>，并钳在 {@code 0..POINT_MAX}。
     *
     * <p>累积量本身已经是「每 tick」的量（见 {@link BehaviorProvider.Accrual#perBlock}），
     * 所以这里是一句加法 —— 对跳 tick、掉帧天然健壮，也不会因为扫描节流而丢点。
     */
    public static double pointsStep(double points, double accrualPerTick) {
        double next = accrualPerTick > 0.0D ? points + accrualPerTick : points - decayPerTick();
        return Math.max(0.0D, Math.min(WuzangConfig.POINT_MAX, next));
    }

    // ---------------- 一、受凉（原版环境温度 → 体温档位） ----------------

    /**
     * 原版可观测条件的「寒冷度」计数：湿身、降雨、寒冷群系、细雪、寒冷群系的夜里。
     *
     * <p>这是文档「单开五脏兜底 = 原版环境温度（群系温度 + 天气 + 昼夜折算成体温档位）」
     * 的可实现近似：<b>不是</b>真的算体温，而是把原版能观测到的致冷因素数出来。
     */
    public static int coldSeverity(boolean wet, boolean rain, boolean coldBiome,
                                   boolean night, boolean powderSnow) {
        int severity = 0;
        if (wet) {
            severity++;
        }
        if (rain) {
            severity++;
        }
        if (coldBiome) {
            severity++;
        }
        if (powderSnow) {
            severity++;
        }
        if (night && coldBiome) {
            severity++;
        }
        return severity;
    }

    /** 寒冷度 → 每块累积的受凉点（0 正常 / +1 微凉 / +2 冷 / +4 失温）。 */
    public static double coldPointsPerBlock(int severity) {
        if (severity <= 0) {
            return WuzangConfig.COLD_POINTS_NORMAL;
        }
        if (severity == 1) {
            return WuzangConfig.COLD_POINTS_CHILL;
        }
        if (severity <= 3) {
            return WuzangConfig.COLD_POINTS_COLD;
        }
        return WuzangConfig.COLD_POINTS_HYPOTHERMIA;
    }

    /** 受凉点 → 档位效果（≥4 用二档，否则 ≥2 用一档，否则不驱动）。 */
    public static List<Delta> coldDeltas(double points) {
        if (points >= WuzangConfig.COLD_TIER2_AT) {
            return List.of(
                    new Delta(WuzangType.LUNG, false, WuzangConfig.COLD_TIER2_LUNG_QI),
                    new Delta(WuzangType.SPLEEN, false, WuzangConfig.COLD_TIER2_SPLEEN_QI));
        }
        if (points >= WuzangConfig.COLD_TIER1_AT) {
            return List.of(
                    new Delta(WuzangType.LUNG, false, WuzangConfig.COLD_TIER1_LUNG_QI),
                    new Delta(WuzangType.SPLEEN, false, WuzangConfig.COLD_TIER1_SPLEEN_QI));
        }
        return List.of();
    }

    // ---------------- 二、熬夜 / 昼夜颠倒 ----------------

    /**
     * 熬夜的<b>当日一次性结算</b>：熬夜点 ≥3 才结算，量就是文档原值（不折算 —— 它是事件而非速率）。
     */
    public static List<Delta> stayUpDailySettlement(double points) {
        if (points < WuzangConfig.STAY_UP_TIER1_AT) {
            return List.of();
        }
        return List.of(
                new Delta(WuzangType.LIVER, false, WuzangConfig.STAY_UP_DAY_LIVER_QI),
                new Delta(WuzangType.HEART, false, WuzangConfig.STAY_UP_DAY_HEART_QI),
                new Delta(WuzangType.KIDNEY, false, WuzangConfig.STAY_UP_DAY_KIDNEY_QI),
                new Delta(WuzangType.KIDNEY, true, WuzangConfig.STAY_UP_DAY_KIDNEY_BLOOD));
    }

    /** 连续熬夜达到 {@link WuzangConfig#STAY_UP_TREND_DAYS} 日后的「肾精亏虚趋势」：持续压低（走折算）。 */
    public static List<Delta> stayUpTrendDeltas(int consecutiveDays) {
        if (consecutiveDays < WuzangConfig.STAY_UP_TREND_DAYS) {
            return List.of();
        }
        return List.of(
                new Delta(WuzangType.KIDNEY, false, WuzangConfig.STAY_UP_TREND_KIDNEY_QI),
                new Delta(WuzangType.KIDNEY, true, WuzangConfig.STAY_UP_TREND_KIDNEY_BLOOD));
    }

    // ---------------- 三、过劳 / 剧烈劳作 ----------------

    /** 劳作点 ≥5 → 肾气 −2；酸困 > 50 → 肾气<b>额外</b> −1（文档明写的叠加档）。 */
    public static List<Delta> overworkDeltas(double points, boolean soreness) {
        List<Delta> out = new ArrayList<>(2);
        if (points >= WuzangConfig.OVERWORK_TIER1_AT) {
            out.add(new Delta(WuzangType.KIDNEY, false, WuzangConfig.OVERWORK_TIER1_KIDNEY_QI));
        }
        if (soreness) {
            out.add(new Delta(WuzangType.KIDNEY, false, WuzangConfig.OVERWORK_SORENESS_KIDNEY_QI));
        }
        return out;
    }

    // ---------------- 四、暴饮暴食 / 生冷 ----------------

    /** 暴食点 ≥2 与生冷点 ≥3 各自分档，两者的效果<b>相加</b>（它们是两种不同的损伤）。 */
    public static List<Delta> eatingDeltas(double binge, double cold) {
        List<Delta> out = new ArrayList<>(4);
        if (binge >= WuzangConfig.EATING_BINGE_TIER_AT) {
            out.add(new Delta(WuzangType.SPLEEN, false, WuzangConfig.EATING_BINGE_SPLEEN_QI));
            out.add(new Delta(WuzangType.SPLEEN, true, WuzangConfig.EATING_BINGE_SPLEEN_BLOOD));
        }
        if (cold >= WuzangConfig.EATING_COLD_TIER_AT) {
            out.add(new Delta(WuzangType.SPLEEN, false, WuzangConfig.EATING_COLD_SPLEEN_QI));
            out.add(new Delta(WuzangType.SPLEEN, true, WuzangConfig.EATING_COLD_SPLEEN_BLOOD));
        }
        return out;
    }

    // ---------------- 五、压力 / 情绪压抑 ----------------

    /** 压力按数值分档（不是点）：60–80 → 肝气 +1；> 80 → 肝气 +2。 */
    public static List<Delta> pressureDeltas(double spirit) {
        if (spirit < 0.0D) {
            return List.of();
        }
        if (spirit > WuzangConfig.PRESSURE_SPIRIT_TIER2) {
            return List.of(new Delta(WuzangType.LIVER, false, WuzangConfig.PRESSURE_TIER2_LIVER_QI));
        }
        if (spirit >= WuzangConfig.PRESSURE_SPIRIT_TIER1) {
            return List.of(new Delta(WuzangType.LIVER, false, WuzangConfig.PRESSURE_TIER1_LIVER_QI));
        }
        return List.of();
    }

    /** 当日是否要罚：<b>没睡够</b> 且 熬夜点 ≥3。 */
    public static boolean stayUpDayPenalized(double points, boolean sleptEnough) {
        return !sleptEnough && points >= WuzangConfig.STAY_UP_TIER1_AT;
    }

    /** 「连续熬夜天数」的下一步：罚了就 +1，没罚就清零（某天不熬夜即断链）。 */
    public static int nextTrendDays(int trendDays, boolean penalized) {
        return penalized ? trendDays + 1 : 0;
    }

    // ---------------- 汇总 ----------------

    /**
     * 某一项行为当前的档位效果。
     *
     * @param soreness  过劳的叠加档（运动拓展·酸困 &gt; 50）是否成立
     * @param trendDays 连续熬夜天数（用于肾精亏虚趋势）
     */
    public static List<Delta> deltas(WuzangBehavior behavior, double primary, double secondary,
                                     boolean soreness, int trendDays) {
        return switch (behavior) {
            case COLD_DAMP -> coldDeltas(primary);
            case STAY_UP -> stayUpTrendDeltas(trendDays);
            case OVERWORK -> overworkDeltas(primary, soreness);
            case EATING -> eatingDeltas(primary, secondary);
            case MENTAL_PRESSURE -> List.of();   // 压力按数值分档，见 pressureDeltas
        };
    }

    /** 档位摘要，例如 {@code 肺气-3.0、脾气-2.0}（调试输出用）。 */
    public static String describe(List<Delta> deltas) {
        if (deltas.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (Delta d : deltas) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(d.zang().zhName()).append(d.blood() ? "血" : "气")
                    .append(String.format(java.util.Locale.ROOT, "%+.1f", d.perBlock()));
        }
        return sb.toString();
    }
}
