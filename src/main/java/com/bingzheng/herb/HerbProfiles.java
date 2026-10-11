package com.bingzheng.herb;

/**
 * 垂直切片阶段的药材档案表。
 *
 * <p>原生基础药材 7 味 + 炮制产物 2 味，共 9 味，均由用户裁定。
 * 完整的 62 味清单见 {@code assets/bingzhengmod/data/herbs_temp.json}（临时占位，待人工补全）。
 *
 * <p>//TODO 后续迁移配置文件：本表后续应从数据文件加载，而不是硬编码在代码里。
 */
public final class HerbProfiles {

    // ---------------- 原生基础药材（is_raw = true）----------------
    public static final HerbProfile GUI_ZHI = HerbProfile.raw("gui_zhi", "桂枝", false);
    public static final HerbProfile SHAO_YAO = HerbProfile.raw("shao_yao", "芍药", false);
    public static final HerbProfile SHENG_JIANG = HerbProfile.raw("sheng_jiang", "生姜", false);
    public static final HerbProfile DA_ZAO = HerbProfile.raw("da_zao", "大枣", false);
    public static final HerbProfile GAN_CAO = HerbProfile.raw("gan_cao", "甘草", false);
    public static final HerbProfile FENG_MI = HerbProfile.raw("feng_mi", "蜂蜜", false);
    /** 生附子：<b>剧毒</b>，必须经简化三步炮制转为制附子后方可入药。 */
    public static final HerbProfile SHENG_FU_ZI = HerbProfile.raw("sheng_fu_zi", "生附子", true);

    // ---------------- 炮制产物（is_raw = false）----------------
    /** 炙甘草 = 甘草 + 蜂蜜 蜜炙。 */
    public static final HerbProfile ZHI_GAN_CAO = HerbProfile.processed("zhi_gan_cao", "炙甘草", "gan_cao");
    /** 制附子 = 生附子 经浸泡/辅料煮制/晾干三步炮制，毒性已去除。 */
    public static final HerbProfile ZHI_FU_ZI = HerbProfile.processed("zhi_fu_zi", "制附子", "sheng_fu_zi");

    private HerbProfiles() {
    }
}
