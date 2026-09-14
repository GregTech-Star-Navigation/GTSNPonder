package com.gtsn.ponder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块位与其可接受模块的<b>效果元数据</b>（{@link ModuleEffectInfo} / {@link ModuleOption}）的
 * 外部可观察行为：模块位携带每个可接受模块的效果汇总，供自动生成器产出「安装 → 效果汇总」演示。
 *
 * <p>纯 Java、零 MC / GT 依赖（自动生成缝可在 headless 单测中以 DTO 夹具断言）。</p>
 */
class ModuleSlotTest {

    private static ModuleEffectInfo effect(int parallel, double speed, double energy,
            double input, double output, int tier) {
        return new ModuleEffectInfo(parallel, speed, energy, input, output, tier);
    }

    @Test
    void effectInfoExposesAllForkSummaryFields() {
        ModuleEffectInfo info = effect(4, 0.5d, 0.8d, 1.2d, 1.5d, 1);

        assertEquals(4, info.parallelCapacity());
        assertEquals(0.5d, info.speedMultiplier(), 1.0e-9d);
        assertEquals(0.8d, info.energyMultiplier(), 1.0e-9d);
        assertEquals(1.2d, info.inputMultiplier(), 1.0e-9d);
        assertEquals(1.5d, info.outputMultiplier(), 1.0e-9d);
        assertEquals(1, info.tierBonus());
        assertFalse(info.isEmpty());
    }

    @Test
    void emptyEffectInfoIsNeutral() {
        assertTrue(ModuleEffectInfo.EMPTY.isEmpty());
        assertEquals(new ModuleEffectInfo(0, 1.0d, 1.0d, 1.0d, 1.0d, 0), ModuleEffectInfo.EMPTY);
    }

    @Test
    void effectInfoRejectsNonPositiveMultipliersAndNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> effect(-1, 1.0d, 1.0d, 1.0d, 1.0d, 0));
        assertThrows(IllegalArgumentException.class, () -> effect(0, 0.0d, 1.0d, 1.0d, 1.0d, 0));
        assertThrows(IllegalArgumentException.class, () -> effect(0, 1.0d, 1.0d, 1.0d, 1.0d, -1));
    }

    @Test
    void moduleOptionCarriesIdAndEffectAndRejectsBlankId() {
        ModuleOption option = new ModuleOption("gtceu:parallel_module", effect(4, 1.0d, 1.0d, 1.0d, 1.0d, 0));

        assertEquals("gtceu:parallel_module", option.moduleId());
        assertEquals(4, option.effect().parallelCapacity());
        assertThrows(IllegalArgumentException.class, () -> new ModuleOption(" ", ModuleEffectInfo.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ModuleOption(null, ModuleEffectInfo.EMPTY));
    }

    @Test
    void moduleOptionWithoutEffectDegradesToEmpty() {
        ModuleOption option = new ModuleOption("gtceu:plain_module", null);

        assertTrue(option.effect().isEmpty());
    }

    @Test
    void moduleSlotCarriesPerModuleEffects() {
        ModuleOption parallel = new ModuleOption("gtceu:parallel_module", effect(4, 1.0d, 1.0d, 1.0d, 1.0d, 0));
        ModuleOption speed = new ModuleOption("gtceu:speed_module", effect(0, 0.5d, 1.0d, 1.0d, 1.0d, 0));
        ModuleSlot slot = new ModuleSlot(0, 0, 0, 1, 1, 1, false,
                List.of("gtceu:parallel_module", "gtceu:speed_module"), List.of(parallel, speed));

        assertEquals(List.of(parallel, speed), slot.moduleOptions());
        assertEquals(Optional.of(parallel), slot.optionFor("gtceu:parallel_module"));
        assertEquals(0.5d, slot.effectOf("gtceu:speed_module").orElseThrow().speedMultiplier(), 1.0e-9d);
        assertTrue(slot.optionFor("gtceu:absent").isEmpty(),
                "unknown module must yield no option");
        assertTrue(slot.effectOf("gtceu:absent").isEmpty());
    }

    @Test
    void moduleSlotWithoutOptionsStillWorksViaLegacyConstructor() {
        ModuleSlot slot = new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of());

        assertTrue(slot.moduleOptions().isEmpty());
        assertTrue(slot.optionFor("gtceu:anything").isEmpty());
        assertTrue(slot.hasAcceptableModules());
    }

    @Test
    void moduleSlotOptionsAreDefensivelyCopied() {
        ModuleSlot slot = new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of(),
                List.of(new ModuleOption("gtceu:a", ModuleEffectInfo.EMPTY)));
        List<ModuleOption> options = slot.moduleOptions();
        assertThrows(UnsupportedOperationException.class,
                () -> options.add(new ModuleOption("gtceu:b", ModuleEffectInfo.EMPTY)));
    }
}
