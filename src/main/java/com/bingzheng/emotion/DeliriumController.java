package com.bingzheng.emotion;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.Locale;
import java.util.UUID;

/**
 * 【神扰】的全部行为实现。
 *
 * <p>约束遵循：
 * <ul>
 *   <li>所有属性改动都走 {@link EntityAttributeModifier} 临时修饰器，
 *       <b>绝不直接改写原版 maxHealth / movementSpeed / attackSpeed 的底层字段</b>。</li>
 *   <li>攻速在计算完成后调用原版 {@code EntityAttribute#clamp} 钳位
 *       （这正是「Minecraft 原版攻击速度硬上限」的官方表达方式）。</li>
 *   <li>生命上限的扣除使用 {@code MULTIPLY_TOTAL = -0.5}，天然满足「最多扣一半」的硬上限。</li>
 *   <li>神扰与静坐的优先级：静坐时增益与干扰全部暂停，但计时器照常流逝。</li>
 * </ul>
 */
public final class DeliriumController {

    // 三个独立 UUID，保证修饰器可以精确增删而不会误伤其它模组的修饰器
    private static final UUID SPEED_ID = UUID.fromString("1a2b3c4d-0001-4000-8000-000000000001");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("1a2b3c4d-0002-4000-8000-000000000002");
    private static final UUID MAX_HEALTH_ID = UUID.fromString("1a2b3c4d-0003-4000-8000-000000000003");

    private static final String SPEED_NAME = "bingzhengmod.delirium.speed";
    private static final String ATTACK_SPEED_NAME = "bingzhengmod.delirium.attack_speed";
    private static final String MAX_HEALTH_NAME = "bingzhengmod.delirium.max_health";

    private DeliriumController() {
    }

    /**
     * 神扰的每 tick 处理。
     *
     * @param damaged 本 tick 玩家是否掉血（由血量差识别，纯观察）
     * @return 情志数据是否发生变化
     */
    public static boolean tick(ServerPlayerEntity player, EmotionState state, long tick, boolean damaged) {
        boolean dirty = false;
        boolean hasEffect = player.hasStatusEffect(EmotionEffects.SHEN_RAO);
        boolean sitting = MeditationService.isSitting(player);

        // ---- 状态迁移 ----
        if (hasEffect && !state.isDeliriumActive()) {
            begin(player, state);
            dirty = true;
        } else if (!hasEffect && state.isDeliriumActive()) {
            // 兜底：正常情况下 ShenRaoEffect.onRemoved 已经在同一 tick 处理过了。
            // 这条分支覆盖「重登后效果已消失」「drain 削到 0」等 onRemoved 可能没走到的路径。
            onDeliriumEnded(player);
            dirty = true;
        }
        if (!hasEffect) {
            return dirty;
        }

        // ---- 时间外部交互：受伤害追加；火邪也会持续追加 ----
        if (damaged) {
            extend(player, EmotionConfig.DELIRIUM_EXTEND_ON_DAMAGE);
        }
        if (tick % 20 == 0 && EmotionHooks.pathogen().isFireAfflicted(player)) {
            extend(player, EmotionConfig.DELIRIUM_EXTEND_ON_FIRE / 20);
        }

        // ---- 后台数值：神耗上涨 + 随机扰动七情 ----
        if (!sitting && tick % EmotionConfig.DELIRIUM_SPIRIT_RISE_INTERVAL == 0) {
            state.addSpiritConsumption(EmotionConfig.DELIRIUM_SPIRIT_RISE);
            // v0.9.11：记进来源账本（只影响调试输出的归类，不改任何判定）
            EmotionFatigue.noteSpirit(player, FatigueLedger.Spirit.DELIRIUM, EmotionConfig.DELIRIUM_SPIRIT_RISE);
            dirty = true;
        }
        if (tick % EmotionConfig.DELIRIUM_EMOTION_JITTER_INTERVAL == 0) {
            jitter(player, state);
            dirty = true;
        }

        // ---- 静坐拥有最高豁免优先级：增益与干扰全部暂停 ----
        // 注意：必须同时清掉疾跑标记，否则「强制疾跑」会一直粘在玩家身上（旧版漏掉了这一步）
        if (sitting) {
            clearModifiers(player);
            player.setSprinting(false);
            return dirty;
        }

        // ---- 行为干扰 ----
        player.setSprinting(true);                                   // 强制疾跑，玩家无法手动关闭
        player.addExhaustion(EmotionConfig.DELIRIUM_EXHAUSTION_PER_TICK);  // 饥饿持续流失
        if (tick % EmotionConfig.DELIRIUM_SHIFT_INTERVAL == 0) {
            randomShift(player);                                     // 偶尔跑偏
        }

        // ---- 属性增益：每秒重算一次，以便随病邪动态缩放 ----
        if (tick % 20 == 0) {
            applyModifiers(player);
        }
        clampHealth(player);
        return dirty;
    }

