package com.bingzheng.wuzang;

import com.bingzheng.protect.MilkSafeEffects;
import com.mojang.serialization.DataResult;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 五脏拓展的开发期自检（只在 {@code -Dfabric.development=true} 时运行）。
 *
 * <p>断言的是 {@link WuzangRules} 里的<b>决策规则本身</b>，而不是几个配置常量；
 * 业务代码调用的是同一批函数，所以「被测的就是在跑的」。
 *
 * <p>无需世界、无需玩家，模组启动时即可跑完。
 *
 * <p><b>唯一的包外引用</b>是 {@link MilkSafeEffects}（全局牛奶保护层）。
 * 它是跨模块的公共基础设施而不是某个业务模块的代码，且这里只用它的公开只读 API
 * 来证明五脏的 20 个效果确实受到牛奶保护。
 */
public final class WuzangSelfCheck {

    private static int passed;
    private static int failed;
    private static final List<String> FAILURES = new ArrayList<>();

    private WuzangSelfCheck() {
    }

    public static void run() {
        if (!Boolean.getBoolean("fabric.development")) {
            return;
        }
        passed = 0;
        failed = 0;
        FAILURES.clear();
        Wuzang.LOGGER.info("【病证·五脏自检】开始，共 23 项。");

        checkInitialValues();
        checkCodecRoundTrip();
        checkClamp();
        checkThresholdBoundaries();
        checkNoBloodExcess();
        checkBothDeficient();
        checkMarkerDerivation();
        checkRestoreDirection();
        checkRestoreNoOvershoot();
        checkRestoreMultipliers();
        checkBloodLossPriority();
        checkBloodLossAllocation();
        checkEmotionSignalMapping();
        checkLinkSafety();
        checkEffectNamespaces();
        checkMilkProtection();
        checkHooksAreNoops();
        checkConfigSanity();
        checkShichenPhaseTable();
        checkShichenTargetCurve();
        checkRhythmApproach();
        checkHysteresis();
        checkWaveNotJudged();

        for (String f : FAILURES) {
            Wuzang.LOGGER.error("【病证·五脏自检】[FAIL] {}", f);
        }
        Wuzang.LOGGER.info("【病证·五脏自检】完成：通过 {} 项，失败 {} 项。", passed, failed);
        if (failed > 0) {
            Wuzang.LOGGER.error("【病证·五脏自检】存在 {} 项失败！", failed);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            Wuzang.LOGGER.info("【病证·五脏自检】[OK] {}", name);
        } else {
            failed++;
            FAILURES.add(name + " —— " + detail);
        }
    }

    // 1 初始值
    private static void checkInitialValues() {
        WuzangState s = WuzangState.fresh();
        boolean ok = true;
        for (WuzangType type : WuzangType.values()) {
            ok &= s.qiOf(type) == WuzangConfig.START && s.bloodOf(type) == WuzangConfig.START;
            ok &= !s.isImbalanced(type);
        }
        check("新玩家五脏气血全部为平人基线 " + (int) WuzangConfig.START + "，且无任何虚实标记", ok,
                "初始值不是 " + WuzangConfig.START);
    }

    // 2 持久化：Codec 往返一致
    private static void checkCodecRoundTrip() {
        WuzangState original = WuzangState.fresh();
        original.setQi(WuzangType.LIVER, 95.5F);
        original.setQi(WuzangType.HEART, 12.25F);
        original.setBlood(WuzangType.KIDNEY, 33.75F);
        original.setBlood(WuzangType.SPLEEN, 18.0F);

        DataResult<NbtElement> encoded = WuzangState.CODEC.encodeStart(NbtOps.INSTANCE, original);
        NbtElement nbt = encoded.result().orElse(null);
        WuzangState decoded = nbt == null ? null
                : WuzangState.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(null);

        boolean ok = decoded != null;
        if (decoded != null) {
            for (WuzangType type : WuzangType.values()) {
                ok &= Math.abs(decoded.qiOf(type) - original.qiOf(type)) < 0.001F;
                ok &= Math.abs(decoded.bloodOf(type) - original.bloodOf(type)) < 0.001F;
                ok &= decoded.isQiDeficient(type) == original.isQiDeficient(type);
                ok &= decoded.isBloodDeficient(type) == original.isBloodDeficient(type);
                ok &= decoded.isQiExcess(type) == original.isQiExcess(type);
            }
        }
        check("持久化 Codec 往返一致（五脏气血 10 个浮点 + 三类虚实标记）", ok,
                "解码结果与原文不一致或编解码失败");
    }

