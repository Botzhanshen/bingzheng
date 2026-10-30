package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.bingzheng.protect.MilkSafeEffects;
import com.mojang.serialization.DataResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 情志拓展的开发期自检（只在 {@code -Dfabric.development=true} 时运行）。
 *
 * <p>断言的是 {@link EmotionRules} 里的<b>决策规则本身</b>，而不是几个配置常量；
 * 业务代码调用的是同一批函数，所以「被测的就是在跑的」。
 *
 * <p>无需世界、无需玩家，模组启动时即可跑完。
 */
public final class EmotionSelfCheck {

    private static int passed;
    private static int failed;
    private static final List<String> FAILURES = new ArrayList<>();

    private EmotionSelfCheck() {
    }

    public static void run() {
        if (!Boolean.getBoolean("fabric.development")) {
            return;
        }
        passed = 0;
        failed = 0;
        FAILURES.clear();
        BingzhengMod.LOGGER.info("【病证·情志自检】开始，共 53 项。");

        checkCodecRoundTrip();
        checkEmotionClamp();
        checkDecayConvergence();
        checkDecayNoOvershoot();
        checkSpiritDecayAcceleration();
        checkThresholdRule();
        checkMergeRule();
        checkShenAnRule();
        checkRestlessBuffer();
        checkRestlessJitterClamp();
        checkDeliriumHardCap();
        checkMeditationDrain();
        checkAttackSpeedClamp();
        checkMaxHealthPenalty();
        checkEffectNamespaces();
        checkConfigSanity();
        checkDeliriumClearsDisturbances();
        checkCooldownBlocksOnlyDelirium();
        checkRestlessIndependentOfCooldown();
        checkMilkProtectionSet();
        checkBackendDataNotAnEffect();
        checkEndOfDeliriumDecay();
        checkDeliriumExitFlow();
        checkShichenPhaseTable();
        checkShichenTargetCurve();
        checkRhythmApproach();
        checkDisturbanceHysteresis();
        checkWaveNotJudged();
        checkGriefExclusions();
        checkFearLatch();
        checkBehaviorRecovery();
        checkBehaviorSettlement();
        checkWorryFrightOutside();
        checkHudCellConversion();
        checkHudRowMapping();
        checkHudToggle();
        checkHudSyncChannel();
        checkHudNoWuzangDependency();
        checkHudModeGuard();
        checkHudLayout();
        checkHudCellGap();
        checkFatigueZeroAndClamp();
        checkFatigueAccrual();
        checkFatigueDecay();
        checkFatiguePersistence();
        checkFatigueTimeBase();
        checkSleepGradient();
        checkSleepDebt();
        checkSunlight();
        checkNightWindowExcludesLateEvening();
        checkUnsleptDebtTiers();
        checkFatigueDebugOutput();

        for (String f : FAILURES) {
            BingzhengMod.LOGGER.error("【病证·情志自检】[FAIL] {}", f);
        }
        BingzhengMod.LOGGER.info("【病证·情志自检】完成：通过 {} 项，失败 {} 项。", passed, failed);
        if (failed > 0) {
            BingzhengMod.LOGGER.error("【病证·情志自检】存在 {} 项失败！", failed);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            BingzhengMod.LOGGER.info("【病证·情志自检】[OK] {}", name);
        } else {
            failed++;
            FAILURES.add(name + " —— " + detail);
        }
    }

    // 1 持久化：Codec 往返一致（决定退出重进数值是否保留）
    private static void checkCodecRoundTrip() {
        EmotionState original = EmotionState.fresh();
        original.setSpiritConsumption(37.5F);
        original.setBodyConsumption(22.25F);
        original.addEmotion(EmotionType.ANGER, 45.0F);
        original.addEmotion(EmotionType.FEAR, 30.0F);
        original.setBaseline(EmotionType.JOY, 18.0F);
        original.setCooldownDelirium(42);
        original.setMeditationNightCounter(2);
        original.setMeditationProgress(123);
        original.setDeliriumActive(true);
        original.setRestlessTicks(210);

        DataResult<NbtElement> encoded = EmotionState.CODEC.encodeStart(NbtOps.INSTANCE, original);
        NbtElement nbt = encoded.result().orElse(null);
        EmotionState decoded = nbt == null ? null
                : EmotionState.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(null);

        boolean ok = decoded != null
                && Math.abs(decoded.getSpiritConsumption() - 37.5F) < 0.001F
                && Math.abs(decoded.getBodyConsumption() - 22.25F) < 0.001F
                && Math.abs(decoded.emotion(EmotionType.ANGER) - original.emotion(EmotionType.ANGER)) < 0.001F
                && Math.abs(decoded.emotion(EmotionType.FEAR) - original.emotion(EmotionType.FEAR)) < 0.001F
                && Math.abs(decoded.baselineOf(EmotionType.JOY) - 18.0F) < 0.001F
                && decoded.getCooldownDelirium() == 42
                && decoded.getMeditationNightCounter() == 2
                && decoded.getMeditationProgress() == 123
                && decoded.isDeliriumActive()
                && decoded.getRestlessTicks() == 210;
        check("持久化 Codec 往返一致（七情/基线/神耗/体耗/冷却/冥想计数/神扰标记/心绪不宁）", ok,
                "解码结果与原文不一致或编解码失败");

        // 旧存档兼容：缺少 restless_ticks 字段时必须能正常加载并回落到默认值 0
        boolean legacyOk = false;
        if (nbt instanceof net.minecraft.nbt.NbtCompound compound) {
            net.minecraft.nbt.NbtCompound legacy = compound.copy();
            legacy.remove("restless_ticks");
            EmotionState fromLegacy = EmotionState.CODEC.parse(NbtOps.INSTANCE, legacy).result().orElse(null);
            legacyOk = fromLegacy != null
                    && fromLegacy.getRestlessTicks() == 0
                    && Math.abs(fromLegacy.getSpiritConsumption() - 37.5F) < 0.001F
                    && fromLegacy.isDeliriumActive();
        }
        check("旧存档兼容：缺少「心绪不宁」字段时回落为 0 且其余数据不丢", legacyOk,
                "旧版本 NBT 无法加载");
    }

    // 2 七情钳位
    private static void checkEmotionClamp() {
        EmotionState s = EmotionState.fresh();
        s.setEmotion(EmotionType.ANGER, 999.0F);
        float hi = s.emotion(EmotionType.ANGER);
        s.setEmotion(EmotionType.ANGER, -50.0F);
        float lo = s.emotion(EmotionType.ANGER);
        s.setSpiritConsumption(500.0F);
        float spirit = s.getSpiritConsumption();
        boolean ok = hi == EmotionConfig.EMOTION_MAX && lo == EmotionConfig.EMOTION_MIN
                && spirit == EmotionConfig.CONSUMPTION_MAX;
        check("七情与神耗的取值钳位（0-100）", ok, "hi=" + hi + " lo=" + lo + " spirit=" + spirit);
    }

    // 3 七情向基线收敛（验收项 1）
    private static void checkDecayConvergence() {
        float base = 10.0F;
        float v = 100.0F;
        for (int i = 0; i < 600; i++) {
            v = EmotionRules.decayStep(v, base);
        }
        boolean ok = Math.abs(v - base) < 0.5F;
        check("七情向基线自动回落并收敛（不是归零）", ok, "600 步后 v=" + v + " base=" + base);
    }

    // 4 衰减不过冲
    private static void checkDecayNoOvershoot() {
        float base = 30.0F;
        float v = 90.0F;
        float prevDist = Math.abs(v - base);
        boolean ok = true;
        for (int i = 0; i < 600 && ok; i++) {
            v = EmotionRules.decayStep(v, base);
            float dist = Math.abs(v - base);
            if (dist > prevDist + 0.0001F) {
                ok = false;
            }
            prevDist = dist;
        }
        check("衰减单调收敛、不过冲基线", ok, "出现远离基线的跳变");
    }

    // 5 神安加快神耗衰减（验收项 7）
    private static void checkSpiritDecayAcceleration() {
        float normal = EmotionRules.spiritDecayRate(false);
        float shenAn = EmotionRules.spiritDecayRate(true);
        check("神安加快神耗自然衰减", shenAn > normal && normal > 0.0F,
                "normal=" + normal + " shenAn=" + shenAn);
    }