    // ---------------- 起止 ----------------

    private static void begin(ServerPlayerEntity player, EmotionState state) {
        state.setDeliriumActive(true);
        player.removeStatusEffect(EmotionEffects.SHEN_AN);   // 强制互斥
        applyModifiers(player);
        EmotionHooks.crossModule().onDeliriumStart(player);
        EmotionHooks.crossModule().onRecoveryPaused(player, true);
        player.sendMessage(Text.translatable("message.bingzhengmod.delirium_start"), false);
    }

    /**
     * 神扰<b>真正结束</b>的唯一处理入口，由两处调用、且**幂等**（{@code deliriumActive} 守卫）：
     * <ol>
     *   <li>{@link ShenRaoEffect#onRemoved} —— 神扰被移除的那一刻（自然到期 / 指令清除 / 图腾）；</li>
     *   <li>{@link #tick} 的状态迁移兜底 —— 覆盖 onRemoved 没走到的路径。</li>
     * </ol>
     *
     * <p>顺序按数据流来：先还属性，再衰减高位情绪，最后启动两个计时器。
     */
    public static void onDeliriumEnded(ServerPlayerEntity player) {
        EmotionState state = EmotionAttachments.get(player);
        if (!state.isDeliriumActive()) {
            return;   // 已经处理过，直接返回（幂等）
        }
        state.setDeliriumActive(false);

        // 属性归还
        clearModifiers(player);
        clampHealth(player);

        // ① 高位情绪衰减：只打「仍然站在阈值之上」的那些，低于阈值的原样保留（绝不归零）
        decayHighEmotions(player, state);

        // ② 两个计时器各司其职：冷却只拦神扰再生成，心绪不宁只封神安 + 心悸抖动
        state.setCooldownDelirium(EmotionConfig.DELIRIUM_COOLDOWN_TICKS);
        state.setRestlessTicks(EmotionConfig.RESTLESS_TICKS);

        EmotionAttachments.set(player, state);
        EmotionHooks.crossModule().onRecoveryPaused(player, false);
        EmotionHooks.crossModule().onDeliriumEnd(player);
        player.sendMessage(Text.translatable("message.bingzhengmod.delirium_end"), false);
    }

