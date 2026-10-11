package com.bingzheng.recipe;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * 一次成功的配方匹配结果。
 *
 * @param recipe           命中的配方
 * @param slotToIngredient 长度等于输入格数的数组；第 i 格命中了第几味药，-1 表示该格为空
 */
public record XiuheMatch(XiuheRecipe recipe, int[] slotToIngredient) {

    /** 产出（副本，避免被调用方改动配方本体）。 */
    public ItemStack result() {
        return this.recipe.result().copy();
    }

    /**
     * 消耗掉这次合成用掉的药材。
     *
     * <p>整张布局恰好合一份成药（文档：「合成1份」），所以命中的格子会被整格清空。
     */
    public void consume(Inventory input) {
        for (int i = 0; i < this.slotToIngredient.length && i < input.size(); i++) {
            if (this.slotToIngredient[i] >= 0) {
                input.setStack(i, ItemStack.EMPTY);
            }
        }
    }
}
