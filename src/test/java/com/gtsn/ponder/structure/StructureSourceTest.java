package com.gtsn.ponder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StructureSource} DTO 的外部可观察行为：尺寸校验、方块越界拒绝、控制器可选、
 * 体积计算与不可变性。纯 Java、零 MC 依赖。
 */
class StructureSourceTest {

    @Test
    void keepsDeclaredSizeAndBlocks() {
        StructureSource source = StructureSource.builder("gtceu:coke_oven")
                .displayName("Coke Oven")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(0, 0, 0, "gtceu:coke_oven_bricks")
                .addBlock(1, 1, 1, "gtceu:coke_oven")
                .build();

        assertEquals("gtceu:coke_oven", source.id());
        assertEquals("Coke Oven", source.displayName());
        assertEquals(3, source.sizeX());
        assertEquals(3, source.sizeY());
        assertEquals(3, source.sizeZ());
        assertEquals(2, source.blockCount());
        assertEquals(27, source.volume());
        assertFalse(source.isEmpty());
        assertTrue(source.hasController());
        assertEquals(new StructureSource.ControllerCell(1, 1, 1), source.controller());
    }

    @Test
    void displayNameFallsBackToId() {
        StructureSource source = StructureSource.builder("gtceu:x").size(1, 1, 1)
                .addBlock(0, 0, 0, "minecraft:stone").build();
        assertEquals("gtceu:x", source.displayName());
    }

    @Test
    void controllerIsOptional() {
        StructureSource source = StructureSource.builder("gtceu:x").size(1, 1, 1)
                .addBlock(0, 0, 0, "minecraft:stone").build();

        assertFalse(source.hasController());
        assertThrows(IllegalStateException.class, source::controller);
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class,
                () -> StructureSource.builder("gtceu:x").size(0, 3, 3).build());
    }

    @Test
    void rejectsBlockOutsideDeclaredSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> StructureSource.builder("gtceu:x").size(2, 2, 2)
                        .addBlock(2, 0, 0, "minecraft:stone").build());
        assertTrue(ex.getMessage().contains("outside"));
    }

    @Test
    void blockListIsImmutable() {
        StructureSource source = StructureSource.builder("gtceu:x").size(1, 1, 1)
                .addBlock(0, 0, 0, "minecraft:stone").build();
        List<StructureBlock> blocks = source.blocks();
        assertThrows(UnsupportedOperationException.class,
                () -> blocks.add(new StructureBlock(0, 0, 0, "minecraft:dirt")));
    }

    @Test
    void structureBlockRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new StructureBlock(-1, 0, 0, "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new StructureBlock(0, 0, 0, " "));
        assertThrows(IllegalArgumentException.class, () -> new StructureBlock(0, 0, 0, null));
    }
}
