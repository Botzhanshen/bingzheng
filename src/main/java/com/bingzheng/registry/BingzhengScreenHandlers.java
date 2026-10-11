package com.bingzheng.registry;

import com.bingzheng.BingzhengMod;
import com.bingzheng.screen.XiuheTableScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

/** 本模组的界面处理器注册表。 */
public final class BingzhengScreenHandlers {

    public static final ScreenHandlerType<XiuheTableScreenHandler> XIUHE_TABLE =
            Registry.register(Registries.SCREEN_HANDLER, BingzhengMod.id("xiuhe_table"),
                    new ScreenHandlerType<>(XiuheTableScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private BingzhengScreenHandlers() {
    }

    /** 触发本类静态初始化。 */
    public static void register() {
        BingzhengMod.LOGGER.debug("【病证】界面处理器注册完成。");
    }
}
