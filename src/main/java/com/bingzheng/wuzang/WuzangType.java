package com.bingzheng.wuzang;

/**
 * 五脏枚举。顺序固定为：肝、心、脾、肺、肾，与 {@link WuzangState} 的下标一一对应。
 *
 * <p>每脏带两个标识：
 * <ul>
 *   <li>{@link #key()} —— 英文键，用于附件字段名与命令参数（liver / heart / ...）；</li>
 *   <li>{@link #pinyin()} —— 拼音，用于状态效果 ID（gan / xin / ...），与情志模块的效果命名风格一致。</li>
 * </ul>
 *
 * <p>五脏的五行属性（青赤黄白黑）只用于表现层配色，<b>不参与任何数值逻辑</b> ——
 * 文档没有记载五行生克传变，本项目也不自行新增。
 */
public enum WuzangType {

    LIVER(0, "肝", "liver", "gan"),
    HEART(1, "心", "heart", "xin"),
    SPLEEN(2, "脾", "spleen", "pi"),
    LUNG(3, "肺", "lung", "fei"),
    KIDNEY(4, "肾", "kidney", "shen");

    /** 五脏总数。 */
    public static final int COUNT = 5;

    private final int index;
    private final String zhName;
    private final String key;
    private final String pinyin;

    WuzangType(int index, String zhName, String key, String pinyin) {
        this.index = index;
        this.zhName = zhName;
        this.key = key;
        this.pinyin = pinyin;
    }

    public int index() {
        return this.index;
    }

    /** 中文名，如「肝」。 */
    public String zhName() {
        return this.zhName;
    }

    /** 英文键，如 {@code liver}。 */
    public String key() {
        return this.key;
    }

    /** 拼音，如 {@code gan}，用于效果 ID。 */
    public String pinyin() {
        return this.pinyin;
    }

    public static WuzangType byIndex(int index) {
        for (WuzangType t : values()) {
            if (t.index == index) {
                return t;
            }
        }
        return LIVER;
    }

    /**
     * 按英文键或拼音解析，用于调试命令。
     *
     * <p><b>为什么没有中文分支（v0.4.2 清理）：</b>命令参数用的是 Brigadier 的
     * {@code StringArgumentType.word()}，而它的字符白名单只有
     * {@code 0-9 A-Z a-z _ - . +}（javap 实测 {@code StringReader#isAllowedInUnquotedString}），
     * 中文根本传不进这个方法 —— 原先写在这里的中文匹配分支是永远不会被触发的死代码。
     * 中文脏名一律在参数解析阶段就被 Brigadier 拒掉。
     */
    public static WuzangType parse(String raw) {
        for (WuzangType t : values()) {
            if (t.key.equalsIgnoreCase(raw) || t.pinyin.equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return null;
    }
}
