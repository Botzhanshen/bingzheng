package com.bingzheng.registry;

import com.bingzheng.BingzhengMod;
import com.bingzheng.herb.HerbProfile;
import com.bingzheng.herb.HerbProfiles;
import com.bingzheng.item.DecoctionItem;
import com.bingzheng.item.HerbItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 本模组的物品注册表。所有 ID 一律走 {@code bingzhengmod:} 命名空间。 */
public final class BingzhengItems {

    // ---------------- 原生基础药材 ----------------
    public static final Item GUI_ZHI = herb(HerbProfiles.GUI_ZHI);
    public static final Item SHAO_YAO = herb(HerbProfiles.SHAO_YAO);
    public static final Item SHENG_JIANG = herb(HerbProfiles.SHENG_JIANG);
    public static final Item DA_ZAO = herb(HerbProfiles.DA_ZAO);
    public static final Item GAN_CAO = herb(HerbProfiles.GAN_CAO);
    public static final Item FENG_MI = herb(HerbProfiles.FENG_MI);
    /** 生附子：带毒性标记。 */
    public static final Item SHENG_FU_ZI = herb(HerbProfiles.SHENG_FU_ZI);

    // ---------------- 炮制产物 ----------------
    public static final Item ZHI_GAN_CAO = herb(HerbProfiles.ZHI_GAN_CAO);
    public static final Item ZHI_FU_ZI = herb(HerbProfiles.ZHI_FU_ZI);

    // ---------------- 成药（汤剂）----------------
    /** 桂枝汤药剂：服用后触发「表气调和」。 */
    public static final Item GUI_ZHI_TANG = register("gui_zhi_tang",
            new DecoctionItem(BingzhengEffects.HARMONIZE_EXTERIOR, 60 * 20, 0,
                    new Item.Settings().maxCount(16)));

    private BingzhengItems() {
    }

    private static Item herb(HerbProfile profile) {
        return register(profile.id().getPath(), new HerbItem(profile, new Item.Settings()));
    }

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, BingzhengMod.id(path), item);
    }

    /** 触发本类静态初始化。必须在 {@link BingzhengEffects#register()} 之后调用。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】物品注册完成。");
    }
}
