package com.gtsn.ponder.generate;

import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「使用场景」生成器（{@link SingleBlockUsageGenerator}）的外部可观察行为：给定单方块机器源夹具，
 * 断言产出的 {@link SceneData}（机器本体 + 使用讲解步骤、封闭 {@link StepType}、确定性、可播放性）。
 * 纯逻辑、零 MC / GT，不依赖 GT 运行时。
 */
class SingleBlockUsageGeneratorTest {

    /** 基础电力粉碎机：物品输入 / 输出、接电、有配方类型。 */
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

    /** 洗矿机（未登记手写解说）：物品 + 流体进出，接电，用于结构化模板路径。 */
    private static SingleBlockMachineSource electrolyzer() {
        return SingleBlockMachineSource.builder("gtceu:lv_ore_washer")
                .displayName("block.gtceu.lv_ore_washer")
                .blockId("gtceu:lv_ore_washer")
                .tier(1, "LV")
                .itemInputs(1)
                .itemOutputs(2)
                .fluidInputs(1)
                .fluidOutputs(1)
                .energy(true)
                .addRecipeType("gtceu:ore_washer")
                .build();
    }

    /** 无物品 / 流体输入输出的机器（仅能量）：验证退化旁白键。 */
    private static SingleBlockMachineSource energyOnly() {
        return SingleBlockMachineSource.builder("gtceu:lv_transformer_1a")
                .blockId("gtceu:lv_transformer_1a")
                .tier(1, "LV")
                .energy(true)
                .build();
    }

    /** 未被手写解说登记的单方块机器（验证结构化模板回退）。 */
    private static SingleBlockMachineSource machineWithoutCuration() {
        return SingleBlockMachineSource.builder("gtceu:lv_electric_furnace")
                .displayName("block.gtceu.lv_electric_furnace")
                .blockId("gtceu:lv_electric_furnace")
                .tier(1, "LV")
                .itemInputs(1)
                .itemOutputs(1)
                .energy(true)
                .addRecipeType("gtceu:electric_furnace")
                .build();
    }

