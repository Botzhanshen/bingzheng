package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/** 情志拓展自己的创造模式物品栏（不改动内核已有的「病证·药材」页）。 */
public final class EmotionItemGroups {

    public static final ItemGroup EMOTION = Registry.register(
            Registries.ITEM_GROUP,
            BingzhengMod.id("emotion"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.bingzhengmod.emotion"))
                    .icon(() -> new ItemStack(EmotionBlocks.MEDITATION_CUSHION_ITEM))
                    .entries((context, entries) -> entries.add(EmotionBlocks.MEDITATION_CUSHION_ITEM))
                    .build());

    private EmotionItemGroups() {
    }

    public static void register() {
        BingzhengMod.LOGGER.debug("【病证·情志】物品栏注册完成。");
    }
}
