package com.bingzheng.wuzang;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 五脏拓展的自持常量。
 *
 * <p><b>刻意不复用内核的 {@code BingzhengMod.id()} / {@code BingzhengMod.LOGGER}</b>：
 * 五脏是对外打包的独立拓展，本包内<b>没有任何一处</b>引用 {@code com.bingzheng.emotion} 或
 * {@code com.bingzheng} 下的既有类（唯一例外是自检里对全局牛奶保护层的一次只读调用，见 {@code WuzangSelfCheck}）。
 * 这样拆包时情志可以完全不安装，五脏照常运行。
 *
 * <p>命名空间与内核保持一致（{@code bingzhengmod}），这样牛奶保护层的「命名空间默认全保护」规则
 * 会自动覆盖五脏的全部表现层效果，无需额外登记。
 */
public final class Wuzang {

    /** 与内核一致的命名空间。 //TODO 后续迁移外部配置（拆包时随 mod id 调整） */
    public static final String MOD_ID = "bingzhengmod";

    /** 与内核同名，因此拿到的是同一个 logger 实例，无需引用内核类。 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Wuzang() {
    }

    /** 形如 {@code bingzhengmod:xxx} 的标识符。所有注册项都必须经由此方法。 */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
