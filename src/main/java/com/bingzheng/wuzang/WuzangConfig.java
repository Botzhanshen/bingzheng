package com.bingzheng.wuzang;

/**
 * 五脏拓展的全部数值参数。
 *
 * <p><b>本类里每一个数字都标注了 {@code //TODO 后续迁移外部配置}</b>，
 * 现阶段先集中在此，便于整体搬迁到配置文件。
 */
public final class WuzangConfig {

    private WuzangConfig() {
    }

    // ---------------- 数值区间与基线 ----------------

    /** 气血取值范围。 //TODO 后续迁移外部配置 */
    public static final float MIN = 0.0F;
    public static final float MAX = 100.0F;

    /** 平人基线：健康时五脏气血都应停在这里。 //TODO 后续迁移外部配置 */
    public static final float BASELINE = 80.0F;

    /** 新玩家初始值（平人）。 //TODO 后续迁移外部配置 */
    public static final float START = 80.0F;

    /** 与基线的差距小于该值就视为已归位，不再逐格挪动（避免浮点抖动）。 //TODO 后续迁移外部配置 */
    public static final float RESTORE_MARGIN = 0.01F;

    // ---------------- 阈值判定 ----------------

    /** 低于该值判为「虚」（气虚 / 血虚分别判定）。 //TODO 后续迁移外部配置 */
    public static final float DEFICIENT_THRESHOLD = 40.0F;

    /**
     * 高于该值判为「亢」。 //TODO 后续迁移外部配置
     *
     * <p><b>只对气判定</b>：文档里没有「血亢」这种说法，血只有亏虚一途。
     */
    public static final float EXCESS_THRESHOLD = 90.0F;

    /**
     * 「虚」的<b>解除</b>阈值（迟滞上沿，v0.6.0 新增）。
     *
     * <p>单点判定在数值缓慢漂移时会贴着阈值反复横跳（闪断），因此改成区间迟滞：
     * 低于 {@link #DEFICIENT_THRESHOLD} 才<b>触发</b>虚，回升到本值才<b>解除</b>，
     * 两者之间的 5 点就是迟滞带。 //TODO 后续迁移外部配置
     */
    public static final float DEFICIENT_RELEASE = 45.0F;

    /** 「亢」的<b>解除</b>阈值（迟滞下沿）。亢 90 触发、回落 ≤ 本值解除。 //TODO 后续迁移外部配置 */
    public static final float EXCESS_RELEASE = 85.0F;

    // ---------------- 自然衰减 / 恢复 ----------------

    /** 向基线靠拢的轮次间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int RESTORE_INTERVAL = 40;

    /** 常态下每轮向基线靠拢的**基准**步长（偏离等于参考距离时的步长）。 //TODO 后续迁移外部配置 */
    public static final float RESTORE_STEP = 0.25F;

    /**
     * 「偏离越远、回落/回升越明显」的参考距离（v1.05 文档语义）。
     *
     * <p>偏离正好等于该值时步长就是 {@link #RESTORE_STEP}；偏离更大按比例放大，
     * 上限 {@link #RESTORE_MAX_FACTOR} 倍。偏离更小时步长同比缩小（越接近基线越慢）。
     * //TODO 后续迁移外部配置
     */
    public static final float RESTORE_DISTANCE_SCALE = 20.0F;

    /** 恢复步长的距离放大上限（防止从 0 回升时一步跳太远）。 //TODO 后续迁移外部配置 */
    public static final float RESTORE_MAX_FACTOR = 4.0F;

    /** 静坐冥想时恢复速率的倍率（文档：缓慢同步恢复五脏气血）。 //TODO 后续迁移外部配置 */
    public static final float MEDITATING_RESTORE_MULTIPLIER = 3.0F;

    /** 睡眠时恢复速率的倍率（文档：睡眠大幅加速气血修复）。 //TODO 后续迁移外部配置 */
    public static final float SLEEPING_RESTORE_MULTIPLIER = 8.0F;

    // ---------------- 情志联动（五脏只在收到信号时自行改自己的数值） ----------------

