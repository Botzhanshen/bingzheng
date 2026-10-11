package com.bingzheng.registry;

import com.bingzheng.BingzhengMod;
import com.bingzheng.block.XiuheTableBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** 本模组的方块注册表。 */
public final class BingzhengBlocks {

    /** 修合台：木质基础修合台，用于药材炮制与方剂配伍合成。 */
    public static final Block XIUHE_TABLE = Registry.register(
            Registries.BLOCK,
            BingzhengMod.id("xiuhe_table"),
            new XiuheTableBlock(AbstractBlock.Settings.create()
                    .strength(2.0F)
                    .sounds(BlockSoundGroup.WOOD)));

    public static final Item XIUHE_TABLE_ITEM = Registry.register(
            Registries.ITEM,
            BingzhengMod.id("xiuhe_table"),
            new BlockItem(XIUHE_TABLE, new Item.Settings()));

    private BingzhengBlocks() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】方块注册完成。");
    }
}
