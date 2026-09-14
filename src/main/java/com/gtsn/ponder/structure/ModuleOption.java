package com.gtsn.ponder.structure;

/**
 * 模块位可接受的某个模块：{@code moduleId}（模块注册名，如 {@code gtceu:parallel_module}）
 * 与其 {@link ModuleEffectInfo 效果汇总}。
 *
 * <p>{@code effect} 为 {@code null} 时退化为 {@link ModuleEffectInfo#EMPTY}（模块无效果声明）。
 * 不可变；纯 Java、零 MC / GT 依赖。</p>
 */
public record ModuleOption(String moduleId, ModuleEffectInfo effect) {

    public ModuleOption {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("module id must be non-blank");
        }
        effect = effect == null ? ModuleEffectInfo.EMPTY : effect;
    }

    /** 便捷构造：无效果声明的模块。 */
    public ModuleOption(String moduleId) {
        this(moduleId, ModuleEffectInfo.EMPTY);
    }

    /** 是否声明了非中性效果。 */
    public boolean hasEffect() {
        return !effect.isEmpty();
    }

    @Override
    public String toString() {
        return "ModuleOption[" + moduleId + (hasEffect() ? " " + effect : "") + "]";
    }
}