    /** 怒 → 肝：肝气上升。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_LIVER_QI_DELTA = 8.0F;

    /** 喜 → 心：心气上升。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_HEART_QI_DELTA = 8.0F;

    /** 思 → 脾：脾气损耗。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_SPLEEN_QI_DELTA = -8.0F;

    /** 悲 → 肺：肺气损耗。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_LUNG_QI_DELTA = -8.0F;

    /** 恐 → 肾：肾气损耗。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_KIDNEY_QI_DELTA = -8.0F;

    /**
     * 怒 → 肝：<b>久怒耗肝血</b>（血降）。
     *
     * <p>v1.05「四、气血升降与行为驱动规则」新增：怒气上涌是气升，久怒另耗肝血。
     * //TODO 后续迁移外部配置
     */
    public static final float EMOTION_LIVER_BLOOD_DELTA = -6.0F;

    /**
     * 喜 → 神耗：<b>过喜伤心神</b>（神耗升）。
     *
     * <p>写的是<b>情志拓展</b>的神耗（经反射软链接），情志不在场时静默不生效。
     * //TODO 后续迁移外部配置
     */
    public static final float EMOTION_SPIRIT_ON_JOY = 1.5F;

    /** 恐 → 神耗：<b>惊恐耗神</b>（神耗升）。同上。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_SPIRIT_ON_FRIGHT = 1.5F;

    // ---------------- 受伤 ----------------

    /** 每点实际伤害折算的失血量（点）。 //TODO 后续迁移外部配置 */
    public static final float INJURY_BLOOD_PER_DAMAGE = 0.6F;

    // ---------------- 扫描节流 ----------------

    /** 全局扫描间隔（tick）——节流，非每 tick。 //TODO 后续迁移外部配置 */
    public static final int SCAN_INTERVAL = 20;

    // ---------------- 行为强度点（v0.7.0 任务单一：行为驱动量化规范 v2） ----------------

    /**
     * 一个「结算块」的长度（tick）：<b>10 分钟 = 12000 tick</b>。
     *
     * <p>文档里所有累积量与档位效果都是「每 10 分钟」给出的，本常量就是这个时间的载体。
     * //TODO 后续迁移外部配置
     */
    public static final int POINT_BLOCK_TICKS = 12000;

    /** 强度点的上限（防止长期累积到荒谬的档位）。 //TODO 后续迁移外部配置 */
    public static final double POINT_MAX = 12.0D;

    /** 没有行为累积时，每个结算块按恢复递减的强度点。 //TODO 后续迁移外部配置 */
    public static final double POINT_DECAY_PER_BLOCK = 1.0D;

    /**
     * 文档原值 → 实际驱动速率的折算系数。
     *
     * <p><b>为什么需要它（这是本轮唯一的数值裁定，请重点验收）</b>：
     * 文档的档位量是「每 10 分钟」的离散量（例如肺气 −2 / 10 分钟 = 0.0001667 点/tick），
     * 而自然恢复早已按 v0.5.1 批准的曲线运行：距离基线 1 点时约 0.00625 点/tick。
     * 若照文档原值直接铺开，行为驱动的平衡点只偏离基线约 <b>0.5 点</b> —— 行为层等于失效。
     * 取 75 折算后，最重档（肺气 −3/块）的速率 ≈ 0.0188 点/tick，
     * 与 v0.5.0/v0.6.0 满强度（0.0125 点/tick）同量级，行为层维持原有驱动力。
     * <b>设为 1.0 即完全按文档原值</b>（行为几乎推不动气血）。
     * //TODO 后续迁移外部配置
     */
    public static final float POINT_RATE_SCALE = 75.0F;

    // ---- 一、受凉（每块按体温档位累积受凉点） ----

    /** 体温档位对应的每块受凉点：正常 / 微凉 / 冷 / 失温。 //TODO 后续迁移外部配置 */
    public static final double COLD_POINTS_NORMAL = 0.0D;
    public static final double COLD_POINTS_CHILL = 1.0D;
    public static final double COLD_POINTS_COLD = 2.0D;
    public static final double COLD_POINTS_HYPOTHERMIA = 4.0D;

    /** 受凉点档位线：≥2 → 一档；≥4 → 二档（档位替换，不相加）。 //TODO 后续迁移外部配置 */
    public static final double COLD_TIER1_AT = 2.0D;
    public static final double COLD_TIER2_AT = 4.0D;

    /** 受凉一档：肺气 −2、脾气 −1（每块）。 //TODO 后续迁移外部配置 */
    public static final float COLD_TIER1_LUNG_QI = -2.0F;
    public static final float COLD_TIER1_SPLEEN_QI = -1.0F;

