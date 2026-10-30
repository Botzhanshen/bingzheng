package com.bingzheng.wuzang;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 五脏拓展的玩家专属数据。
 *
 * <p>五藏各维护两组浮点：<b>气</b>与<b>血</b>，取值 0~100，平人基线 80。
 * 字段命名与文档一致：{@code liverQi / liverBlood / heartQi / ...}，
 * 在 NBT 里对应 {@code qi[0] / blood[0] ...}（下标与 {@link WuzangType} 一一对应）。
 *
 * <p><b>本类同时是「虚实标记」的主数据源</b>：{@code qiDeficient / bloodDeficient / qiExcess}
 * 三个布尔数组随数值一起持久化。就算表现层状态效果被意外清除（例如别的模组强清），
 * 下一轮扫描也会依据这里的数值与标记把 buff 重新挂回来 —— 病根不在 buff 上。
 *
 * <p>本类可变：一个扫描轮次里会连续改动多个字段，用 record + with 链会非常啰嗦。
 * 改动完成后<b>必须调用</b> {@link WuzangAttachments#set} 写回，以触发附件的持久化标记。
 *
 * <p><b>玩家看不到这里任何一个数字。</b>全部数值后台隐形运行，不绘制任何 UI。
 */
public final class WuzangState {

    private final float[] qi = new float[WuzangType.COUNT];
    private final float[] blood = new float[WuzangType.COUNT];
    private final boolean[] qiDeficient = new boolean[WuzangType.COUNT];
    private final boolean[] bloodDeficient = new boolean[WuzangType.COUNT];
    private final boolean[] qiExcess = new boolean[WuzangType.COUNT];

    /** 新玩家初始状态：五脏气血全部为平人基线。 */
    public static WuzangState fresh() {
        return new WuzangState(null, null, null, null, null);
    }

    private WuzangState(float[] qi, float[] blood,
                        boolean[] qiDeficient, boolean[] bloodDeficient, boolean[] qiExcess) {
        float[] q = qi == null ? null : qi;
        float[] b = blood == null ? null : blood;
        for (int i = 0; i < WuzangType.COUNT; i++) {
            this.qi[i] = WuzangRules.clamp(q != null && i < q.length ? q[i] : WuzangConfig.START);
            this.blood[i] = WuzangRules.clamp(b != null && i < b.length ? b[i] : WuzangConfig.START);
        }
        // v0.6.0：标记改为**迟滞态**（虚 40 触发 / 45 解除，亢 90 触发 / 85 解除），不再只是数值的纯函数，
        // 还带一个「上次落在阈值哪一侧」的记忆。因此必须先把落盘的标记读回来当初始态、再按迟滞规则重算；
        // 若仍无条件由数值单点重推，存档里的迟滞态会在上线那一瞬间被抹掉。
        copyFlags(qiDeficient, this.qiDeficient);
        copyFlags(bloodDeficient, this.bloodDeficient);
        copyFlags(qiExcess, this.qiExcess);
        refreshMarkers();
    }

    /** 落盘标记回填：长度不匹配（旧存档 / 异常数据）时按「未触发」处理，绝不越界。 */
    private static void copyFlags(boolean[] src, boolean[] dst) {
        if (src == null) {
            return;
        }
        for (int i = 0; i < dst.length && i < src.length; i++) {
            dst[i] = src[i];
        }
    }

    /**
     * 由 Codec 使用的工厂。
     *
     * <p>参数即 Codec 组合后的类型（{@code float[]} / {@code boolean[]}），
     * 无需装箱 —— 数组编解码用 {@code xmap} 直接产出数组。
     */
    public static WuzangState of(float[] qi, float[] blood,
                                 boolean[] qiDeficient, boolean[] bloodDeficient, boolean[] qiExcess) {
        return new WuzangState(qi, blood, qiDeficient, bloodDeficient, qiExcess);
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) {
            out.add(v);
        }
        return out;
    }

    private static boolean[] toBoolArray(List<Boolean> list) {
        boolean[] out = new boolean[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static List<Boolean> toBoolList(boolean[] arr) {
        List<Boolean> out = new ArrayList<>(arr.length);
        for (boolean v : arr) {
            out.add(v);
        }
        return out;
    }

    private static final Codec<float[]> FLOAT_ARRAY_CODEC =
            Codec.FLOAT.listOf().xmap(WuzangState::toFloatArray, WuzangState::toFloatList);

    private static final Codec<boolean[]> BOOL_ARRAY_CODEC =
            Codec.BOOL.listOf().xmap(WuzangState::toBoolArray, WuzangState::toBoolList);

    public static final Codec<WuzangState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FLOAT_ARRAY_CODEC.fieldOf("qi").forGetter(WuzangState::qi),
            FLOAT_ARRAY_CODEC.fieldOf("blood").forGetter(WuzangState::blood),
            BOOL_ARRAY_CODEC.fieldOf("qi_deficient").forGetter(WuzangState::qiDeficientFlags),
            BOOL_ARRAY_CODEC.fieldOf("blood_deficient").forGetter(WuzangState::bloodDeficientFlags),
            BOOL_ARRAY_CODEC.fieldOf("qi_excess").forGetter(WuzangState::qiExcessFlags)
    ).apply(instance, WuzangState::of));

    // ---------------- 访问器 ----------------

    /** 五脏「气」的数组本体（长度 5，下标见 {@link WuzangType}）。 */
    public float[] qi() {
        return this.qi;
    }

    /** 五脏「血」的数组本体（长度 5，下标见 {@link WuzangType}）。 */
    public float[] blood() {
        return this.blood;
    }

    public boolean[] qiDeficientFlags() {
        return this.qiDeficient;
    }

    public boolean[] bloodDeficientFlags() {
        return this.bloodDeficient;
    }

    public boolean[] qiExcessFlags() {
        return this.qiExcess;
    }

    public float qiOf(WuzangType type) {
        return this.qi[type.index()];
    }

    public float bloodOf(WuzangType type) {
        return this.blood[type.index()];
    }

    public boolean isQiDeficient(WuzangType type) {
        return this.qiDeficient[type.index()];
    }

    public boolean isBloodDeficient(WuzangType type) {
        return this.bloodDeficient[type.index()];
    }

    public boolean isQiExcess(WuzangType type) {
        return this.qiExcess[type.index()];
    }

    /** 气血两虚是「气虚 + 血虚」的组合，由两个标记派生，不单独落盘。 */
    public boolean isBothDeficient(WuzangType type) {
        int i = type.index();
        return this.qiDeficient[i] && this.bloodDeficient[i];
    }

    /** 该脏是否处于任何异常状态（虚 / 亢）。 */
    public boolean isImbalanced(WuzangType type) {
        return isQiDeficient(type) || isBloodDeficient(type) || isQiExcess(type);
    }

    /** 失衡的脏数（供未来「多脏失衡」钩子使用，本次不产生实际逻辑）。 */
    public int imbalancedCount() {
        int n = 0;
        for (WuzangType type : WuzangType.values()) {
            if (isImbalanced(type)) {
                n++;
            }
        }
        return n;
    }

    // ---------------- 变更器 ----------------

    public void setQi(WuzangType type, float value) {
        this.qi[type.index()] = WuzangRules.clamp(value);
        refreshMarkers(type);
    }

    public void setBlood(WuzangType type, float value) {
        this.blood[type.index()] = WuzangRules.clamp(value);
        refreshMarkers(type);
    }

    public void addQi(WuzangType type, float delta) {
        setQi(type, this.qi[type.index()] + delta);
    }

    public void addBlood(WuzangType type, float delta) {
        setBlood(type, this.blood[type.index()] + delta);
    }

    /** 把五脏气血全部重置回基线。 */
    public void resetToBaseline() {
        for (int i = 0; i < WuzangType.COUNT; i++) {
            this.qi[i] = WuzangConfig.BASELINE;
            this.blood[i] = WuzangConfig.BASELINE;
        }
        refreshMarkers();
    }

    /**
     * 依据当前数值重算三个虚实标记数组。
     *
     * <p>每次扫描结束时调用一次；表现层 buff 的增减完全由这组标记驱动。
     */
    public void refreshMarkers() {
        for (WuzangType type : WuzangType.values()) {
            refreshMarkers(type);
        }
    }

    /**
     * 只重算某一脏的三个标记。
     *
     * <p><b>迟滞</b>（v0.6.0）：触发用单点阈值（虚 &lt;40、亢 &gt;90），解除用更靠内的上沿
     * （虚 ≥45、亢 ≤85），中间 5 点是不敏感带 —— 数值缓慢漂移时不会贴着阈值反复横跳。
     *
     * <p>判定输入是<b>稳定值</b>（{@code this.qi / this.blood}）：时辰波动与微观抖动
     * 从不写进这两个数组，因此它们天然不参与判定。
     */
    private void refreshMarkers(WuzangType type) {
        int i = type.index();
        this.qiDeficient[i] = WuzangRules.deficientHysteresis(this.qi[i], this.qiDeficient[i]);
        this.bloodDeficient[i] = WuzangRules.deficientHysteresis(this.blood[i], this.bloodDeficient[i]);
        this.qiExcess[i] = WuzangRules.excessHysteresis(this.qi[i], this.qiExcess[i]);
    }


    /** 标记的一行摘要，供调试命令使用，例如 {@code 肝:气虚+血虚 心:气亢}；全平则返回「平」。 */
    public String markerSummary() {
        StringBuilder sb = new StringBuilder();
        for (WuzangType type : WuzangType.values()) {
            StringBuilder one = new StringBuilder();
            if (isQiDeficient(type)) {
                one.append("气虚");
            }
            if (isBloodDeficient(type)) {
                one.append(one.length() > 0 ? "+" : "").append("血虚");
            }
            if (isQiExcess(type)) {
                one.append(one.length() > 0 ? "+" : "").append("气亢");
            }
            if (one.length() > 0) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(type.zhName()).append(':').append(one);
            }
        }
        return sb.length() == 0 ? "平" : sb.toString();
    }

    @Override
    public String toString() {
        return "WuzangState[qi=" + toFloatList(this.qi) + ", blood=" + toFloatList(this.blood) + "]";
    }
}
