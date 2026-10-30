package com.bingzheng.emotion;

import com.bingzheng.BingzhengMod;
import com.bingzheng.protect.MilkSafeEffects;
import net.fabricmc.api.ModInitializer;

/**
 * 情志拓展的模组入口。
 *
 * <p>刻意做成 <b>独立 entrypoint</b>，而不是往内核的 {@code BingzhengMod.onInitialize} 里塞代码，
 * 这样内核保持零改动（版本 0.1.0 仍处于冻结状态）。
 * 入口在 {@code fabric.mod.json} 的 {@code main} 数组中追加。
 */
public class EmotionInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        BingzhengMod.LOGGER.info("【病证·情志】拓展开始装载……");

        EmotionAttachments.register();
        EmotionEffects.register();
        EmotionBlocks.register();
        EmotionItemGroups.register();
        EmotionService.register();
        EmotionCommands.register();

        // 牛奶过滤层：本模组全部状态效果对「饮用牛奶」免疫（唯一一处 Mixin 已在 mixin 配置里装载）。
        // 放在内核 entrypoint 之后调用，保证内核效果也已注册完毕、都能被名单覆盖。
        MilkSafeEffects.register();

        EmotionSelfCheck.run();

        BingzhengMod.LOGGER.info("【病证·情志】拓展装载完成。");
    }
}