    /** 受凉二档：肺气 −3、脾阳（=脾气）−2（每块）。 //TODO 后续迁移外部配置 */
    public static final float COLD_TIER2_LUNG_QI = -3.0F;
    public static final float COLD_TIER2_SPLEEN_QI = -2.0F;

    // ---- 二、熬夜 / 昼夜颠倒 ----

    /** 夜间清醒每块累积的熬夜点。 //TODO 后续迁移外部配置 */
    public static final double STAY_UP_POINTS_PER_BLOCK = 1.0D;

    /** 当日结算门槛：熬夜点 ≥3 才结算。 //TODO 后续迁移外部配置 */
    public static final double STAY_UP_TIER1_AT = 3.0D;

    /** 当日（天亮/睡醒）一次性结算：肝气 +2、心气 +1、肾气 −1、肾血 −1。 //TODO 后续迁移外部配置 */
    public static final float STAY_UP_DAY_LIVER_QI = 2.0F;
    public static final float STAY_UP_DAY_HEART_QI = 1.0F;
    public static final float STAY_UP_DAY_KIDNEY_QI = -1.0F;
    public static final float STAY_UP_DAY_KIDNEY_BLOOD = -1.0F;

    /** 连续多少日熬夜点 ≥3 后进入「肾精亏虚趋势」。 //TODO 后续迁移外部配置 */
    public static final int STAY_UP_TREND_DAYS = 3;

    /** 趋势期的持续压低（每块，走折算）：肾气 −1、肾血 −1。 //TODO 后续迁移外部配置 */
    public static final float STAY_UP_TREND_KIDNEY_QI = -1.0F;
    public static final float STAY_UP_TREND_KIDNEY_BLOOD = -1.0F;

    /** 夜间睡眠达到这么多 tick 视为「睡到天亮」，当日不结算并清零。 //TODO 后续迁移外部配置 */
    public static final float STAY_UP_SLEEP_ADEQUATE_TICKS = 200.0F;

    /** 顶替源判据：神耗 ≥ 此值视为处于「熬夜/透支」态。 //TODO 后续迁移外部配置 */
    public static final float STAY_UP_SPIRIT_ACTIVE = 30.0F;

    // ---- 三、过劳 / 剧烈劳作 ----

    /** 连续劳作每块累积的劳作点。 //TODO 后续迁移外部配置 */
    public static final double OVERWORK_POINTS_PER_BLOCK = 1.0D;

    /** 劳作点档位线：≥5 → 肾气 −2（每块）。 //TODO 后续迁移外部配置 */
    public static final double OVERWORK_TIER1_AT = 5.0D;
    public static final float OVERWORK_TIER1_KIDNEY_QI = -2.0F;

    /** 叠加档（运动拓展·酸困 > 50）：肾气额外 −1（每块）。 //TODO 后续迁移外部配置 */
    public static final float SORENESS_THRESHOLD = 50.0F;
    public static final float OVERWORK_SORENESS_KIDNEY_QI = -1.0F;

    /** 顶替源判据：体耗 ≥ 此值视为处于「劳作」态。 //TODO 后续迁移外部配置 */
    public static final float OVERWORK_BODY_ACTIVE = 30.0F;

    /** 破坏方块后仍视为「连续劳作」的时长（tick）。 //TODO 后续迁移外部配置 */
    public static final int OVERWORK_LABOR_MEMORY_TICKS = 40;

    // ---- 四、暴饮暴食 / 生冷 ----

    /** 一次「5 分钟内进食超胃容量」累积的暴食点；一次生冷/未烹饪累积的生冷点。 //TODO 后续迁移外部配置 */
    public static final double EATING_BINGE_POINTS = 1.0D;
    public static final double EATING_COLD_POINTS = 1.0D;

    /** 暴食判定窗口（tick，5 分钟）与窗口内算「超胃容量」的进食次数。 //TODO 后续迁移外部配置 */
    public static final int EATING_BINGE_WINDOW_TICKS = 6000;
    public static final int EATING_BINGE_COUNT = 4;

    /** 档位线：暴食点 ≥2；生冷点 ≥3（两个点各自分档，效果相加）。 //TODO 后续迁移外部配置 */
    public static final double EATING_BINGE_TIER_AT = 2.0D;
    public static final double EATING_COLD_TIER_AT = 3.0D;

