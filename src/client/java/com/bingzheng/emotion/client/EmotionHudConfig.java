package com.bingzheng.emotion.client;

import com.bingzheng.emotion.EmotionHudRules;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * HUD 的<b>客户端配置</b>（v0.9.0）：{@code config/bingzhengmod-emotion-hud.properties}。
 *
 * <p>只有一个开关 {@code hud.enabled}，<b>默认开</b>（与 {@link EmotionHudRules#DEFAULT_ENABLED} 一致）。
 * 配置读不到/坏了也按默认值走 —— 不允许因为一个配置项把客户端搞崩。
 */
public final class EmotionHudConfig {

    /** 配置键名。 */
    public static final String KEY_ENABLED = "hud.enabled";

    private static Boolean cached;

    private EmotionHudConfig() {
    }

    /** 配置文件路径（{@code config/} 下）。 */
    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("bingzhengmod-emotion-hud.properties");
    }

    /** HUD 是否启用（带缓存，渲染路径上不重复读盘）。 */
    public static boolean enabled() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    /** 重新读盘（自检/切换开关用）。 */
    public static boolean reload() {
        cached = load();
        return cached;
    }

    private static boolean load() {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return EmotionHudRules.DEFAULT_ENABLED;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (Exception e) {
            return EmotionHudRules.DEFAULT_ENABLED;
        }
        String raw = properties.getProperty(KEY_ENABLED);
        return raw == null ? EmotionHudRules.DEFAULT_ENABLED : Boolean.parseBoolean(raw.trim());
    }

    /** 写回开关（给用户手动改配置时留的入口，也可由代码调用）。 */
    public static void save(boolean enabled) {
        Properties properties = new Properties();
        properties.setProperty(KEY_ENABLED, Boolean.toString(enabled));
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "bingzhengmod emotion HUD (v0.9.0)");
            }
            cached = enabled;
        } catch (Exception ignored) {
            // 写不进去就算了：不影响本次运行
        }
    }
}
