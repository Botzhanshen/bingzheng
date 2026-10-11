package com.bingzheng.client;

import com.bingzheng.BingzhengMod;
import com.bingzheng.registry.BingzhengScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;

/** 客户端入口：HUD、界面与渲染相关的一切都在这里装配。 */
public class BingzhengModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BingzhengMod.LOGGER.info("【病证】客户端装载完成。");

        // 把界面处理器与客户端 Screen 绑定
        ScreenRegistry.register(BingzhengScreenHandlers.XIUHE_TABLE, XiuheTableScreen::new);

        SmokeTestHook.installIfRequested();
    }
}
