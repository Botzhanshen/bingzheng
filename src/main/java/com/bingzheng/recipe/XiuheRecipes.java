package com.bingzheng.recipe;

import com.bingzheng.BingzhengMod;
import com.bingzheng.registry.BingzhengItems;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 修合台的配方注册表 —— 完全独立于原版配方系统。
 *
 * <p>判定规则（「无序多重集」）：
 * <ol>
 *   <li>每种药材占用的格子数必须与配方一致；</li>
 *   <li>这些格子里每格的数量必须与配方的「每格数量」完全一致；</li>
 *   <li>不允许出现配方之外的任何药材（多余的格子一律判失败）。</li>
 * </ol>
 *
 * <p>之所以不校验摆放位置：文档表格只给出「格数」，没有坐标；而多数方剂（如桂枝汤 6+6+5+5+3）
 * 恰好占满 25 格，位置规则在数学上无法成立。
 *
 * <p>//TODO 后续迁移配置文件：配方整体应从数据文件加载，而不是硬编码。
 */
public final class XiuheRecipes {

    private static final List<XiuheRecipe> RECIPES = new ArrayList<>();
    private static boolean initialized = false;

    private XiuheRecipes() {
    }

    /** 必须在 {@code BingzhengItems.register()} 之后调用。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 配方 1：甘草 + 蜂蜜 → 炙甘草（蜜炙）
        add(new XiuheRecipe(BingzhengMod.id("zhi_gan_cao"), "炙甘草",
                List.of(new XiuheIngredient(BingzhengItems.GAN_CAO, 1, 1),
                        new XiuheIngredient(BingzhengItems.FENG_MI, 1, 1)),
                new ItemStack(BingzhengItems.ZHI_GAN_CAO)));

        // 配方 2：生附子 → 制附子
        // 「简化三步炮制逻辑」：文档原文的三步（洁净水浸泡 3 天 → 甘草黑豆武火煮沸转文火 10 分钟 → 晒药台晾晒 2 天）
        // 在本切片里压缩为修合台内的一次操作。//TODO 后续迁移配置文件，并在引入晒药台后拆回三步。
        add(new XiuheRecipe(BingzhengMod.id("zhi_fu_zi"), "制附子",
                List.of(new XiuheIngredient(BingzhengItems.SHENG_FU_ZI, 1, 1)),
                new ItemStack(BingzhengItems.ZHI_FU_ZI)));

        // 配方 3：桂枝汤（《伤寒论》）桂枝6 + 芍药6 + 生姜5 + 大枣5 + 炙甘草3 = 25 格
        add(new XiuheRecipe(BingzhengMod.id("gui_zhi_tang"), "桂枝汤",
                List.of(new XiuheIngredient(BingzhengItems.GUI_ZHI, 6, 1),
                        new XiuheIngredient(BingzhengItems.SHAO_YAO, 6, 1),
                        new XiuheIngredient(BingzhengItems.SHENG_JIANG, 5, 1),
                        new XiuheIngredient(BingzhengItems.DA_ZAO, 5, 1),
                        new XiuheIngredient(BingzhengItems.ZHI_GAN_CAO, 3, 1)),
                new ItemStack(BingzhengItems.GUI_ZHI_TANG)));
    }

    private static void add(XiuheRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<XiuheRecipe> all() {
        return List.copyOf(RECIPES);
    }

    /** 输入格内容 → 命中的配方；没有命中返回 {@link Optional#empty()}。 */
    public static Optional<XiuheMatch> match(Inventory input) {
        for (XiuheRecipe recipe : RECIPES) {
            Optional<XiuheMatch> m = matchOne(recipe, input);
            if (m.isPresent()) {
                return m;
            }
        }
        return Optional.empty();
    }

    private static Optional<XiuheMatch> matchOne(XiuheRecipe recipe, Inventory input) {
        int[] assign = new int[input.size()];
        Arrays.fill(assign, -1);

        for (int gi = 0; gi < recipe.ingredients().size(); gi++) {
            XiuheIngredient ing = recipe.ingredients().get(gi);
            int found = 0;
            for (int i = 0; i < input.size() && found < ing.slots(); i++) {
                if (assign[i] >= 0) {
                    continue;
                }
                ItemStack stack = input.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.getItem() == ing.item() && stack.getCount() == ing.perSlot()) {
                    assign[i] = gi;
                    found++;
                }
            }
            if (found != ing.slots()) {
                return Optional.empty(); // 格数不足
            }
        }

        // 不允许任何多余药材
        for (int i = 0; i < input.size(); i++) {
            if (assign[i] < 0 && !input.getStack(i).isEmpty()) {
                return Optional.empty();
            }
        }
        return Optional.of(new XiuheMatch(recipe, assign));
    }
}
