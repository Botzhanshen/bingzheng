package com.bingzheng.protect;

import com.bingzheng.BingzhengMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 【牛奶保护名单】—— 本模组状态效果对「饮用牛奶」这一条路径的过滤层。
 *
 * <p>原版 {@code MilkBucketItem#finishUsing} 会直接调用 {@code LivingEntity#clearStatusEffects()}，
 * 把玩家身上<b>全部</b>状态效果一次清空。本模组的病证 / 情志效果如果任由牛奶清掉，
 * 整套中草药治疗体系就失去意义。
 *
 * <p><b>规则</b>：
 * <ul>
 *   <li>{@code bingzhengmod:} 命名空间下的状态效果<b>默认全部受保护</b>（牛奶删不掉），
 *       于是「未来全部中医病证类自定义效果」自动纳入，无需逐个登记；</li>
 *   <li>原版与其它模组的效果<b>完全不受影响</b>，牛奶对它们的行为与不加本过滤层时逐字节一致；</li>
 *   <li>若某个自定义效果将来确实应该被牛奶清除，把它的路径填进
 *       {@link #MILK_CLEARABLE_PATHS} 即可豁免。</li>
 * </ul>
 *
 * <p><b>失效策略（v0.4.1 决策）</b>：装载这一层的 Mixin 配置使用 {@code defaultRequire = 0}，
 * 即发生字节码冲突时<b>保护静默失效 + 输出 warn 日志，游戏继续运行</b>，
 * 宁可少一层表现层保护，也不让整合包因为本模组起不来。
 * 这条取舍是安全的 —— 底层 Attachment 里的长期数值（五脏气血 / 七情 / 神耗体耗 / 营卫）
 * <b>永远不受牛奶影响</b>，本层保护的只是「表现层状态效果会不会被牛奶清掉」。
 *
 * <p><b>保护范围仅限「饮用牛奶」这一条路径。</b>下列行为完全不受影响：
 * <ul>
 *   <li>{@code /effect clear} 管理员指令 —— 仍然可以清空本模组效果（走的是另一条代码路径）；</li>
 *   <li>死亡复活、不死图腾 —— 仍然清空全部效果（存活通关的天然清除手段保留）；</li>
 *   <li>本模组自己的汤药 / 方剂 / 治疗逻辑 —— 仍可任意手动移除这些状态。</li>
 * </ul>
 *
 * <p><b>更底层的一层防线</b>：神耗、体耗、七情浮点等长期数值<b>根本不是 StatusEffect</b>，
 * 它们存放在 Fabric Persistent Attachment（{@code bingzhengmod:emotion}）里，
 * 与状态效果系统毫无交集 —— 就算某个状态效果被意外清掉，底层累积数据也不会丢。
 *
 * <p>//TODO 后续迁移配置文件：受保护名单开放为配置项。
 * //TODO【长期】第三方各类牛奶 / 乳制品（瓶装奶、热奶等）目前<b>不</b>在本过滤层覆盖范围内，
 * 它们在自己的 drink / finishUsing 里调用 clearStatusEffects，本 Mixin 只认 {@code minecraft:milk_bucket}。
 * 待整合包阶段再逐一定位并扩展过滤名单。
 */
public final class MilkSafeEffects {

    /**
     * 显式豁免名单：即使属于 {@code bingzhengmod:} 命名空间，牛奶也照常清除。
     *
     * <p>这里填的是效果<b>路径</b>（如 {@code some_path}），不是完整 ID。
     * 目前为空 —— 全部自定义效果都受保护。
     */
    private static final Set<String> MILK_CLEARABLE_PATHS = new HashSet<>();

    /**
     * 必须受保护的已知效果路径。
     *
     * <p>这份清单不做运行时判定（判定规则是「命名空间默认全保护」），
     * 它只服务于<b>启动自检断言</b>：任何一项漏保护都会让自检当场失败。
     */
    public static final String[] REQUIRED_PROTECTED_PATHS = {
            // 内核（核心包《病证》）
            "xinmai_bizu",          // 心脉痹阻
            "harmonize_exterior",   // 表气调和
            // 情志拓展：七种气机扰动
            "gan_qi_kang", "xin_shen_yang", "fei_qi_yu", "pi_yi_chan",
            "fei_qi_xiao", "shen_qi_she", "shen_zha_yao",
            // 情志拓展：神扰 / 神安 / 静坐同步标记
            "shen_rao", "shen_an", "meditating",
    };

    private static boolean loggedList;
    private static boolean loggedFirstFilter;

    private MilkSafeEffects() {
    }

    /** 触发本类静态初始化，并把当前受保护名单打印进日志（便于验收时核对）。 */
    public static void register() {
        if (loggedList) {
            return;
        }
        loggedList = true;
        Set<String> found = new HashSet<>();
        for (Identifier id : Registries.STATUS_EFFECT.getIds()) {
            if (isProtected(id)) {
                found.add(id.toString());
            }
        }
        BingzhengMod.LOGGER.info("【病证·牛奶过滤】已启用：受保护状态效果 {} 项 —— {}", found.size(), found);
    }

    /** 该效果是否受牛奶保护；非 {@code bingzhengmod:} 命名空间一律返回 false。 */
    public static boolean isProtected(StatusEffect effect) {
        return effect != null && isProtected(Registries.STATUS_EFFECT.getId(effect));
    }

    /** 该 ID 是否受牛奶保护。<b>只有本模组自己注册出来的效果会被保护。</b> */
    public static boolean isProtected(Identifier id) {
        if (id == null || !BingzhengMod.MOD_ID.equals(id.getNamespace())) {
            return false;   // 原版与其它模组的效果：牛奶行为完全不变
        }
        return !MILK_CLEARABLE_PATHS.contains(id.getPath());
    }

    /**
     * 牛奶驱动的「过滤式清空」，取代原版 {@code LivingEntity#clearStatusEffects()}。
     *
     * <p>遍历玩家身上全部效果：受保护的<b>原样留下</b>，其余逐个走原版
     * {@code removeStatusEffect} 移除（与 {@code /effect clear} 用的是同一条路径，
     * 因此客户端 HUD 图标会正常消失）。
     *
     * @return 是否有任何效果被真正清除（语义与原版 {@code clearStatusEffects} 一致）
     */
    public static boolean clearForMilk(LivingEntity entity) {
        if (entity.getWorld().isClient()) {
            return false;   // 只允许服务端裁决，客户端不得自行改效果表
        }
        List<StatusEffect> toRemove = new ArrayList<>();
        int protectedCount = 0;
        for (StatusEffectInstance instance : entity.getStatusEffects()) {
            StatusEffect effect = instance.getEffectType();
            if (isProtected(effect)) {
                protectedCount++;
            } else {
                toRemove.add(effect);
            }
        }
        boolean cleared = false;
        for (StatusEffect effect : toRemove) {
            if (entity.removeStatusEffect(effect)) {
                cleared = true;
            }
        }
        if (protectedCount > 0 && !loggedFirstFilter) {
            loggedFirstFilter = true;
            BingzhengMod.LOGGER.info("【病证·牛奶过滤】已生效：保留 {} 个本模组效果，清除 {} 个其它效果。",
                    protectedCount, toRemove.size());
        }
        return cleared;
    }
}
