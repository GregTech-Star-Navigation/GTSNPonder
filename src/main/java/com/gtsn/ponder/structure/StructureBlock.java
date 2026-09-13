package com.gtsn.ponder.structure;

/**
 * 结构源中的一个方块单元：结构局部坐标 {@code (x, y, z)} 与方块注册名 {@code blockId}。
 *
 * <p>坐标以结构包围盒的角点为原点、0 基；{@code blockId} 是原版注册名（如
 * {@code gtceu:bronze_casing}），由视口 / 世界桥在客户端解析为实际 {@code BlockState}
 * （缺资源时跳过）。</p>
 *
 * <p>纯 Java：不依赖 Minecraft，也不依赖 GT（结构源是适配包产出的中立 DTO）。</p>
 */
public record StructureBlock(int x, int y, int z, String blockId) {

    public StructureBlock {
        if (x < 0 || y < 0 || z < 0) {
            throw new IllegalArgumentException("structure block coordinates must be non-negative: "
                    + x + "," + y + "," + z);
        }
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must be non-blank");
        }
    }
}
