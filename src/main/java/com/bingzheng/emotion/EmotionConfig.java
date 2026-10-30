package com.bingzheng.emotion;

/**
 * 情志拓展的全部数值参数。
 *
 * <p><b>本类里每一个数字都标注了 {@code //TODO 后续迁移外部配置}。</b>
 * 现阶段先集中在此，便于整体搬迁到配置文件。
 */
public final class EmotionConfig {

    private EmotionConfig() {
    }

    // ---------------- 七情数值 ----------------

    /** 七情浮点的取值区间。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_MIN = 0.0F;
    public static final float EMOTION_MAX = 100.0F;

    /** 新玩家的七情初始值 / 基线。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_BASELINE_DEFAULT = 10.0F;

    /** 七情向基线回落的插值系数（每次衰减轮次）。 //TODO 后续迁移外部配置 */
    public static final float EMOTION_DECAY_RATE = 0.02F;

    /** 七情衰减轮次的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int EMOTION_DECAY_INTERVAL = 40;

    /**
     * 冲破该阈值即生成对应的气机扰动（配套迟滞：解除 = 本值 − {@link #DISTURBANCE_RELEASE_BAND}）。
     *
     * <p><b>v0.7.1 用户裁定：由 60 上调到 70</b>（解除线随之由 55 变为 65）。
     * 连带后果见 {@link #DELIRIUM_END_EMOTION_DECAY} —— 抬到 70 后，
     * 神扰退出时的 0.65 衰减（100 → 65）不再停在阈值之上，
     * 因此「极端情况下二次爆发」这条旧性质<b>失效</b>，需要外部事件重新把情绪推过 70。
     * //TODO 后续迁移外部配置
     */
    public static final float DISTURBANCE_THRESHOLD = 70.0F;

    /** 单个气机扰动的持续时间（tick）。 //TODO 后续迁移外部配置 */
    public static final int DISTURBANCE_DURATION = 30 * 20;

    /** 允许同时存在的气机扰动数量上限。 */
    public static final int DISTURBANCE_MAX = 2;

    /** 合并判定（定时扫描）的间隔（tick）。 */
    public static final int MERGE_SCAN_INTERVAL = 20;

    // ---------------- 神耗 / 体耗 ----------------

    /** 神耗、体耗的取值区间。 //TODO 后续迁移外部配置 */
    public static final float CONSUMPTION_MIN = 0.0F;
    public static final float CONSUMPTION_MAX = 100.0F;

    /** 新玩家的初始神耗 / 体耗。 //TODO 后续迁移外部配置 */
    public static final float CONSUMPTION_START = 0.0F;

    /** 神耗自然衰减速率（每次衰减轮次扣减量）。 //TODO 后续迁移外部配置 */
    public static final float SPIRIT_DECAY = 0.25F;

    /** 体耗自然衰减速率（每次衰减轮次扣减量）。 //TODO 后续迁移外部配置 */
    public static final float BODY_DECAY = 0.35F;

    /** 消耗量衰减轮次的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int CONSUMPTION_DECAY_INTERVAL = 40;

    // ---------------- 神扰 ----------------

    /** 神扰的基础持续时间（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_BASE_TICKS = 60 * 20;

    /** 神扰持续时间的硬封顶（tick）——无论如何追加都不会超过它。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_HARD_CAP_TICKS = 150 * 20;

    /** 受伤害追加的神扰时间（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_EXTEND_ON_DAMAGE = 3 * 20;

    /** 火邪发作追加的神扰时间（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_EXTEND_ON_FIRE = 10 * 20;

    /** 神扰结束后的生成冷却（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_COOLDOWN_TICKS = 70;

    /**
     * 神扰<b>完全结束</b>时，对「仍然站在扰动阈值之上」的情绪值施加的衰减系数。
     *
     * <p>只有 {@code >= DISTURBANCE_THRESHOLD} 的情绪会被乘上它；低于阈值的原样保留。
     * <b>绝不归零</b> —— 残余情绪要留给心绪不宁阶段的心悸抖动。
     *
     * <p><b>取值含义（v0.7.1 起阈值 70、上限 100）：</b>0.65 → 顶格的 100 衰减到 65，
     * <b>已经落在阈值 70 之下</b>。也就是说 —— <b>「极端情况下二次爆发」这条性质在阈值抬到 70 后
     * 不再成立</b>：一次神扰过后没有任何情绪还站在阈值上，必须由外部事件重新推过 70 才可能再爆发。
     *
     * <p>这是用户 v0.7.1 的裁定（**阈值 70 优先**，本系数维持在 0.6–0.7 区间不变）；
     * 当年 v0.3.1 刻意保留二次爆发的意图由此被取代，<b>不要再按旧注释理解本字段</b>。
     * 若要恢复二次爆发，只有两条路：把本系数抬到 &gt;0.70（超出用户给定区间），或把阈值降回 60。
     * //TODO 后续迁移外部配置
     */
    public static final float DELIRIUM_END_EMOTION_DECAY = 0.65F;

