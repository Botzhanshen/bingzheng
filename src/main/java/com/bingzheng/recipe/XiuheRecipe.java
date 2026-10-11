package com.bingzheng.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 一条修合台配方。
 *
 * <p>这是<b>本模组自有的配方体系</b>，与 Vanilla 的 {@code RecipeManager} 完全无关：
 * 不注册任何 minecraft:recipe_type，也不读取数据包里的 recipes 目录，
 * 因此不会与其它模组或数据包发生任何冲突（约束 6）。
 *
 * @param id          形如 {@code bingzhengmod:gui_zhi_tang} 的配方标识
 * @param nameZh      中文名（开发期可读性用）
 * @param ingredients 药材需求
 * @param result      产出
 */
public record XiuheRecipe(Identifier id, String nameZh, List<XiuheIngredient> ingredients, ItemStack result) {

    /** 本配方总共占用的格子数；修合台是 5×5 共 25 格。 */
    public int totalSlots() {
        return this.ingredients.stream().mapToInt(XiuheIngredient::slots).sum();
    }
}