    // 3 钳位
    private static void checkClamp() {
        WuzangState s = WuzangState.fresh();
        s.setQi(WuzangType.LIVER, 999.0F);
        float hi = s.qiOf(WuzangType.LIVER);
        s.setQi(WuzangType.LIVER, -50.0F);
        float lo = s.qiOf(WuzangType.LIVER);
        s.setBlood(WuzangType.HEART, 500.0F);
        check("气血取值钳位在 0-100", hi == WuzangConfig.MAX && lo == WuzangConfig.MIN
                        && s.bloodOf(WuzangType.HEART) == WuzangConfig.MAX,
                "hi=" + hi + " lo=" + lo);
    }

    // 4 阈值边界
    private static void checkThresholdBoundaries() {
        boolean ok = WuzangRules.deficient(WuzangConfig.DEFICIENT_THRESHOLD - 0.1F)
                && !WuzangRules.deficient(WuzangConfig.DEFICIENT_THRESHOLD)
                && !WuzangRules.excess(WuzangConfig.EXCESS_THRESHOLD)
                && WuzangRules.excess(WuzangConfig.EXCESS_THRESHOLD + 0.1F);
        check("阈值判定：<40 为虚、>90 为亢（边界值 40 不虚、90 不亢）", ok,
                "虚阈值=" + WuzangConfig.DEFICIENT_THRESHOLD + " 亢阈值=" + WuzangConfig.EXCESS_THRESHOLD);
    }

    // 5 血无「亢」
    private static void checkNoBloodExcess() {
        boolean noXueKang = true;
        int bloodRelated = 0;
        for (String path : WuzangEffects.allPaths()) {
            if (path.endsWith("xue_kang")) {
                noXueKang = false;
            }
            if (path.contains("xue")) {
                bloodRelated++;
            }
        }
        // 每脏两个与血有关的效果：xue_xu、qi_xue_liang_xu
        boolean countOk = bloodRelated == WuzangType.COUNT * 2;
        check("血脉只有亏虚没有「血亢」：血相关效果仅 血虚 / 气血两虚 两类", noXueKang && countOk,
                "出现 xue_kang=" + !noXueKang + " 血相关条数=" + bloodRelated);
    }

    // 6 气血两虚
    private static void checkBothDeficient() {
        boolean ok = WuzangRules.bothDeficient(39.0F, 39.0F)
                && !WuzangRules.bothDeficient(39.0F, 40.0F)
                && !WuzangRules.bothDeficient(40.0F, 39.0F)
                && !WuzangRules.bothDeficient(80.0F, 80.0F);
        check("气血两虚 = 同脏气与血同时判虚", ok, "组合判定不符");
    }

    // 7 标记派生
    private static void checkMarkerDerivation() {
        WuzangState s = WuzangState.fresh();
        s.setQi(WuzangType.LIVER, 30.0F);           // 肝气虚
        s.setBlood(WuzangType.HEART, 20.0F);        // 心血虚
        s.setQi(WuzangType.LUNG, 95.0F);            // 肺气亢
        s.setQi(WuzangType.SPLEEN, 25.0F);
        s.setBlood(WuzangType.SPLEEN, 25.0F);       // 脾气血两虚
        boolean ok = s.isQiDeficient(WuzangType.LIVER) && !s.isBloodDeficient(WuzangType.LIVER)
                && s.isBloodDeficient(WuzangType.HEART) && !s.isQiExcess(WuzangType.HEART)
                && s.isQiExcess(WuzangType.LUNG) && !s.isQiDeficient(WuzangType.LUNG)
                && s.isBothDeficient(WuzangType.SPLEEN)
                && s.imbalancedCount() == 4;
        check("单脏与组合标记按数值正确派生（含气血两虚、失衡计数）", ok,
                "失衡数=" + s.imbalancedCount() + " 摘要=" + s.markerSummary());
    }