    // 6 阈值判定（验收项 2）
    private static void checkThresholdRule() {
        float t = EmotionConfig.DISTURBANCE_THRESHOLD;
        boolean ok = !EmotionRules.exceedsThreshold(t - 0.1F) && EmotionRules.exceedsThreshold(t);
        check("单项情绪冲破阈值才生成气机扰动", ok, "阈值=" + t);
    }

    // 7 合并与冷却（验收项 3）
    private static void checkMergeRule() {
        boolean ok = !EmotionRules.shouldMerge(0, 0)
                && !EmotionRules.shouldMerge(1, 0)
                && EmotionRules.shouldMerge(2, 0)
                && EmotionRules.shouldMerge(3, 0)
                && !EmotionRules.shouldMerge(2, EmotionConfig.DELIRIUM_COOLDOWN_TICKS);
        check("≥2 个任意气机扰动即合并为神扰；冷却期间拒绝合并", ok, "合并/冷却规则不符");
    }

    // 8 神安与神扰互斥（验收项 7）
    private static void checkShenAnRule() {
        boolean ok = EmotionRules.shouldHaveShenAn(false, 0, false)
                && !EmotionRules.shouldHaveShenAn(false, 1, false)
                && !EmotionRules.shouldHaveShenAn(true, 0, false)
                && !EmotionRules.shouldHaveShenAn(true, 2, false)
                // 心绪不宁期间，即使无扰动、无神扰，也不得生成神安
                && !EmotionRules.shouldHaveShenAn(false, 0, true);
        check("神安仅在「无神扰、0 扰动、且不在心绪不宁」时存在（与神扰强制互斥）", ok, "互斥/缓冲规则不符");
    }

    /** 神扰结束后的心绪不宁缓冲：期间封锁神安。 */
    private static void checkRestlessBuffer() {
        boolean ok = EmotionConfig.RESTLESS_TICKS > 0
                && !EmotionRules.shouldHaveShenAn(false, 0, true)
                && EmotionRules.shouldHaveShenAn(false, 0, false);
        check("神扰结束后进入心绪不宁缓冲、缓冲期内封锁神安", ok,
                "restlessTicks=" + EmotionConfig.RESTLESS_TICKS);
    }

    /** 心悸抖动绝不会自己生成气机扰动。 */
    private static void checkRestlessJitterClamp() {
        float below = EmotionConfig.DISTURBANCE_THRESHOLD - 5.0F;
        float clamped = EmotionRules.restlessJitterClamp(below, 100.0F);
        float alreadyHigh = EmotionConfig.DISTURBANCE_THRESHOLD + 5.0F;
        float untouched = EmotionRules.restlessJitterClamp(alreadyHigh, alreadyHigh + 1.0F);
        boolean ok = clamped < EmotionConfig.DISTURBANCE_THRESHOLD
                && Math.abs(untouched - (alreadyHigh + 1.0F)) < 0.001F;
        check("心悸抖动绝不会把「未超标」的情绪推过阈值", ok, "clamped=" + clamped);
    }

    // 9 神扰时长硬封顶（验收项 5）
    private static void checkDeliriumHardCap() {
        int cap = EmotionConfig.DELIRIUM_HARD_CAP_TICKS;
        int extended = EmotionRules.extendDelirium(cap - 10, 100000);
        boolean ok = extended == cap
                && EmotionRules.extendDelirium(100, 50) == 150
                && EmotionRules.extendDelirium(100, -50) == 100;
        check("神扰时长追加受全局硬封顶约束", ok, "extended=" + extended + " cap=" + cap);
    }

    // 10 静坐削减神扰计时（验收项 8）
    private static void checkMeditationDrain() {
        boolean ok = EmotionRules.drainDelirium(5, 100) == 0
                && EmotionRules.drainDelirium(100, 20) == 80
                && EmotionRules.drainDelirium(100, -5) == 100;
        check("静坐削减神扰剩余时间、且不会削成负数", ok, "削减规则不符");
    }

    // 11 攻速钳位（验收项 4）
    private static void checkAttackSpeedClamp() {
        double base = 4.0D;
        double huge = EmotionRules.attackSpeedBonus(base, 99.0D);
        double total = base * (1.0D + huge);
        double mid = EmotionRules.attackSpeedBonus(base, 0.3D);
        double high = EmotionRules.attackSpeedBonus(base, 0.9D);
        boolean ok = EmotionRules.attackSpeedBonus(base, 0.0D) == 0.0D
                && huge > 0.0D
                && total <= EmotionConfig.ATTACK_SPEED_CEILING + 0.0001D
                && total <= EntityAttributes.GENERIC_ATTACK_SPEED.clamp(total) + 0.0001D
                && mid <= high;
        check("攻速增益被原版属性 clamp 钳位、不超硬上限", ok,
                "huge=" + huge + " total=" + total + " ceiling=" + EmotionConfig.ATTACK_SPEED_CEILING);
    }

    // 12 生命上限扣除（验收项 4）
    private static void checkMaxHealthPenalty() {
        double penalty = EmotionRules.maxHealthPenalty();
        boolean ok = penalty >= -0.5D && penalty <= 0.0D;
        check("神扰临时扣除生命上限、最多扣一半（走修饰器）", ok, "penalty=" + penalty);
    }

    // 13 命名空间（约束 4）
    private static void checkEffectNamespaces() {
        int bad = 0;
        for (EmotionType type : EmotionType.values()) {
            if (!namespaceOk(type.disturbancePath(), EmotionEffects.disturbanceOf(type))) {
                bad++;
            }
        }
        if (!namespaceOk("shen_rao", EmotionEffects.SHEN_RAO)) {
            bad++;
        }
        if (!namespaceOk("shen_an", EmotionEffects.SHEN_AN)) {
            bad++;
        }
        if (!namespaceOk("meditating", EmotionEffects.MEDITATING)) {
            bad++;
        }
        check("11 个情志状态效果全部注册在 bingzhengmod: 命名空间下（含隐藏静坐标记）",
                bad == 0, "有 " + bad + " 个异常");
    }

