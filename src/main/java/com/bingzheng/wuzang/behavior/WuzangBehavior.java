package com.bingzheng.wuzang.behavior;

/**
 * 五脏的行为驱动项。
 *
 * <p>每一项都遵循同一套规则：
 * <ul>
 *   <li><b>内置兜底</b>：只用原版可观测的状态（时间、天气、湿度、饥饿、劳作）自行判定，
 *       保证<b>单开五脏</b>时行为也能驱动气血升降；</li>
 *   <li><b>跨拓展顶替</b>：一旦对应拓展装好并 {@link WuzangBehaviorRegistry#install} 了权威数据源，
 *       内置兜底立刻停用，改读该拓展的数据 —— <b>同一项判定只由一处数据驱动</b>。</li>
 * </ul>
 *
 * <p><b>注意：七情不在这里。</b>七情走的是既有的「气机扰动信号 → 五条固定映射」冲量通道
 * （数据源本身就来自情志拓展）。把它塞进本枚举会让同一条信号同时走冲量与强度两条路，
 * 违反「不重复计算」。因此五项行为一概不读七情信号。
 */
public enum WuzangBehavior {

    /** 熬夜 / 昼夜颠倒。 */
    STAY_UP("熬夜/昼夜颠倒", "情志拓展·神耗"),

    /** 受凉 / 淋雨 / 湿处。 */
    COLD_DAMP("受凉/淋雨/湿处", "温湿拓展·核心体温/环境温湿度"),

    /** 压力 / 情绪压抑。 */
    MENTAL_PRESSURE("压力/情绪压抑", "情志拓展·神耗"),

    /** 暴饮暴食 / 生冷。 */
    EATING("暴饮暴食/生冷", "消化拓展·食积/膏脂"),

    /** 过劳 / 剧烈劳作。 */
    OVERWORK("过劳/剧烈劳作", "情志拓展·体耗");

    private final String zhName;
    private final String authorityName;

    WuzangBehavior(String zhName, String authorityName) {
        this.zhName = zhName;
        this.authorityName = authorityName;
    }

    /** 中文名，用于调试输出。 */
    public String zhName() {
        return this.zhName;
    }

    /** 文档指定的权威数据源名称（该拓展装好后应当顶替本项）。 */
    public String authorityName() {
        return this.authorityName;
    }
}
