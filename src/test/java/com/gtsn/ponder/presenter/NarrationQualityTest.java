package com.gtsn.ponder.presenter;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旁白「读得懂」质量守卫（工单 #18）：自动生成的旁白（结构化模板回退路径）在渲染后<b>不得</b>露出
 * 原始注册 id（{@code gtceu:...}）或 {@link StructureRole} 枚举名。用「一切键都存在」的注入谓词模拟
 * 完整语言表：所有 id / 角色 / 层级参数都应解析为语言键，留下的字面量只能是尺寸 / 计数 / 坐标 / 占位符。
 *
 * <p>纯逻辑、零 MC / GT：直接对生成器的 {@code narrationArgs} 跑 {@link NarrationLocalization}。</p>
 */
class NarrationQualityTest {

    private static StructureSource uncuratedMultiblock() {
        return StructureSource.builder("gtceu:test_machine")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(1, 1, 1, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addBlock(0, 0, 0, "gtceu:test_hatch", StructureRole.ITEM_INPUT)
                .addBlock(2, 0, 0, "gtceu:test_hatch", StructureRole.FLUID_INPUT)
                .addBlock(0, 0, 2, "gtceu:test_hatch", StructureRole.ITEM_OUTPUT)
                .addBlock(2, 0, 2, "gtceu:test_hatch", StructureRole.FLUID_OUTPUT)
                .addBlock(0, 2, 0, "gtceu:test_hatch", StructureRole.ENERGY_INPUT)
                .addBlock(2, 2, 0, "gtceu:test_hatch", StructureRole.MUFFLER)
                .addBlock(0, 2, 2, "gtceu:test_hatch", StructureRole.MAINTENANCE)
                .build();
    }

    private static SingleBlockMachineSource uncuratedSingleBlock() {
        return SingleBlockMachineSource.builder("gtceu:test_machine")
                .blockId("gtceu:test_machine")
                .tier(1, "LV")
                .itemInputs(1)
                .itemOutputs(1)
                .fluidInputs(1)
                .fluidOutputs(1)
                .energy(true)
                .addRecipeType("gtceu:test_recipe")
                .build();
    }

    private static void assertNoRawTokens(String sceneLabel, SceneData scene) {
        for (SceneStep step : scene.steps()) {
            if (step.narration() == null) {
                continue;
            }
            List<List<NarrationLocalization.Part>> resolved =
                    NarrationLocalization.resolveArgs(step.narrationArgs(), key -> true);
            for (List<NarrationLocalization.Part> arg : resolved) {
                for (NarrationLocalization.Part part : arg) {
                    if (part.translatable()) {
                        continue;
                    }
                    assertFalse(part.value().contains(":"),
                            sceneLabel + " step '" + step.id() + "' leaks a raw registry id: "
                                    + part.value());
                    assertFalse(StructureRole.fromName(part.value()).isPresent(),
                            sceneLabel + " step '" + step.id() + "' leaks a role enum name: "
                                    + part.value());
                }
            }
        }
    }

    @Test
    void fallbackMultiblockNarrationLeaksNoRawIdsOrEnumNames() {
        assertNoRawTokens("multiblock", SceneGenerator.generate(uncuratedMultiblock()));
    }

    @Test
    void fallbackUsageNarrationLeaksNoRawIdsOrEnumNames() {
        assertNoRawTokens("single-block", SingleBlockUsageGenerator.generate(uncuratedSingleBlock()));
    }

    @Test
    void fallbackTemplatesStayReadableCompleteSentences() {
        // 模板键必须存在且非空（渲染后即完整句子），避免回退到空旁白。
        SceneData multiblock = SceneGenerator.generate(uncuratedMultiblock());
        assertTrue(multiblock.steps().stream()
                        .filter(step -> step.narration() != null)
                        .allMatch(step -> !step.narration().isBlank()),
                "every narration step must carry a non-blank localization key");
    }
}
