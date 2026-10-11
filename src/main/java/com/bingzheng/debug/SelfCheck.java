package com.bingzheng.debug;

import com.bingzheng.BingzhengMod;
import com.bingzheng.herb.HerbProfile;
import com.bingzheng.item.HerbItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发期自检：只在 {@code -Dfabric.development=true}（Loom 的 runClient 默认开启）时运行。
 *
 * <p>检查三件事：
 * <ol>
 *   <li>本模组注册的物品是否全部落在 {@code bingzhengmod:} 命名空间内（约束 4）</li>
 *   <li>每个炮制药材的 base 基名是否指向一个真实存在的原生药材</li>
 *   <li>毒性标记是否只出现在应出现的地方（生附子有毒、制附子无毒）</li>
 * </ol>
 * 任何一条不通过都会以 WARN/ERROR 打印出来，便于立刻发现。
 */
public final class SelfCheck {

    private SelfCheck() {
    }

    public static void run() {
        if (!Boolean.getBoolean("fabric.development")) {
            return; // 正式游玩环境不跑
        }

        List<String> problems = new ArrayList<>();
        List<String> herbRows = new ArrayList<>();
        int itemCount = 0;

        for (Identifier id : Registries.ITEM.getIds()) {
            if (!BingzhengMod.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            itemCount++;
            Item item = Registries.ITEM.get(id);
            if (!(item instanceof HerbItem herbItem)) {
                continue;
            }
            HerbProfile p = herbItem.profile();

            // 检查 1：命空间
            if (!BingzhengMod.MOD_ID.equals(p.id().getNamespace())) {
                problems.add("药材 " + p.nameZh() + " 的档案 id 不在本模组命名空间：" + p.id());
            }
            // 检查 2：炮制品必须有真实存在的基名
            if (!p.isRaw()) {
                if (p.baseId() == null) {
                    problems.add("炮制品 " + p.nameZh() + " 没有 base 基名");
                } else if (!Registries.ITEM.containsId(p.baseId())) {
                    problems.add("炮制品 " + p.nameZh() + " 的基名 " + p.baseId() + " 不是已注册物品");
                }
            }
            herbRows.add("    " + (p.isRaw() ? "原生" : "炮制") + "  " + pad(p.nameZh()) + pad(p.id().toString())
                    + (p.toxic() ? "  [有毒标记]" : "")
                    + (p.baseId() != null ? "  base=" + p.baseId() : ""));
        }

        BingzhengMod.LOGGER.info("【病证·自检】已注册物品 {} 件，其中药材 {} 味：", itemCount, herbRows.size());
        herbRows.forEach(BingzhengMod.LOGGER::info);
        BingzhengMod.LOGGER.info("【病证·自检】毒性标记检查：生附子={}，制附子={}",
                profileOf("sheng_fu_zi"), profileOf("zhi_fu_zi"));
        BingzhengMod.LOGGER.info("【病证·自检】营卫附属数据：{} （区间 {}-{}，默认 {}）",
                com.bingzheng.attachment.BingzhengAttachments.YINGWEI.identifier(),
                com.bingzheng.attachment.BingzhengAttachments.YINGWEI_MIN,
                com.bingzheng.attachment.BingzhengAttachments.YINGWEI_MAX,
                com.bingzheng.attachment.BingzhengAttachments.YINGWEI_DEFAULT);

        if (problems.isEmpty()) {
            BingzhengMod.LOGGER.info("【病证·自检】全部通过。");
        } else {
            problems.forEach(p -> BingzhengMod.LOGGER.error("【病证·自检】不通过：{}", p));
        }
    }

    private static String profileOf(String path) {
        Item item = Registries.ITEM.get(BingzhengMod.id(path));
        if (item instanceof HerbItem herbItem) {
            return herbItem.profile().toxic() ? "有毒" : "无毒";
        }
        return "未注册";
    }

    private static String pad(String s) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 10) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
