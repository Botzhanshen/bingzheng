package com.bingzheng.emotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 疲劳调试输出的排版（v0.9.11，纯函数）。
 *
 * <p>三行固定结构，每行 ≤ {@link #MAX_LINE_CHARS} 字（中文按字计，避免聊天栏随机折行）：
 * <ol>
 *   <li>现状横排：神耗、体耗、睡眠债与债倍率；</li>
 *   <li>神耗来源：只列非零来源，按贡献从大到小取前两条，多出的以省略号收尾；</li>
 *   <li>体耗来源：同上。</li>
 * </ol>
 */
public final class FatigueText {

    /** 每行字数上限（按 Unicode 码点计）。 */
    public static final int MAX_LINE_CHARS = 26;

    /** 单行最多列几个来源。 */
    private static final int MAX_PARTS = 2;

    private FatigueText() {
    }

    public static List<String> lines(EmotionFatigue.Snapshot snapshot) {
        List<String> out = new ArrayList<>(3);
        out.add(head(snapshot));
        out.add(sourceLine(" 神耗 ", spiritParts(snapshot)));
        String bodyLine = sourceLine(" 体耗 ", bodyParts(snapshot));
        if (snapshot.overnight()) {
            // 加了通宵标记就得让位：先摘掉省略号，保证整行仍 ≤26 字
            bodyLine = bodyLine.replace(" …", "") + " 通宵";
        }
        out.add(bodyLine);
        return out;
    }

    private static String head(EmotionFatigue.Snapshot s) {
        float rate = EmotionSleepRules.spiritRate(s.debt());
        String debt = " 债" + s.debt();
        if (s.debt() > 0) {
            debt += "×" + String.format(Locale.ROOT, "%.1f", rate);
        }
        // 通宵标记挪到体耗行尾（第 1 行要压到 26 字以内）
        return "【疲劳】神耗" + one(s.spirit()) + " 体耗" + one(s.body()) + debt;
    }

    private record Part(String text, double points) {
    }

    private static List<Part> spiritParts(EmotionFatigue.Snapshot s) {
        FatigueLedger l = s.ledger();
        List<Part> parts = new ArrayList<>(4);
        add(parts, "夜间", l.spirit(FatigueLedger.Spirit.NIGHT));
        add(parts, "交互", l.spirit(FatigueLedger.Spirit.GUI));
        add(parts, "神扰", l.spirit(FatigueLedger.Spirit.DELIRIUM));
        add(parts, "外源", l.spirit(FatigueLedger.Spirit.OTHER));
        return parts;
    }

    private static List<Part> bodyParts(EmotionFatigue.Snapshot s) {
        FatigueLedger l = s.ledger();
        List<Part> parts = new ArrayList<>(4);
        add(parts, "饥饿", l.body(FatigueLedger.Body.HUNGER));
        add(parts, "挖掘" + s.blocksBroken(), l.body(FatigueLedger.Body.BLOCKS));
        add(parts, "疾跑" + s.sprintMeters(), l.body(FatigueLedger.Body.SPRINT));
        add(parts, "游泳" + s.swimMeters(), l.body(FatigueLedger.Body.SWIM));
        return parts;
    }

    private static void add(List<Part> parts, String label, double points) {
        if (points > 0.0001D) {
            parts.add(new Part(label + "+" + one(points), points));
        }
    }

    /** 按贡献降序取前 {@link #MAX_PARTS} 条；还有剩余就补一个省略号。 */
    private static String sourceLine(String prefix, List<Part> parts) {
        if (parts.isEmpty()) {
            return prefix + "—";
        }
        parts.sort((a, b) -> Double.compare(b.points(), a.points()));
        StringBuilder sb = new StringBuilder(prefix);
        int shown = Math.min(MAX_PARTS, parts.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(parts.get(i).text());
        }
        if (parts.size() > shown) {
            sb.append(" …");
        }
        return sb.toString();
    }

    /** 字数（按码点，中文一个字算一个）。 */
    public static int charCount(String line) {
        return line.codePointCount(0, line.length());
    }

    /** 自检用：造一份「所有来源都非零」的最坏样例，返回最长的那一行。 */
    public static String worstCaseLine() {
        FatigueLedger ledger = new FatigueLedger();
        ledger.addSpirit(FatigueLedger.Spirit.NIGHT, 12.3D);
        ledger.addSpirit(FatigueLedger.Spirit.GUI, 4.5D);
        ledger.addSpirit(FatigueLedger.Spirit.DELIRIUM, 2.5D);
        ledger.addSpirit(FatigueLedger.Spirit.OTHER, 1.5D);
        ledger.addBody(FatigueLedger.Body.HUNGER, 18.0D);
        ledger.addBody(FatigueLedger.Body.BLOCKS, 12.3D);
        ledger.addBody(FatigueLedger.Body.SPRINT, 3.5D);
        ledger.addBody(FatigueLedger.Body.SWIM, 1.5D);
        var snapshot = new EmotionFatigue.Snapshot(17, 123, 77, 45, 1200, 240, false, "crafting_table",
                6, 2999, true, 42.5F, 18.0F, ledger);
        String worst = "";
        for (String line : lines(snapshot)) {
            if (charCount(line) > charCount(worst)) {
                worst = line;
            }
        }
        return worst;
    }

    public static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
