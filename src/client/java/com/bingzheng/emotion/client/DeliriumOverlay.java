package com.bingzheng.emotion.client;

import com.bingzheng.emotion.EmotionConfig;
import com.bingzheng.emotion.EmotionEffects;
import com.bingzheng.emotion.EmotionHooks;
import com.bingzheng.emotion.MeditationRules;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Random;

/**
 * 【神扰】的画面叠加特效。
 *
 * <p><b>刻意不做全屏硬染色</b>，只做三样：
 * <ol>
 *   <li>屏幕边缘深红色向外渐变晕染（画面正中保留原始世界色彩）</li>
 *   <li>细颗粒噪点</li>
 *   <li>间断的短促红色脉冲</li>
 * </ol>
 * 全部通过 HUD 图层绘制，不触碰世界渲染管线，因此对光影模组（Iris / OptiFine 等）兼容。
 *
 * <p>静坐状态下以上全部效果暂停。
 */
public final class DeliriumOverlay {

    /** 边缘色：深红。 //TODO 后续迁移外部配置 */
    private static final int EDGE_RED = 0x8B0000;

    private static final Random NOISE = new Random();

    private DeliriumOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(DeliriumOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        StatusEffectInstance delirium = client.player.getStatusEffect(EmotionEffects.SHEN_RAO);
        if (delirium == null) {
            return;
        }
        // 静坐拥有最高豁免优先级：画面效果全部暂停
        // 只认服务端同步过来的静坐标记 —— 与服务端裁决严格一致，不会「偶尔闪现」
        if (MeditationRules.isSittingByEffect(client.player)) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float pulse = pulse(client);

        renderEdgeVignette(context, width, height, pulse);
        renderNoise(context, width, height);
    }

    /** 计算当前帧的脉冲强度 0..1。火邪加快、水寒减慢。 */
    private static float pulse(MinecraftClient client) {
        double multiplier = EmotionHooks.attributeMultiplier(client.player);
        int interval = EmotionConfig.OVERLAY_PULSE_INTERVAL;
        if (multiplier > 1.0D) {
            interval = (int) (interval * EmotionConfig.OVERLAY_PULSE_FIRE_HASTEN);
        } else if (multiplier < 1.0D) {
            interval = (int) (interval * EmotionConfig.OVERLAY_PULSE_COLD_SLOWDOWN);
        }
        interval = Math.max(10, interval);

        long time = client.world.getTime();
        long phase = time % interval;
        if (phase >= EmotionConfig.OVERLAY_PULSE_LENGTH) {
            return 0.0F;
        }
        return 1.0F - (float) phase / EmotionConfig.OVERLAY_PULSE_LENGTH;
    }

    /**
     * 边缘渐变晕染：由外向内一圈圈叠画，越靠内越透明。
     * 正中大片区域完全不绘制，保证世界色彩原样保留。
     */
    private static void renderEdgeVignette(DrawContext context, int width, int height, float pulse) {
        int depth = Math.max(1, (int) (Math.min(width, height) * EmotionConfig.OVERLAY_EDGE_THICKNESS));
        int baseAlpha = EmotionConfig.OVERLAY_EDGE_MAX_ALPHA;
        int boosted = (int) (baseAlpha * (1.0F + 0.9F * pulse));

        for (int i = 0; i < depth; i++) {
            int alpha = boosted * (depth - i) / depth;
            if (alpha <= 0) {
                continue;
            }
            int color = (Math.min(alpha, 255) << 24) | EDGE_RED;
            context.fill(i, i, width - i, i + 1, color);                 // 上
            context.fill(i, height - i - 1, width - i, height - i, color); // 下
            context.fill(i, i, i + 1, height - i, color);                // 左
            context.fill(width - i - 1, i, width - i, height - i, color); // 右
        }
    }

    /** 全屏细颗粒噪点（稀疏点阵，不是整屏染色）。 */
    private static void renderNoise(DrawContext context, int width, int height) {
        int maxAlpha = EmotionConfig.OVERLAY_NOISE_MAX_ALPHA;
        for (int i = 0; i < EmotionConfig.OVERLAY_NOISE_COUNT; i++) {
            int x = NOISE.nextInt(Math.max(1, width));
            int y = NOISE.nextInt(Math.max(1, height));
            int alpha = NOISE.nextInt(maxAlpha + 1);
            if (alpha <= 0) {
                continue;
            }
            context.fill(x, y, x + 1, y + 1, (alpha << 24) | EDGE_RED);
        }
    }
}