    private static boolean namespaceOk(String path, StatusEffect effect) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect);
        if (id == null || !BingzhengMod.MOD_ID.equals(id.getNamespace()) || !path.equals(id.getPath())) {
            BingzhengMod.LOGGER.error("【病证·情志自检】效果命名空间异常：期望 bingzhengmod:{}，实际 {}", path, id);
            return false;
        }
        return true;
    }

    // 14 配置自洽
    private static void checkConfigSanity() {
        boolean ok = EmotionType.COUNT == 7
                && EmotionConfig.DISTURBANCE_MAX == 2
                && EmotionConfig.DISTURBANCE_THRESHOLD < EmotionConfig.EMOTION_MAX
                && EmotionConfig.DELIRIUM_BASE_TICKS < EmotionConfig.DELIRIUM_HARD_CAP_TICKS;
        check("配置自洽（七情 7 项 / 扰动上限 2 / 阈值小于上限 / 基础时长小于硬封顶）", ok, "配置互相矛盾");
    }
    // ---------------- v0.3.0 追加 ----------------

    // 18【Bug-3】神扰施加的那一刻强制清空全部气机扰动
    private static void checkDeliriumClearsDisturbances() {
        // 结构断言：注册出来的神扰必须是 ShenRaoEffect，onApplied 清理钩子才挂在它身上。
        // 若有人把它换回普通 MarkerEffect，这一项会立刻失败。
        boolean hooked = EmotionEffects.SHEN_RAO instanceof ShenRaoEffect;
        // 行为断言：神扰期间不允许再生成新扰动（否则会残留到神扰结束、二次合并）
        boolean gate = !EmotionRules.shouldRaiseDisturbance(true)
                && EmotionRules.shouldRaiseDisturbance(false);
        check("神扰被施加的瞬间强制清空全部气机扰动（onApplied 钩子已挂载），且神扰期间不再生成新扰动",
                hooked && gate, "钩子=" + hooked + " 闸门=" + gate);
    }

    // 19【Bug-3】冷却只拦神扰、不拦扰动
    private static void checkCooldownBlocksOnlyDelirium() {
        int cd = EmotionConfig.DELIRIUM_COOLDOWN_TICKS;
        boolean blocksDelirium = !EmotionRules.shouldMerge(2, cd) && !EmotionRules.shouldMerge(7, cd);
        boolean disturbanceStillAllowed = EmotionRules.shouldRaiseDisturbance(false);
        boolean reopens = EmotionRules.shouldMerge(2, 0);
        check("神扰冷却只拦截「神扰生成」、不阻止新的气机扰动产生，冷却走完才重新判定",
                blocksDelirium && disturbanceStillAllowed && reopens,
                "拦神扰=" + blocksDelirium + " 放扰动=" + disturbanceStillAllowed + " 冷却后重开=" + reopens);
    }

    // 20 冷却与心绪不宁两个计时器互相独立
    private static void checkRestlessIndependentOfCooldown() {
        EmotionState s = EmotionState.fresh();
        s.setCooldownDelirium(3);
        s.setRestlessTicks(EmotionConfig.RESTLESS_TICKS);
        for (int i = 0; i < 3; i++) {
            s.setCooldownDelirium(s.getCooldownDelirium() - 1);
        }
        s.setRestlessTicks(s.getRestlessTicks() - 1);   // 心绪不宁只走 1 tick
        boolean cooldownGone = s.getCooldownDelirium() == 0;
        boolean restlessAlive = s.getRestlessTicks() == EmotionConfig.RESTLESS_TICKS - 1;
        boolean stillBlocks = !EmotionRules.shouldHaveShenAn(false, 0, s.getRestlessTicks() > 0);

        EmotionState t = EmotionState.fresh();
        t.setCooldownDelirium(EmotionConfig.DELIRIUM_COOLDOWN_TICKS);
        t.setRestlessTicks(0);
        boolean unblocked = EmotionRules.shouldHaveShenAn(false, 0, t.getRestlessTicks() > 0);

        check("神扰冷却与心绪不宁互相独立：冷却归零后心绪不宁仍继续封锁神安，心绪不宁归零后神安即解封",
                cooldownGone && restlessAlive && stillBlocks && unblocked,
                "冷却归零=" + cooldownGone + " 心绪不宁仍在=" + restlessAlive
                        + " 仍封锁神安=" + stillBlocks + " 解封=" + unblocked);
    }

    // 21 牛奶保护名单（只保护本模组效果）
    private static void checkMilkProtectionSet() {
        StringBuilder missing = new StringBuilder();
        int bad = 0;
        for (String path : MilkSafeEffects.REQUIRED_PROTECTED_PATHS) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(BingzhengMod.id(path));
            if (effect == null || !MilkSafeEffects.isProtected(effect)) {
                bad++;
                missing.append(path).append(' ');
            }
        }
        StatusEffect vanillaSpeed = Registries.STATUS_EFFECT.get(new Identifier("minecraft", "speed"));
        boolean vanillaUntouched = vanillaSpeed != null && !MilkSafeEffects.isProtected(vanillaSpeed);
        boolean foreignUntouched = !MilkSafeEffects.isProtected(new Identifier("someothermod", "some_effect"));
        check("牛奶保护名单覆盖全部 " + MilkSafeEffects.REQUIRED_PROTECTED_PATHS.length
                        + " 个本模组效果，原版与其它模组效果完全不受影响",
                bad == 0 && vanillaUntouched && foreignUntouched,
                "漏保护 " + bad + " 项：" + missing + " 原版不受影响=" + vanillaUntouched
                        + " 外模组不受影响=" + foreignUntouched);
    }

    // 22 长期数值不在 StatusEffect 上（牛奶碰不到底层数据）
    private static void checkBackendDataNotAnEffect() {
        boolean persistent = EmotionAttachments.EMOTION.isPersistent();
        boolean hasCodec = EmotionAttachments.EMOTION.persistenceCodec() != null;
        check("神耗/体耗/七情等长期数值保存在持久化 Attachment（不是 StatusEffect），牛奶永远碰不到",
                persistent && hasCodec, "persistent=" + persistent + " codec=" + hasCodec);
    }

    // 23 神扰退出时的高位情绪衰减（只打超阈值的，绝不归零）
    private static void checkEndOfDeliriumDecay() {
        float high = EmotionConfig.EMOTION_MAX;
        float decayed = EmotionRules.endOfDeliriumDecay(high);
        boolean dropped = decayed < high && decayed > 0.0F;                 // 降了，但没归零
        boolean matchesCoefficient =
                Math.abs(decayed - high * EmotionConfig.DELIRIUM_END_EMOTION_DECAY) < 0.001F;
        boolean coefficientInRange = EmotionConfig.DELIRIUM_END_EMOTION_DECAY >= 0.6F
                && EmotionConfig.DELIRIUM_END_EMOTION_DECAY <= 0.7F;         // 用户指定 0.6-0.7
        float low = EmotionConfig.DISTURBANCE_THRESHOLD - 10.0F;
        boolean lowUntouched = EmotionRules.endOfDeliriumDecay(low) == low; // 低于阈值原样不动
        boolean boundaryDecays = EmotionRules.endOfDeliriumDecay(EmotionConfig.DISTURBANCE_THRESHOLD)
                < EmotionConfig.DISTURBANCE_THRESHOLD;                      // 正好等于阈值也要衰减
        check("神扰退出时高位情绪按系数衰减（>0 且 <原值，绝不归零）、低于阈值的原样保留、系数在 0.6-0.7",
                dropped && matchesCoefficient && coefficientInRange && lowUntouched && boundaryDecays,
                "100→" + decayed + " 系数=" + EmotionConfig.DELIRIUM_END_EMOTION_DECAY
                        + " 低位不动=" + lowUntouched + " 边界衰减=" + boundaryDecays);
    }

    // 24 神扰退出的完整数据流（用户给的 ①~⑥）
    private static void checkDeliriumExitFlow() {
        EmotionState s = EmotionState.fresh();
        // ① 两条情绪顶格 → 会生成扰动并合并成神扰
        s.setEmotion(EmotionType.ANGER, EmotionConfig.EMOTION_MAX);
        s.setEmotion(EmotionType.FEAR, EmotionConfig.EMOTION_MAX);
        s.setEmotion(EmotionType.JOY, 20.0F);

        // ④ 神扰结束：高位衰减 + 启动两个计时器
        for (EmotionType t : EmotionType.values()) {
            s.setEmotion(t, EmotionRules.endOfDeliriumDecay(s.emotion(t)));
        }
        s.setCooldownDelirium(EmotionConfig.DELIRIUM_COOLDOWN_TICKS);
        s.setRestlessTicks(EmotionConfig.RESTLESS_TICKS);

        boolean highDropped = s.emotion(EmotionType.ANGER) < EmotionConfig.EMOTION_MAX
                && s.emotion(EmotionType.ANGER) > 0.0F
                && s.emotion(EmotionType.FEAR) < EmotionConfig.EMOTION_MAX;
        boolean lowUntouched = s.emotion(EmotionType.JOY) == 20.0F;
        // 心绪不宁阶段的心悸抖动仍然有非零起点可抖
        boolean jitterHasHeadroom = s.emotion(EmotionType.JOY) > 0.0F;
        // ⑤ 冷却期间：拦神扰，但不拦气机扰动
        boolean cooldownBlocks = !EmotionRules.shouldMerge(2, s.getCooldownDelirium())
                && !EmotionRules.shouldMerge(7, s.getCooldownDelirium())
                && EmotionRules.shouldRaiseDisturbance(false);
        boolean restlessBlocksShenAn = !EmotionRules.shouldHaveShenAn(false, 0, s.getRestlessTicks() > 0);
        // ⑥ 冷却走完后，若仍有 ≥2 条超阈值才允许再次合并。
        //    【v0.7.1】阈值抬到 70 后，神扰退出的 0.65 衰减（100 → 65）已压在阈值之下，
        //    因此「极端情况二次爆发」不再由这条路径自然发生 —— 这里只断言**规则本身**仍然自洽。

        int stillHigh = 0;
        for (EmotionType t : EmotionType.values()) {
            if (EmotionRules.exceedsThreshold(s.emotion(t))) {
                stillHigh++;
            }
        }
        boolean secondBurstMatchesRule = EmotionRules.shouldMerge(stillHigh, 0) == (stillHigh >= 2);
        check("神扰退出数据流：高位衰减→冷却挡神扰→心绪不宁封神安→冷却走完后仍按「≥2 条超阈值」判定合并资格",
                highDropped && lowUntouched && jitterHasHeadroom && cooldownBlocks
                        && restlessBlocksShenAn && secondBurstMatchesRule,
                "高位下降=" + highDropped + " 低位不动=" + lowUntouched + " 抖动有余量=" + jitterHasHeadroom
                        + " 冷却拦神扰=" + cooldownBlocks + " 心绪不宁封神安=" + restlessBlocksShenAn
                        + " 二次爆发=" + secondBurstMatchesRule + "(仍超阈值 " + stillHigh + " 条)");
    }

    // ---------------- v0.7.0 追加：时辰节律波动 ----------------

    // 25 时辰相位表
    private static void checkShichenPhaseTable() {
        boolean mapping = EmotionShichen.sovereignShichen(EmotionType.ANGER) == 1      // 丑时肝 → 怒
                && EmotionShichen.sovereignShichen(EmotionType.JOY) == 6              // 午时心 → 喜
                && EmotionShichen.sovereignShichen(EmotionType.PENSIVENESS) == 5      // 巳时脾 → 思
                && EmotionShichen.sovereignShichen(EmotionType.GRIEF) == 2            // 寅时肺 → 悲
                && EmotionShichen.sovereignShichen(EmotionType.FEAR) == 9;            // 酉时肾 → 恐
        // 忧、惊不参与（延续「忧惊不补」）
        boolean worryFrightOut = !EmotionShichen.hasPhase(EmotionType.WORRY)
                && !EmotionShichen.hasPhase(EmotionType.FRIGHT)
                && EmotionShichen.targetOffsetForSegment(EmotionType.WORRY, 0) == 0.0F
                && EmotionShichen.targetOffsetForSegment(EmotionType.FRIGHT, 6) == 0.0F;
        // 时间轴锚点：MC 0 刻 = 06:00（卯时）；6000 刻 = 12:00（午时，当令喜）
        boolean anchors = EmotionShichen.index(0L) == 3
                && "卯".equals(EmotionShichen.name(0L))
                && "午".equals(EmotionShichen.name(6000L))
                && EmotionShichen.sovereignAt(6000L) == EmotionType.JOY
                && EmotionShichen.sovereignAt(0L) == null;
        boolean wrap = EmotionShichen.names().length == EmotionConfig.SHICHEN_COUNT
                && "子".equals(EmotionShichen.names()[0])
                && EmotionShichen.index(-1L) == EmotionShichen.index(23999L)
                && EmotionShichen.index(24000L) == EmotionShichen.index(0L);
        check("七情时辰相位表：丑时怒、午时喜、巳时思、寅时悲、酉时恐；忧/惊不参与；"
                        + "MC 0 刻＝卯时、6000 刻＝午时（当令喜）",
                mapping && worryFrightOut && anchors && wrap,
                "映射=" + mapping + " 忧惊除外=" + worryFrightOut + " 锚点=" + anchors + " 回绕=" + wrap);
    }

    // 26 时辰目标曲线
    private static void checkShichenTargetCurve() {
        boolean ok = true;
        StringBuilder detail = new StringBuilder();
        for (EmotionType type : EmotionType.values()) {
            if (!EmotionShichen.hasPhase(type)) {
                continue;
            }
            int sovereign = EmotionShichen.sovereignShichen(type);
            int opposite = EmotionShichen.oppositeShichen(type);
            float peak = EmotionShichen.targetOffsetForSegment(type, sovereign);
            float trough = EmotionShichen.targetOffsetForSegment(type, opposite);
            float max = Float.NEGATIVE_INFINITY;
            float min = Float.POSITIVE_INFINITY;
            float sum = 0.0F;
            for (int seg = 0; seg < EmotionConfig.SHICHEN_COUNT; seg++) {
                float v = EmotionShichen.targetOffsetForSegment(type, seg);
                max = Math.max(max, v);
                min = Math.min(min, v);
                sum += v;
            }
            ok &= Math.abs(peak - EmotionConfig.RHYTHM_AMPLITUDE) < 1.0E-4F;
            ok &= Math.abs(trough + EmotionConfig.RHYTHM_AMPLITUDE) < 1.0E-4F;
            ok &= Math.abs(max - peak) < 1.0E-4F && Math.abs(min - trough) < 1.0E-4F;
            ok &= Math.abs(sum) < 1.0E-3F;
            detail.append(type.zhName()).append(':').append(peak).append('/').append(trough).append(' ');
        }
        check("七情时辰目标曲线：当令时辰恰为 +振幅（±2）、对冲时辰恰为 −振幅，且是该情一天中的最高/最低点；"
                        + "十二段相抵不产生整体偏移",
                ok, detail.toString());
    }

    // 27 变速到达
    private static void checkRhythmApproach() {
        float target = EmotionConfig.RHYTHM_AMPLITUDE;
        float moveNear = EmotionRules.approach(target - 1.0F, target, 0.1F, 0.0F) - (target - 1.0F);
        float moveFar = EmotionRules.approach(target - 4.0F, target, 0.1F, 0.0F) - (target - 4.0F);
        boolean proportional = Math.abs(moveNear - 0.1F) < 1.0E-4F
                && Math.abs(moveFar - 0.4F) < 1.0E-4F && moveFar > moveNear;

        float[] distances = { 2.0F, 1.0F, 0.5F, 0.25F, 0.1F };
        float last = Float.MAX_VALUE;
        boolean decelerating = true;
        for (float d : distances) {
            float move = EmotionRules.approach(target - d, target, 0.2F, 0.0F) - (target - d);
            decelerating &= move > 0.0F && move < last;
            last = move;
        }

        float v = -EmotionConfig.RHYTHM_AMPLITUDE;
        boolean monotone = true;
        for (int i = 0; i < 4000; i++) {
            double sample = (i * 37 % 100) / 100.0D;
            float factor = EmotionRules.approachFactor(EmotionConfig.RHYTHM_SPEED,
                    EmotionConfig.RHYTHM_SPEED_JITTER, sample, EmotionConfig.RHYTHM_SPEED_MIN);
            float next = EmotionRules.approach(v, target, factor);
            monotone &= next >= v - 1.0E-6F && next <= target + 1.0E-6F;
            v = next;
        }
        boolean converged = Math.abs(v - target) <= EmotionConfig.RHYTHM_MARGIN + 1.0E-5F;

        boolean factorSafe = true;
        for (int i = 0; i <= 100; i++) {
            float factor = EmotionRules.approachFactor(EmotionConfig.RHYTHM_SPEED,
                    EmotionConfig.RHYTHM_SPEED_JITTER, i / 100.0D, EmotionConfig.RHYTHM_SPEED_MIN);
            factorSafe &= factor > 0.0F && factor < 1.0F;
        }
        boolean capped = EmotionRules.approach(0.0F, 10.0F, 100.0F, 0.0F) < 10.0F;
        check("七情变速到达：位移＝距目标距离×随机化系数（不匀速）、越近越慢（软着陆）、"
                        + "4000 tick 内收敛且从未越过目标；系数恒在 (0,1) 内（结构上不可能振荡）",
                proportional && decelerating && monotone && converged && factorSafe && capped,
                "等比=" + proportional + " 减速=" + decelerating + " 收敛=" + converged + " 终点=" + v);
    }

    // 28 气机扰动的迟滞
    private static void checkDisturbanceHysteresis() {
        float threshold = EmotionConfig.DISTURBANCE_THRESHOLD;
        float release = EmotionRules.releaseThreshold();
        boolean band = Math.abs(threshold - release - EmotionConfig.DISTURBANCE_RELEASE_BAND) < 1.0E-4F
                && release < threshold;
        // 未锁定：必须达到触发阈值才武装
        boolean armedAtThreshold = EmotionRules.disturbanceArmed(threshold, false)
                && !EmotionRules.disturbanceArmed(threshold - 0.1F, false);
        // 已锁定：稳定值仍在解除线之上就保持锁定（不重复生成）
        boolean holdsInBand = EmotionRules.latchSurvives(release, true)
                && EmotionRules.latchSurvives(threshold + 5.0F, true)
                && !EmotionRules.latchSurvives(release - 0.1F, true);
        // 迟滞带内来回走：一旦触发，只要没跌到解除线就一直不重新武装
        boolean noFlap = true;
        boolean latched = false;
        if (EmotionRules.disturbanceArmed(threshold + 0.5F, latched)) {
            latched = true;                        // 触发一次
        }
        for (float value : new float[] { threshold + 3.0F, threshold - 1.0F, release + 0.5F,
                threshold + 2.0F, release + 0.1F }) {
            if (latched && !EmotionRules.latchSurvives(value, true)) {
                latched = false;
            }
            noFlap &= !EmotionRules.disturbanceArmed(value, latched);
        }
        boolean rearmed = !EmotionRules.latchSurvives(release - 0.5F, true)
                && EmotionRules.disturbanceArmed(threshold + 0.5F, false);
        check("气机扰动改为迟滞区间：触发阈值 " + EmotionConfig.DISTURBANCE_THRESHOLD + "、解除 = 阈值 − "
                        + EmotionConfig.DISTURBANCE_RELEASE_BAND + "；同一条情绪触发后锁定，"
                        + "迟滞带内来回走不闪断，跌到解除线才重新武装",
                band && armedAtThreshold && holdsInBand && noFlap && rearmed,
                "触发=" + threshold + " 解除=" + release + " 带=" + band + " 锁定保持=" + holdsInBand
                        + " 不闪断=" + noFlap + " 重新武装=" + rearmed);
    }

    // 29 波动与抖动只进现值
    private static void checkWaveNotJudged() {
        float threshold = EmotionConfig.DISTURBANCE_THRESHOLD;
        float release = EmotionRules.releaseThreshold();
        float amp = EmotionConfig.RHYTHM_AMPLITUDE;
        float jitter = EmotionConfig.RHYTHM_JITTER_AMPLITUDE;

        // 稳定值差一点点（阈值−1）被波动推到阈值之上 —— 现值越线，但判定不认它
        float stableBelow = threshold - 1.0F;
        float displayAbove = EmotionRules.displayValue(stableBelow, amp, jitter);
        boolean displayCrosses = displayAbove >= threshold
                && !EmotionRules.disturbanceArmed(stableBelow, false);
        // 稳定值刚过阈值（阈值+2）触发后，现值瞬时跌回阈值之下 —— 锁定依旧保持
        float stableAbove = threshold + 2.0F;
        float displayBelow = EmotionRules.displayValue(stableAbove, -amp, -jitter);
        boolean latchHolds = displayBelow < threshold
                && EmotionRules.disturbanceArmed(stableAbove, false)
                && EmotionRules.latchSurvives(stableAbove, true);
        // 现值仍被钳在 0..100
        boolean clamped = EmotionRules.displayValue(EmotionConfig.EMOTION_MAX, amp, jitter) == EmotionConfig.EMOTION_MAX
                && EmotionRules.displayValue(EmotionConfig.EMOTION_MIN, -amp, -jitter) == EmotionConfig.EMOTION_MIN;
        // 抖动零均值、每次重掷而非累加（所以不漂移）
        boolean zeroMean = Math.abs(EmotionRules.jitter(0.0D, jitter) + EmotionRules.jitter(1.0D, jitter)) < 1.0E-6F
                && Math.abs(EmotionRules.jitter(0.5D, jitter)) < 1.0E-6F
                && Math.abs(EmotionRules.jitter(1.0D, jitter) - jitter) < 1.0E-6F;
        check("时辰波动与微观抖动只进「现值」：稳定值差 1 点被波动推过阈值也不触发；"
                        + "已触发者现值瞬时跌回阈值之下仍保持锁定；现值钳在 0..100",
                displayCrosses && latchHolds && clamped && zeroMean,
                "现值越线=" + displayAbove + "（未触发） 现值回落=" + displayBelow + "（仍锁定）");
    }


    // ---------------- v0.8.0 追加：五志行为驱动 ----------------

    // 30 悲的排除规则
    private static void checkGriefExclusions() {
        // 主动丢弃（Q 扔出，带 owner）→ 不计
        boolean thrown = EmotionBehaviorRules.griefPointsForItem(
                new EmotionBehaviorRules.ItemFate(true, false, true, false)) == 0.0F;
        // 岩浆/火/虚空销毁 → 计 1 点
        boolean hazard = EmotionBehaviorRules.griefPointsForItem(
                new EmotionBehaviorRules.ItemFate(false, false, true, false))
                == EmotionConfig.GRIEF_POINTS_ITEM_DESTROYED;
        // 超时消失 → 计 1 点
        boolean despawn = EmotionBehaviorRules.griefPointsForItem(
                new EmotionBehaviorRules.ItemFate(false, false, false, true))
                == EmotionConfig.GRIEF_POINTS_ITEM_DESPAWNED;
        // 死亡掉落物超时消失 → 不重复计（死亡已 +3）
        boolean deathDrop = EmotionBehaviorRules.griefPointsForItem(
                new EmotionBehaviorRules.ItemFate(false, true, false, true)) == 0.0F;
        // 既非销毁也非超时（例如区块卸载）→ 不计
        boolean unloaded = EmotionBehaviorRules.griefPointsForItem(
                new EmotionBehaviorRules.ItemFate(false, false, false, false)) == 0.0F;
        check("悲的排除规则：主动丢弃不计、岩浆销毁计、超时消失计、死亡掉落物超时消失不再重复计",
                thrown && hazard && despawn && deathDrop && unloaded,
                "主动丢弃=" + thrown + " 销毁=" + hazard + " 超时=" + despawn + " 死亡掉落=" + deathDrop);
    }

    // 31 恐的锁定状态机
    private static void checkFearLatch() {
        // 从无到有才计一次
        boolean first = EmotionBehaviorRules.fearShouldAward(false, true)
                && !EmotionBehaviorRules.fearShouldAward(true, true);
        // 近距离但「没有攻击意图」（targeted=false）→ 不计
        boolean noIntent = !EmotionBehaviorRules.fearShouldAward(false, false);
        // 脱战重新武装：连续无目标超过 FEAR_REARM_TICKS 后锁定失效
        boolean stillLocked = EmotionBehaviorRules.fearStillLocked(true, 0)
                && EmotionBehaviorRules.fearStillLocked(true, EmotionConfig.FEAR_REARM_TICKS - 1);
        boolean rearmed = !EmotionBehaviorRules.fearStillLocked(true, EmotionConfig.FEAR_REARM_TICKS)
                && EmotionBehaviorRules.fearShouldAward(false, true);
        check("恐的判定：被锁定只计一次、持续锁定不重复、近距离无攻击意图不计、脱战后重新武装才再计",
                first && noIntent && stillLocked && rearmed,
                "首次=" + first + " 无意图=" + noIntent + " 持续=" + stillLocked + " 重新武装=" + rearmed);
    }

    // 32 恢复
    private static void checkBehaviorRecovery() {
        float baseline = EmotionConfig.EMOTION_BASELINE_DEFAULT;
        float value = baseline + 12.0F;
        for (int block = 0; block < 12; block++) {
            value = EmotionBehaviorRules.recoveryStep(value, baseline);
        }
        boolean twelveBlocks = Math.abs(value - baseline) < 0.001F;
        // 不越下限：已经在基线上时再回落也不会低于基线
        float floor = EmotionBehaviorRules.recoveryStep(baseline, baseline);
        boolean noUnderflow = floor >= baseline;
        boolean dueAtOneBlock = EmotionBehaviorRules.recoveryDue(EmotionConfig.BEHAVIOR_RECOVERY_INTERVAL)
                && !EmotionBehaviorRules.recoveryDue(EmotionConfig.BEHAVIOR_RECOVERY_INTERVAL - 1L);
        check("五志恢复：无行为时每时辰向基线回落 1 点（12 时辰回落 12 点）、不越下限（不低于基线）",
                twelveBlocks && noUnderflow && dueAtOneBlock,
                "12 时辰后=" + value + " 下限=" + floor + " 时钟=" + dueAtOneBlock);
    }

    // 33 结算
    private static void checkBehaviorSettlement() {
        boolean angerSettles = EmotionBehaviorRules.shouldSettle(EmotionType.ANGER, EmotionConfig.ANGER_THRESHOLD)
                && !EmotionBehaviorRules.shouldSettle(EmotionType.ANGER, EmotionConfig.ANGER_THRESHOLD - 0.1F);
        boolean joySettles = EmotionBehaviorRules.shouldSettle(EmotionType.JOY, EmotionConfig.JOY_THRESHOLD);
        boolean fearSettles = EmotionBehaviorRules.shouldSettle(EmotionType.FEAR, EmotionConfig.FEAR_THRESHOLD);
        // 点清零（不是扣阈值）
        boolean cleared = EmotionBehaviorRules.pointsAfterSettle(EmotionType.ANGER, 99.0F) == 0.0F;
        // 点累积有上限
        float capped = EmotionBehaviorRules.pointsStep(EmotionConfig.BEHAVIOR_POINT_MAX, 5.0F);
        boolean ceiling = Math.abs(capped - EmotionConfig.BEHAVIOR_POINT_MAX) < 1.0E-6F;
        // 跨 70 才触发：+1 后刚好到达阈值 → exceedsThreshold 为真、且迟滞未锁时允许生成扰动
        float below = EmotionConfig.DISTURBANCE_THRESHOLD - 1.0F;
        boolean crosses = !EmotionRules.exceedsThreshold(below)
                && EmotionRules.exceedsThreshold(below + 1.0F)
                && EmotionRules.disturbanceArmed(below + 1.0F, false);
        check("五志结算：点达阈值 → 该情稳定值 +1、点清零、点有上限；"
                        + "稳定值跨过 " + EmotionConfig.DISTURBANCE_THRESHOLD + " 即满足既有扰动/冲量触发条件",
                angerSettles && joySettles && fearSettles && cleared && ceiling && crosses,
                "结算=" + angerSettles + " 清零=" + cleared + " 上限=" + ceiling + " 跨阈值=" + crosses);
    }

    // 34 忧 / 惊 不入框架
    private static void checkWorryFrightOutside() {
        boolean outsideFramework = !EmotionBehaviorRules.isFiveZhi(EmotionType.WORRY)
                && !EmotionBehaviorRules.isFiveZhi(EmotionType.FRIGHT)
                && EmotionBehaviorRules.isFiveZhi(EmotionType.ANGER)
                && EmotionBehaviorRules.isFiveZhi(EmotionType.JOY)
                && EmotionBehaviorRules.isFiveZhi(EmotionType.PENSIVENESS)
                && EmotionBehaviorRules.isFiveZhi(EmotionType.GRIEF)
                && EmotionBehaviorRules.isFiveZhi(EmotionType.FEAR);
        // 无阈值 = 永不结算
        boolean noSettle = !EmotionBehaviorRules.shouldSettle(EmotionType.WORRY, 9999.0F)
                && !EmotionBehaviorRules.shouldSettle(EmotionType.FRIGHT, 9999.0F);
        // 不参与时辰相位（忧、惊无相位）
        boolean noPhase = !EmotionShichen.hasPhase(EmotionType.WORRY)
                && !EmotionShichen.hasPhase(EmotionType.FRIGHT)
                && EmotionShichen.targetOffsetForSegment(EmotionType.WORRY, 3) == 0.0F
                && EmotionShichen.targetOffsetForSegment(EmotionType.FRIGHT, 6) == 0.0F;
        check("忧、惊不入行为框架（无阈值永不结算）且不参与时辰相位；五志为怒喜思悲恐五项",
                outsideFramework && noSettle && noPhase,
                "框架内=" + outsideFramework + " 不结算=" + noSettle + " 无相位=" + noPhase);
    }


    // ---------------- v0.9.0 追加：神耗 / 体耗 HUD ----------------

    // 35 数值 → 格数
    private static void checkHudCellConversion() {
        boolean boundaries = EmotionHudRules.cells(0.0F) == 0.0F
                && EmotionHudRules.cells(5.0F) == 0.5F
                && EmotionHudRules.cells(10.0F) == 1.0F
                && EmotionHudRules.cells(15.0F) == 1.5F
                && EmotionHudRules.cells(50.0F) == 5.0F
                && EmotionHudRules.cells(95.0F) == 9.5F
                && EmotionHudRules.cells(100.0F) == 10.0F;
        boolean decimals = EmotionHudRules.cells(12.3F) == 1.0F
                && EmotionHudRules.cells(14.99F) == 1.0F
                && EmotionHudRules.cells(97.5F) == 9.5F;
        boolean clamped = EmotionHudRules.cells(-5.0F) == 0.0F
                && EmotionHudRules.cells(105.0F) == 10.0F;
        check("HUD 换算：0/5/10/15/50/95/100 七点全对（0→0、5→半格、10→1、15→1.5、50→5、95→9.5、100→10）；"
                        + "小数 12.3→1 格；越界 −5→0、105→10",
                boundaries && decimals && clamped,
                "边界=" + boundaries + " 小数=" + decimals + " 钳制=" + clamped);
    }

    // 36 上下行映射与三态
    private static void checkHudRowMapping() {
        // 神耗在上（行号 0）、体耗在下（行号 1）
        boolean rows = EmotionHudRules.ROW_SPIRIT == 0 && EmotionHudRules.ROW_BODY == 1
                && EmotionHudRules.valueForRow(EmotionHudRules.ROW_SPIRIT, 37.0F, 88.0F) == 37.0F
                && EmotionHudRules.valueForRow(EmotionHudRules.ROW_BODY, 37.0F, 88.0F) == 88.0F;
        // 每行 10 格、每格 10 点、半格 5 点
        boolean scale = EmotionHudRules.SEGMENTS == 10
                && EmotionHudRules.POINTS_PER_SEGMENT == 10.0F
                && EmotionHudRules.HALF_SEGMENT_POINTS == 5.0F;
        // 三态：格数 3.5 → 前 3 满、第 4 半、其余空
        boolean frames = EmotionHudRules.frameFor(3.5F, 0) == EmotionHudRules.FRAME_FULL
                && EmotionHudRules.frameFor(3.5F, 2) == EmotionHudRules.FRAME_FULL
                && EmotionHudRules.frameFor(3.5F, 3) == EmotionHudRules.FRAME_HALF
                && EmotionHudRules.frameFor(3.5F, 4) == EmotionHudRules.FRAME_EMPTY
                && EmotionHudRules.frameFor(0.0F, 0) == EmotionHudRules.FRAME_EMPTY
                && EmotionHudRules.frameFor(10.0F, 9) == EmotionHudRules.FRAME_FULL;
        check("HUD 版式：神耗在上、体耗在下；每行 10 格 / 每格 10 点 / 半格 5 点；空·半·满三态按格数正确分帧",
                rows && scale && frames,
                "行映射=" + rows + " 刻度=" + scale + " 三态=" + frames);
    }

    // 37 开关
    private static void checkHudToggle() {
        boolean defaultOn = EmotionHudRules.DEFAULT_ENABLED
                && EmotionHudRules.shouldRegister(true)
                && !EmotionHudRules.shouldRegister(false);
        check("HUD 开关默认开；关闭后不注册渲染回调（渲染路径整体不存在，不是「画了但不显示」）",
                defaultOn, "默认开=" + EmotionHudRules.DEFAULT_ENABLED);
    }

    // 38 同步通道
    private static void checkHudSyncChannel() {
        boolean channel = EmotionHudSync.CHANNEL != null
                && "bingzhengmod".equals(EmotionHudSync.CHANNEL.getNamespace())
                && EmotionHudSync.CHANNEL.getPath().contains("hud");
        // 变化才推；不变则只靠定时兜底（不会每 tick 发包）
        boolean changed = EmotionHudRules.shouldSync(10.0F, 20.0F, 10.5F, 20.0F)
                && !EmotionHudRules.shouldSync(10.0F, 20.0F, 10.0F, 20.0F);
        boolean interval = EmotionConfig.HUD_SYNC_INTERVAL > 0;
        check("HUD 同步：通道在 bingzhengmod 命名空间下、数值变化才立即推送（未变化只走定时兜底）、间隔为正",
                channel && changed && interval,
                "通道=" + EmotionHudSync.CHANNEL + " 变化才推=" + changed + " 间隔=" + EmotionConfig.HUD_SYNC_INTERVAL);
    }

    // 39 与五脏零耦合
    private static void checkHudNoWuzangDependency() {
        boolean clean = true;
        try {
            Class<?> rules = Class.forName("com.bingzheng.emotion.EmotionHudRules");
            for (var method : rules.getDeclaredMethods()) {
                clean &= !method.getReturnType().getName().contains("wuzang");
                for (var param : method.getParameterTypes()) {
                    clean &= !param.getName().contains("wuzang");
                }
            }
        } catch (Throwable t) {
            clean = false;
        }
        check("HUD 与五脏零耦合：规则类的签名里没有任何 wuzang 类型 —— 单开情志即可同步与渲染",
                clean, "无五脏符号=" + clean);
    }


    // ---------------- v0.9.1 追加：布局修复 + 量化规范 v1 ----------------

    // 40 模式守卫
    private static void checkHudModeGuard() {
        // GameMode 序：0 生存 / 1 创造 / 2 冒险 / 3 旁观
        boolean guard = EmotionHudRules.modeAllows(0)      // 生存 → 渲染
                && !EmotionHudRules.modeAllows(1)          // 创造 → 不渲染
                && EmotionHudRules.modeAllows(2)           // 冒险 → 渲染
                && !EmotionHudRules.modeAllows(3);         // 旁观 → 不渲染
        check("HUD 模式守卫：生存 / 冒险渲染，创造 / 旁观不渲染", guard, "守卫=" + guard);
    }

    // 41 布局判定（不写死坐标）
    private static void checkHudLayout() {
        int healthTop = EmotionHudRules.healthTop(240);
        // ① 无护甲：两行紧贴血量条顶边上方
        int noArmor = EmotionHudRules.rowsTop(healthTop, false, healthTop - 30);
        boolean hugsHealth = noArmor + EmotionHudRules.ROWS_HEIGHT == healthTop;
        // ② 原版有护甲（护甲紧贴血量条，中间只剩 1px）：整体上移到护甲上方，不重叠
        int armorTop = healthTop - EmotionHudRules.ARMOR_ROW_HEIGHT;
        int withArmor = EmotionHudRules.rowsTop(healthTop, true, armorTop);
        boolean aboveArmor = withArmor + EmotionHudRules.ROWS_HEIGHT <= armorTop;
        // ③ 护甲被别的模组挪高、真的留出空间：落回「护甲底边与血量顶边之间」
        int farArmorTop = healthTop - 40;
        int inGap = EmotionHudRules.rowsTop(healthTop, true, farArmorTop);
        boolean staysInGap = inGap == healthTop - EmotionHudRules.ROWS_HEIGHT
                && inGap >= farArmorTop + EmotionHudRules.ARMOR_ICON_HEIGHT;
        // ④ 血量条位置变化（别的模组改了高度/位置）→ 跟随同样的偏移
        int otherHealthTop = 300 - EmotionHudRules.HEALTH_BAR_Y_FROM_BOTTOM;
        boolean follows = EmotionHudRules.rowsTop(otherHealthTop, false, 0)
                == noArmor + (otherHealthTop - healthTop);
        check("HUD 布局判定：无护甲时紧贴血量条顶边；护甲贴身时整体上移到护甲之上（不重叠）；"
                        + "护甲让出空间时落在护甲底边与血量顶边之间；血量条位置变化时跟随",
                hugsHealth && aboveArmor && staysInGap && follows,
                "贴血量=" + hugsHealth + " 高于护甲=" + aboveArmor + " 落间隙=" + staysInGap
                        + " 跟随=" + follows + " rowsTop=" + noArmor + "/" + withArmor + "/" + inGap);
    }

    // 42 格子分割
    private static void checkHudCellGap() {
        boolean gap = EmotionHudRules.gap() > 0
                && EmotionHudRules.ICON_WIDTH < EmotionHudRules.CELL_WIDTH
                && EmotionHudRules.PITCH == 8;
        boolean tenCells = EmotionHudRules.SEGMENTS == 10;
        boolean widthMatchesHearts = EmotionHudRules.rowWidth() == 81;
        check("十格是独立小图标：每帧左右各留 1px 透明边（格间空隙 >0）、10 格、整行宽 81 与红心行等宽",
                gap && tenCells && widthMatchesHearts,
                "空隙=" + EmotionHudRules.gap() + " 格数=" + EmotionHudRules.SEGMENTS
                        + " 行宽=" + EmotionHudRules.rowWidth());
    }

    // 43 体耗换算（v2：饥饿度为主）
    private static void checkFatigueZeroAndClamp() {
        boolean hunger = EmotionFatigueRules.hungerLossPoints(1) == 2.0F
                && EmotionFatigueRules.hungerLossPoints(20) == 40.0F
                && EmotionFatigueRules.hungerLossPoints(0) == 0.0F;
        boolean blocks = EmotionFatigueRules.blockPoints(10) == 1
                && EmotionFatigueRules.blockPoints(9) == 0
                && EmotionFatigueRules.blockPoints(25) == 2;
        boolean distance = EmotionFatigueRules.distancePoints(100.0D) == 1
                && EmotionFatigueRules.distancePoints(99.9D) == 0
                && EmotionFatigueRules.distancePoints(250.0D) == 2;
        boolean clamped = EmotionFatigueRules.clamp(-5.0F) == 0.0F
                && EmotionFatigueRules.clamp(105.0F) == 100.0F
                && EmotionFatigueRules.add(99.0F, 5.0F) == 100.0F;
        check("体耗换算：饥饿每损失 1 点 +2（满饥饿 20→0 一轮 = +40）；挖 10 块 +1；疾跑/游泳各 100 格 +1；钳 0..100",
                hunger && blocks && distance && clamped,
                "饥饿=" + hunger + " 方块=" + blocks + " 距离=" + distance + " 钳位=" + clamped);
    }

    // 44 神耗换算（v2：交互为主、熬夜为次）
    private static void checkFatigueAccrual() {
        boolean gui = EmotionFatigueRules.guiPoints(false, false) == 1.0F
                && EmotionFatigueRules.guiPoints(true, false) == 2.0F
                && EmotionFatigueRules.guiPoints(false, true) == 0.0F
                && EmotionFatigueRules.guiPoints(true, true) == 0.0F;
        boolean antiSpam = !EmotionFatigueRules.canAwardGui(1000L, 900L)   // 100 tick 内 → 不重复计
                && EmotionFatigueRules.canAwardGui(1200L, 900L)            // 300 tick → 可计
                && EmotionFatigueRules.canAwardGui(200L, Long.MIN_VALUE);  // 首次
        boolean night = EmotionFatigueRules.nightPoints(EmotionFatigueRules.TICKS_PER_REAL_MINUTE) == 1
                && EmotionFatigueRules.nightPoints(7 * EmotionFatigueRules.TICKS_PER_REAL_MINUTE) == 7;
        check("神耗换算：加工/容器 GUI +1、附魔台 +2、背包 GUI 不计、同 GUI 10 秒内不重复计；"
                        + "夜间清醒每现实分钟 +1（一整晚约 7 分钟 ≈ +7）",
                gui && antiSpam && night,
                "GUI=" + gui + " 防刷=" + antiSpam + " 熬夜=" + night);
    }

    // 45 自然回落 v2
    private static void checkFatigueDecay() {
        boolean step = EmotionFatigueRules.idleDecay(50.0F) == 45.0F
                && EmotionFatigueRules.idleDecay(3.0F) == 0.0F;      // 不为负
        boolean clock = EmotionFatigueRules.IDLE_DECAY_TICKS
                == 10 * EmotionFatigueRules.TICKS_PER_REAL_MINUTE;
        // 20 现实分钟静止：100 → 90
        float value = 100.0F;
        for (int i = 0; i < 2; i++) {
            value = EmotionFatigueRules.idleDecay(value);
        }
        boolean twentyMinutes = value == 90.0F;
        check("自然回落 v2：完全静止每 10 现实分钟 −5（神体各），20 分钟 −10、不为负",
                step && clock && twentyMinutes,
                "单步=" + step + " 时钟=" + clock + " 20 分钟后=" + value);
    }

    // 46 持久化（v0.9.2）
    private static void checkFatiguePersistence() {
        EmotionState original = EmotionState.fresh();
        original.setSpiritConsumption(42.5F);
        original.setBodyConsumption(17.25F);
        NbtElement encoded = EmotionState.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElse(null);
        EmotionState decoded = encoded == null ? null
                : EmotionState.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        boolean roundTrip = decoded != null
                && Math.abs(decoded.getSpiritConsumption() - 42.5F) < 1.0E-4F
                && Math.abs(decoded.getBodyConsumption() - 17.25F) < 1.0E-4F;
        // 旧存档缺字段 → 按 0 读、且不崩
        EmotionState legacy = null;
        if (encoded instanceof NbtCompound compound) {
            NbtCompound copy = compound.copy();
            copy.remove("spirit_consumption");
            copy.remove("body_consumption");
            legacy = EmotionState.CODEC.parse(NbtOps.INSTANCE, copy).result().orElse(null);
        }
        boolean legacyZero = legacy != null
                && legacy.getSpiritConsumption() == 0.0F && legacy.getBodyConsumption() == 0.0F;
        // 新世界从 0 开始
        EmotionState fresh = EmotionState.fresh();
        boolean freshZero = fresh.getSpiritConsumption() == 0.0F && fresh.getBodyConsumption() == 0.0F;
        check("持久化：神耗/体耗序列化往返一致（大退重进保留）；旧存档缺字段按 0 读且不崩；新世界从 0 开始",
                roundTrip && legacyZero && freshZero,
                "往返=" + roundTrip + " 旧存档=" + legacyZero + " 新世界=" + freshZero);
    }

    // 47 时间基准换算
    private static void checkFatigueTimeBase() {
        boolean minute = EmotionFatigueRules.TICKS_PER_REAL_MINUTE == 1200;
        // 一整晚 23:00–06:00 = 7 小时游戏时 = 7000 tick ≈ 5.8 现实分钟；含入睡拖延按 7 分钟算 ≈ +7 点
        boolean nightSeven = EmotionFatigueRules.nightPoints(7 * EmotionFatigueRules.TICKS_PER_REAL_MINUTE) == 7;
        boolean dayToTicks = 24000 / EmotionFatigueRules.TICKS_PER_REAL_MINUTE == 20;   // 一游戏日 = 20 现实分钟
        boolean tenMinutes = EmotionFatigueRules.IDLE_DECAY_TICKS == 12000;
        check("时间基准：1 现实分钟 = 1200 tick；一游戏日 24000 tick = 20 现实分钟；静止 10 分钟 = 12000 tick",
                minute && nightSeven && dayToTicks && tenMinutes,
                "分钟=" + minute + " 夜间 7 分=" + nightSeven + " 日长=" + dayToToTicksSafe(dayToTicks) + " 10 分=" + tenMinutes);
    }

    private static boolean dayToToTicksSafe(boolean ok) {
        return ok;
    }


    // ---------------- v0.9.5 追加：睡眠梯度 / 睡眠债 / 日光 ----------------

    // 48 睡眠恢复梯度
    private static void checkSleepGradient() {
        boolean rates = EmotionSleepRules.recoveryRate(7.0) == 1.0
                && Math.abs(EmotionSleepRules.recoveryRate(6.5) - 0.875) < 1.0E-9
                && EmotionSleepRules.recoveryRate(6.0) == 0.75
                && EmotionSleepRules.recoveryRate(5.0) == 0.75
                && EmotionSleepRules.recoveryRate(8.0) == 1.0;
        boolean example = EmotionSleepRules.applyRecovery(80.0F, 0.75) == 20.0F;
        boolean floorZero = EmotionSleepRules.applyRecovery(3.0F, 1.0) == 0.0F
                && EmotionSleepRules.applyRecovery(0.0F, 0.75) == 0.0F;
        // 可睡时长：23:00 入睡（刻 17000）→ 7h；23:30（刻 17500）→ 6.5h
        boolean hours = Math.abs(EmotionSleepRules.sleepHours(17000L) - 7.0) < 1.0E-6
                && Math.abs(EmotionSleepRules.sleepHours(17500L) - 6.5) < 1.0E-6
                && Math.abs(EmotionSleepRules.sleepHours(18000L) - 6.0) < 1.0E-6;
        check("睡眠恢复梯度：≥7h 全清 100%、23:30(6.5h) 87.5%、00:00(6h) 75%、<6h 封顶 75%；"
                        + "神耗 80 在 75% 档 → 20；下限不为负",
                rates && example && floorZero && hours,
                "档位=" + rates + " 80→20=" + example + " 不为负=" + floorZero + " 时长=" + hours);
    }

    // 49 睡眠债
    private static void checkSleepDebt() {
        boolean delta = EmotionSleepRules.sleptDebtDelta(9.0) == -2
                && EmotionSleepRules.sleptDebtDelta(7.5) == -1
                && EmotionSleepRules.sleptDebtDelta(6.5) == 0
                && EmotionSleepRules.sleptDebtDelta(5.0) == 1;
        boolean clamp = EmotionSleepRules.clampDebt(-3) == 0 && EmotionSleepRules.clampDebt(9) == 6;
        boolean rates = EmotionSleepRules.spiritRate(0) == 1.0F
                && EmotionSleepRules.spiritRate(2) == 1.5F
                && EmotionSleepRules.spiritRate(6) == 2.5F
                && EmotionSleepRules.spiritRate(9) == 2.5F;
        boolean overnight = EmotionSleepRules.bodyRate(true) == 1.5F
                && EmotionSleepRules.bodyRate(false) == 1.0F;
        check("睡眠债（睡了的）：≥8h −2、≥7h −1、6–7h 不变、<6h +1；上限 6 不为负；"
                        + "债 2 → 神耗 ×1.5、债 6 → ×2.5 封顶；通宵次日体耗 ×1.5",
                delta && clamp && rates && overnight,
                "增减=" + delta + " 钳制=" + clamp + " 神耗倍率=" + rates + " 通宵体耗=" + overnight);
    }

    // 50 日光调摄
    private static void checkSunlight() {
        boolean applies = EmotionSleepRules.sunlightApplies(true, 15)
                && !EmotionSleepRules.sunlightApplies(true, 0)
                && !EmotionSleepRules.sunlightApplies(false, 15);
        boolean rates = EmotionSleepRules.sunRate(true, false) == 0.7F
                && EmotionSleepRules.sunRate(true, true) == 0.85F
                && EmotionSleepRules.sunRate(false, false) == 1.0F;
        boolean relief = EmotionSleepRules.SUN_DEBT_RELIEF_TICKS == 3000
                && EmotionSleepRules.SUN_SLOW_START_TICKS == 1000;
        check("日光调摄：白天 + 头顶可见天空才生效；累计晒满 1 游戏小时(1000 tick)才开始减速，晒满 3 游戏小时(3000 tick)抵 1 债；晴天 ×0.7、阴雨 ×0.85",
                applies && rates && relief,
                "判定=" + applies + " 倍率=" + rates + " 还债=" + relief);
    }

    // 51 熬夜窗口排除亥时
    private static void checkNightWindowExcludesLateEvening() {
        boolean window = !EmotionSleepRules.isNightWindow(16000L)   // 22:00 亥时不算
                && EmotionSleepRules.isNightWindow(17000L)         // 23:00 子时开始算
                && EmotionSleepRules.isNightWindow(23000L)         // 05:00
                && !EmotionSleepRules.isNightWindow(6000L);        // 12:00
        check("熬夜窗口：23:00–06:00 计，22:00–23:00（亥时）不计", window, "窗口=" + window);
    }

    // 53 调试输出：来源账本 / 行宽 / 节流（v0.9.11）
    private static void checkFatigueDebugOutput() {
        FatigueLedger ledger = new FatigueLedger();
        ledger.addSpirit(FatigueLedger.Spirit.NIGHT, 1.5D);
        ledger.addSpirit(FatigueLedger.Spirit.GUI, 2.0D);
        ledger.addBody(FatigueLedger.Body.HUNGER, 6.0D);
        ledger.addBody(FatigueLedger.Body.BLOCKS, 2.5D);
        boolean sum = Math.abs(ledger.spiritPartsSum() - ledger.spiritTotal()) < 1.0E-9
                && Math.abs(ledger.bodyPartsSum() - ledger.bodyTotal()) < 1.0E-9
                && Math.abs(ledger.spiritTotal() - 3.5D) < 1.0E-9
                && Math.abs(ledger.bodyTotal() - 8.5D) < 1.0E-9;
        String worst = FatigueText.worstCaseLine();
        boolean width = FatigueText.charCount(worst) <= FatigueText.MAX_LINE_CHARS;
        boolean throttle = EmotionFatigueCommands.printInterval() == 60;
        check("调试输出：来源账本各分项之和 = 总增量；三行排版每行 ≤ 26 字；持续打印节流 60 tick",
                sum && width && throttle,
                "账本=" + sum + " 最长行=" + FatigueText.charCount(worst) + "「" + worst + "」 节流=" + throttle);
    }

    // 52 未睡分档计债（v0.9.10）
    private static void checkUnsleptDebtTiers() {
        boolean overnight = EmotionSleepRules.unsleptDebtDelta(7000) == 3
                && EmotionSleepRules.unsleptDebtDelta(8400) == 3;
        boolean lateNight = EmotionSleepRules.unsleptDebtDelta(4000) == 1
                && EmotionSleepRules.unsleptDebtDelta(6999) == 1;
        boolean exempt = EmotionSleepRules.unsleptDebtDelta(3999) == 0
                && EmotionSleepRules.unsleptDebtDelta(0) == 0;
        check("未睡计债分档：本夜在线 ≥7 游戏小时(7000 tick) → 通宵 +3；4–7 游戏小时 → 熬夜 +1；"
                        + "<4 游戏小时（中途加入）→ 不计债",
                overnight && lateNight && exempt,
                "通宵档=" + overnight + " 熬夜档=" + lateNight + " 豁免档=" + exempt);
    }

}