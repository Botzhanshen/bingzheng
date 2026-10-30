package com.bingzheng.emotion;

/**
 * 七情枚举。顺序固定为：怒、喜、忧、思、悲、恐、惊，与 {@link EmotionState} 的下标一一对应。
 *
 * <p>每一项对应一种「气机扰动」状态效果，命名见 {@link EmotionEffects}。
 */
public enum EmotionType {

    ANGER(0, "怒", "gan_qi_kang", "肝气亢"),
    JOY(1, "喜", "xin_shen_yang", "心神扬"),
    WORRY(2, "忧", "fei_qi_yu", "肺气郁"),
    PENSIVENESS(3, "思", "pi_yi_chan", "脾意缠"),
    GRIEF(4, "悲", "fei_qi_xiao", "肺气消"),
    FEAR(5, "恐", "shen_qi_she", "肾气慑"),
    FRIGHT(6, "惊", "shen_zha_yao", "神乍摇");

    /** 七情的总数。 */
    public static final int COUNT = 7;

    private final int index;
    private final String zhName;
    private final String disturbancePath;
    private final String disturbanceZhName;

    EmotionType(int index, String zhName, String disturbancePath, String disturbanceZhName) {
        this.index = index;
        this.zhName = zhName;
        this.disturbancePath = disturbancePath;
        this.disturbanceZhName = disturbanceZhName;
    }

    /** 在七情数组中的下标。 */
    public int index() {
        return this.index;
    }

    /** 七情本名，如「怒」。 */
    public String zhName() {
        return this.zhName;
    }

    /** 对应气机扰动效果的注册路径，如 {@code gan_qi_kang}。 */
    public String disturbancePath() {
        return this.disturbancePath;
    }

    /** 对应气机扰动的中文显示名，如「肝气亢」。 */
    public String disturbanceZhName() {
        return this.disturbanceZhName;
    }

    public static EmotionType byIndex(int index) {
        for (EmotionType t : values()) {
            if (t.index == index) {
                return t;
            }
        }
        return ANGER;
    }
}
