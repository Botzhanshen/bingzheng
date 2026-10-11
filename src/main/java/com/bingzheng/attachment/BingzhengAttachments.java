package com.bingzheng.attachment;

import com.bingzheng.BingzhengMod;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * 本模组的自定义玩家数据 —— 全部走 Fabric 的 <b>Attachment（附属数据）</b>机制。
 *
 * <p>这样做是为了严格满足约束 1：<b>绝不改写原版玩家的任何数据域</b>。
 * 营卫值不存在于原版玩家 NBT 的任何字段里，而是挂在 {@code bingzhengmod:yingwei}
 * 这个本模组自己的持久化附件上，重进世界依然保留。
 *
 * <p>//TODO 后续迁移配置文件：默认值、上下限都应改为从配置读取。
 */
public final class BingzhengAttachments {

    /** 营卫值下限。 */
    public static final int YINGWEI_MIN = 0;
    /** 营卫值上限。 */
    public static final int YINGWEI_MAX = 100;
    /** 营卫值初始值（新玩家 / 从未设置过时）。 */
    public static final int YINGWEI_DEFAULT = 50;

    /**
     * 「营卫」——本模组自定义的生存数值，与原版生命/饥饿/体温完全无关。
     *
     * <p>使用 {@code createPersistent} + {@link Codec#INT}，因此会随玩家存档一起持久化。
     */
    public static final AttachmentType<Integer> YINGWEI =
            AttachmentRegistry.createPersistent(BingzhengMod.id("yingwei"), Codec.INT);

    private BingzhengAttachments() {
    }

    /** 触发本类静态初始化（注册 Attachment 类型）。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】附属数据注册完成。");
    }

    /** 读取营卫值；从未设置过时自动初始化为默认值。 */
    public static int getYingwei(LivingEntity entity) {
        Integer value = entity.getAttachedOrCreate(YINGWEI, () -> YINGWEI_DEFAULT);
        return MathHelper.clamp(value, YINGWEI_MIN, YINGWEI_MAX);
    }

    /** 写入营卫值（自动夹取到合法区间）。 */
    public static void setYingwei(LivingEntity entity, int value) {
        entity.setAttached(YINGWEI, MathHelper.clamp(value, YINGWEI_MIN, YINGWEI_MAX));
    }

    /** 增减营卫值，返回变化后的数值。 */
    public static int addYingwei(LivingEntity entity, int delta) {
        int next = MathHelper.clamp(getYingwei(entity) + delta, YINGWEI_MIN, YINGWEI_MAX);
        setYingwei(entity, next);
        return next;
    }
}
