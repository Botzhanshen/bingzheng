package com.bingzheng.registry;

import com.bingzheng.BingzhengMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/**
 * 本模组自己的创造模式物品栏。
 *
 * <p>刻意新建独立物品栏，而不是往原版物品栏里塞东西，以符合「不侵入原版」的约束。
 */
public final class BingzhengItemGroups {

    public static final ItemGroup HERBS = Registry.register(
            Registries.ITEM_GROUP,
            BingzhengMod.id("herbs"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.bingzhengmod.herbs"))
                    .icon(() -> new ItemStack(BingzhengItems.GUI_ZHI))
                    .entries((context, entries) -> {
                        entries.add(BingzhengItems.GUI_ZHI);
                        entries.add(BingzhengItems.SHAO_YAO);
                        entries.add(BingzhengItems.SHENG_JIANG);
                        entries.add(BingzhengItems.DA_ZAO);
                        entries.add(BingzhengItems.GAN_CAO);
                        entries.add(BingzhengItems.FENG_MI);
                        entries.add(BingzhengItems.SHENG_FU_ZI);
                        entries.add(BingzhengItems.ZHI_GAN_CAO);
                        entries.add(BingzhengItems.ZHI_FU_ZI);
                        entries.add(BingzhengItems.GUI_ZHI_TANG);
                        entries.add(BingzhengBlocks.XIUHE_TABLE_ITEM);
                    })
                    .build());

    private BingzhengItemGroups() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】物品栏注册完成。");
    }
}
