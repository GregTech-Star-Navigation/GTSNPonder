package com.gtsn.ponder.structure;

/**
 * 模块的<b>效果汇总</b>（{@link ModuleOption#effect()}）：组织 GT fork 模块系统里
 * {@code ModuleEffect} 家族的数值化摘要，供自动生成器产出「安装 → 效果汇总」演示。
 *
 * <p>字段与 fork 的 {@code ModuleEffectSummary} 一一对应：
 * 并行容量、速度 / 能耗 / 输入 / 输出乘数、等级加成。中性 DTO——GT 访问（读取
 * {@code ModuleEffectSummary}）只发生在唯一适配包 {@code com.gtsn.ponder.gt}，
 * 其余包只与本记录打交道。</p>
 *
 * <p>{@link #EMPTY} 表示「无配方效果」的退化模块（全部为中性值：乘数 1、计数 0）。
 * 不可变；纯 Java、零 MC / GT 依赖。</p>
 */
public record ModuleEffectInfo(
        int parallelCapacity,
        double speedMultiplier,
        double energyMultiplier,
        double inputMultiplier,
        double outputMultiplier,
        int tierBonus) {

    /** 无配方效果的中性汇总：并行 / 等级 0，全部乘数 1。 */
    public static final ModuleEffectInfo EMPTY =
            new ModuleEffectInfo(0, 1.0d, 1.0d, 1.0d, 1.0d, 0);

    public ModuleEffectInfo {
        if (parallelCapacity < 0) {
            throw new IllegalArgumentException("parallel capacity must be >= 0: " + parallelCapacity);
        }
        if (tierBonus < 0) {
            throw new IllegalArgumentException("tier bonus must be >= 0: " + tierBonus);
        }
        requirePositive(speedMultiplier, "speed multiplier");
        requirePositive(energyMultiplier, "energy multiplier");
        requirePositive(inputMultiplier, "input multiplier");
        requirePositive(outputMultiplier, "output multiplier");
    }

    /** 是否为中性（无配方效果）汇总。 */
    public boolean isEmpty() {
        return equals(EMPTY);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0d) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite: " + value);
        }
    }
}
