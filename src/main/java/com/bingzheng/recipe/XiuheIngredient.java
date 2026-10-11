package com.bingzheng.recipe;

import net.minecraft.item.Item;

/**
 * 修合台配方中的一味药。
 *
 * <p>文档原文对每味药给出两个维度：<b>格数</b>（占几格）与<b>每格数量</b>。
 * 二者都参与配方判定——这正是「数量参与配方判定」的实现方式。
 *
 * @param item    药材物品
 * @param slots   需要占用的格子数
 * @param perSlot 每格应放的个数
 */
public record XiuheIngredient(Item item, int slots, int perSlot) {
}
