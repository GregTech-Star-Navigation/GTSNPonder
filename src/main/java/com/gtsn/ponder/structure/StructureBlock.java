package com.gtsn.ponder.structure;

import java.util.Objects;

/**
 * 结构源中的一个方块单元：结构局部坐标 {@code (x, y, z)}、方块注册名 {@code blockId}
 * 与角色 {@link StructureRole role}。
 *
 * <p>坐标以结构包围盒的角点为原点、0 基；{@code blockId} 是原版注册名（如
 * {@code gtceu:bronze_casing}），由视口 / 世界桥在客户端解析为实际 {@code BlockState}
 * （缺资源时跳过）。</p>
 *
 * <p>角色默认为 {@link StructureRole#PLAIN}（4 参构造器）；适配包在识别出控制器 / 仓口 / 总线
 * 时传入显式角色。角色是「仓口 / 总线分类」的载体，见 {@link StructureRole}。</p>
 *
 * <p>纯 Java：不依赖 Minecraft，也不依赖 GT（结构源是适配包产出的中立 DTO）。</p>
 */
public record StructureBlock(int x, int y, int z, String blockId, StructureRole role) {

    public StructureBlock {
        if (x < 0 || y < 0 || z < 0) {
            throw new IllegalArgumentException("structure block coordinates must be non-negative: "
                    + x + "," + y + "," + z);
        }
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must be non-blank");
        }
        Objects.requireNonNull(role, "role");
    }

    /** 便捷构造：无特殊角色（{@link StructureRole#PLAIN}）的方块单元。 */
    public StructureBlock(int x, int y, int z, String blockId) {
        this(x, y, z, blockId, StructureRole.PLAIN);
    }

    /** 是否为仓口 / 总线（见 {@link StructureRole#isHatch()}）。 */
    public boolean isHatch() {
        return role.isHatch();
    }
}
