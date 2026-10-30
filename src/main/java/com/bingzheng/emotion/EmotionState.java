package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 情志拓展的玩家专属数据 —— 与内核的营卫附件<b>互相隔离</b>。
 *
 * <p>存放在 Fabric <b>Persistent Attachment</b> 里（{@code bingzhengmod:emotion}），
 * <b>不侵占原版玩家数据域的任何字段</b>。
 *
 * <p>本类可变：一个服务端 tick 里会连续改动多个字段，用 record + with 链会非常啰嗦。
 * 改动完成后<b>必须调用</b> {@link EmotionAttachments#set} 写回，以触发附件的持久化标记。
 *
 * <p><b>玩家看不到这里任何一个数字。</b>全部数值后台隐形运行，不绘制任何 UI。
 */
public final class EmotionState {

    private final float[] values = new float[EmotionType.COUNT];
    private final float[] baseline = new float[EmotionType.COUNT];

    private float spiritConsumption;
    private float bodyConsumption;
    private int cooldownDelirium;
    private int meditationNightCounter;
    private int meditationProgress;
    private boolean deliriumActive;
    private int restlessTicks;

    /** 新玩家初始状态。 */
    public static EmotionState fresh() {
        return new EmotionState(new float[EmotionType.COUNT], null,
                EmotionConfig.CONSUMPTION_START, EmotionConfig.CONSUMPTION_START,
                0, 0, 0, false, 0);
    }

    private EmotionState(float[] values, float[] baseline,
                         float spiritConsumption, float bodyConsumption,
                         int cooldownDelirium, int meditationNightCounter,
                         int meditationProgress, boolean deliriumActive, int restlessTicks) {
        float[] v = values == null ? new float[EmotionType.COUNT] : values;
        for (int i = 0; i < EmotionType.COUNT; i++) {
            this.values[i] = clampEmotion(i < v.length ? v[i] : EmotionConfig.EMOTION_BASELINE_DEFAULT);
        }
        float[] b = baseline == null ? this.values.clone() : baseline;
        for (int i = 0; i < EmotionType.COUNT; i++) {
            this.baseline[i] = clampEmotion(i < b.length ? b[i] : EmotionConfig.EMOTION_BASELINE_DEFAULT);
        }
        this.spiritConsumption = clampConsumption(spiritConsumption);
        this.bodyConsumption = clampConsumption(bodyConsumption);
        this.cooldownDelirium = Math.max(0, cooldownDelirium);
        this.meditationNightCounter = Math.max(0, meditationNightCounter);
        this.meditationProgress = Math.max(0, meditationProgress);
        this.deliriumActive = deliriumActive;
        this.restlessTicks = Math.max(0, restlessTicks);
    }

    /**
     * 由 Codec 使用的工厂。
     *
     * <p>参数用的是装箱类型：{@code RecordCodecBuilder} 组合后的函数签名就是装箱的
     * （Codec&lt;Float&gt;/Codec&lt;Integer&gt;/Codec&lt;Boolean&gt;），写成基本类型会匹配不上。
     */
    public static EmotionState of(float[] values, float[] baseline,
                                  Float spiritConsumption, Float bodyConsumption,
                                  Integer cooldownDelirium, Integer meditationNightCounter,
                                  Integer meditationProgress, Boolean deliriumActive,
                                  Integer restlessTicks) {
        return new EmotionState(values, baseline,
                spiritConsumption == null ? EmotionConfig.CONSUMPTION_START : spiritConsumption,
                bodyConsumption == null ? EmotionConfig.CONSUMPTION_START : bodyConsumption,
                cooldownDelirium == null ? 0 : cooldownDelirium,
                meditationNightCounter == null ? 0 : meditationNightCounter,
                meditationProgress == null ? 0 : meditationProgress,
                deliriumActive != null && deliriumActive,
                restlessTicks == null ? 0 : restlessTicks);
    }

    private static float clampEmotion(float v) {
        return Math.max(EmotionConfig.EMOTION_MIN, Math.min(EmotionConfig.EMOTION_MAX, v));
    }

    private static float clampConsumption(float v) {
        return Math.max(EmotionConfig.CONSUMPTION_MIN, Math.min(EmotionConfig.CONSUMPTION_MAX, v));
    }

    private static float[] toArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static List<Float> toList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) {
            out.add(v);
        }
        return out;
    }

    private static final Codec<float[]> FLOAT_ARRAY_CODEC =
            Codec.FLOAT.listOf().xmap(EmotionState::toArray, EmotionState::toList);

    public static final Codec<EmotionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FLOAT_ARRAY_CODEC.fieldOf("emotion_values").forGetter(EmotionState::values),
            FLOAT_ARRAY_CODEC.fieldOf("emotion_baseline").forGetter(EmotionState::baseline),
            // 【v0.9.2】神耗 / 体耗改为 optional + 默认 0：旧存档缺这两个字段也能读进来（向后兼容），
            // 且数值会随附件持久化 —— 大退重进不再被清零。
            Codec.FLOAT.optionalFieldOf("spirit_consumption", 0.0F)
                    .forGetter(EmotionState::getSpiritConsumption),
            Codec.FLOAT.optionalFieldOf("body_consumption", 0.0F)
                    .forGetter(EmotionState::getBodyConsumption),
            Codec.INT.fieldOf("cooldown_delirium").forGetter(EmotionState::getCooldownDelirium),
            Codec.INT.fieldOf("meditation_night_counter").forGetter(EmotionState::getMeditationNightCounter),
            Codec.INT.fieldOf("meditation_progress").forGetter(EmotionState::getMeditationProgress),
            Codec.BOOL.fieldOf("delirium_active").forGetter(EmotionState::isDeliriumActive),
            // 用 optionalFieldOf 提供默认值：这样旧存档缺这个字段也能正常加载
            Codec.INT.optionalFieldOf("restless_ticks", 0).forGetter(EmotionState::getRestlessTicks)
    ).apply(instance, EmotionState::of));

    // ---------------- 访问器 ----------------

    public float[] values() {
        return this.values;
    }

    public float[] baseline() {
        return this.baseline;
    }

    public float emotion(EmotionType type) {
        return this.values[type.index()];
    }

    public float baselineOf(EmotionType type) {
        return this.baseline[type.index()];
    }

    public float getSpiritConsumption() {
        return this.spiritConsumption;
    }

    public float getBodyConsumption() {
        return this.bodyConsumption;
    }

    public int getCooldownDelirium() {
        return this.cooldownDelirium;
    }

    public int getMeditationNightCounter() {
        return this.meditationNightCounter;
    }

    public int getMeditationProgress() {
        return this.meditationProgress;
    }

    public boolean isDeliriumActive() {
        return this.deliriumActive;
    }

    /** 心绪不宁剩余 tick；> 0 期间禁止生成神安。 */
    public int getRestlessTicks() {
        return this.restlessTicks;
    }

    // ---------------- 变更器 ----------------

    public void addEmotion(EmotionType type, float delta) {
        this.values[type.index()] = clampEmotion(this.values[type.index()] + delta);
    }

    public void setEmotion(EmotionType type, float value) {
        this.values[type.index()] = clampEmotion(value);
    }

    public void setBaseline(EmotionType type, float value) {
        this.baseline[type.index()] = clampEmotion(value);
    }

    public void addSpiritConsumption(float delta) {
        this.spiritConsumption = clampConsumption(this.spiritConsumption + delta);
    }

    public void setSpiritConsumption(float value) {
        this.spiritConsumption = clampConsumption(value);
    }

    public void addBodyConsumption(float delta) {
        this.bodyConsumption = clampConsumption(this.bodyConsumption + delta);
    }

    public void setBodyConsumption(float value) {
        this.bodyConsumption = clampConsumption(value);
    }

    public void setCooldownDelirium(int ticks) {
        this.cooldownDelirium = Math.max(0, ticks);
    }

    public void setMeditationNightCounter(int n) {
        this.meditationNightCounter = Math.max(0, n);
    }

    public void setMeditationProgress(int ticks) {
        this.meditationProgress = Math.max(0, ticks);
    }

    public void setDeliriumActive(boolean active) {
        this.deliriumActive = active;
    }

    public void setRestlessTicks(int ticks) {
        this.restlessTicks = Math.max(0, ticks);
    }

    /** 七情最大值（调试/自检用）。 */
    public EmotionType peak() {
        int best = 0;
        for (int i = 1; i < EmotionType.COUNT; i++) {
            if (this.values[i] > this.values[best]) {
                best = i;
            }
        }
        return EmotionType.byIndex(best);
    }

    @Override
    public String toString() {
        return "EmotionState" + BingzhengMod.MOD_ID + Arrays.toString(this.values);
    }
}
