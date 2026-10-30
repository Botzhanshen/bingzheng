package com.bingzheng.wuzang;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 五脏对外的<b>预留钩子层</b> —— 全部是空实现，本次构建<b>不含任何实际逻辑</b>。
 *
 * <p>覆盖文档记载的、但本阶段明确不实现的联动关系：
 * <ul>
 *   <li>与核心包：肺气虚寒者更易反复风寒感冒；脾胃弱则感冒恢复更慢</li>
 *   <li>与天癸拓展：肾精亏虚压低肾精周期上限</li>
 *   <li>与目窍拓展：肝血亏虚诱发肝虚目惑证</li>
 *   <li>与骨节拓展：肾精亏虚加重骨质疏松、骨折恢复变慢</li>
 *   <li>与皮腠拓展：肺气虚寒降低皮肤屏障</li>
 *   <li>与机能拓展：多脏失衡是全身机能衰竭的主要诱因</li>
 *   <li>与情志拓展的双向病症联动：心火亢盛加重躁郁证、肝郁气滞使郁证加重、神耗过高反噬肝心</li>
 * </ul>
 *
 * <p>所有默认实现都是空方法体。自检会断言「没有任何模块安装过实现」，
 * 以此证明本阶段确实<b>没有实际执行逻辑</b>。将来对应拓展接入时，只需
 * {@code installXxx(...)} 装上自己的实现，五脏这边一行都不用改。
 */
public final class WuzangHooks {

    private WuzangHooks() {
    }

    /** 与核心包（肺 / 脾）。 */
    public interface CorePackage {
        default void onLungQiDeficient(ServerPlayerEntity player) {
        }

        default void onSpleenQiDeficient(ServerPlayerEntity player) {
        }
    }

    /** 与天癸拓展（肾精）。 */
    public interface Tiangui {
        default void onKidneyEssenceDeficient(ServerPlayerEntity player) {
        }
    }

    /** 与目窍拓展（肝血）。 */
    public interface EyeOrifice {
        default void onLiverBloodDeficient(ServerPlayerEntity player) {
        }
    }

    /** 与骨节拓展（肾主骨）。 */
    public interface BoneJoint {
        default void onKidneyEssenceDeficient(ServerPlayerEntity player) {
        }
    }

    /** 与皮腠拓展（肺主皮毛）。 */
    public interface SkinPores {
        default void onLungQiDeficient(ServerPlayerEntity player) {
        }
    }

    /** 与机能拓展（多脏失衡）。 */
    public interface Functions {
        default void onMultiZangImbalance(ServerPlayerEntity player, int imbalancedCount) {
        }
    }

    /** 与情志拓展的双向病症联动（文档记载，仅预留）。 */
    public interface EmotionFeedback {
        default void onHeartFireExcess(ServerPlayerEntity player) {
        }

        default void onLiverQiStagnation(ServerPlayerEntity player) {
        }

        default void onSpiritConsumptionOverload(ServerPlayerEntity player) {
        }
    }

    private static final CorePackage NONE_CORE = new CorePackage() {
    };
    private static final Tiangui NONE_TIANGUI = new Tiangui() {
    };
    private static final EyeOrifice NONE_EYE = new EyeOrifice() {
    };
    private static final BoneJoint NONE_BONE = new BoneJoint() {
    };
    private static final SkinPores NONE_SKIN = new SkinPores() {
    };
    private static final Functions NONE_FUNCTIONS = new Functions() {
    };
    private static final EmotionFeedback NONE_FEEDBACK = new EmotionFeedback() {
    };

    private static CorePackage core = NONE_CORE;
    private static Tiangui tiangui = NONE_TIANGUI;
    private static EyeOrifice eyeOrifice = NONE_EYE;
    private static BoneJoint boneJoint = NONE_BONE;
    private static SkinPores skinPores = NONE_SKIN;
    private static Functions functions = NONE_FUNCTIONS;
    private static EmotionFeedback emotionFeedback = NONE_FEEDBACK;

    public static CorePackage core() {
        return core;
    }

    public static Tiangui tiangui() {
        return tiangui;
    }

    public static EyeOrifice eyeOrifice() {
        return eyeOrifice;
    }

    public static BoneJoint boneJoint() {
        return boneJoint;
    }

    public static SkinPores skinPores() {
        return skinPores;
    }

    public static Functions functions() {
        return functions;
    }

    public static EmotionFeedback emotionFeedback() {
        return emotionFeedback;
    }

    // ---------------- 安装点（将来由对应拓展调用） ----------------

    public static void installCore(CorePackage impl) {
        core = impl == null ? NONE_CORE : impl;
    }

    public static void installTiangui(Tiangui impl) {
        tiangui = impl == null ? NONE_TIANGUI : impl;
    }

    public static void installEyeOrifice(EyeOrifice impl) {
        eyeOrifice = impl == null ? NONE_EYE : impl;
    }

    public static void installBoneJoint(BoneJoint impl) {
        boneJoint = impl == null ? NONE_BONE : impl;
    }

    public static void installSkinPores(SkinPores impl) {
        skinPores = impl == null ? NONE_SKIN : impl;
    }

    public static void installFunctions(Functions impl) {
        functions = impl == null ? NONE_FUNCTIONS : impl;
    }

    public static void installEmotionFeedback(EmotionFeedback impl) {
        emotionFeedback = impl == null ? NONE_FEEDBACK : impl;
    }

    /** 是否全部钩子都还是默认空实现（自检用：证明本阶段没有实际联动逻辑）。 */
    public static boolean allDefaults() {
        return core == NONE_CORE
                && tiangui == NONE_TIANGUI
                && eyeOrifice == NONE_EYE
                && boneJoint == NONE_BONE
                && skinPores == NONE_SKIN
                && functions == NONE_FUNCTIONS
                && emotionFeedback == NONE_FEEDBACK;
    }
}