    // 8 自然恢复方向
    private static void checkRestoreDirection() {
        float near = WuzangRules.restoreStep(70.0F, WuzangConfig.RESTORE_STEP);   // 距离 10
        float mid = WuzangRules.restoreStep(60.0F, WuzangConfig.RESTORE_STEP);    // 距离 20 = 参考距离
        float far = WuzangRules.restoreStep(20.0F, WuzangConfig.RESTORE_STEP);    // 距离 60
        float down = WuzangRules.restoreStep(95.0F, WuzangConfig.RESTORE_STEP);   // 高于基线 → 回落
        float at = WuzangRules.restoreStep(WuzangConfig.BASELINE, WuzangConfig.RESTORE_STEP);

        float moveNear = near - 70.0F;
        float moveMid = mid - 60.0F;
        float moveFar = far - 20.0F;
        boolean direction = moveNear > 0.0F && moveMid > 0.0F && moveFar > 0.0F && down < 95.0F
                && at == WuzangConfig.BASELINE;
        // v1.05：「偏离越远，回落/回升越明显」——步长随距离单调增大
        boolean monotoneInDistance = moveMid > moveNear && moveFar > moveMid;
        // 偏离正好等于参考距离时，步长就是基准步长（归一化点）
        boolean referencePoint = Math.abs(moveMid - WuzangConfig.RESTORE_STEP) < 0.001F;
        check("恢复曲线：低于基线回升、高于基线回落、已在基线不动；且**偏离越远步长越大**（v1.05 语义）",
                direction && monotoneInDistance && referencePoint,
                "近=" + moveNear + " 中=" + moveMid + " 远=" + moveFar + " 回落=" + down + " 基线=" + at);
    }

    // 9 恢复不过冲
    private static void checkRestoreNoOvershoot() {
        // 巨步长：直接正好归位，不会冲到基线另一侧
        float a = WuzangRules.restoreStep(79.9F, 10000.0F);
        float b = WuzangRules.restoreStep(80.1F, 10000.0F);
        float c = WuzangRules.restoreStep(20.0F, 10000.0F);
        // 小步长：结果必须落在「当前值与基线之间」，绝不越过
        float d = WuzangRules.restoreStep(79.99F, WuzangConfig.RESTORE_STEP);
        float e = WuzangRules.restoreStep(80.01F, WuzangConfig.RESTORE_STEP);
        boolean ok = a == WuzangConfig.BASELINE && b == WuzangConfig.BASELINE && c == WuzangConfig.BASELINE
                && d > 79.99F && d <= WuzangConfig.BASELINE
                && e < 80.01F && e >= WuzangConfig.BASELINE;
        check("恢复绝不过冲：巨步长正好归位，小步长始终停在当前值与基线之间", ok,
                "a=" + a + " b=" + b + " c=" + c + " d=" + d + " e=" + e);
    }

    // 10 静坐 / 睡眠倍率
    private static void checkRestoreMultipliers() {
        float normal = WuzangRules.restoreMultiplier(false, false);
        float sit = WuzangRules.restoreMultiplier(true, false);
        float sleep = WuzangRules.restoreMultiplier(false, true);
        boolean ok = normal == 1.0F && sit > normal && sleep > sit;
        check("静坐加速恢复、睡眠大幅加速（睡眠 &gt; 静坐 &gt; 常态）", ok,
                "normal=" + normal + " sit=" + sit + " sleep=" + sleep);
    }

    // 11 失血优先次序
    private static void checkBloodLossPriority() {
        WuzangType[] order = WuzangRules.bloodLossPriority();
        boolean ok = order.length == WuzangType.COUNT
                && order[0] == WuzangType.HEART
                && order[1] == WuzangType.LIVER;
        check("受伤失血优先耗损心、肝之血", ok, "次序前缀=" + order[0] + "," + order[1]);
    }

