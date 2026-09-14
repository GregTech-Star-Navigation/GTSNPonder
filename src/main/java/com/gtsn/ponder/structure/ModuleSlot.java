package com.gtsn.ponder.structure;

import java.util.List;
import java.util.Optional;

/**
 * 模块位（Module Slot）：组织 GT fork 独有模块系统的「区域」声明——
 * 结构局部坐标下的偏移（{@code offsetX/Y/Z}）、尺寸（{@code sizeX/Y/Z}）与其**可接受模块**。
 *
 * <p><b>坐标约定</b>：偏移与尺寸都以结构包围盒原点为基准、0 基，与 {@link StructureBlock}
 * 的 {@code (x, y, z)} 同一坐标系（适配包会把 GT 的「控制器相对」区域映射到此坐标系；映射
 * 细节与守卫见 {@code com.gtsn.ponder.gt.GtStructureAdapter}）。因此本记录只在
 * {@link StructureSource} 已声明控制器时才可能出现——无控制器则无法锚定区域。</p>
 *
 * <p><b>可接受模块</b>：{@code acceptsAnyModule} 为真表示任意已注册模块均可安装；
 * 否则以 {@code acceptableModuleIds} 列出允许的模块注册名。两者同时为空且
 * {@code acceptsAnyModule} 为假表示「不接受任何模块」的退化声明——本 DTO 允许并如实表达
 * （{@link #hasAcceptableModules()} 为假），由上层生成器决定是否跳过安装演示。</p>
 *
 * <p><b>模块效果</b>：{@code moduleOptions} 为每个可接受模块携带其 {@link ModuleEffectInfo 效果汇总}
 * （由适配包从 fork 的 {@code ModuleEffectSummary} 读出），供自动生成器的「安装 → 效果汇总」演示。
 * 手作 / 夹具可省略（退化为空列表），此时安装演示只叙述模块标识、效果汇总标为「无效果」。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖。不可变：列表在构造时拷贝。</p>
 */
public record ModuleSlot(
        int offsetX,
        int offsetY,
        int offsetZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        boolean acceptsAnyModule,
        List<String> acceptableModuleIds,
        List<ModuleOption> moduleOptions) {

    /** 场景元素选择器字面量：按模块位下标把区域展开为单元（{@code params.index}）。 */
    public static final String SELECTOR = "moduleslot";

    public ModuleSlot {
        if (offsetX < 0 || offsetY < 0 || offsetZ < 0) {
            throw new IllegalArgumentException("module slot offset must be non-negative: "
                    + offsetX + "," + offsetY + "," + offsetZ);
        }
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("module slot size must be positive: "
                    + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (acceptableModuleIds == null) {
            throw new IllegalArgumentException("acceptableModuleIds must not be null");
        }
        for (String moduleId : acceptableModuleIds) {
            if (moduleId == null || moduleId.isBlank()) {
                throw new IllegalArgumentException("acceptable module id must be non-blank");
            }
        }
        acceptableModuleIds = List.copyOf(acceptableModuleIds);
        if (moduleOptions == null) {
            throw new IllegalArgumentException("moduleOptions must not be null");
        }
        moduleOptions = List.copyOf(moduleOptions);
    }

    /**
     * 便捷构造：无模块效果元数据的模块位（手作 / 旧夹具）。等价于 {@code moduleOptions = List.of()}。
     */
    public ModuleSlot(int offsetX, int offsetY, int offsetZ, int sizeX, int sizeY, int sizeZ,
            boolean acceptsAnyModule, List<String> acceptableModuleIds) {
        this(offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, acceptsAnyModule, acceptableModuleIds, List.of());
    }

    /** 某模块的效果汇总（无该模块 / 未声明效果时为空）。 */
    public Optional<ModuleEffectInfo> effectOf(String moduleId) {
        return optionFor(moduleId).map(ModuleOption::effect);
    }

    /** 某模块的选项元数据（无该模块时为空）。 */
    public Optional<ModuleOption> optionFor(String moduleId) {
        if (moduleId == null) {
            return Optional.empty();
        }
        for (ModuleOption option : moduleOptions) {
            if (option.moduleId().equals(moduleId)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    /** 区域体积（用于预算 / 排序）。 */
    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    /** 是否存在可安装模块：{@link #acceptsAnyModule} 为真，或列出的可接受模块非空。 */
    public boolean hasAcceptableModules() {
        return acceptsAnyModule || !acceptableModuleIds.isEmpty();
    }

    /** 给定模块注册名是否可装入本模块位。 */
    public boolean accepts(String moduleId) {
        return acceptsAnyModule || (moduleId != null && acceptableModuleIds.contains(moduleId));
    }
}