    /**
     * 把仍然站在扰动阈值之上的情绪乘系数打下去。
     *
     * <p><b>不归零</b>：低于阈值的情绪一个都不动，超阈值的也只是乘系数，
     * 残余留给心绪不宁阶段的心悸抖动。
     *
     * @return 被打下去的情绪条数
     */
    public static int decayHighEmotions(ServerPlayerEntity player, EmotionState state) {
        int count = 0;
        StringBuilder detail = new StringBuilder();
        for (EmotionType type : EmotionType.values()) {
            float current = state.emotion(type);
            float next = EmotionRules.endOfDeliriumDecay(current);
            if (next == current) {
                continue;
            }
            if (detail.length() > 0) {
                detail.append('、');
            }
            detail.append(type.zhName()).append(' ').append(fmt(current)).append('→').append(fmt(next));
            state.setEmotion(type, next);
            count++;
        }
        if (count > 0) {
            // 仅在 emotion_debug 打开时输出，帮助人工核对「确实降下来了、且没归零」
            EmotionDebugRecorder.onDeliriumEnded(player, detail.toString(),
                    EmotionConfig.DELIRIUM_END_EMOTION_DECAY);
        }
        return count;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 追加神扰剩余时间，受全局硬封顶约束。 */
    public static void extend(ServerPlayerEntity player, int ticks) {
        if (ticks <= 0) {
            return;
        }
        StatusEffectInstance instance = player.getStatusEffect(EmotionEffects.SHEN_RAO);
        if (instance == null) {
            return;
        }
        int next = EmotionRules.extendDelirium(instance.getDuration(), ticks);
        if (next <= instance.getDuration()) {
            return;   // 已到硬封顶
        }
        // 续期用的是「移除 + 重加」，必须用重入标记挡住 onRemoved，
        // 否则玩家每挨一次打都会被当成一次「神扰结束」→ 白送一次高位情绪衰减。
        ShenRaoEffect.beginReissue();
        try {
            player.removeStatusEffect(EmotionEffects.SHEN_RAO);
            player.addStatusEffect(new StatusEffectInstance(EmotionEffects.SHEN_RAO, next, instance.getAmplifier()));
        } finally {
            ShenRaoEffect.endReissue();
        }
    }

    /**
     * 减去神扰剩余时间（唯一允许主动缩短的途径是静坐）。
     *
     * <p>由于 {@code StatusEffectInstance} 没有公开的 setDuration，
     * 只能「移除 + 按新时长重加」。
     */
    public static void drain(ServerPlayerEntity player, int ticks) {
        StatusEffectInstance instance = player.getStatusEffect(EmotionEffects.SHEN_RAO);
        if (instance == null) {
            return;
        }
        int next = EmotionRules.drainDelirium(instance.getDuration(), ticks);
        if (next <= 0) {
            // 被静坐削到 0 = 神扰真正结束：不加重入标记，让 onRemoved 正常启动衰减与计时器
            player.removeStatusEffect(EmotionEffects.SHEN_RAO);
            return;
        }
        ShenRaoEffect.beginReissue();
        try {
            player.removeStatusEffect(EmotionEffects.SHEN_RAO);
            player.addStatusEffect(new StatusEffectInstance(EmotionEffects.SHEN_RAO, next, instance.getAmplifier()));
        } finally {
            ShenRaoEffect.endReissue();
        }
    }

    // ---------------- 属性 ----------------

    private static void applyModifiers(ServerPlayerEntity player) {
        double multiplier = EmotionHooks.attributeMultiplier(player);

        // 移速：临时修饰器，MULTIPLY_BASE
        setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, SPEED_NAME,
                EmotionConfig.DELIRIUM_SPEED_BONUS * multiplier);

        // 攻速：先算总量，再用原版 clamp 钳位，最后反推修饰器数值
        applyAttackSpeed(player, EmotionConfig.DELIRIUM_ATTACK_SPEED_BONUS * multiplier);

        // 生命上限：MULTIPLY_TOTAL = -0.5，最多扣一半
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.removeModifier(MAX_HEALTH_ID);
            health.addPersistentModifier(new EntityAttributeModifier(MAX_HEALTH_ID, MAX_HEALTH_NAME,
                    EmotionRules.maxHealthPenalty(),
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            clampHealth(player);
        }
    }

    private static void setModifier(ServerPlayerEntity player,
                                    net.minecraft.entity.attribute.EntityAttribute attribute,
                                    UUID id, String name, double value) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (value != 0.0D) {
            instance.addPersistentModifier(new EntityAttributeModifier(
                    id, name, value, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void applyAttackSpeed(ServerPlayerEntity player, double rawBonus) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        instance.removeModifier(ATTACK_SPEED_ID);
        double base = instance.getValue();                 // 不含本模组修饰器时的总值

        double bonus = EmotionRules.attackSpeedBonus(base, rawBonus);
        if (bonus > 0.0D) {
            instance.addPersistentModifier(new EntityAttributeModifier(ATTACK_SPEED_ID, ATTACK_SPEED_NAME,
                    bonus, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void clearModifiers(ServerPlayerEntity player) {
        removeModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID);
        removeModifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED_ID);
        removeModifier(player, EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH_ID);
    }

    private static void removeModifier(ServerPlayerEntity player,
                                       net.minecraft.entity.attribute.EntityAttribute attribute, UUID id) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    /** 生命上限被扣后把当前血量钳到新上限，避免出现「超出上限」的异常状态。 */
    private static void clampHealth(ServerPlayerEntity player) {
        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    // ---------------- 行为干扰 ----------------

    private static void randomShift(ServerPlayerEntity player) {
        Random random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        player.addVelocity(
                Math.cos(angle) * EmotionConfig.DELIRIUM_SHIFT_STRENGTH,
                0.0D,
                Math.sin(angle) * EmotionConfig.DELIRIUM_SHIFT_STRENGTH);
        player.velocityModified = true;
    }

    /** 后台随机扰动 1-2 项七情；水寒病证会压低扰动幅度。 */
    private static void jitter(ServerPlayerEntity player, EmotionState state) {
        Random random = player.getRandom();
        double multiplier = EmotionHooks.attributeMultiplier(player);
        float scale = (float) Math.min(1.0D, multiplier);   // 只压低，不放大
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            EmotionType type = EmotionType.byIndex(random.nextInt(EmotionType.COUNT));
            float delta = (random.nextFloat() * 2.0F - 1.0F)
                    * EmotionConfig.DELIRIUM_EMOTION_JITTER * scale;
            state.addEmotion(type, delta);
        }
    }
}
