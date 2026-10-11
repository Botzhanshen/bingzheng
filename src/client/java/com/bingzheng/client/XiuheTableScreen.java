package com.bingzheng.client;

import com.bingzheng.BingzhengMod;
import com.bingzheng.screen.XiuheTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 修合台界面渲染。
 *
 * <p>美术资源为程序化生成的占位贴图（{@code textures/gui/xiuhe_table.png}），
 * 槽位坐标必须与 {@link XiuheTableScreenHandler} 中的常量严格对应。
 */
public class XiuheTableScreen extends HandledScreen<XiuheTableScreenHandler> {

    private static final Identifier TEXTURE = BingzhengMod.id("textures/gui/xiuhe_table.png");

    public XiuheTableScreen(XiuheTableScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = XiuheTableScreenHandler.GUI_WIDTH;
        this.backgroundHeight = XiuheTableScreenHandler.GUI_HEIGHT;
        this.playerInventoryTitleY = 116;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
