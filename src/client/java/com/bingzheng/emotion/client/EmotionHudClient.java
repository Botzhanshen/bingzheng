package com.bingzheng.emotion.client;

import com.bingzheng.BingzhengMod;
import com.bingzheng.emotion.EmotionHudRules;
import com.bingzheng.emotion.EmotionHudSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

/**
 * 神耗 / 体耗的 <b>HUD 双行条</b>（v0.9.1：布局判定 + 模式守卫 + 格子分割）。
 *
 * <p>渲染顺序（从上到下）：护甲值 → 神耗 / 体耗条 → 血量条。
 * 位置不写死：{@link EmotionHudRules#rowsTop} 按血量条顶边与护甲行位置动态判定，
 * 空间不够时整体上移到护甲上方，<b>绝不重叠</b>。
 *
 * <p>十格是<b>独立小图标</b>：贴图每帧左右各留 1px 透明边，按 8px 节距绘制 → 格间 1px 空隙。
 * 只渲染生存 / 冒险（创造、旁观不画）。
 */
public final class EmotionHudClient {

    private static final Identifier SPIRIT_TEXTURE =
            BingzhengMod.id("textures/gui/hud/spirit_bars.png");
    private static final Identifier BODY_TEXTURE =
            BingzhengMod.id("textures/gui/hud/fatigue_bars.png");

    /** 贴图：3 帧横排（空 / 半 / 满），每帧 9×4。 */
    private static final int TEXTURE_WIDTH = 27;
    private static final int TEXTURE_HEIGHT = 4;

    private static float spirit;
    private static float body;
    private static boolean registered;

    private EmotionHudClient() {
    }

    /** 注册通道接收（无论 HUD 开关都要收，否则打开开关后要重连才有数据）。 */
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(EmotionHudSync.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    float receivedSpirit = buf.readFloat();
                    float receivedBody = buf.readFloat();
                    spirit = receivedSpirit;
                    body = receivedBody;
                });
    }

    /** 注册渲染（只在开关为开时调用）。 */
    public static void registerRenderer() {
        HudRenderCallback.EVENT.register(EmotionHudClient::render);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen) {
                ScreenEvents.afterRender(screen).register(
                        (s2, drawContext, mouseX, mouseY, tickDelta) -> render(drawContext, tickDelta));
            }
        });
        registered = true;
    }

    /** 是否已登记渲染回调（关闭时为 false）。 */
    public static boolean rendererRegistered() {
        return registered;
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        // ① 模式守卫：只画生存 / 冒险
        GameMode mode = client.interactionManager == null ? null
                : client.interactionManager.getCurrentGameMode();
        if (mode == null || !EmotionHudRules.modeAllows(mode.ordinal())) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int baseX = EmotionHudRules.baseX(width);
        int healthTop = EmotionHudRules.healthTop(height);
        // ② 布局判定：护甲在不在，决定两行条贴谁
        boolean armorPresent = client.player.getArmor() > 0;
        int armorTop = healthTop - EmotionHudRules.ARMOR_ROW_HEIGHT;
        int rowsTop = EmotionHudRules.rowsTop(healthTop, armorPresent, armorTop);

        int spiritY = rowsTop;
        int bodyY = rowsTop + EmotionHudRules.CELL_HEIGHT;

        drawRow(context, SPIRIT_TEXTURE, baseX, spiritY, spirit);
        drawRow(context, BODY_TEXTURE, baseX, bodyY, body);
    }

    private static void drawRow(DrawContext context, Identifier texture,
                                int baseX, int y, float value) {
        float cells = EmotionHudRules.cells(value);
        for (int index = 0; index < EmotionHudRules.SEGMENTS; index++) {
            int frame = EmotionHudRules.frameFor(cells, index);
            int x = baseX + index * EmotionHudRules.PITCH;
            context.drawTexture(texture, x, y, frame * EmotionHudRules.CELL_WIDTH, 0,
                    EmotionHudRules.CELL_WIDTH, EmotionHudRules.CELL_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }
}
