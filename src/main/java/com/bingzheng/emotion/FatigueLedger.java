package com.bingzheng.emotion;

/**
 * 疲劳来源账本（v0.9.11）。
 *
 * <p><b>只记账，不参与任何判定</b>：它只回答一个问题 ——「这点疲劳动是从哪来的」。
 * 累计口径是「自登录 / 上次 {@code fatigue reset} 以来各来源的写入量」，
 * 不扣除睡眠恢复、静坐、日照与静止回落（那些是整体扣减，不归属任何单一来源）。
 */
public final class FatigueLedger {

    /** 神耗来源。 */
    public enum Spirit {
        /** 夜间清醒（按现实分钟折算，已乘债务与日照倍率）。 */
        NIGHT,
        /** 打开加工 / 容器类界面（附魔台双倍）。 */
        GUI,
        /** 神扰期间持续上涨。 */
        DELIRIUM,
        /** 其他外部写入（五脏经公开 API 回写等）。 */
        OTHER
    }

    /** 体耗来源。 */
    public enum Body {
        /** 饥饿值损失。 */
        HUNGER,
        /** 破坏方块。 */
        BLOCKS,
        /** 疾跑距离。 */
        SPRINT,
        /** 游泳距离。 */
        SWIM
    }

    private double night;
    private double gui;
    private double delirium;
    private double other;
    private double hunger;
    private double blocks;
    private double sprint;
    private double swim;
    private double spiritTotal;
    private double bodyTotal;

    public void addSpirit(Spirit source, double amount) {
        if (amount == 0.0D) {
            return;
        }
        switch (source) {
            case NIGHT -> night += amount;
            case GUI -> gui += amount;
            case DELIRIUM -> delirium += amount;
            default -> other += amount;
        }
        spiritTotal += amount;
    }

    public void addBody(Body source, double amount) {
        if (amount == 0.0D) {
            return;
        }
        switch (source) {
            case HUNGER -> hunger += amount;
            case BLOCKS -> blocks += amount;
            case SPRINT -> sprint += amount;
            default -> swim += amount;
        }
        bodyTotal += amount;
    }

    public double spirit(Spirit source) {
        return switch (source) {
            case NIGHT -> night;
            case GUI -> gui;
            case DELIRIUM -> delirium;
            default -> other;
        };
    }

    public double body(Body source) {
        return switch (source) {
            case HUNGER -> hunger;
            case BLOCKS -> blocks;
            case SPRINT -> sprint;
            default -> swim;
        };
    }

    public double spiritTotal() {
        return spiritTotal;
    }

    public double bodyTotal() {
        return bodyTotal;
    }

    /** 各神耗来源之和；自检断言它恒等于 {@link #spiritTotal()}。 */
    public double spiritPartsSum() {
        return night + gui + delirium + other;
    }

    /** 各体耗来源之和；自检断言它恒等于 {@link #bodyTotal()}。 */
    public double bodyPartsSum() {
        return hunger + blocks + sprint + swim;
    }

    public void clear() {
        night = 0.0D;
        gui = 0.0D;
        delirium = 0.0D;
        other = 0.0D;
        hunger = 0.0D;
        blocks = 0.0D;
        sprint = 0.0D;
        swim = 0.0D;
        spiritTotal = 0.0D;
        bodyTotal = 0.0D;
    }
}
