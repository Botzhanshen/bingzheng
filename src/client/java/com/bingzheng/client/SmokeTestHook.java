package com.bingzheng.client;

import com.bingzheng.BingzhengMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

/**
 * 开发期「冒烟测试」钩子：让 {@code runClient} 能自动退出，便于无人值守地验证
 * 「游戏能否正常启动」。
 *
 * <p>两种启用方式，任选其一（都不设则完全不生效，对正常游玩零影响）：
 * <ul>
 *   <li>系统属性 {@code -Dbingzhengmod.smoketest.seconds=<秒>}
 *       —— 用 {@code gradlew runClient -PbzSmoketest=25} 触发</li>
 *   <li>环境变量 {@code BINGZHENGMOD_SMOKETEST=<秒>}
 *       —— 用 {@code $env:BINGZHENGMOD_SMOKETEST=25} 触发</li>
 * </ul>
 *
 * <p><b>踩坑记录：</b>全新的 {@code run/} 目录首次启动时，MC 不会直接进主菜单，而是先停在
 * {@link AccessibilityOnboardingScreen}（辅助功能引导页）等玩家点「继续」。
 * 无人值守时它永远等不到，所以这里必须把引导页也算作「已到达可交互状态」。
 * 另外 {@code run/options.txt} 里写下 {@code onboardAccessibility:false} 可让 MC 直接跳过该页。
 *
 * <p>约束遵循情况：
 * <ul>
 *   <li>约束 3：只<b>监听</b>每 tick 事件做追加判断，不取消、不篡改任何事件。</li>
 *   <li>约束 5：不涉及任何 Mixin。</li>
 *   <li>约束 4：不注册任何全局 ID。</li>
 * </ul>
 */
public final class SmokeTestHook {

    /** 方式一：系统属性名。 */
    public static final String PROPERTY = "bingzhengmod.smoketest.seconds";
    /** 方式二：环境变量名。 */
    public static final String ENV_VAR = "BINGZHENGMOD_SMOKETEST";

    private SmokeTestHook() {
    }

    /** 由客户端入口调用；两种方式都没配置时直接返回。 */
    public static void installIfRequested() {
        String raw = System.getProperty(PROPERTY);
        String source = "系统属性";
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(ENV_VAR);
            source = "环境变量";
        }
        if (raw == null || raw.isBlank()) {
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            BingzhengMod.LOGGER.warn("【病证】冒烟测试参数无效（{}）：{}，已忽略。", source, raw);
            return;
        }
        if (seconds <= 0) {
            return;
        }
        BingzhengMod.LOGGER.info("【病证】冒烟测试已启用（来源：{}），将在就绪 {} 秒后自动退出。", source, seconds);

        final int limitTicks = seconds * 20; // MC 固定 20 tick/秒
        final int[] elapsed = {0};
        final boolean[] armed = {false};

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!armed[0]) {
                Screen screen = client.currentScreen;
                // 加载遮罩未消失说明还在读资源；引导页/主菜单都算「已就绪可交互」。
                if (client.getOverlay() != null
                        || !(screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
                    return;
                }
                armed[0] = true;
                BingzhengMod.LOGGER.info("【病证】冒烟测试已就绪（当前界面：{}），开始计时。",
                        screen.getClass().getSimpleName());
            }

            if (++elapsed[0] >= limitTicks) {
                BingzhengMod.LOGGER.info("【病证】冒烟测试计时结束，自动退出客户端。");
                client.scheduleStop();
            }
        });
    }
}
