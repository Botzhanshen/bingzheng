package com.bingzheng;

import com.bingzheng.attachment.BingzhengAttachments;
import com.bingzheng.command.BingzhengCommands;
import com.bingzheng.debug.RecipeSelfCheck;
import com.bingzheng.debug.SelfCheck;
import com.bingzheng.recipe.XiuheRecipes;
import com.bingzheng.registry.BingzhengBlocks;
import com.bingzheng.registry.BingzhengEffects;
import com.bingzheng.registry.BingzhengItemGroups;
import com.bingzheng.registry.BingzhengItems;
import com.bingzheng.registry.BingzhengScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心包《病证》主入口。
 *
 * <p>本类只负责按依赖顺序装配。注册顺序很重要：
 * 附属数据 → 状态效果 → 物品 → 方块 → 界面处理器 → 物品栏 → 配方 → 命令 → 自检。
 */
public class BingzhengMod implements ModInitializer {

    public static final String MOD_ID = "bingzhengmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 形如 {@code bingzhengmod:xxx} 的命名空间标识。所有注册项都必须经由此方法。 */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("【病证】核心包开始装载……");

        BingzhengAttachments.register();
        BingzhengEffects.register();
        BingzhengItems.register();
        BingzhengBlocks.register();
        BingzhengScreenHandlers.register();
        BingzhengItemGroups.register();
        XiuheRecipes.init();
        BingzhengCommands.register();

        SelfCheck.run();
        RecipeSelfCheck.run();

        LOGGER.info("【病证】核心包装载完成。");
    }
}