    /** 神扰期间额外施加的饥饿消耗（每 tick，exhaustion 单位）。 //TODO 后续迁移外部配置 */
    public static final float DELIRIUM_EXHAUSTION_PER_TICK = 0.012F;

    /** 随机横向前进偏移的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_SHIFT_INTERVAL = 25;

    /** 随机横向前进偏移的力度。 //TODO 后续迁移外部配置 */
    public static final double DELIRIUM_SHIFT_STRENGTH = 0.055D;

    /** 神扰期间后台扰动七情浮点的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_EMOTION_JITTER_INTERVAL = 60;

    /** 神扰期间后台扰动七情浮点的基础幅度。 //TODO 后续迁移外部配置 */
    public static final float DELIRIUM_EMOTION_JITTER = 3.5F;

    /** 神扰持续期间神耗的上涨速率（每上涨轮次）。 //TODO 后续迁移外部配置 */
    public static final float DELIRIUM_SPIRIT_RISE = 0.6F;

    /** 神扰神耗上涨的轮次间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int DELIRIUM_SPIRIT_RISE_INTERVAL = 40;

    // ---- 神扰属性增益 ----

    /** 移速加成基准（MULTIPLY_BASE 运算）。 //TODO 后续迁移外部配置 */
    public static final double DELIRIUM_SPEED_BONUS = 0.20D;

    /** 攻速加成基准（MULTIPLY_BASE 运算）。 //TODO 后续迁移外部配置 */
    public static final double DELIRIUM_ATTACK_SPEED_BONUS = 0.30D;

    /** 攻速总值的保守硬上限——最终仍会经过原版 {@code EntityAttribute#clamp} 钳位。 //TODO 后续迁移外部配置 */
    public static final double ATTACK_SPEED_CEILING = 8.0D;

    /** 临时扣除最大生命值的比例上限（MULTIPLY_TOTAL，取负值）。最多扣一半。 */
    public static final double DELIRIUM_MAX_HEALTH_PENALTY = -0.5D;

    /** 火邪对属性增益的放大系数。 //TODO 后续迁移外部配置 */
    public static final double PATHOGEN_FIRE_MULTIPLIER = 1.5D;

    /** 水寒对属性增益的压制系数。 //TODO 后续迁移外部配置 */
    public static final double PATHOGEN_COLD_MULTIPLIER = 0.5D;

    // ---------------- 神安 ----------------

    /** 神安额外加快神耗衰减的倍率。 //TODO 后续迁移外部配置 */
    public static final float SHEN_AN_SPIRIT_DECAY_MULTIPLIER = 1.6F;

    /**
     * 是否由情志模块提供「营卫自动恢复」。
     *
     * <p>内核目前只有汤药驱动的营卫恢复，没有被动恢复；而文档要求
     * 「神扰期间营卫自动恢复暂停、神安加快营卫自动恢复」。
     * 为了让这两条可落地，本模块通过内核的<b>公开 API</b> 读写营卫附件补充了这一恢复源
     * （<b>未修改内核任何代码</b>）。若你认为这属于内核职责，把本开关置 false 即可完全关闭。
     */
    public static final boolean EMOTION_PROVIDES_YINGWEI_AUTO_RECOVERY = true;