    // 12 失血分摊
    private static void checkBloodLossAllocation() {
        float[] full = { 100.0F, 100.0F, 100.0F, 100.0F, 100.0F };
        float[] alloc = WuzangRules.allocateBloodLoss(full, 150.0F);
        boolean ok = Math.abs(alloc[WuzangType.HEART.index()] - 100.0F) < 0.001F
                && Math.abs(alloc[WuzangType.LIVER.index()] - 50.0F) < 0.001F
                && alloc[WuzangType.SPLEEN.index()] == 0.0F
                && alloc[WuzangType.LUNG.index()] == 0.0F
                && alloc[WuzangType.KIDNEY.index()] == 0.0F;
        float[] low = { 10.0F, 10.0F, 10.0F, 10.0F, 10.0F };
        float[] all = WuzangRules.allocateBloodLoss(low, 100.0F);
        float sum = 0.0F;
        for (float v : all) {
            sum += v;
        }
        boolean ok2 = Math.abs(sum - 50.0F) < 0.001F;
        float[] none = WuzangRules.allocateBloodLoss(low, 0.0F);
        boolean ok3 = none[0] == 0.0F && none[4] == 0.0F;
        check("失血分摊：先扣心肝、血不够时不扣成负数、扣不满就扣完为止", ok && ok2 && ok3,
                "alloc=" + alloc[0] + "/" + alloc[1] + " sum=" + sum);
    }

    // 13 情志 → 脏腑 对照表
    private static void checkEmotionSignalMapping() {
        List<WuzangLinks.EmotionSignal> signals = WuzangLinks.emotionSignals();
        boolean ok = signals.size() == 5;
        ok &= hasSignal(signals, "gan_qi_kang", WuzangType.LIVER, true);
        ok &= hasSignal(signals, "xin_shen_yang", WuzangType.HEART, true);
        ok &= hasSignal(signals, "pi_yi_chan", WuzangType.SPLEEN, false);
        ok &= hasSignal(signals, "fei_qi_xiao", WuzangType.LUNG, false);
        ok &= hasSignal(signals, "shen_qi_she", WuzangType.KIDNEY, false);
        // v1.05 附加量：久怒耗肝血（血降）、过喜伤心神（神耗升）、惊恐耗神（神耗升）
        ok &= bloodSign(signals, "gan_qi_kang") < 0;
        ok &= spiritSign(signals, "xin_shen_yang") > 0;
        ok &= spiritSign(signals, "shen_qi_she") > 0;
        // 思/悲 文档里没有附加量，不得凭空多出气血或神耗
        ok &= bloodSign(signals, "pi_yi_chan") == 0 && spiritSign(signals, "pi_yi_chan") == 0;
        ok &= bloodSign(signals, "fei_qi_xiao") == 0 && spiritSign(signals, "fei_qi_xiao") == 0;
        // 文档没有给「忧」「惊」安排脏腑，因此这两条信号不得存在
        boolean noExtra = true;
        for (WuzangLinks.EmotionSignal signal : signals) {
            String path = signal.effectPath();
            if ("fei_qi_yu".equals(path) || "shen_zha_yao".equals(path)) {
                noExtra = false;
            }
        }
        check("情志 → 脏腑对照表严格照 v1.05：怒升肝气+耗肝血、喜升心气+耗神、思耗脾气、悲耗肺气、恐耗肾气+耗神（忧/惊不补）",
                ok && noExtra, "条数=" + signals.size() + " 无额外项=" + noExtra);
    }

    /** 取某条信号的血变化符号（+1 / -1 / 0，0 也表示该信号不存在）。 */
    private static int bloodSign(List<WuzangLinks.EmotionSignal> signals, String path) {
        for (WuzangLinks.EmotionSignal signal : signals) {
            if (signal.effectPath().equals(path)) {
                return Float.compare(signal.bloodDelta(), 0.0F);
            }
        }
        return 0;
    }

