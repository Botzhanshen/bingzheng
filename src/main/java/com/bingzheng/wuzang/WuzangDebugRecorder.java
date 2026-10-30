package com.bingzheng.wuzang;

import com.bingzheng.wuzang.behavior.WuzangBehavior;
import com.bingzheng.wuzang.behavior.WuzangBehaviorPoints;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRules;
import com.bingzheng.wuzang.behavior.WuzangBehaviorRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 五脏拓展的调试数据打印。
 *
 * <p>风格与情志拓展的 {@code emotion_debug} 完全一致：
 * <b>正式版本默认关闭</b>，需要手动加 JVM 参数 {@code -Dbingzhengmod.debug.console=true}
 * （开发环境 {@code fabric.development} 自动可用），并且调用者必须是 OP。
 *
 * <p>输出走聊天栏文本，<b>不绘制任何 HUD</b>。
 */
public final class WuzangDebugRecorder {

    private static final Set<UUID> ENABLED = new HashSet<>();

    private WuzangDebugRecorder() {
    }

    /** 本次运行是否允许使用调试命令。 */
    public static boolean isAvailable() {
        return Boolean.getBoolean(WuzangConfig.DEBUG_CONSOLE_PROPERTY)
                || Boolean.getBoolean("fabric.development");
    }

    /** 开关某玩家的调试打印，返回切换后的状态。 */
    public static boolean toggle(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (ENABLED.remove(id)) {
            return false;
        }
        ENABLED.add(id);
        return true;
    }

    public static boolean isEnabled(ServerPlayerEntity player) {
        return ENABLED.contains(player.getUuid());
    }

    public static void prune(Set<UUID> online) {
        ENABLED.retainAll(online);
    }

    /** 节流打印一行完整的五脏气血与标记。 */
    public static void tick(ServerPlayerEntity player, WuzangState state, long tick) {
        if (!isEnabled(player) || tick % WuzangConfig.DEBUG_PRINT_INTERVAL != 0) {
            return;
        }
        // 第一行是**现值**（稳定值 + 时辰波动 + 微观抖动），所以数字会一直动；
        // 稳定值与波动偏移、时辰名见下面第二行。
        player.sendMessage(Text.translatable("command.bingzhengmod.wuzang_debug.line",
                fmt(WuzangRhythm.displayQi(state, WuzangType.LIVER)),
                fmt(WuzangRhythm.displayBlood(state, WuzangType.LIVER)),
                fmt(WuzangRhythm.displayQi(state, WuzangType.HEART)),
                fmt(WuzangRhythm.displayBlood(state, WuzangType.HEART)),
                fmt(WuzangRhythm.displayQi(state, WuzangType.SPLEEN)),
                fmt(WuzangRhythm.displayBlood(state, WuzangType.SPLEEN)),
                fmt(WuzangRhythm.displayQi(state, WuzangType.LUNG)),
                fmt(WuzangRhythm.displayBlood(state, WuzangType.LUNG)),
                fmt(WuzangRhythm.displayQi(state, WuzangType.KIDNEY)),
                fmt(WuzangRhythm.displayBlood(state, WuzangType.KIDNEY))), false);
        // 时辰行：让「数值为什么在动」一眼可读，也是实机验收时辰波动的观测点。
        long time = player.getWorld().getTimeOfDay();
        player.sendMessage(Text.translatable("command.bingzhengmod.wuzang_debug.rhythm",
                WuzangShichen.name(time), sovereignName(time),
                signed(WuzangRhythm.waveQi(WuzangType.LIVER)),
                signed(WuzangRhythm.waveQi(WuzangType.HEART)),
                signed(WuzangRhythm.waveQi(WuzangType.SPLEEN)),
                signed(WuzangRhythm.waveQi(WuzangType.LUNG)),
                signed(WuzangRhythm.waveQi(WuzangType.KIDNEY)),
                fmt(state.qiOf(WuzangType.LIVER)), fmt(state.qiOf(WuzangType.HEART)),
                fmt(state.qiOf(WuzangType.SPLEEN)), fmt(state.qiOf(WuzangType.LUNG)),
                fmt(state.qiOf(WuzangType.KIDNEY))), false);
        // 行为驱动强度：用于「实测后调参」——一眼看出每项走的是兜底还是顶替
        player.sendMessage(Text.translatable("command.bingzhengmod.wuzang_debug.behaviors",
                state.markerSummary(), behaviorSummary(player)), false);
    }

    /** 五项行为的强度点 / 压力数值与当前档位（v0.7.0 口径）。 */
    private static String behaviorSummary(ServerPlayerEntity player) {
        boolean soreness = WuzangBehaviorRegistry.sorenessActive(player);
        int trendDays = WuzangBehaviorPoints.trendDays(player);
        double pressure = WuzangBehaviorRegistry.pressureValue(player);
        StringBuilder sb = new StringBuilder();
        for (WuzangBehavior behavior : WuzangBehavior.values()) {
            if (sb.length() > 0) {
                sb.append(" ｜ ");
            }
            double shown;
            java.util.List<WuzangBehaviorRules.Delta> deltas;
            boolean takenOver;
            if (behavior == WuzangBehavior.MENTAL_PRESSURE) {
                shown = pressure;
                deltas = WuzangBehaviorRules.pressureDeltas(pressure);
                takenOver = WuzangBehaviorRegistry.pressureTakenOver();
            } else {
                shown = WuzangBehaviorPoints.points(player, behavior);
                deltas = WuzangBehaviorRules.deltas(behavior, shown,
                        WuzangBehaviorPoints.secondary(player, behavior), soreness, trendDays);
                takenOver = WuzangBehaviorRegistry.isTakenOver(behavior);
            }
            sb.append(behavior.zhName()).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", shown))
                    .append(behavior == WuzangBehavior.MENTAL_PRESSURE ? "点(神耗)" : "点")
                    .append("→").append(WuzangBehaviorRules.describe(deltas))
                    .append(takenOver ? "(顶替)" : "(兜底)");
        }
        if (trendDays > 0) {
            sb.append(" ｜ 连续熬夜 ").append(trendDays).append(" 日");
        }
        if (soreness) {
            sb.append(" ｜ 酸困叠加生效");
        }
        return sb.toString();
    }

    /** 此刻当令的脏（十二时辰里只有五个时辰有脏当令，其余显示「—」）。 */
    private static String sovereignName(long timeOfDay) {
        WuzangType type = WuzangShichen.sovereignAt(timeOfDay);
        return type == null ? "—" : type.zhName();
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 带符号两位小数：波动偏移幅度很小，要两位小数才看得出「正在逼近目标」。 */
    private static String signed(float v) {
        return String.format(Locale.ROOT, "%+.2f", v);
    }
}