    /** 神安期间营卫自动恢复量（每次轮次）。 //TODO 后续迁移外部配置 */
    public static final int SHEN_AN_YINGWEI_RECOVERY = 1;

    /** 神安营卫恢复快于常态的倍率（常态由本模块提供，见 EmotionService）。 //TODO 后续迁移外部配置 */
    public static final int YINGWEI_AUTO_RECOVERY = 1;

    /** 营卫恢复轮次的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int YINGWEI_RECOVERY_INTERVAL = 60;

    // ---------------- 静坐 / 夜间冥想 ----------------

    /** 静坐判定：水平速度低于该值视为静止。 //TODO 后续迁移外部配置 */
    public static final double MEDITATION_STILL_SPEED = 0.05D;

    /** 静坐每轮额外消耗的神扰剩余时间（tick）。 //TODO 后续迁移外部配置 */
    public static final int MEDITATION_DELIRIUM_DRAIN = 20;

    /** 静坐消耗神扰时间的轮次间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int MEDITATION_DRAIN_INTERVAL = 10;

    /** 静坐降低神耗的速率（每轮次）。 //TODO 后续迁移外部配置 */
    public static final float MEDITATION_SPIRIT_RELIEF = 1.5F;

    /** 静坐降低神耗的轮次间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int MEDITATION_RELIEF_INTERVAL = 20;

    /** 一次完整夜间冥想所需的 tick 数（约 3 分钟游戏内时间）。 //TODO 后续迁移外部配置 */
    public static final int MEDITATION_NIGHT_SESSION_TICKS = 3 * 60 * 20;

    /** 抵消一晚失眠所需的完整夜间冥想轮次数。 */
    public static final int MEDITATION_SESSIONS_PER_NIGHT_DEBT = 1;

    /** 连续完成多少轮夜间冥想可抵消三晚失眠（文档：连续完成三轮完整夜间冥想）。 */
    public static final int MEDITATION_SESSIONS_TO_CLEAR = 3;

    // ---------------- 心绪不宁（神扰结束后的缓冲阶段）----------------

    /**
     * 神扰完全结束后，「心绪不宁」阶段的时长（tick）。
     * 此期间禁止生成神安，并施加微弱心悸扰动。 //TODO 后续迁移外部配置
     */
    public static final int RESTLESS_TICKS = 300;

    /** 心绪不宁期间，心悸抖动的幅度。 //TODO 后续迁移外部配置 */
    public static final float RESTLESS_JITTER = 0.8F;

    /** 心悸抖动的间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int RESTLESS_JITTER_INTERVAL = 20;

    /**
     * 心悸抖动的安全边距：抖动**永远不会把某一条情绪推过扰动阈值**。
     * 阈值减去该边距，即为抖动后的上限。 //TODO 后续迁移外部配置
     */
    public static final float RESTLESS_JITTER_MARGIN = 1.0F;

    // ---------------- 静坐判定 ----------------

    /** 进入静坐所需的连续静止 tick 数（防止瞬时抖动误判）。 //TODO 后续迁移外部配置 */
    public static final int MEDITATION_ENTER_TICKS = 5;

    /** 静坐期间允许偏离锚点的水平距离（格）；超过即视为移动、打断静坐。 //TODO 后续迁移外部配置 */
    public static final double MEDITATION_LEAVE_RADIUS = 0.75D;

    // ---------------- 时辰节律波动（v0.7.0 任务单二） ----------------

    /** 一天的 tick 数（MC 原版 24000）。 //TODO 后续迁移外部配置 */
    public static final int TICKS_PER_DAY = 24000;

    /** 一个时辰的 tick 数（2 游戏小时 = 2000 tick）。 //TODO 后续迁移外部配置 */
    public static final int SHICHEN_TICKS = 2000;

    /** 一天十二个时辰。 //TODO 后续迁移外部配置 */
    public static final int SHICHEN_COUNT = 12;