    /** 取某条信号的神耗变化符号（+1 / -1 / 0）。 */
    private static int spiritSign(List<WuzangLinks.EmotionSignal> signals, String path) {
        for (WuzangLinks.EmotionSignal signal : signals) {
            if (signal.effectPath().equals(path)) {
                return Float.compare(signal.spiritDelta(), 0.0F);
            }
        }
        return 0;
    }

    private static boolean hasSignal(List<WuzangLinks.EmotionSignal> signals, String path,
                                     WuzangType zang, boolean positive) {
        for (WuzangLinks.EmotionSignal signal : signals) {
            if (signal.effectPath().equals(path)) {
                return signal.zang() == zang && (signal.qiDelta() > 0) == positive;
            }
        }
        return false;
    }

    // 14 链接容错（模块缺失时安全）
    private static void checkLinkSafety() {
        boolean safe = WuzangLinks.byPath("__definitely_not_a_real_effect__") == null;
        // 只断言「查不到也不炸」；情志在不在场属于可选信息，仅打印，不作为通过条件 ——
        // 否则「不装情志也能跑」这条就被自己的自检破坏了。
        boolean present = WuzangLinks.byPath(WuzangLinks.MEDITATING_PATH) != null;
        check("链接层查表失败只返回 null、不抛异常（情志缺失时整段静默失效，五脏可独立运行）",
                safe, "safe=" + safe + " ｜ 本次运行情志在场=" + present);
    }

