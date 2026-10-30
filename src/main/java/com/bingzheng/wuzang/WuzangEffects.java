package com.bingzheng.wuzang;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * 五脏表现层的状态效果注册表。全部使用 {@code bingzhengmod:wuzang_} 前缀。
 *
 * <p><b>ID 前缀为什么是 {@code wuzang_}：</b>情志模块已经占用了 {@code gan_qi_kang}（肝气亢）。
 * 五脏的肝气亢在中医上是同一个证，但注册表里绝不允许同名 ID ——
 * 加了前缀就永久不会与内核、情志以及任何后续拓展撞名，
 * 也让五脏将来能干净地拆成独立 jar。
 *
 * <p>20 个效果 = 五藏 × 四态：
 * <ul>
 *   <li>{@code qi_xu} 气虚（气 &lt; 40）</li>
 *   <li>{@code xue_xu} 血虚（血 &lt; 40）</li>
 *   <li>{@code qi_xue_liang_xu} 气血两虚（同脏气血皆 &lt; 40）</li>
 *   <li>{@code qi_kang} 气亢（气 &gt; 90）—— <b>血脉没有亢，不生成对应效果</b></li>
 * </ul>
 *
 * <p>它们<b>全部是纯表现层</b>：不带任何属性修饰，被清除也不会影响底层数据，
 * 下一轮扫描会依据 {@link WuzangState} 的虚实标记重新挂回。
 *
 * <p>因为注册在 {@code bingzhengmod:} 命名空间下，它们<b>自动落入牛奶保护层</b>
 * （见 {@code MilkSafeEffects} 的命名空间规则），饮用牛奶无法清除。
 */
public final class WuzangEffects {

    /** ID 后缀：气虚。 */
    public static final String QI_XU = "qi_xu";
    /** ID 后缀：血虚。 */
    public static final String XUE_XU = "xue_xu";
    /** ID 后缀：气血两虚。 */
    public static final String LIANG_XU = "qi_xue_liang_xu";
    /** ID 后缀：气亢。 */
    public static final String QI_KANG = "qi_kang";

    private static final StatusEffect[] QI_DEFICIENT = new StatusEffect[WuzangType.COUNT];
    private static final StatusEffect[] BLOOD_DEFICIENT = new StatusEffect[WuzangType.COUNT];
    private static final StatusEffect[] BOTH_DEFICIENT = new StatusEffect[WuzangType.COUNT];
    private static final StatusEffect[] QI_EXCESS = new StatusEffect[WuzangType.COUNT];

    private WuzangEffects() {
    }

    /** 注册全部 20 个效果。必须在附件之后、扫描之前调用。 */
    public static void register() {
        for (WuzangType type : WuzangType.values()) {
            int base = baseColor(type);
            QI_DEFICIENT[type.index()] = create(type, QI_XU, shade(base, 0.55F));
            BLOOD_DEFICIENT[type.index()] = create(type, XUE_XU, blend(base, 0x8B2A2A, 0.55F));
            BOTH_DEFICIENT[type.index()] = create(type, LIANG_XU, blend(base, 0x6A2F6A, 0.60F));
            QI_EXCESS[type.index()] = create(type, QI_KANG, blend(base, 0xFFFFFF, 0.35F));
        }
        Wuzang.LOGGER.info("【病证·五脏】状态效果注册完成：{} 项，全部在 {}: 命名空间下（自动纳入牛奶保护层）。",
                allPaths().length, Wuzang.MOD_ID);
    }

    private static StatusEffect create(WuzangType type, String suffix, int color) {
        return Registry.register(Registries.STATUS_EFFECT, Wuzang.id(path(type, suffix)),
                new WuzangMarkerEffect(StatusEffectCategory.HARMFUL, color));
    }

    /** 完整注册路径，例如 {@code wuzang_gan_qi_xu}。 */
    public static String path(WuzangType type, String suffix) {
        return "wuzang_" + type.pinyin() + "_" + suffix;
    }

    /** 全部 20 条注册路径（自检、语言文件、牛奶保护核对用）。 */
    public static String[] allPaths() {
        String[] suffixes = { QI_XU, XUE_XU, LIANG_XU, QI_KANG };
        String[] out = new String[WuzangType.COUNT * suffixes.length];
        int i = 0;
        for (WuzangType type : WuzangType.values()) {
            for (String suffix : suffixes) {
                out[i++] = path(type, suffix);
            }
        }
        return out;
    }

    /** 全部 20 个已注册效果。 */
    public static List<StatusEffect> all() {
        List<StatusEffect> out = new ArrayList<>(WuzangType.COUNT * 4);
        for (WuzangType type : WuzangType.values()) {
            out.add(QI_DEFICIENT[type.index()]);
            out.add(BLOOD_DEFICIENT[type.index()]);
            out.add(BOTH_DEFICIENT[type.index()]);
            out.add(QI_EXCESS[type.index()]);
        }
        return out;
    }

    public static StatusEffect qiDeficient(WuzangType type) {
        return QI_DEFICIENT[type.index()];
    }

    public static StatusEffect bloodDeficient(WuzangType type) {
        return BLOOD_DEFICIENT[type.index()];
    }

    public static StatusEffect bothDeficient(WuzangType type) {
        return BOTH_DEFICIENT[type.index()];
    }

    public static StatusEffect qiExcess(WuzangType type) {
        return QI_EXCESS[type.index()];
    }

    /** 五色配属（青赤黄白黑），仅用于表现层配色，不参与任何数值逻辑。 */
    private static int baseColor(WuzangType type) {
        switch (type) {
            case LIVER:
                return 0x2E8B57;   // 肝属木，青
            case HEART:
                return 0xC0392B;   // 心属火，赤
            case SPLEEN:
                return 0xD4A017;   // 脾属土，黄
            case LUNG:
                return 0xE6E6E6;   // 肺属金，白
            case KIDNEY:
            default:
                return 0x3B3B4F;   // 肾属水，黑
        }
    }

    private static int shade(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