    /**
     * 时辰相位的零点偏移（tick）：MC 0 刻是 06:00（卯时），而时辰以 23:00 起子时，
     * 故整条时间轴前移 7 个游戏小时 = 7000 tick。 //TODO 后续迁移外部配置
     */
    public static final int SHICHEN_PHASE_OFFSET = 7000;

    /**
     * 时辰波动的振幅：当令时辰 +本值、对冲时辰 −本值。
     *
     * <p><b>刻意比五脏的 ±4 小</b>：七情贴着气机扰动阈值，波动必须保守，
     * 免得显示上的起伏让人误以为「快要爆了」。 //TODO 后续迁移外部配置
     */
    public static final float RHYTHM_AMPLITUDE = 2.0F;

    /** 波动偏移的结算间隔（tick）。1 = 每 tick 结算。 //TODO 后续迁移外部配置 */
    public static final int RHYTHM_INTERVAL = 1;

    /** 变速到达的基准速度系数（每 tick 吃掉剩余距离的比例）。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED = 0.03F;

    /** 速度系数的每 tick 随机微调幅度（±比例）。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED_JITTER = 0.5F;

    /** 速度系数下限：保证「始终收敛」。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_SPEED_MIN = 0.005F;

    /** 速度系数上限：<b>必须严格小于 1</b>，否则一步跨过目标会来回振荡。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_FACTOR_CAP = 0.5F;

    /** 到达死区：剩余距离小于本值即归位。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_MARGIN = 0.01F;

    /** 微观抖动幅度（±本值），只影响显示。 //TODO 后续迁移外部配置 */
    public static final float RHYTHM_JITTER_AMPLITUDE = 0.2F;

    /** 微观抖动的重掷间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int RHYTHM_JITTER_INTERVAL = 5;

    // ---------------- 气机扰动的迟滞（v0.7.0） ----------------

    /**
     * 迟滞带宽：<b>解除 = 触发阈值 − 本值</b>。
     *
     * <p>触发阈值是 {@link #DISTURBANCE_THRESHOLD}（v0.7.1 起为 70，解除线即 65）；
     * 迟滞本身解决的是「同一条情绪贴着阈值反复生成扰动」—— 一条情绪生成过扰动后会被锁定，
     * 直到它的稳定值跌到解除线以下才重新武装。
     * //TODO 后续迁移外部配置
     */
    public static final float DISTURBANCE_RELEASE_BAND = 5.0F;

    // ---------------- 五志行为驱动（v0.8.0 任务单：七情量化规范 v1） ----------------

    /**
     * 参与行为驱动的「五志」：怒、喜、思、悲、恐。
     *
     * <p><b>忧、惊不入框架</b>（延续「忧惊不补」），它们走联动扩展：忧 ← 瘟疫拓展、惊 ← 雷暴。
     * //TODO 后续迁移外部配置
     */
    public static final int BEHAVIOR_EMOTION_COUNT = 5;

    /** 强度点上限（防止长期堆积后一结算跳太多）。 //TODO 后续迁移外部配置 */
    public static final float BEHAVIOR_POINT_MAX = 30.0F;

    /**
     * 恢复：<b>无行为触发时，稳定值每时辰向基线回落 1 点</b>（行为推高不永久堆积）。
     *
     * <p>⚠️ <b>本项取代了这五情原有的「每 40 tick × 2% 插值衰减」</b>：
     * 五志的稳定值要能被行为推动，就必须靠这个慢恢复；
     * 若仍沿用 2%/40t（70 → 69 只需 40 tick），行为驱动的 +1 会在 1 秒内被抹平，整层形同不存在。
     * 想恢复旧手感：把 {@link #LEGACY_DECAY_FOR_BEHAVIOR_EMOTIONS} 置 true。
     * 忧、惊**不受影响**（它们不走本框架）。
     * //TODO 后续迁移外部配置
     */
    public static final int BEHAVIOR_RECOVERY_INTERVAL = 2000;

    /** 每轮恢复（向基线回落）的点数。 //TODO 后续迁移外部配置 */
    public static final float BEHAVIOR_RECOVERY_STEP = 1.0F;