    private static Optional<SceneStep> step(SceneData scene, String id) {
        return scene.steps().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Test
    void structureOfIsAOneByOneMachineBlockActingAsController() {
        StructureSource structure = SingleBlockUsageGenerator.structureOf(macerator());

        assertEquals(1, structure.sizeX());
        assertEquals(1, structure.sizeY());
        assertEquals(1, structure.sizeZ());
        assertEquals(1, structure.blockCount());
        assertTrue(structure.hasController());
        StructureBlock block = structure.blocks().get(0);
        assertEquals("gtceu:lv_macerator", block.blockId());
        assertEquals(StructureRole.CONTROLLER, block.role());
        assertEquals(0, structure.controller().x());
    }

    @Test
    void generatedUsageSceneCarriesAutoSourceAndUsageIdentifiers() {
        SceneData scene = SingleBlockUsageGenerator.generate(macerator());

        assertEquals(Source.AUTO, scene.source());
        assertEquals(SingleBlockUsageGenerator.GENERATOR_VERSION, scene.generatorVersion());
        assertEquals("gtceu:lv_macerator", scene.target());
        assertEquals("default", scene.variant());
        assertEquals(SingleBlockUsageGenerator.sceneIdFor("gtceu:lv_macerator"), scene.id());
        assertTrue(scene.id().startsWith(SingleBlockUsageGenerator.SCENE_ID_PREFIX), scene.id());
        assertFalse(scene.steps().isEmpty());
    }

    @Test
    void usageSceneTitleIsTheGtBlockLocalizationKey() {
        // 复用 GT 自身的方块名键，故任意单方块机器（含目录合成条目）都有本地化标题。
        assertEquals("block.gtceu.lv_macerator",
                SingleBlockUsageGenerator.titleKeyFor("gtceu:lv_macerator"));
        assertEquals(SingleBlockUsageGenerator.titleKeyFor("gtceu:lv_macerator"),
                SingleBlockUsageGenerator.generate(macerator()).title());
        assertEquals("block.gtsnponder.test", SingleBlockUsageGenerator.titleKeyFor("gtsnponder:test"));
    }

    @Test
    void declaredElementsAreJustTheMachineBlock() {
        SceneData scene = SingleBlockUsageGenerator.generate(macerator());

        assertEquals(List.of(SingleBlockUsageGenerator.ELEMENT_MACHINE),
                scene.elements().stream().map(SceneElement::id).toList());
        SceneElement machine = scene.elements().get(0);
        assertEquals("all", machine.params().get("selector"),
                "the single-block structure's element selects the whole (1x1x1) structure");
    }

    @Test
    void usageSceneShapeIsUsageStepsNotBuildOrFormedDemo() {
        SceneData scene = SingleBlockUsageGenerator.generate(electrolyzer());

        assertEquals(List.of("focus.camera", "build.machine", "text.purpose", "highlight.machine",
                "text.setup", "outline.machine", "text.inputs", "text.outputs", "text.energy",
                "text.pitfalls", "text.covers"),
                scene.steps().stream().map(SceneStep::id).toList());

        assertTrue(scene.steps().stream().noneMatch(s -> s.id().startsWith("formed.")),
                "single-block usage scenes must not contain a formed demonstration");
        assertTrue(scene.steps().stream().noneMatch(s -> s.type() == StepType.INSTALL_MODULE),
                "single-block usage scenes must not install multiblock modules");
        assertTrue(scene.steps().stream().noneMatch(s -> s.id().startsWith("build.layer.")
                        || s.id().startsWith("build.role.")),
                "single-block usage scenes must not contain a layer/role build sequence");

        SceneStep camera = step(scene, "focus.camera").orElseThrow();
        assertEquals(StepType.CAMERA, camera.type());
        assertEquals(Boolean.TRUE, camera.params().get("fit"));
        assertEquals(SingleBlockUsageGenerator.FIT_MARGIN,
                ((Number) camera.params().get("margin")).doubleValue(), 1.0e-9d);

        SceneStep highlight = step(scene, "highlight.machine").orElseThrow();
        assertEquals(StepType.HIGHLIGHT, highlight.type());
        assertEquals(List.of(SingleBlockUsageGenerator.ELEMENT_MACHINE), highlight.targets());
        assertEquals(Boolean.TRUE, highlight.params().get("visible"));

        // 反馈 1（工单 #21）：本体高亮之外还有一段蓝色轮廓，形成「高亮 → 轮廓」的两段式节奏。
        SceneStep outline = step(scene, "outline.machine").orElseThrow();
        assertEquals(StepType.OUTLINE, outline.type());
        assertEquals(List.of(SingleBlockUsageGenerator.ELEMENT_MACHINE), outline.targets());
        assertEquals(Boolean.TRUE, outline.params().get("visible"));
        long highlightPhases = scene.steps().stream()
                .filter(s -> s.type() == StepType.HIGHLIGHT || s.type() == StepType.OUTLINE)
                .count();
        assertTrue(highlightPhases >= 2, "usage scenes need a highlight sequence, not a single frame");
    }

    @Test
    void narrationCoversPurposeSetupInputsOutputsEnergyAndPitfalls() {
        SceneData scene = SingleBlockUsageGenerator.generate(electrolyzer());

        assertEquals(SingleBlockUsageGenerator.NARRATION_PURPOSE,
                step(scene, "text.purpose").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_SETUP,
                step(scene, "text.setup").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_INPUTS,
                step(scene, "text.inputs").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_OUTPUTS,
                step(scene, "text.outputs").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_ENERGY,
                step(scene, "text.energy").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_PITFALLS,
                step(scene, "text.pitfalls").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_COVERS,
                step(scene, "text.covers").orElseThrow().narration());

        // 机器特定：模板参数携带 id / 等级 / 配方类型与槽位·罐数。
        assertEquals(List.of("gtceu:lv_ore_washer", "LV", "gtceu:ore_washer"),
                step(scene, "text.purpose").orElseThrow().narrationArgs());
        assertEquals(List.of("1", "1"), step(scene, "text.inputs").orElseThrow().narrationArgs());
        assertEquals(List.of("2", "1"), step(scene, "text.outputs").orElseThrow().narrationArgs());
    }

    @Test
    void narrationFollowsTheFixedTeachingOrder() {
        SceneData scene = SingleBlockUsageGenerator.generate(electrolyzer());
        List<String> ids = scene.steps().stream().map(SceneStep::id).toList();

        int purpose = ids.indexOf("text.purpose");
        int setup = ids.indexOf("text.setup");
        int inputs = ids.indexOf("text.inputs");
        int outputs = ids.indexOf("text.outputs");
        int energy = ids.indexOf("text.energy");
        int pitfalls = ids.indexOf("text.pitfalls");
        assertTrue(purpose >= 0 && setup >= 0 && inputs >= 0 && outputs >= 0 && energy >= 0 && pitfalls >= 0,
                () -> "missing a teaching step: " + ids);
        assertTrue(purpose < setup && setup < inputs && inputs < outputs && outputs < energy
                        && energy < pitfalls,
                () -> "teaching steps must follow 用途→搭建→输入→输出→供能→坑: " + ids);
        assertTrue(ids.indexOf("highlight.machine") < setup,
                () -> "the machine highlight must precede the setup explanation: " + ids);
    }

    @Test
    void curatedSingleBlockMachineUsesTheHandWrittenKeys() {
        SceneData scene = SingleBlockUsageGenerator.generate(macerator());

        assertTrue(MachineDescriptions.isCurated("gtceu:lv_macerator"));
        assertEquals(MachineDescriptions.key("gtceu:lv_macerator", MachineDescriptions.Field.PURPOSE),
                step(scene, "text.purpose").orElseThrow().narration());
        assertTrue(step(scene, "text.purpose").orElseThrow().narrationArgs().isEmpty(),
                "hand-written purpose is a complete sentence (no template args)");
        assertEquals(MachineDescriptions.key("gtceu:lv_macerator", MachineDescriptions.Field.PITFALLS),
                step(scene, "text.pitfalls").orElseThrow().narration());
    }

    @Test
    void uncuratedSingleBlockMachineFallsBackToTheStructuredTemplate() {
        SceneData scene = SingleBlockUsageGenerator.generate(machineWithoutCuration());

        assertFalse(MachineDescriptions.isCurated("gtceu:lv_electric_furnace"));
        assertEquals(SingleBlockUsageGenerator.NARRATION_PURPOSE,
                step(scene, "text.purpose").orElseThrow().narration());
        assertEquals(List.of("gtceu:lv_electric_furnace", "LV", "gtceu:electric_furnace"),
                step(scene, "text.purpose").orElseThrow().narrationArgs());
    }

    @Test
    void machinesWithoutInputsOrOutputsSaySoExplicitly() {
        SceneData scene = SingleBlockUsageGenerator.generate(energyOnly());

        assertEquals(SingleBlockUsageGenerator.NARRATION_INPUTS_NONE,
                step(scene, "text.inputs").orElseThrow().narration());
        assertEquals(SingleBlockUsageGenerator.NARRATION_OUTPUTS_NONE,
                step(scene, "text.outputs").orElseThrow().narration());
        assertEquals(List.of("0", "0"), step(scene, "text.inputs").orElseThrow().narrationArgs());
    }

    @Test
    void sameSourceProducesByteEqualJsonAndResolvableElements() {
        for (SingleBlockMachineSource source : List.of(macerator(), electrolyzer(), energyOnly())) {
            SceneData first = SingleBlockUsageGenerator.generate(source);
            SceneData second = SingleBlockUsageGenerator.generate(source);
            assertEquals(SceneDataWriter.toJson(first), SceneDataWriter.toJson(second),
                    "usage generation must be deterministic for " + source.id());

            StructureSource structure = SingleBlockUsageGenerator.structureOf(source);
            SceneElementResolver resolver = new SceneElementResolver(structure);
            Map<String, List<StructureBlock>> resolved = resolver.resolveAll(first.elements());
            for (SceneElement element : first.elements()) {
                assertFalse(resolved.get(element.id()).isEmpty(),
                        "element '" + element.id() + "' resolves to nothing for " + source.id());
            }
        }
    }

    @Test
    void everyStepTargetReferencesADeclaredElement() {
        for (SingleBlockMachineSource source : List.of(macerator(), electrolyzer())) {
            SceneData scene = SingleBlockUsageGenerator.generate(source);
            List<String> elementIds = scene.elements().stream().map(SceneElement::id).toList();
            for (SceneStep step : scene.steps()) {
                for (String target : step.targets()) {
                    assertTrue(elementIds.contains(target),
                            "step '" + step.id() + "' targets undeclared element '" + target + "'");
                }
            }
        }
    }
}