    // 15 命名空间
    private static void checkEffectNamespaces() {
        int bad = 0;
        for (String path : WuzangEffects.allPaths()) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(Wuzang.id(path));
            Identifier id = effect == null ? null : Registries.STATUS_EFFECT.getId(effect);
            if (id == null || !Wuzang.MOD_ID.equals(id.getNamespace()) || !path.equals(id.getPath())) {
                bad++;
            }
        }
        check("20 个五脏表现层效果全部注册在 " + Wuzang.MOD_ID + ": 命名空间下", bad == 0,
                "有 " + bad + " 个异常");
    }

    // 16 牛奶保护
    private static void checkMilkProtection() {
        int bad = 0;
        for (String path : WuzangEffects.allPaths()) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(Wuzang.id(path));
            if (effect == null || !MilkSafeEffects.isProtected(effect)) {
                bad++;
            }
        }
        check("20 个五脏效果全部落在牛奶保护层内（饮用牛奶无法清除表现层 buff）", bad == 0,
                "有 " + bad + " 个未被保护");
    }

    // 17 钩子全空
    private static void checkHooksAreNoops() {
        check("全部跨拓展 / 病症联动钩子都还是默认空实现（本阶段无实际执行逻辑）",
                WuzangHooks.allDefaults(), "已有模块安装了实现");
    }

    // 18 配置自洽
    private static void checkConfigSanity() {
        check("配置自洽（0 &lt; 虚阈值 &lt; 迟滞上沿 &lt; 基线 &lt; 迟滞下沿 &lt; 亢阈值 &lt; 100；时辰表正好铺满一天；"
                        + "变速系数恒在 (0,1)）",
                WuzangRules.configSane(),
                "阈值/基线/倍率/时辰表/变速系数互相矛盾：虚" + WuzangConfig.DEFICIENT_THRESHOLD
                        + "→" + WuzangConfig.DEFICIENT_RELEASE + " 基线" + WuzangConfig.BASELINE
                        + " 亢" + WuzangConfig.EXCESS_THRESHOLD + "→" + WuzangConfig.EXCESS_RELEASE);
    }

    // 19 时辰相位表（子午流注）
    private static void checkShichenPhaseTable() {
        boolean mapping = WuzangShichen.sovereignShichen(WuzangType.LIVER) == 1      // 丑时肝
                && WuzangShichen.sovereignShichen(WuzangType.HEART) == 6             // 午时心
                && WuzangShichen.sovereignShichen(WuzangType.SPLEEN) == 5            // 巳时脾
                && WuzangShichen.sovereignShichen(WuzangType.LUNG) == 2              // 寅时肺
                && WuzangShichen.sovereignShichen(WuzangType.KIDNEY) == 9;           // 酉时肾
        boolean distinct = true;
        for (WuzangType a : WuzangType.values()) {
            for (WuzangType b : WuzangType.values()) {
                if (a != b && WuzangShichen.sovereignShichen(a) == WuzangShichen.sovereignShichen(b)) {
                    distinct = false;
                }
            }
        }
        // 时间轴锚点：MC 0 刻 = 06:00（卯时）；6000 刻 = 12:00（午时，当令心）
        boolean anchors = WuzangShichen.index(0L) == 3
                && "卯".equals(WuzangShichen.name(0L))
                && "午".equals(WuzangShichen.name(6000L))
                && WuzangShichen.sovereignAt(6000L) == WuzangType.HEART
                && WuzangShichen.sovereignAt(0L) == null
                && WuzangShichen.isSovereign(WuzangType.HEART, 6000L);
        // 时辰名表、负时间回绕、跨天回绕
        boolean wrap = WuzangShichen.names().length == WuzangConfig.SHICHEN_COUNT
                && "子".equals(WuzangShichen.names()[0])
                && WuzangShichen.index(-1L) == WuzangShichen.index(23999L)
                && WuzangShichen.index(24000L) == WuzangShichen.index(0L);
        check("时辰相位表照子午流注：肝丑、心午、脾巳、肺寅、肾酉，五个当令时辰互不相同；"
                        + "MC 0 刻＝卯时、6000 刻＝午时（当令心）",
                mapping && distinct && anchors && wrap,
                "映射=" + mapping + " 互异=" + distinct + " 锚点=" + anchors + " 回绕=" + wrap);
    }

    // 20 时辰目标曲线
    private static void checkShichenTargetCurve() {
        boolean ok = true;
        StringBuilder detail = new StringBuilder();
        for (WuzangType type : WuzangType.values()) {
            int sovereign = WuzangShichen.sovereignShichen(type);
            int opposite = WuzangShichen.oppositeShichen(type);
            float peak = WuzangShichen.targetOffsetForSegment(type, sovereign);
            float trough = WuzangShichen.targetOffsetForSegment(type, opposite);
            float max = Float.NEGATIVE_INFINITY;
            float min = Float.POSITIVE_INFINITY;
            float sum = 0.0F;
            for (int seg = 0; seg < WuzangConfig.SHICHEN_COUNT; seg++) {
                float v = WuzangShichen.targetOffsetForSegment(type, seg);
                max = Math.max(max, v);
                min = Math.min(min, v);
                sum += v;
            }
            ok &= Math.abs(peak - WuzangConfig.RHYTHM_AMPLITUDE) < 1.0E-4F;
            ok &= Math.abs(trough + WuzangConfig.RHYTHM_AMPLITUDE) < 1.0E-4F;
            ok &= Math.abs(max - peak) < 1.0E-4F && Math.abs(min - trough) < 1.0E-4F;
            ok &= Math.abs(sum) < 1.0E-3F;   // 十二段正负相抵：不会把整条曲线整体抬高或压低
            detail.append(type.zhName()).append(':').append(peak).append('/').append(trough).append(' ');
        }
        check("时辰目标曲线：当令时辰恰为 +振幅、对冲时辰恰为 −振幅，且分别是该脏一天中的最高/最低点；"
                        + "十二段相抵不产生整体偏移",
                ok, detail.toString());
    }

    // 21 变速到达
    private static void checkRhythmApproach() {
        float target = WuzangConfig.RHYTHM_AMPLITUDE;

        // ① 位移 = 距目标距离 × 系数：同一系数下距离越远位移越大 —— 即「不是匀速线性插值」
        float moveNear = WuzangRules.approach(target - 1.0F, target, 0.1F, 0.0F) - (target - 1.0F);
        float moveFar = WuzangRules.approach(target - 4.0F, target, 0.1F, 0.0F) - (target - 4.0F);
        boolean proportional = Math.abs(moveNear - 0.1F) < 1.0E-4F
                && Math.abs(moveFar - 0.4F) < 1.0E-4F && moveFar > moveNear;

        // ② 软着陆：越接近目标，单 tick 位移越小（严格递减）
        float[] distances = { 4.0F, 2.0F, 1.0F, 0.5F, 0.1F };
        float last = Float.MAX_VALUE;
        boolean decelerating = true;
        for (float d : distances) {
            float move = WuzangRules.approach(target - d, target, 0.2F, 0.0F) - (target - d);
            decelerating &= move > 0.0F && move < last;
            last = move;
        }

        // ③ 固定随机样本序列下跑 4000 tick：单调逼近、一次都没越过目标、最终收敛
        float v = -WuzangConfig.RHYTHM_AMPLITUDE;
        boolean monotone = true;
        for (int i = 0; i < 4000; i++) {
            double sample = (i * 37 % 100) / 100.0D;
            float factor = WuzangRules.approachFactor(WuzangConfig.RHYTHM_SPEED,
                    WuzangConfig.RHYTHM_SPEED_JITTER, sample, WuzangConfig.RHYTHM_SPEED_MIN);
            float next = WuzangRules.approach(v, target, factor);
            monotone &= next >= v - 1.0E-6F && next <= target + 1.0E-6F;
            v = next;
        }
        boolean converged = Math.abs(v - target) <= WuzangConfig.RHYTHM_MARGIN + 1.0E-5F;

        // ④ 速度系数恒在 (0,1)：这是「不振荡」的结构性保证（≥1 就会一步跨过目标再跨回来）
        boolean factorSafe = true;
        for (int i = 0; i <= 100; i++) {
            float factor = WuzangRules.approachFactor(WuzangConfig.RHYTHM_SPEED,
                    WuzangConfig.RHYTHM_SPEED_JITTER, i / 100.0D, WuzangConfig.RHYTHM_SPEED_MIN);
            factorSafe &= factor > 0.0F && factor < 1.0F;
        }
        // 就算传一个荒谬的大系数，也被上限拦住，仍然跨不过目标
        boolean capped = WuzangRules.approach(0.0F, 10.0F, 100.0F, 0.0F) < 10.0F;

        check("变速到达：位移＝距目标距离×随机化系数（不匀速）、越近越慢（软着陆）、"
                        + "4000 tick 内收敛且从未越过目标；系数恒在 (0,1) 内（结构上不可能振荡）",
                proportional && decelerating && monotone && converged && factorSafe && capped,
                "等比=" + proportional + " 减速=" + decelerating + " 单调=" + monotone
                        + " 收敛=" + converged + " 终点=" + v + " 系数安全=" + factorSafe);
    }

    // 22 阈值迟滞
    private static void checkHysteresis() {
        boolean qiTrigger = !WuzangRules.deficientHysteresis(41.0F, false)
                && !WuzangRules.deficientHysteresis(WuzangConfig.DEFICIENT_THRESHOLD, false)
                && WuzangRules.deficientHysteresis(39.9F, false);
        boolean qiRelease = WuzangRules.deficientHysteresis(41.0F, true)
                && WuzangRules.deficientHysteresis(44.9F, true)
                && !WuzangRules.deficientHysteresis(WuzangConfig.DEFICIENT_RELEASE, true);
        boolean kangTrigger = !WuzangRules.excessHysteresis(WuzangConfig.EXCESS_THRESHOLD, false)
                && WuzangRules.excessHysteresis(90.1F, false);
        boolean kangRelease = WuzangRules.excessHysteresis(85.1F, true)
                && !WuzangRules.excessHysteresis(WuzangConfig.EXCESS_RELEASE, true);

        // 迟滞带内来回走：只要没碰到解除线，标记就一直保持 —— 这就是「不闪断」
        WuzangState s = WuzangState.fresh();
        boolean noFlap = true;
        s.setQi(WuzangType.LIVER, 39.5F);
        noFlap &= s.isQiDeficient(WuzangType.LIVER);
        for (float v : new float[] { 41.0F, 43.0F, 44.9F, 42.0F, 40.5F, 44.0F }) {
            s.setQi(WuzangType.LIVER, v);
            noFlap &= s.isQiDeficient(WuzangType.LIVER);
        }
        s.setQi(WuzangType.LIVER, WuzangConfig.DEFICIENT_RELEASE);
        noFlap &= !s.isQiDeficient(WuzangType.LIVER);
        // 气血两虚由两个迟滞标记组合而成
        s.setQi(WuzangType.LIVER, 39.0F);
        s.setBlood(WuzangType.LIVER, 39.0F);
        noFlap &= s.isBothDeficient(WuzangType.LIVER);
        s.setQi(WuzangType.LIVER, 46.0F);
        noFlap &= !s.isBothDeficient(WuzangType.LIVER);
        // 亢的迟滞同样在状态里生效
        s.setQi(WuzangType.HEART, 95.0F);
        noFlap &= s.isQiExcess(WuzangType.HEART);
        s.setQi(WuzangType.HEART, 86.0F);
        noFlap &= s.isQiExcess(WuzangType.HEART);
        s.setQi(WuzangType.HEART, 84.0F);
        noFlap &= !s.isQiExcess(WuzangType.HEART);

        check("阈值改为迟滞区间：虚 &lt;40 触发、≥45 解除；亢 &gt;90 触发、≤85 解除；"
                        + "在迟滞带内来回走标记不闪断；气血两虚按两个迟滞标记组合",
                qiTrigger && qiRelease && kangTrigger && kangRelease && noFlap,
                "虚触发=" + qiTrigger + " 虚解除=" + qiRelease + " 亢触发=" + kangTrigger
                        + " 亢解除=" + kangRelease + " 不闪断=" + noFlap);
    }

    // 23 波动与抖动不参与判定
    private static void checkWaveNotJudged() {
        // 稳定值 43（未虚）被负向波动 + 抖动拉到 38.7：现值已低于虚阈值，但仍不判虚
        WuzangState s = WuzangState.fresh();
        s.setQi(WuzangType.LIVER, 43.0F);
        float low = WuzangRules.displayValue(43.0F, -WuzangConfig.RHYTHM_AMPLITUDE, -0.3F);
        boolean lowCrosses = low < WuzangConfig.DEFICIENT_THRESHOLD
                && !s.isQiDeficient(WuzangType.LIVER);
        // 稳定值 88（未亢）被正向波动推到 92.3：现值已越过亢阈值，但仍不判亢
        WuzangState t = WuzangState.fresh();
        t.setQi(WuzangType.HEART, 88.0F);
        float high = WuzangRules.displayValue(88.0F, WuzangConfig.RHYTHM_AMPLITUDE, 0.3F);
        boolean highCrosses = high > WuzangConfig.EXCESS_THRESHOLD
                && !t.isQiExcess(WuzangType.HEART);
        // 现值仍被钳在 0..100
        boolean clamped = WuzangRules.displayValue(99.0F, WuzangConfig.RHYTHM_AMPLITUDE, 0.3F) == WuzangConfig.MAX
                && WuzangRules.displayValue(1.0F, -WuzangConfig.RHYTHM_AMPLITUDE, -0.3F) == WuzangConfig.MIN;
        // 抖动零均值、每次重掷而不是累加（所以不会漂移）
        float amp = WuzangConfig.RHYTHM_JITTER_AMPLITUDE;
        boolean zeroMean = Math.abs(WuzangRules.jitter(0.0D, amp) + WuzangRules.jitter(1.0D, amp)) < 1.0E-6F
                && Math.abs(WuzangRules.jitter(0.5D, amp)) < 1.0E-6F
                && Math.abs(WuzangRules.jitter(1.0D, amp) - amp) < 1.0E-6F;
        check("时辰波动与微观抖动只进「现值」：稳定值 43 的现值可低到 38.7、稳定值 88 的现值可高到 92.3，"
                        + "虚实标记都不动；现值仍钳在 0..100",
                lowCrosses && highCrosses && clamped && zeroMean,
                "低=" + low + " 未虚=" + !s.isQiDeficient(WuzangType.LIVER)
                        + " 高=" + high + " 未亢=" + !t.isQiExcess(WuzangType.HEART));
    }
}
