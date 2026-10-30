package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.bingzheng.emotion.block.MeditationCushionBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** 情志拓展的方块注册表。 */
public final class EmotionBlocks {

    /** 静坐坐垫：站上去保持静止即可进入静坐状态。 */
    public static final Block MEDITATION_CUSHION = Registry.register(
            Registries.BLOCK,
            BingzhengMod.id("meditation_cushion"),
            new MeditationCushionBlock(AbstractBlock.Settings.create()
                    .strength(0.6F)
                    .sounds(BlockSoundGroup.WOOL)));

    public static final Item MEDITATION_CUSHION_ITEM = Registry.register(
            Registries.ITEM,
            BingzhengMod.id("meditation_cushion"),
            new BlockItem(MEDITATION_CUSHION, new Item.Settings()));

    private EmotionBlocks() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证·情志】方块注册完成。");
    }
}