    /** 暴食档：脾气 −2、脾血 −1（每块）。 //TODO 后续迁移外部配置 */
    public static final float EATING_BINGE_SPLEEN_QI = -2.0F;
    public static final float EATING_BINGE_SPLEEN_BLOOD = -1.0F;

    /** 生冷档：脾气 −2、脾血 −2（每块）。 //TODO 后续迁移外部配置 */
    public static final float EATING_COLD_SPLEEN_QI = -2.0F;
    public static final float EATING_COLD_SPLEEN_BLOOD = -2.0F;

    // ---- 五、压力 / 情绪压抑（按神耗值分档，不累积点） ----

    /** 压力分档：神耗 60–80 → 肝气 +1；> 80 → 肝气 +2（每块）。 //TODO 后续迁移外部配置 */
    public static final float PRESSURE_SPIRIT_TIER1 = 60.0F;
    public static final float PRESSURE_SPIRIT_TIER2 = 80.0F;
    public static final float PRESSURE_TIER1_LIVER_QI = 1.0F;
    public static final float PRESSURE_TIER2_LIVER_QI = 2.0F;

    // ---------------- 时辰波动（v0.6.0：五脏时辰节律） ----------------

    /** 一天的 tick 数（MC 原版：24000 tick = 20 分钟）。 //TODO 后续迁移外部配置 */
    public static final int TICKS_PER_DAY = 24000;

    /** 一个时辰的 tick 数（2 游戏小时 = 2000 tick），即文档里的「以 2 小时为一段」。 //TODO 后续迁移外部配置 */
    public static final int SHICHEN_TICKS = 2000;

    /** 一天十二个时辰。 //TODO 后续迁移外部配置 */
    public static final int SHICHEN_COUNT = 12;

    /**
     * 时辰相位的零点偏移（tick）。
     *
     * <p>MC 的 0 刻是清晨 06:00（卯时），而时辰以 23:00 起子时，
     * 故整条时间轴前移 7 个游戏小时 = 7000 tick。 //TODO 后续迁移外部配置
     */
    public static final int SHICHEN_PHASE_OFFSET = 7000;

    /** 时辰波动的振幅：当令时辰 +本值、对冲时辰 −本值（以平人基线为轴）。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_AMPLITUDE = 4.0F;

    /** 波动偏移的结算间隔（tick）。1 = 每 tick 结算（文档：每 tick 计算位移）。 //TODO 后续迁移外部配置 */
    public static final int RHYTHM_INTERVAL = 1;

    /** 变速到达的<b>基准</b>速度系数（每 tick 吃掉剩余距离的这个比例）。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED = 0.03F;

    /** 速度系数的每 tick 随机微调幅度（±本比例，0.5 = ±50%）。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED_JITTER = 0.5F;

    /** 速度系数下限：再随机也不会低于它，保证「始终收敛」而不是原地打转。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED_MIN = 0.005F;

    /**
     * 速度系数上限。
     *
     * <p><b>必须严格小于 1</b>：系数 ≥ 1 时一步就会跨过目标，下一 tick 再跨回来 → 振荡。
     * 自检里直接断言这条不变量。 //TODO 后续迁移外部配置
     */
    public static final float RHYTHM_FACTOR_CAP = 0.5F;

    /** 到达死区：剩余距离小于本值就直接归位，避免无限逼近。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_MARGIN = 0.01F;

    /** 微观抖动的幅度（±本值），只影响显示、不影响判定。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_JITTER_AMPLITUDE = 0.3F;

    /** 微观抖动的重掷间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int RHYTHM_JITTER_INTERVAL = 5;

    // ---------------- 调试 ----------------

    /**
     * 调试控制台命令的开关系统属性名。
     *
     * <p>与情志模块<b>同名</b>（项目级调试开关），但此处只是一个字符串字面量，
     * 不存在对情志模块的代码引用。正式版本默认关闭；
     * 需要手动加 {@code -Dbingzhengmod.debug.console=true}（开发环境自动可用）。
     * //TODO 后续迁移外部配置
     */
    public static final String DEBUG_CONSOLE_PROPERTY = "bingzhengmod.debug.console";

    /** 调试数据打印的节流间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int DEBUG_PRINT_INTERVAL = 20;
}
