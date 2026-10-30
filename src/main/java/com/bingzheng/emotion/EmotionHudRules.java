package com.bingzheng.emotion;

/**
 * 神耗 / 体耗 HUD 的<b>纯规则层</b>（v0.9.1：布局判定 + 格子分割 + 模式守卫）。
 *
 * <p>只放「可断言的决定」，不放任何渲染对象：
 * 数值→格数、格数→贴图帧、上下行映射、<b>两行条的 y 坐标判定</b>、
 * <b>游戏模式守卫</b>、以及格子分割的几何常量。客户端只负责按这些结论画。
 */
public final class EmotionHudRules {

    // ---------------- 数值 → 格 ----------------

    /** 每行的格数（每行 10 格）。 */
    public static final int SEGMENTS = 10;
    /** 每格代表的点数。 */
    public static final float POINTS_PER_SEGMENT = 10.0F;
    /** 半格门槛（v mod 10 ≥ 5 记半格）。 */
    public static final float HALF_SEGMENT_POINTS = 5.0F;

    /** 上行 = 神耗。 */
    public static final int ROW_SPIRIT = 0;
    /** 下行 = 体耗。 */
    public static final int ROW_BODY = 1;

    /** HUD 默认开关。 */
    public static final boolean DEFAULT_ENABLED = true;

    /** 贴图帧：空 / 半 / 满。 */
    public static final int FRAME_EMPTY = 0;
    public static final int FRAME_HALF = 1;
    public static final int FRAME_FULL = 2;

    // ---------------- 几何（全部集中，供渲染与自检共用） ----------------

    /** 两条（神耗 + 体耗）。 */
    public static final int ROWS = 2;
    /** 单格高 = 红心高（9）的一半取整。 */
    public static final int CELL_HEIGHT = 4;
    /** 两行总高。 */
    public static final int ROWS_HEIGHT = ROWS * CELL_HEIGHT;

    /** 贴图单帧宽（与红心同宽 9）。 */
    public static final int CELL_WIDTH = 9;
    /** 可见图标宽：左右各留 1px 透明边，形成**格与格之间的空隙**。 */
    public static final int ICON_WIDTH = 7;
    /** 格距：与原版红心同节距（8）。 */
    public static final int PITCH = 8;

    /** 血量条顶边距屏幕底部的距离（原版 39）。 */
    public static final int HEALTH_BAR_Y_FROM_BOTTOM = 39;
    /** 血量条起点距屏幕中心的偏移（原版 91）。 */
    public static final int BASE_X_OFFSET = 91;

    /** 护甲行高（原版护甲图标 9px + 1px 间隙）。 */
    public static final int ARMOR_ROW_HEIGHT = 10;
    /** 护甲图标高度。 */
    public static final int ARMOR_ICON_HEIGHT = 9;

    private EmotionHudRules() {
    }

    /** 数值钳到 0..100。 */
    public static float clamp(float value) {
        return Math.max(0.0F, Math.min(100.0F, value));
    }

    /** 数值 → 格数（0..10，粒度 0.5）：{@code floor(v/10) + (v mod 10 ≥ 5 ? 0.5 : 0)}。 */
    public static float cells(float value) {
        float v = clamp(value);
        int whole = (int) (v / POINTS_PER_SEGMENT);
        if (whole >= SEGMENTS) {
            return SEGMENTS;
        }
        float remainder = v - whole * POINTS_PER_SEGMENT;
        return whole + (remainder >= HALF_SEGMENT_POINTS ? 0.5F : 0.0F);
    }

    /** 第 {@code index} 格用哪一帧（满 / 半 / 空）。 */
    public static int frameFor(float cells, int index) {
        if (index < 0 || index >= SEGMENTS) {
            return FRAME_EMPTY;
        }
        if (cells >= index + 1) {
            return FRAME_FULL;
        }
        return cells >= index + 0.5F ? FRAME_HALF : FRAME_EMPTY;
    }

    /** 该行显示哪一项（0 = 神耗，1 = 体耗）。 */
    public static float valueForRow(int row, float spirit, float body) {
        return row == ROW_SPIRIT ? spirit : body;
    }

    /** 关闭 HUD 时不注册渲染回调。 */
    public static boolean shouldRegister(boolean enabled) {
        return enabled;
    }

    /** 数值变化才推送（未变化走定时兜底）。 */
    public static boolean shouldSync(float lastSpirit, float lastBody, float spirit, float body) {
        return Math.abs(lastSpirit - spirit) > 0.001F || Math.abs(lastBody - body) > 0.001F;
    }

    // ---------------- 模式守卫 ----------------

    /**
     * 该游戏模式是否渲染 HUD。
     *
     * <p>原版 {@code GameMode} 枚举序：0 生存、1 创造、2 冒险、3 旁观。
     * <b>只渲染生存 / 冒险</b>：创造与旁观没有血量条语义，画上去反而是噪音。
     */
    public static boolean modeAllows(int gameModeOrdinal) {
        return gameModeOrdinal == 0 || gameModeOrdinal == 2;
    }

    // ---------------- 布局判定（不写死坐标） ----------------

    /** 血量条顶边 y（原版锚点：屏幕高 − 39）。 */
    public static int healthTop(int screenHeight) {
        return screenHeight - HEALTH_BAR_Y_FROM_BOTTOM;
    }

    /** 血量条起点 x（原版锚点：屏幕宽/2 − 91）。 */
    public static int baseX(int screenWidth) {
        return screenWidth / 2 - BASE_X_OFFSET;
    }

    /** 格与格之间的空隙（>0 表示是分开的独立小图标，不是一整条）。 */
    public static int gap() {
        return PITCH - ICON_WIDTH;
    }

    /** 十格总宽：与原版红心行完全等宽（9 + 8×9 = 81），因此左端天然对齐。 */
    public static int rowWidth() {
        return PITCH * (SEGMENTS - 1) + CELL_WIDTH;
    }

    /**
     * <b>神耗 / 体耗两行的顶边 y</b>。
     *
     * <p>目标顺序（从上到下）：护甲值 → 神耗 / 体耗条 → 血量条。
     * 默认紧贴血量条顶边上方；若这样会压到护甲行，则整体上移到护甲行上方 ——
     * <b>「不重叠」优先于「贴着血量条」</b>。
     *
     * <p>原版里护甲行与血量条之间只有 1px，8px 的双行条放不进去；
     * 若将来有模组把护甲挪高（护甲下方真的留出了空间），本函数会自动落回
     * 「护甲底边与血量顶边之间」——两种情形都由同一处判定。
     *
     * @param healthTop    血量条顶边 y
     * @param armorPresent 是否渲染护甲行
     * @param armorTop     护甲行顶边 y（armorPresent 为 false 时忽略）
     */
    public static int rowsTop(int healthTop, boolean armorPresent, int armorTop) {
        int y = healthTop - ROWS_HEIGHT;
        if (armorPresent) {
            int armorBottom = armorTop + ARMOR_ICON_HEIGHT;
            if (y < armorBottom) {
                y = armorTop - ROWS_HEIGHT;
            }
        }
        return y;
    }

    /** 只给「有护甲且护甲紧贴血量条」的原版情形用的便捷重载（护甲顶边 = 血量顶边 − 10）。 */
    public static int rowsTop(int healthTop, boolean armorPresent) {
        return rowsTop(healthTop, armorPresent, healthTop - ARMOR_ROW_HEIGHT);
    }
}