    /** 是否为五志恢复旧版快衰减（默认 false = 按任务单「每时辰 −1 点」）。 //TODO 后续迁移外部配置 */
    public static final boolean LEGACY_DECAY_FOR_BEHAVIOR_EMOTIONS = false;

    // ---- 怒：受击 ----

    /** 敌对生物攻击 / 玩家攻击 / 爆炸波及 每次累积的怒点。 //TODO 后续迁移外部配置 */
    public static final float ANGER_POINTS_MOB_HIT = 1.0F;
    public static final float ANGER_POINTS_PLAYER_HIT = 3.0F;
    public static final float ANGER_POINTS_EXPLOSION = 2.0F;

    /** 怒点阈值：≥5 结算，怒稳定值 +1，点清零。 //TODO 后续迁移外部配置 */
    public static final float ANGER_THRESHOLD = 5.0F;

    // ---- 喜：成就 / Boss ----

    /** 成就达成 +1 点；击杀 Boss +3 点。 //TODO 后续迁移外部配置 */
    public static final float JOY_POINTS_ADVANCEMENT = 1.0F;
    public static final float JOY_POINTS_BOSS_KILL = 3.0F;

    /** 喜点阈值：≥3 结算。 //TODO 后续迁移外部配置 */
    public static final float JOY_THRESHOLD = 3.0F;

    /** 成就轮询间隔（tick）与只统计的命名空间（原版成就）。 //TODO 后续迁移外部配置 */
    public static final int JOY_ADVANCEMENT_SCAN_TICKS = 100;
    public static final String JOY_ADVANCEMENT_NAMESPACE = "minecraft";

    // ---- 思：专注操作 ----

    /** 专注方块判定半径（格）与「仍在专注」的记忆时长（tick）。 //TODO 后续迁移外部配置 */
    public static final int FOCUS_MEMORY_TICKS = 12000;

    /** 思：每持续专注一个「结算块」（10 分钟）累积的点数。 //TODO 后续迁移外部配置 */
    public static final float PENSIVENESS_POINTS_PER_BLOCK = 1.0F;
    public static final int PENSIVENESS_BLOCK_TICKS = 12000;

    /** 思点阈值：≥3 结算。 //TODO 后续迁移外部配置 */
    public static final float PENSIVENESS_THRESHOLD = 3.0F;

    // ---- 悲：意外 / 被动损失 ----

    /** 物品被岩浆/火/虚空/爆炸销毁 +1；超时消失 +1；驯服生物死亡 +2；玩家死亡 +3。 //TODO 后续迁移外部配置 */
    public static final float GRIEF_POINTS_ITEM_DESTROYED = 1.0F;
    public static final float GRIEF_POINTS_ITEM_DESPAWNED = 1.0F;
    public static final float GRIEF_POINTS_PET_DEATH = 2.0F;
    public static final float GRIEF_POINTS_PLAYER_DEATH = 3.0F;

    /** 悲点阈值：≥3 结算。 //TODO 后续迁移外部配置 */
    public static final float GRIEF_THRESHOLD = 3.0F;

    /** 物品结局算给谁：取物品位置附近最大半径内最近的玩家（超时/销毁都按此归属）。 //TODO 后续迁移外部配置 */
    public static final double GRIEF_ATTRIBUTION_RADIUS = 32.0D;

    /** 原版物品超时消失的年龄（tick）：6000 = 5 分钟。 //TODO 后续迁移外部配置 */
    public static final int GRIEF_ITEM_DESPAWN_AGE = 6000;

    /** 死亡掉落窗口（tick）：死亡瞬间产生的掉落物标记为「死亡掉落」，其消失不再单独计悲点。 //TODO 后续迁移外部配置 */
    public static final int GRIEF_DEATH_DROP_WINDOW = 40;

    // ---- 恐：被锁定 / 黑暗 / 坠落 ----

    /** 被敌对生物锁定为目标：首次锁定 +2 点；同一目标持续锁定不重复计。 //TODO 后续迁移外部配置 */
    public static final float FEAR_POINTS_LOCKED = 2.0F;

