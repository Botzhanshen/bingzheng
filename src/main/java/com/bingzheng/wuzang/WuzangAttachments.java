package com.bingzheng.wuzang;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;

/**
 * 五脏拓展的持久化附件 {@code bingzhengmod:wuzang}。
 *
 * <p>与内核的 {@code bingzhengmod:yingwei}、情志的 {@code bingzhengmod:emotion}
 * 平级、独立存储，互不影响。
 *
 * <p><b>这是五脏的唯一底层数据源</b>：状态效果只是它的表现层。
 */
public final class WuzangAttachments {

    /**
     * 五脏气血与虚实标记的持久化附件。
     *
     * <p><b>{@code copyOnDeath()}（v0.4.1 决策）</b>：死亡复活时数值<b>原样保留</b>，
     * 不会重置回平人基线 80。依据 Fabric 自己的实现
     * （{@code AttachmentTargetImpl#transfer(original, target, isDeath)}：
     * {@code if (!isDeath || type.copyOnDeath())}），玩家复活时只有标记了
     * {@code copyOnDeath} 的附件才会被搬到新实体上。
     *
     * <p>语义上这与情志模块<b>刻意不同</b>：七情、神耗体耗、神扰计时都是「一时状态」，
     * 死亡即清；而五脏气血是玩家的「体质底子」，应当跨死亡延续。
     */
    public static final AttachmentType<WuzangState> WUZANG = AttachmentRegistry
            .<WuzangState>builder()
            .persistent(WuzangState.CODEC)
            .copyOnDeath()
            .buildAndRegister(Wuzang.id("wuzang"));

    private WuzangAttachments() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        Wuzang.LOGGER.debug("【病证·五脏】附件注册完成。");
    }

    /** 读取五脏状态；从未设置过时初始化为平人状态。 */
    public static WuzangState get(LivingEntity entity) {
        return entity.getAttachedOrCreate(WUZANG, WuzangState::fresh);
    }

    /** 写入五脏状态。 */
    public static void set(LivingEntity entity, WuzangState state) {
        entity.setAttached(WUZANG, state);
    }
}
