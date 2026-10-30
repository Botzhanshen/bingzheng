package com.bingzheng.wuzang.behavior;

import com.bingzheng.wuzang.WuzangConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 情志拓展的<b>权威数据源软链接</b>（神耗 / 体耗）。
 *
 * <p><b>为什么用反射而不是直接 import：</b>五脏要能独立拆包 —— 情志不装时本类必须
 * 「静默失效」而不是「类找不到直接崩」。用反射只读情志的<b>公开成员</b>
 * （{@code EmotionAttachments.EMOTION} 这个 public 附件类型 +
 * {@code EmotionState.getSpiritConsumption()/getBodyConsumption()} 这两个 public 方法），
 * 就能做到「零编译期依赖」，且情志模块一行都不用改。
 *
 * <p>解析失败（情志未装载 / 结构变了）只会让 {@link #present()} 为 false，
 * 于是三项行为继续走内置兜底，不会抛异常、不会崩。
 *
 * <p><b>顶替范围</b>：神耗 → 熬夜、压力；体耗 → 过劳。
 * 七情信号不在这里 —— 它专属「七情行」的冲量通道，避免同源双算。
 */
public final class EmotionAuthority {

    private static final String ATTACHMENTS_CLASS = "com.bingzheng.emotion.EmotionAttachments";
    private static final String STATE_CLASS = "com.bingzheng.emotion.EmotionState";
    private static final String EMOTION_FIELD = "EMOTION";
    private static final String SPIRIT_GETTER = "getSpiritConsumption";
    private static final String BODY_GETTER = "getBodyConsumption";
    private static final String SERVICE_CLASS = "com.bingzheng.emotion.EmotionService";
    private static final String ADD_SPIRIT_METHOD = "addSpiritConsumption";

    private static boolean resolved;
    private static Object emotionAttachmentType;
    private static Method spiritGetter;
    private static Method bodyGetter;
    private static Method addSpiritMethod;

    private EmotionAuthority() {
    }

    /** 解析一次情志的公开成员；失败即视为未装载。幂等。 */
    public static void probe() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> attachments = Class.forName(ATTACHMENTS_CLASS);
            Field field = attachments.getField(EMOTION_FIELD);
            emotionAttachmentType = field.get(null);

            Class<?> state = Class.forName(STATE_CLASS);
            spiritGetter = state.getMethod(SPIRIT_GETTER);
            bodyGetter = state.getMethod(BODY_GETTER);

            // 写入方向（v1.05：过喜 / 惊恐耗神）——同样是公开方法，反射调用
            addSpiritMethod = Class.forName(SERVICE_CLASS)
                    .getMethod(ADD_SPIRIT_METHOD, ServerPlayerEntity.class, float.class);
        } catch (Throwable ignored) {
            emotionAttachmentType = null;
            spiritGetter = null;
            bodyGetter = null;
            addSpiritMethod = null;
        }
    }

    /** 情志拓展是否在场且数据可读。 */
    public static boolean present() {
        probe();
        return emotionAttachmentType != null && spiritGetter != null && bodyGetter != null;
    }

    /**
     * 读取一份情志数值。
     *
     * @return 数值；读不到（未装载 / 该玩家还没有附件）返回 -1
     */
    private static float read(ServerPlayerEntity player, Method getter) {
        if (!present()) {
            return -1.0F;
        }
        try {
            Object state = attached(player);
            if (state == null) {
                return -1.0F;
            }
            Object value = getter.invoke(state);
            return value instanceof Float f ? f : -1.0F;
        } catch (Throwable ignored) {
            return -1.0F;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object attached(ServerPlayerEntity player) {
        return player.getAttached((AttachmentType) emotionAttachmentType);
    }

    /** 神耗；读不到返回 -1。 */
    public static float spirit(ServerPlayerEntity player) {
        return read(player, spiritGetter);
    }

    /** 体耗；读不到返回 -1。 */
    public static float body(ServerPlayerEntity player) {
        return read(player, bodyGetter);
    }

    // ---------------- 写入方向：神耗 ----------------

    /** 是否可以向情志的神耗写数（情志在场且方法可用）。 */
    public static boolean canAddSpirit() {
        probe();
        return addSpiritMethod != null;
    }

    /**
     * 给情志的神耗加一笔（文档：过喜伤心神、惊恐耗神）。
     *
     * <p><b>为什么由五脏来写</b>：这两个附加量是「七情剧烈波动」的后果，而波动信号由五脏监听；
     * 情志模块对此没有自己的入口，且它处于冻结状态。走的是它的<b>公开</b>方法
     * {@code EmotionService.addSpiritConsumption}，五脏这边零编译期依赖。
     *
     * <p><b>不会自激</b>：神耗是「熬夜 / 压力」两项的顶替数据源，但那两项的产物是五脏气血，
     * 不再回头写神耗；只有七情冲量这一条路会写神耗，方向单一。
     *
     * <p>情志不在场时静默忽略（单开五脏时没有任何东西可写）。
     */
    public static void addSpirit(ServerPlayerEntity player, float delta) {
        if (delta == 0.0F || !canAddSpirit()) {
            return;
        }
        try {
            addSpiritMethod.invoke(null, player, delta);
        } catch (Throwable ignored) {
            // 写不进去就放弃：这不影响五脏自身的数值
        }
    }

    // ---------------- 顶替安装 ----------------

    /**
     * 情志在场时，把「消耗型行为」的权威数据源顶替为它的神耗 / 体耗。
     *
     * <p>v0.7.0（强度点口径）的顶替关系：
     * <ul>
     *   <li><b>熬夜</b> ← 神耗 ≥ {@link WuzangConfig#STAY_UP_SPIRIT_ACTIVE} 视为「熬夜 / 透支」态；</li>
     *   <li><b>压力</b> ← 直接读神耗数值分档（60–80 / &gt;80）；</li>
     *   <li><b>过劳</b> ← 体耗 ≥ {@link WuzangConfig#OVERWORK_BODY_ACTIVE} 视为「劳作」态。</li>
     * </ul>
     *
     * <p>受凉与暴食的权威源分别属于温湿拓展、消化拓展（尚不存在），因此不在这里安装。
     * 情志不在场则什么都不装，各项继续走内置兜底。
     */
    public static void installProviders() {
        if (!present()) {
            return;
        }
        WuzangBehaviorRegistry.install(WuzangBehavior.STAY_UP, new BehaviorProvider() {
            @Override
            public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
                float value = spirit(player);
                boolean active = value >= 0.0F && value >= WuzangConfig.STAY_UP_SPIRIT_ACTIVE;
                return BehaviorProvider.Accrual.perBlock(
                        active ? WuzangConfig.STAY_UP_POINTS_PER_BLOCK : 0.0D);
            }

            @Override
            public String sourceName() {
                return "情志拓展·神耗（顶替）";
            }
        });
        WuzangBehaviorRegistry.installPressure(new PressureSource() {
            @Override
            public double value(ServerPlayerEntity player) {
                return spirit(player);
            }

            @Override
            public String sourceName() {
                return "情志拓展·神耗（顶替）";
            }
        });
        WuzangBehaviorRegistry.install(WuzangBehavior.OVERWORK, new BehaviorProvider() {
            @Override
            public BehaviorProvider.Accrual accrual(ServerPlayerEntity player) {
                float value = body(player);
                boolean active = value >= 0.0F && value >= WuzangConfig.OVERWORK_BODY_ACTIVE;
                return BehaviorProvider.Accrual.perBlock(
                        active ? WuzangConfig.OVERWORK_POINTS_PER_BLOCK : 0.0D);
            }

            @Override
            public String sourceName() {
                return "情志拓展·体耗（顶替）";
            }
        });
    }
}
