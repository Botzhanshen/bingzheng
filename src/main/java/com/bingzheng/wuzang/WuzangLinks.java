package com.bingzheng.wuzang;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * 五脏对外的<b>软链接层</b>。
 *
 * <p><b>这里没有一行对情志模块的编译期引用</b>：所有跨模块通讯都退化成一个
 * 字符串 ID + 一次 {@code Registries.STATUS_EFFECT.get(...)} 运行时查表。
 * 于是：
 * <ul>
 *   <li>不安装情志拓展 → 查表返回 null → 所有链接<b>静默失效</b>，五脏照常独立运行；</li>
 *   <li>安装情志拓展 → 链接自动生效，双方代码都不需要改动；</li>
 *   <li>拆包时两边可以各自独立打包，谁也不依赖谁的类。</li>
 * </ul>
 *
 * <p>情志模块<b>不直接读写</b>五脏变量；是五脏在每一轮扫描里主动查表、自己改自己的数值。
 * 这正是文档要求的「由五脏模块监听事件自行处理数据」。
 */
public final class WuzangLinks {

    /**
     * 一条情志 → 脏腑的信号。
     *
     * @param effectPath   情志模块注册的气机扰动效果路径（字符串，非类引用）
     * @param emotionLabel 该信号对应的情志名（仅用于提示文案）
     * @param zang         受影响的脏
     * @param qiDelta      气的变化量（正为升、负为耗）
     * @param bloodDelta   血的变化量（v1.05 新增：久怒耗肝血）
     * @param spiritDelta  神耗的变化量（v1.05 新增：过喜 / 惊恐耗神；写的是情志拓展的神耗）
     */
    public record EmotionSignal(String effectPath, String emotionLabel, WuzangType zang,
                               float qiDelta, float bloodDelta, float spiritDelta) {
    }

    /**
     * 文档记载的情志 → 脏腑对照表，<b>一字不多</b>。
     *
     * <p><b>v1.05 对齐</b>：文档「四、气血升降与行为驱动规则」在五条基础上补了三条附加量 ——
     * 久怒耗肝血（血降）、过喜伤心神（神耗升）、惊恐耗神（神耗升），已全部落到信号表的
     * {@code bloodDelta / spiritDelta} 两个字段上。
     *
     * <p>文档只写了怒、喜、思、悲、恐五条；「忧」与「惊」<b>没有</b>对应的脏腑条目，
     * 因此这里刻意不补 —— 不自行新增文档没有的设定。
     */
    private static final List<EmotionSignal> EMOTION_SIGNALS = List.of(
            // 怒→肝：怒气上涌（气升）；久怒耗肝血（血降）
            new EmotionSignal("gan_qi_kang", "怒", WuzangType.LIVER,
                    WuzangConfig.EMOTION_LIVER_QI_DELTA, WuzangConfig.EMOTION_LIVER_BLOOD_DELTA, 0.0F),
            // 喜→心：大喜耗散（气升）；过喜伤心神（神耗升）
            new EmotionSignal("xin_shen_yang", "喜", WuzangType.HEART,
                    WuzangConfig.EMOTION_HEART_QI_DELTA, 0.0F, WuzangConfig.EMOTION_SPIRIT_ON_JOY),
            // 思→脾：思虑过度（气降）
            new EmotionSignal("pi_yi_chan", "思", WuzangType.SPLEEN,
                    WuzangConfig.EMOTION_SPLEEN_QI_DELTA, 0.0F, 0.0F),
            // 悲→肺：悲忧伤肺（气降）
            new EmotionSignal("fei_qi_xiao", "悲", WuzangType.LUNG,
                    WuzangConfig.EMOTION_LUNG_QI_DELTA, 0.0F, 0.0F),
            // 恐→肾：恐惧伤肾（气降）；惊恐耗神（神耗升）
            new EmotionSignal("shen_qi_she", "恐", WuzangType.KIDNEY,
                    WuzangConfig.EMOTION_KIDNEY_QI_DELTA, 0.0F, WuzangConfig.EMOTION_SPIRIT_ON_FRIGHT));

    /** 情志模块的「静坐中」隐藏标记效果路径（用于静坐加速恢复）。 */
    public static final String MEDITATING_PATH = "meditating";

    private WuzangLinks() {
    }

    /** 按路径查表；模块不存在时返回 null（不抛异常）。 */
    public static StatusEffect byPath(String path) {
        return Registries.STATUS_EFFECT.get(Wuzang.id(path));
    }

    /** 玩家身上是否挂着该路径对应的效果；模块不存在时恒为 false。 */
    public static boolean hasEffect(ServerPlayerEntity player, String path) {
        StatusEffect effect = byPath(path);
        return effect != null && player.hasStatusEffect(effect);
    }

    public static List<EmotionSignal> emotionSignals() {
        return EMOTION_SIGNALS;
    }

    /** 情志拓展是否在场（五条信号里任一条注册得上即视为在场）。 */
    public static boolean emotionModulePresent() {
        for (EmotionSignal signal : EMOTION_SIGNALS) {
            if (byPath(signal.effectPath()) != null) {
                return true;
            }
        }
        return false;
    }

    /** 静坐标记是否在场。 */
    public static boolean meditationModulePresent() {
        return byPath(MEDITATING_PATH) != null;
    }
}
