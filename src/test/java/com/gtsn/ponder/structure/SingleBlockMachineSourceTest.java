package com.gtsn.ponder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单方块机器源（{@link SingleBlockMachineSource}）的外部可观察行为：构造校验与派生的能力视图。
 * 纯逻辑、零 MC / GT。
 */
class SingleBlockMachineSourceTest {

    private static SingleBlockMachineSource macerator() {
        return SingleBlockMachineSource.builder("gtceu:lv_macerator")
                .displayName("block.gtceu.lv_macerator")
                .blockId("gtceu:lv_macerator")
                .tier(1, "LV")
                .itemInputs(1)
                .itemOutputs(1)
                .energy(true)
                .addRecipeType("gtceu:macerator")
                .build();
    }

    @Test
    void carriesMachineMetadataAndDerivedAbilityViews() {
        SingleBlockMachineSource source = macerator();

        assertEquals("gtceu:lv_macerator", source.id());
        assertEquals("gtceu:lv_macerator", source.blockId());
        assertEquals(1, source.tier());
        assertEquals("LV", source.tierName());
        assertEquals(1, source.itemInputs());
        assertEquals(1, source.itemOutputs());
        assertEquals(0, source.fluidInputs());
        assertEquals(0, source.fluidOutputs());
        assertTrue(source.hasEnergy());
        assertTrue(source.hasItemIo());
        assertFalse(source.hasFluidIo());
        assertTrue(source.hasRecipeTypes());
        assertEquals(List.of("gtceu:macerator"), source.recipeTypeIds());
    }

    @Test
    void displayNameFallsBackToTheId() {
        SingleBlockMachineSource source = SingleBlockMachineSource.builder("gtceu:thing")
                .blockId("gtceu:thing")
                .build();

        assertEquals("gtceu:thing", source.displayName());
        assertEquals("", source.tierName());
        assertFalse(source.hasEnergy());
        assertFalse(source.hasRecipeTypes());
    }

    @Test
    void rejectsBlankIdAndBlockId() {
        assertThrows(IllegalArgumentException.class,
                () -> SingleBlockMachineSource.builder("  ").blockId("gtceu:thing").build());
        assertThrows(IllegalArgumentException.class,
                () -> SingleBlockMachineSource.builder("gtceu:thing").blockId("").build());
    }

    @Test
    void rejectsNegativeTierAndCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> SingleBlockMachineSource.builder("gtceu:thing").blockId("gtceu:thing").tier(-1, "ULV").build());
        assertThrows(IllegalArgumentException.class,
                () -> SingleBlockMachineSource.builder("gtceu:thing").blockId("gtceu:thing").itemInputs(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> SingleBlockMachineSource.builder("gtceu:thing").blockId("gtceu:thing").fluidOutputs(-2).build());
    }

    @Test
    void recipeTypeIdsAreImmutableCopies() {
        java.util.List<String> mutable = new java.util.ArrayList<>(List.of("gtceu:first"));
        SingleBlockMachineSource source = SingleBlockMachineSource.builder("gtceu:thing")
                .blockId("gtceu:thing")
                .recipeTypeIds(mutable)
                .build();

        mutable.add("gtceu:second");

        assertEquals(List.of("gtceu:first"), source.recipeTypeIds());
    }
}
