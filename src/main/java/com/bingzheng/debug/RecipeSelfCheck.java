package com.bingzheng.debug;

import com.bingzheng.BingzhengMod;
import com.bingzheng.recipe.XiuheMatch;
import com.bingzheng.recipe.XiuheRecipes;
import com.bingzheng.registry.BingzhengItems;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Optional;

/**
 * 配方引擎自检：用真实 {@code Item} 对象构造布局，跑正例与反例。
 *
 * <p>不需要世界、不需要玩家，因此在模组启动时就能跑完，
 * 保证「配方匹配器真的按文档表格工作」这件事是被验证过的，而不是口头声明。
 */
public final class RecipeSelfCheck {

    private static final int GRID = 25;
    private static int passed;
    private static int failed;

    private RecipeSelfCheck() {
    }

    public static void run() {
        if (!Boolean.getBoolean("fabric.development")) {
            return;
        }
        passed = 0;
        failed = 0;
        BingzhengMod.LOGGER.info("【病证·配方自检】开始，共 {} 条配方已注册。", XiuheRecipes.all().size());

        // ---------- 正例 ----------
        positive("桂枝汤 完整五味、格数与数量均正确", grid()
                .fill(BingzhengItems.GUI_ZHI, 6, 1)
                .fill(BingzhengItems.SHAO_YAO, 6, 1)
                .fill(BingzhengItems.SHENG_JIANG, 5, 1)
                .fill(BingzhengItems.DA_ZAO, 5, 1)
                .fill(BingzhengItems.ZHI_GAN_CAO, 3, 1)
                .build(), BingzhengItems.GUI_ZHI_TANG);

        positive("甘草 + 蜂蜜 → 炙甘草", grid()
                .fill(BingzhengItems.GAN_CAO, 1, 1)
                .fill(BingzhengItems.FENG_MI, 1, 1)
                .build(), BingzhengItems.ZHI_GAN_CAO);

        positive("生附子 → 制附子", grid()
                .fill(BingzhengItems.SHENG_FU_ZI, 1, 1)
                .build(), BingzhengItems.ZHI_FU_ZI);

        // ---------- 反例 ----------
        negative("少放炙甘草一味", grid()
                .fill(BingzhengItems.GUI_ZHI, 6, 1)
                .fill(BingzhengItems.SHAO_YAO, 6, 1)
                .fill(BingzhengItems.SHENG_JIANG, 5, 1)
                .fill(BingzhengItems.DA_ZAO, 5, 1)
                .build());

        negative("药材齐全但有一格数量不对（桂枝某格 2 个）", grid()
                .fill(BingzhengItems.GUI_ZHI, 5, 1)
                .fill(BingzhengItems.GUI_ZHI, 1, 2)
                .fill(BingzhengItems.SHAO_YAO, 6, 1)
                .fill(BingzhengItems.SHENG_JIANG, 5, 1)
                .fill(BingzhengItems.DA_ZAO, 5, 1)
                .fill(BingzhengItems.ZHI_GAN_CAO, 3, 1)
                .build());

        negative("混入无关药材（甘草+蜂蜜 之外多放生附子）", grid()
                .fill(BingzhengItems.GAN_CAO, 1, 1)
                .fill(BingzhengItems.FENG_MI, 1, 1)
                .fill(BingzhengItems.SHENG_FU_ZI, 1, 1)
                .build());

        negative("空台", grid().build());

        BingzhengMod.LOGGER.info("【病证·配方自检】完成：通过 {} 项，失败 {} 项。", passed, failed);
        if (failed > 0) {
            BingzhengMod.LOGGER.error("【病证·配方自检】存在 {} 项失败，请检查配方引擎！", failed);
        }
    }

    private static void positive(String name, SimpleInventory input, Item expected) {
        Optional<XiuheMatch> m = XiuheRecipes.match(input);
        if (m.isPresent() && m.get().result().getItem() == expected) {
            passed++;
            BingzhengMod.LOGGER.info("【病证·配方自检】[OK] 正例通过：{} → {}", name, m.get().recipe().nameZh());
        } else {
            failed++;
            BingzhengMod.LOGGER.error("【病证·配方自检】[FAIL] 正例失败：{}（期望 {}，实际 {}）",
                    name, expected.getTranslationKey(),
                    m.map(x -> x.result().getItem().getTranslationKey()).orElse("无匹配"));
        }
    }

    private static void negative(String name, SimpleInventory input) {
        Optional<XiuheMatch> m = XiuheRecipes.match(input);
        if (m.isEmpty()) {
            passed++;
            BingzhengMod.LOGGER.info("【病证·配方自检】[OK] 反例通过（正确地不匹配）：{}", name);
        } else {
            failed++;
            BingzhengMod.LOGGER.error("【病证·配方自检】[FAIL] 反例失败：{} 竟然匹配上了 {}",
                    name, m.get().recipe().nameZh());
        }
    }

    private static GridBuilder grid() {
        return new GridBuilder();
    }

    /** 小工具：按「占几格、每格几个」铺满 5×5 输入格。 */
    private static final class GridBuilder {
        private final SimpleInventory inv = new SimpleInventory(GRID);
        private int cursor;

        GridBuilder fill(Item item, int slots, int perSlot) {
            for (int s = 0; s < slots && this.cursor < GRID; s++) {
                this.inv.setStack(this.cursor++, new ItemStack(item, perSlot));
            }
            return this;
        }

        SimpleInventory build() {
            return this.inv;
        }
    }
}
