package com.bingzheng.emotion;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 调试控制台数据打印。
 *
 * <p><b>正式 release 版本默认关闭</b>：需要手动加 JVM 参数
 * {@code -Dbingzhengmod.debug.console=true}（开发环境自动可用），
 * 并且调用者必须是 OP —— 普通玩家无法主动开启。
 *
 * <p>输出走聊天栏文本，<b>不绘制任何 HUD</b>，不进入正常生存玩法。
 */
public final class EmotionDebugRecorder {

    private static final Set<UUID> ENABLED = new HashSet<>();

    private EmotionDebugRecorder() {
    }

    /** 本次运行是否允许使用调试命令。 */
    public static boolean isAvailable() {
        return Boolean.getBoolean(EmotionConfig.DEBUG_CONSOLE_PROPERTY)
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

    /** 节流打印一行完整后台数值。 */
    public static void tick(ServerPlayerEntity player, EmotionState state, long tick) {
        if (!isEnabled(player) || tick % EmotionConfig.DEBUG_PRINT_INTERVAL != 0) {
            return;
        }
        // 七情打印的是**现值**（稳定值 + 时辰波动 + 微观抖动），所以数字会一直动；
        // 稳定值与波动偏移见下一行的时辰行。
        player.sendMessage(Text.translatable("command.bingzhengmod.emotion_debug.line",
                fmt(EmotionRhythm.display(state, EmotionType.ANGER)),
                fmt(EmotionRhythm.display(state, EmotionType.JOY)),
                fmt(EmotionRhythm.display(state, EmotionType.WORRY)),
                fmt(EmotionRhythm.display(state, EmotionType.PENSIVENESS)),
                fmt(EmotionRhythm.display(state, EmotionType.GRIEF)),
                fmt(EmotionRhythm.display(state, EmotionType.FEAR)),
                fmt(EmotionRhythm.display(state, EmotionType.FRIGHT))), false);
        // 时辰行：让「数值为什么在动」一眼可读，也是实机验收时辰波动的观测点。
        // 只列参与波动的五情（忧、惊不参与 —— 延续「忧惊不补」）。
        long time = player.getWorld().getTimeOfDay();
        EmotionType sovereign = EmotionShichen.sovereignAt(time);
        player.sendMessage(Text.translatable("command.bingzhengmod.emotion_debug.rhythm",
                EmotionShichen.name(time), sovereign == null ? "—" : sovereign.zhName(),
                signed(EmotionRhythm.wave(EmotionType.ANGER)),
                signed(EmotionRhythm.wave(EmotionType.JOY)),
                signed(EmotionRhythm.wave(EmotionType.PENSIVENESS)),
                signed(EmotionRhythm.wave(EmotionType.GRIEF)),
                signed(EmotionRhythm.wave(EmotionType.FEAR)),
                fmt(state.emotion(EmotionType.ANGER)), fmt(state.emotion(EmotionType.JOY)),
                fmt(state.emotion(EmotionType.PENSIVENESS)), fmt(state.emotion(EmotionType.GRIEF)),
                fmt(state.emotion(EmotionType.FEAR))), false);
        // 五志强度点与恐的锁定态（v0.8.0 行为驱动层的观测点）
        player.sendMessage(Text.translatable("command.bingzhengmod.emotion_debug.behavior",
                EmotionBehaviorPoints.describe(player),
                EmotionBehaviorPoints.fearLocked(player) ? "已锁定" : "未锁定"), false);
    }

    /** 神扰剩余 tick；未激活时为 0。 */
    private static int deliriumRemaining(ServerPlayerEntity player) {
        StatusEffectInstance instance = player.getStatusEffect(EmotionEffects.SHEN_RAO);
        return instance == null ? 0 : instance.getDuration();
    }

    /**
     * 神扰退出时的衰减明细。
     *
     * <p>仅在 {@code emotion_debug} 打开时输出 —— 人工验收「高位情绪确实降下来了、
     * 而且没有归零」时，比盯着 7 个浮点数比对要快得多。
     */
    public static void onDeliriumEnded(ServerPlayerEntity player, String detail, float coefficient) {
        if (!isEnabled(player)) {
            return;
        }
        player.sendMessage(Text.translatable("command.bingzhengmod.emotion_debug.decay",
                detail, String.format(Locale.ROOT, "%.2f", coefficient)), false);
    }

    /** 带符号两位小数：波动偏移幅度很小，要两位小数才看得出「正在逼近目标」。 */
    private static String signed(float v) {
        return String.format(Locale.ROOT, "%+.2f", v);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}