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

    @Test
    void structureBlockRoleDefaultsToPlainAndCarriesExplicitRole() {
        StructureBlock plain = new StructureBlock(0, 0, 0, "minecraft:stone");
        assertEquals(StructureRole.PLAIN, plain.role());
        assertFalse(plain.isHatch());

        StructureBlock hatch = new StructureBlock(0, 0, 0, "gtceu:coke_oven_hatch", StructureRole.OTHER_HATCH);
        assertEquals(StructureRole.OTHER_HATCH, hatch.role());
        assertTrue(hatch.isHatch());
    }

    @Test
    void hatchRolesAreCarriedOnBlocksAndExposedAsHatches() {
        StructureSource source = StructureSource.builder("gtceu:coke_oven")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(new StructureBlock(0, 0, 0, "gtceu:coke_oven_hatch", StructureRole.OTHER_HATCH))
                .addBlock(new StructureBlock(2, 2, 2, "gtceu:bronze_casing"))
                .build();

        assertEquals(2, source.blockCount());
        assertEquals(1, source.hatches().size());
        StructureBlock hatch = source.hatches().get(0);
        assertEquals("gtceu:coke_oven_hatch", hatch.blockId());
        assertTrue(hatch.role().isHatch());
        assertEquals(StructureRole.PLAIN, source.blocks().get(1).role());
    }

    @Test
    void moduleSlotsAreKept() {
        ModuleSlot slot = new ModuleSlot(1, 1, 1, 2, 2, 2, false, List.of("gtceu:test_module"));
        StructureSource source = StructureSource.builder("gtceu:x")
                .size(4, 4, 4)
                .controller(0, 0, 0)
                .addModuleSlot(slot)
                .build();

        assertTrue(source.hasModuleSlots());
        assertEquals(1, source.moduleSlotCount());
        assertEquals(slot, source.moduleSlots().get(0));
        assertTrue(slot.hasAcceptableModules());
        assertTrue(slot.accepts("gtceu:test_module"));
        assertFalse(slot.accepts("gtceu:other_module"));
    }

    @Test
    void moduleSlotAcceptingNoModulesIsRepresented() {
        ModuleSlot slot = new ModuleSlot(0, 0, 0, 1, 1, 1, false, List.of());

        assertFalse(slot.hasAcceptableModules());
        assertFalse(slot.accepts("gtceu:anything"));

        StructureSource source = StructureSource.builder("gtceu:x")
                .size(1, 1, 1)
                .addModuleSlot(slot)
                .build();
        assertEquals(1, source.moduleSlotCount());
    }

    @Test
    void rejectsModuleSlotOutsideDeclaredSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> StructureSource.builder("gtceu:x")
                        .size(2, 2, 2)
                        .addModuleSlot(new ModuleSlot(1, 1, 1, 2, 2, 2, true, List.of()))
                        .build());
        assertTrue(ex.getMessage().contains("module slot"));
    }

    @Test
    void moduleSlotListIsImmutable() {
        StructureSource source = StructureSource.builder("gtceu:x")
                .size(1, 1, 1)
                .addModuleSlot(new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of()))
                .build();
        List<ModuleSlot> slots = source.moduleSlots();
        assertThrows(UnsupportedOperationException.class,
                () -> slots.add(new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of())));
    }

    @Test
    void moduleSlotRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleSlot(-1, 0, 0, 1, 1, 1, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModuleSlot(0, 0, 0, 0, 1, 1, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModuleSlot(0, 0, 0, 1, 1, 1, true, null));
        assertThrows(IllegalArgumentException.class, () -> new ModuleSlot(0, 0, 0, 1, 1, 1, false, List.of(" ")));
    }

    @Test
    void emptyStructureIsAllowed() {
        StructureSource source = StructureSource.builder("gtceu:x").size(1, 1, 1).build();

        assertTrue(source.isEmpty());
        assertEquals(0, source.blockCount());
        assertFalse(source.hasModuleSlots());
        assertEquals(0, source.moduleSlotCount());
        assertTrue(source.hatches().isEmpty());
    }

    @Test
    void blockAndModuleSlotListsAreDefensivelyCopied() {
        StructureBlock block = new StructureBlock(0, 0, 0, "minecraft:stone");
        ModuleSlot slot = new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of());
        StructureSource.Builder builder = StructureSource.builder("gtceu:x")
                .size(1, 1, 1)
                .addBlock(block)
                .addModuleSlot(slot);
        StructureSource source = builder.build();

        // Mutating the builder after build must not affect the built source.
        builder.addBlock(new StructureBlock(0, 0, 0, "minecraft:dirt"));
        builder.addModuleSlot(new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of()));
        assertEquals(1, source.blockCount());
        assertEquals(1, source.moduleSlotCount());
    }
}
