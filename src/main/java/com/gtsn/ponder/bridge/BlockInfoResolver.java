package com.gtsn.ponder.bridge;

import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import java.util.Objects;
import java.util.Optional;

/**
 * 方块信息解析（工单 #21 反馈 3，纯逻辑部分）：把场景里被点击的<b>结构单元</b>翻译成可显示的
 * 本地化键——方块名（{@code block.<ns>.<path>}，GT / 原版随包 lang 均提供中英）与角色名
 * （{@link StructureRole} 经 datagen 本地化，如「能量输入仓」）。
 *
 * <p><b>分层</b>：本类刻意保持纯 Java（零 MC / GT 依赖），只做「结构坐标 → 稳定本地化键」的
 * 确定性推导，故可在 headless 单测锁定；GT 特有的附加信息（机器等级 / 电压层级）由唯一适配包
 * {@code com.gtsn.ponder.gt}（{@code GtBlockInfo}）提供，客户端把两者拼成最终覆盖层文本。</p>
 *
 * <p>缺参 / 坐标越界 / 无对应单元一律返回 {@link Optional#empty()}，不抛异常（前向兼容纪律）。</p>
 */
public final class BlockInfoResolver {

    /**
     * 一个可显示的结构单元信息。
     *
     * @param blockId   方块注册名（如 {@code gtceu:lv_input_bus}）
     * @param nameKey   方块名本地化键（{@code block.<ns>.<path>}）
     * @param roleKey   角色名本地化键；{@link StructureRole#PLAIN}（普通外壳）为 {@code null}
     * @param hatch     是否为仓口 / 总线单元
     * @param controller 是否为多方块控制器单元
     */
    public record BlockInfo(String blockId, String nameKey, String roleKey, boolean hatch, boolean controller) {

        public BlockInfo {
            Objects.requireNonNull(blockId, "blockId must not be null");
            Objects.requireNonNull(nameKey, "nameKey must not be null");
        }

        /** 是否携带可显示的角色名（普通外壳没有）。 */
        public boolean hasRole() {
            return roleKey != null && !roleKey.isBlank();
        }
    }

    private BlockInfoResolver() {
    }

    /** 由一个结构单元解析可显示信息（方块名恒有；角色名除普通外壳外恒有）。 */
    public static Optional<BlockInfo> resolve(StructureBlock block) {
        if (block == null || block.blockId() == null || block.blockId().isBlank()) {
            return Optional.empty();
        }
        StructureRole role = block.role() == null ? StructureRole.PLAIN : block.role();
        String roleKey = role == StructureRole.PLAIN ? null : GeneratedKeys.roleKey(role.name());
        return Optional.of(new BlockInfo(
                block.blockId(),
                GeneratedKeys.blockNameKey(block.blockId()),
                roleKey,
                role.isHatch(),
                role == StructureRole.CONTROLLER));
    }

    /** 结构局部坐标处的单元（不存在则为空）。 */
    public static Optional<StructureBlock> blockAt(StructureSource structure, int x, int y, int z) {
        if (structure == null) {
            return Optional.empty();
        }
        for (StructureBlock block : structure.blocks()) {
            if (block.x() == x && block.y() == y && block.z() == z) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    /** 结构局部坐标处单元的显示信息（不存在则为空）。 */
    public static Optional<BlockInfo> resolveAt(StructureSource structure, int x, int y, int z) {
        return blockAt(structure, x, y, z).flatMap(BlockInfoResolver::resolve);
    }
}
