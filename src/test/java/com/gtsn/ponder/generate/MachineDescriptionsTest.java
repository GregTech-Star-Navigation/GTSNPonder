package com.gtsn.ponder.generate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键机器手写解说注册表（{@link MachineDescriptions}）的行为：登记数量 / 覆盖形态 / 键推导 /
 * 命中判定，以及建议机器集合齐全。纯逻辑、零 MC / GT。
 */
class MachineDescriptionsTest {

    /** 工单 #18 建议的关键机器（多方块 + 单方块），每条都必须登记。 */
    private static final Set<String> SUGGESTED = Set.of(
            "gtceu:coke_oven",
            "gtceu:electric_blast_furnace",
            "gtceu:large_chemical_reactor",
            "gtceu:assembly_line",
            "gtceu:cleanroom",
            "gtceu:distillation_tower",
            "gtceu:gas_large_turbine",
            "gtceu:lv_macerator",
            "gtceu:lv_centrifuge",
            "gtceu:lv_compressor",
            "gtceu:lv_mixer",
            "gtceu:lv_electrolyzer");

    @Test
    void registersAtLeastTwelveMachinesCoveringBothKinds() {
        assertTrue(MachineDescriptions.CURATED.size() >= 12,
                "at least 12 curated machines are required, got " + MachineDescriptions.CURATED.size());

        long multiblocks = MachineDescriptions.CURATED.stream()
                .filter(curated -> curated.kind() == MachineDescriptions.Kind.MULTIBLOCK).count();
        long singleBlocks = MachineDescriptions.CURATED.stream()
                .filter(curated -> curated.kind() == MachineDescriptions.Kind.SINGLE_BLOCK).count();
        assertTrue(multiblocks >= 1, "multiblock machines must be curated");
        assertTrue(singleBlocks >= 1, "single-block machines must be curated");
    }

    @Test
    void coversEverySuggestedKeyMachine() {
        Set<String> curated = new HashSet<>(MachineDescriptions.curatedIds());
        for (String suggested : SUGGESTED) {
            assertTrue(curated.contains(suggested), "suggested machine is not curated: " + suggested);
        }
    }

    @Test
    void curatedIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (MachineDescriptions.Curated curated : MachineDescriptions.CURATED) {
            assertTrue(seen.add(curated.id()), "duplicate curated id: " + curated.id());
        }
    }

    @Test
    void keyDerivationIsStableAndSanitized() {
        assertEquals("ponder.gtsnponder.desc.gtceu_coke_oven.purpose",
                MachineDescriptions.key("gtceu:coke_oven", MachineDescriptions.Field.PURPOSE));
        assertEquals("ponder.gtsnponder.desc.gtceu_coke_oven.pitfalls",
                MachineDescriptions.key("gtceu:coke_oven", MachineDescriptions.Field.PITFALLS));
    }

    @Test
    void allKeysCoverEveryFieldForEveryMachineAndAreUnique() {
        Set<String> keys = new HashSet<>();
        for (MachineDescriptions.Curated curated : MachineDescriptions.CURATED) {
            for (MachineDescriptions.Field field : MachineDescriptions.Field.values()) {
                String key = MachineDescriptions.key(curated.id(), field);
                assertTrue(keys.add(key), "duplicate description key: " + key);
            }
        }
        assertEquals(MachineDescriptions.CURATED.size() * MachineDescriptions.Field.values().length,
                MachineDescriptions.allKeys().size());
        assertEquals(keys, new HashSet<>(MachineDescriptions.allKeys()));
    }

    @Test
    void isCuratedMatchesTheRegistryAndRejectsUnknownIds() {
        assertTrue(MachineDescriptions.isCurated("gtceu:coke_oven"));
        assertTrue(MachineDescriptions.isCurated("gtceu:lv_centrifuge"));
        assertFalse(MachineDescriptions.isCurated("gtceu:lv_electric_furnace"));
        assertFalse(MachineDescriptions.isCurated("gtceu:not_a_real_machine"));
        assertFalse(MachineDescriptions.isCurated(null));
    }

    @Test
    void fieldsFollowTheFixedTeachingOrder() {
        assertEquals(java.util.List.of(
                        MachineDescriptions.Field.PURPOSE,
                        MachineDescriptions.Field.SETUP,
                        MachineDescriptions.Field.INPUTS,
                        MachineDescriptions.Field.OUTPUTS,
                        MachineDescriptions.Field.ENERGY,
                        MachineDescriptions.Field.PITFALLS),
                java.util.List.of(MachineDescriptions.Field.values()));
    }
}
