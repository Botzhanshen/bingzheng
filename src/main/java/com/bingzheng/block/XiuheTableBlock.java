package com.bingzheng.block;

import com.bingzheng.screen.XiuheTableScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 修合台方块：右键打开 5×5 合药界面。 */
public class XiuheTableBlock extends Block {

    public XiuheTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            // 只做「打开我们自己的界面」这一件事，不取消、不改写任何原版逻辑。
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInventory, p) -> new XiuheTableScreenHandler(syncId, playerInventory),
                    Text.translatable("container.bingzhengmod.xiuhe_table")));
        }
        return ActionResult.SUCCESS;
    }
}
