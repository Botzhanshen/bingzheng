package com.bingzheng.emotion.client;

import com.bingzheng.BingzhengMod;
import com.bingzheng.emotion.EmotionHudRules;
import net.fabricmc.api.ClientModInitializer;

/** 情志拓展的客户端入口：只负责装配画面特效。 */
public class EmotionClientInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DeliriumOverlay.register();

        // v0.9.0 神耗 / 体耗 HUD：接收端总是注册（否则打开开关后要重连才有数），
        // 渲染端只在开关为开时注册 —— 关闭后连渲染回调都不存在。
        EmotionHudClient.registerReceiver();
        boolean hudEnabled = EmotionHudRules.shouldRegister(EmotionHudConfig.enabled());
        if (hudEnabled) {
            EmotionHudClient.registerRenderer();
        }
        BingzhengMod.LOGGER.info("【病证·情志】神耗/体耗 HUD：{}（配置文件 {}）",
                hudEnabled ? "已启用" : "已关闭", EmotionHudConfig.path().getFileName());

        BingzhengMod.LOGGER.info("【病证·情志】客户端装载完成。");
    }
}
