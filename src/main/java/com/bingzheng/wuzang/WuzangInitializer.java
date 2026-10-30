package com.bingzheng.wuzang;

import com.bingzheng.wuzang.behavior.WuzangBehaviorRegistry;
import net.fabricmc.api.ModInitializer;

/**
 * 五脏拓展的模组入口。
 *
 * <p>做成<b>独立 entrypoint</b>：五脏是对外打包的独立拓展，将来整体搬进自己的 jar 时，
 * 只要把本类连同 {@code com.bingzheng.wuzang} 包一起搬走即可，
 * 内核与情志模块都不需要任何改动。
 *
 * <p>装配顺序：附件 → 状态效果 → 服务端循环 → 命令 → 自检。
 */
public class WuzangInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        Wuzang.LOGGER.info("【病证·五脏】拓展开始装载……");

        WuzangAttachments.register();
        WuzangEffects.register();
        WuzangService.register();
        WuzangCommands.register();

        // 行为驱动：已装载的拓展会在这里顶替掉五脏的内置兜底判定
        // （今天只有情志拓展可用；温湿/消化/运动等装好后由它们自己 install）
        WuzangBehaviorRegistry.installAvailable();

        WuzangSelfCheck.run();
        WuzangBehaviorSelfCheck.run();

        Wuzang.LOGGER.info("【病证·五脏】拓展装载完成（独立模块；对情志只有反射式软链接，无编译期引用）。");
    }
}
