package com.gtsn.ponder.structure;

import java.util.List;

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
        List<String> acceptableModuleIds) {

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
