package com.bingzheng.herb;

import com.bingzheng.BingzhengMod;
import net.minecraft.util.Identifier;

/**
 * 一味药材的静态档案。全部数据只存在于本模组自己的命名空间下，不占用任何原版字段。
 *
 * @param id      形如 {@code bingzhengmod:gui_zhi} 的标识
 * @param nameZh  中文名（开发期可读性用；游戏内显示一律走 lang 文件）
 * @param isRaw   是否为<b>原生基础药材</b>（自然界采集/生成/种植获得，未经炮制）。
 *                炮制产物为 false，且不计入「62 味原生基数」。
 * @param baseId  炮制产物指向的原生药材；原生药材为 {@code null}
 * @param toxic   <b>模组自定义毒性标记</b>：true 表示生用有毒，必须炮制去毒后才能安全入药。
 *                这是本模组自己的概念，与任何原版状态效果/属性无关。
 */
public record HerbProfile(Identifier id, String nameZh, boolean isRaw, Identifier baseId, boolean toxic) {

    /** 原生基础药材。 */
    public static HerbProfile raw(String path, String nameZh, boolean toxic) {
        return new HerbProfile(BingzhengMod.id(path), nameZh, true, null, toxic);
    }

    /** 炮制产物：本身无毒（毒性已在炮制中去除），基名指向原生药材。 */
    public static HerbProfile processed(String path, String nameZh, String basePath) {
        return new HerbProfile(BingzhengMod.id(path), nameZh, false, BingzhengMod.id(basePath), false);
    }
}