    /** 脱战判定：连续这么多 tick 没有任何敌对生物锁定 → 重新武装。 //TODO 后续迁移外部配置 */
    public static final int FEAR_REARM_TICKS = 60;

    /** 锁定扫描间隔与扫描半径（格）。 //TODO 后续迁移外部配置 */
    public static final int FEAR_SCAN_TICKS = 10;
    public static final double FEAR_SCAN_RADIUS = 48.0D;

    /** 无光源黑暗停留：每 10 分钟 +1 点。 //TODO 后续迁移外部配置 */
    public static final float FEAR_POINTS_DARKNESS = 1.0F;
    public static final int FEAR_DARKNESS_BLOCK_TICKS = 12000;

    /** 坠落 ≥15 格 +2 点。 //TODO 后续迁移外部配置 */
    public static final float FEAR_POINTS_FALL = 2.0F;
    public static final float FEAR_FALL_DISTANCE = 15.0F;

    /** 恐点阈值：≥3 结算。 //TODO 后续迁移外部配置 */
    public static final float FEAR_THRESHOLD = 3.0F;

    // ---- 忧 / 惊：联动扩展（不入点框架） ----

    /** 忧：瘟疫感染期间每时辰 +1（无瘟疫拓展则无来源）。 //TODO 后续迁移外部配置 */
    public static final float WORRY_PER_BLOCK_PLAGUE = 1.0F;

    /** 惊：雷击落在玩家 16 格内 +2 点（一次性）。 //TODO 后续迁移外部配置 */
    public static final float FRIGHT_POINTS_LIGHTNING = 2.0F;
    public static final double FRIGHT_LIGHTNING_RADIUS = 16.0D;

    // ---------------- HUD 同步（v0.9.0） ----------------

    /** 神耗 / 体耗 HUD 的同步间隔（tick）：数值变化时立即推，未变化时每秒兜底一次。 //TODO 后续迁移外部配置 */
    public static final int HUD_SYNC_INTERVAL = 20;

    // ---------------- 调试 ----------------

    /**
     * 调试控制台命令的开关系统属性名。
     *
     * <p>正式 release 版本<b>默认关闭</b>；需要手动加 JVM 参数
     * {@code -Dbingzhengmod.debug.console=true} 才开启，且调用者必须是 OP。
     */
    public static final String DEBUG_CONSOLE_PROPERTY = "bingzhengmod.debug.console";

    /** 调试数据打印的节流间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int DEBUG_PRINT_INTERVAL = 20;

    // ---------------- 画面特效 ----------------

    /** 边缘渐变的最大不透明度（0-255）。 //TODO 后续迁移外部配置 */
    public static final int OVERLAY_EDGE_MAX_ALPHA = 130;

    /** 边缘渐变的厚度占屏幕高度/宽度的比例。 //TODO 后续迁移外部配置 */
    public static final float OVERLAY_EDGE_THICKNESS = 0.30F;

    /** 噪点颗粒数量（每帧）。 //TODO 后续迁移外部配置 */
    public static final int OVERLAY_NOISE_COUNT = 220;

    /** 噪点最大不透明度（0-255）。 //TODO 后续迁移外部配置 */
    public static final int OVERLAY_NOISE_MAX_ALPHA = 26;

    /** 脉冲闪烁的基础间隔（tick）。 //TODO 后续迁移外部配置 */
    public static final int OVERLAY_PULSE_INTERVAL = 70;

    /** 脉冲闪烁的持续时长（tick）。 //TODO 后续迁移外部配置 */
    public static final int OVERLAY_PULSE_LENGTH = 8;

    /** 水寒病证对脉冲频率的压制系数（间隔乘以该值，越大越慢）。 //TODO 后续迁移外部配置 */
    public static final float OVERLAY_PULSE_COLD_SLOWDOWN = 1.8F;

    /** 火邪对脉冲频率的加速系数。 //TODO 后续迁移外部配置 */
    public static final float OVERLAY_PULSE_FIRE_HASTEN = 0.55F;
}
