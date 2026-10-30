package com.bingzheng.emotion.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 静坐坐垫。
 *
 * <p>方块本身没有逻辑：静坐判定完全由 {@code MeditationRules} 在服务端 tick 中完成，
 * 这样「站在坐垫上保持静止」这一条件才能被持续评估。
 * 右键只给一句提示，方便玩家知道怎么用。
 */
public class MeditationCushionBlock extends Block {

    public MeditationCushionBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            player.sendMessage(Text.translatable("message.bingzhengmod.meditation_hint"), true);
        }
        return ActionResult.SUCCESS;
    }
}
