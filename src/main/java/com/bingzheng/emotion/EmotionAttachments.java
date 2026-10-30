package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;

/**
 * 情志拓展的持久化附件。
 *
 * <p>与内核的 {@code bingzhengmod:yingwei} <b>完全隔离</b>，互不影响。
 */
public final class EmotionAttachments {

    /** 情志状态附件。与营卫附件平级、独立存储。 */
    public static final AttachmentType<EmotionState> EMOTION =
            AttachmentRegistry.createPersistent(BingzhengMod.id("emotion"), EmotionState.CODEC);

    private EmotionAttachments() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证·情志】附件注册完成。");
    }

    /** 读取情志状态；从未设置过时初始化为新玩家状态。 */
    public static EmotionState get(LivingEntity entity) {
        return entity.getAttachedOrCreate(EMOTION, EmotionState::fresh);
    }

    /** 写入情志状态。务必传入 {@link EmotionState#copy()} 得到的副本。 */
    public static void set(LivingEntity entity, EmotionState state) {
        entity.setAttached(EMOTION, state);
    }
}
