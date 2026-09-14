package com.gtsn.ponder.generate;

import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动生成器（{@link SceneGenerator}）的外部可观察行为：给定 {@link StructureSource} 夹具，
 * 断言产出的 {@link SceneData}（构建顺序、预算切换、高亮顺序、成型演示、确定性）。
 *
 * <p>这是规格 §Testing Decisions 的「自动生成缝」：{@code StructureSource → SceneGenerator →
 * 断言 SceneData}，不依赖 GT / MC 运行时。</p>
 */
class SceneGeneratorTest {

    // --- 夹具 ---------------------------------------------------------------

    /** 3×3×3 实心（27 方块）≤ 预算 → 逐层模式；含控制器与一个物品输入总线。 */
    private static StructureSource small() {
        return solidGrid("gtceu:small_machine", 3, new int[] { 1, 1, 1 },
                Map.of("0,1,1", StructureRole.ITEM_INPUT));
    }

    /** 5×5×5 实心（125 方块）> 预算 → 角色分组模式；含控制器、物品输入、流体输出。 */
    private static StructureSource medium() {
        return solidGrid("gtceu:medium_machine", 5, new int[] { 2, 2, 2 },
                Map.of("0,2,2", StructureRole.ITEM_INPUT, "4,2,2", StructureRole.FLUID_OUTPUT));
    }

    /** 7×7×7 实心（343 方块）≫ 预算 → 角色分组模式；含控制器、消声仓、能量输入。 */
    private static StructureSource large() {
        return solidGrid("gtceu:large_machine", 7, new int[] { 3, 3, 3 },
                Map.of("0,3,3", StructureRole.MUFFLER, "6,3,3", StructureRole.ENERGY_INPUT));
    }

    /**
     * 生成一个 {@code n×n×n} 实心结构；控制器与仓口按坐标覆盖默认（PLAIN）角色。
     * {@code hatchPositions} 的键为 {@code "x,y,z"}。
     */
    private static StructureSource solidGrid(String id, int n, int[] controller,
            Map<String, StructureRole> hatchPositions) {
        StructureSource.Builder builder = StructureSource.builder(id)
                .displayName("Generated " + id)
                .size(n, n, n)
                .controller(controller[0], controller[1], controller[2]);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    String key = x + "," + y + "," + z;
                    if (x == controller[0] && y == controller[1] && z == controller[2]) {
                        builder.addBlock(x, y, z, "gtceu:test_controller", StructureRole.CONTROLLER);
                    } else if (hatchPositions.containsKey(key)) {
                        builder.addBlock(x, y, z, "gtceu:test_hatch", hatchPositions.get(key));
                    } else {
                        builder.addBlock(x, y, z, "gtceu:test_casing", StructureRole.PLAIN);
                    }
                }
            }
        }
        return builder.build();
    }

    /** 恰好 64 个方块（= 预算）→ 逐层模式。 */
    private static StructureSource exactlyBudget() {
        return filled("gtceu:at_budget", 4, 4, 4, 64);
    }

    /** 65 个方块（预算 + 1）→ 角色分组模式。 */
    private static StructureSource overBudget() {
        return filled("gtceu:over_budget", 5, 4, 4, 65);
    }

    /** 在一个 {@code sx×sy×sz} 包围盒内按确定性顺序铺满 {@code count} 个方块（坐标 (0,0,0) 为控制器）。 */
    private static StructureSource filled(String id, int sx, int sy, int sz, int count) {
        StructureSource.Builder builder = StructureSource.builder(id).size(sx, sy, sz).controller(0, 0, 0);
        int placed = 0;
        outer:
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    if (placed >= count) {
                        break outer;
                    }
                    if (x == 0 && y == 0 && z == 0) {
                        builder.addBlock(x, y, z, "gtceu:test_controller", StructureRole.CONTROLLER);
                    } else {
                        builder.addBlock(x, y, z, "gtceu:test_casing", StructureRole.PLAIN);
                    }
                    placed++;
                }
            }
        }
        return builder.build();
    }

    /** 6 个物品输入总线的 3×3 夹具（验证高亮收敛：仓口块数 > 轮廓上限）。 */
    private static StructureSource manyHatches() {
        StructureSource.Builder builder = StructureSource.builder("gtceu:many_hatches")
                .size(3, 1, 3)
                .controller(0, 0, 0);
        int hatches = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 0 && z == 0) {
                    builder.addBlock(x, 0, z, "gtceu:test_controller", StructureRole.CONTROLLER);
                } else if (hatches < 6) {
                    builder.addBlock(x, 0, z, "gtceu:test_hatch", StructureRole.ITEM_INPUT);
                    hatches++;
                } else {
                    builder.addBlock(x, 0, z, "gtceu:test_casing", StructureRole.PLAIN);
                }
            }
        }
        return builder.build();
    }

    /** 含 1 个模块位（任意模块）的小夹具。 */
    private static StructureSource withModuleSlot() {
        return StructureSource.builder("gtceu:moduled")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(1, 1, 1, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addBlock(0, 0, 0, "gtceu:test_casing")
                .addModuleSlot(new ModuleSlot(0, 0, 0, 1, 1, 1, true, List.of()))
                .build();
    }

    private static int indexOfFirstBuild(List<String> stepIds) {
        for (int i = 0; i < stepIds.size(); i++) {
            if (stepIds.get(i).startsWith("build.")) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static Optional<SceneStep> step(SceneData scene, String id) {
        return scene.steps().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    private static List<String> buildSectionIds(SceneData scene) {
        List<String> ids = new ArrayList<>();
        for (SceneElement element : scene.elements()) {
            if (element.id().startsWith("section.layer.") || element.id().startsWith("section.role.")) {
                ids.add(element.id());
            }
        }
        return ids;
    }

    // --- 头部 / 来源 ---------------------------------------------------------

    @Test
    void generatedSceneCarriesAutoSourceAndGeneratorVersion() {
        SceneData scene = SceneGenerator.generate(medium());

        assertEquals(Source.AUTO, scene.source());
        assertEquals(SceneGenerator.GENERATOR_VERSION, scene.generatorVersion());
        assertEquals("gtceu:medium_machine", scene.target());
        assertEquals("default", scene.variant());
        assertNotNull(scene.id());
        assertTrue(scene.id().startsWith("gtsnponder:auto_"), scene.id());
        assertFalse(scene.steps().isEmpty(), "generated scene must have steps");
    }

    @Test
    void generatedSceneTitleIsTheMachineTitleLocalizationKey() {
        // 标题必须是本地化键（而非硬编码显示名），与 datagen 产出的机器标题键一一对应。
        SceneData scene = SceneGenerator.generate(small());

        assertEquals(GeneratedKeys.machineTitleKey("gtceu:small_machine"), scene.title());
        assertTrue(scene.title().startsWith(GeneratedKeys.MACHINE_TITLE_PREFIX), scene.title());
    }

    @Test
    void generatedSceneIdMatchesSceneIdForTheTarget() {
        // 图鉴目录为「无场景的注册多方块」合成条目时复用同一推导，故这里锁定二者一致。
        for (StructureSource source : List.of(small(), medium(), large())) {
            SceneData scene = SceneGenerator.generate(source);
            assertEquals(SceneGenerator.sceneIdFor(source.id()), scene.id());
            assertTrue(scene.id().startsWith(SceneGenerator.SCENE_ID_PREFIX), scene.id());
        }
    }

    // --- 构建顺序 / 预算切换 --------------------------------------------------

    @Test
    void smallStructureBuildsLayerByLayerAscending() {
        SceneData scene = SceneGenerator.generate(small());

        assertFalse(SceneGenerator.isRoleGrouped(small()), "27 blocks must stay under the budget");
        List<String> layerIds = new ArrayList<>();
        for (SceneElement element : scene.elements()) {
            if (element.id().startsWith("section.layer.")) {
                layerIds.add(element.id());
            }
        }
        assertEquals(List.of("section.layer.0", "section.layer.1", "section.layer.2"), layerIds);
        assertTrue(scene.elements().stream().noneMatch(e -> e.id().startsWith("section.role.")));

        List<String> buildStepIds = scene.steps().stream()
                .filter(s -> s.type() == StepType.SHOW_SECTION && s.id().startsWith("build.layer."))
                .map(SceneStep::id)
                .toList();
        assertEquals(List.of("build.layer.0", "build.layer.1", "build.layer.2"), buildStepIds);
        for (SceneStep build : scene.steps()) {
            if (build.id().startsWith("build.layer.")) {
                assertEquals(1, build.targets().size());
                assertTrue(build.targets().get(0).startsWith("section.layer."));
            }
        }
    }

    @Test
    void oversizedMachineSwitchesToRoleGroupedLodBuild() {
        SceneData scene = SceneGenerator.generate(medium());

        assertTrue(SceneGenerator.isRoleGrouped(medium()), "125 blocks must exceed the budget");
        assertTrue(scene.elements().stream().anyMatch(e -> e.id().equals("section.role.PLAIN")));
        assertTrue(scene.elements().stream().anyMatch(e -> e.id().equals("section.role.ITEM_INPUT")));
        assertTrue(scene.elements().stream().anyMatch(e -> e.id().equals("section.role.FLUID_OUTPUT")));
        assertTrue(scene.elements().stream().noneMatch(e -> e.id().startsWith("section.layer.")),
                "oversized machines must not build layer-by-layer");

        List<SceneStep> buildSteps = scene.steps().stream()
                .filter(s -> s.type() == StepType.SHOW_SECTION && s.id().startsWith("build.role."))
                .toList();
        assertFalse(buildSteps.isEmpty(), "role-grouped build steps expected");
        for (SceneStep build : buildSteps) {
            assertEquals("grouped", build.params().get("lod"), "role build must be LOD-grouped: " + build);
            assertEquals(Boolean.TRUE, build.params().get("fadeIn"), "role build must fade in: " + build);
        }
        assertTrue(buildSteps.size() < medium().blockCount(),
                "LOD build must reveal groups, not animate block-by-block");
    }

    @Test
    void budgetSwitchHappensExactlyAboveTheCellBudget() {
        assertEquals(64, SceneGenerator.CELL_BUDGET);
        assertEquals(64, exactlyBudget().blockCount());
        assertEquals(65, overBudget().blockCount());

        assertFalse(SceneGenerator.isRoleGrouped(exactlyBudget()), "at the budget must stay layer-by-layer");
        assertTrue(SceneGenerator.isRoleGrouped(overBudget()), "above the budget must switch to role-grouped");

        assertTrue(SceneGenerator.generate(exactlyBudget()).elements().stream()
                .anyMatch(e -> e.id().startsWith("section.layer.")));
        assertTrue(SceneGenerator.generate(overBudget()).elements().stream()
                .anyMatch(e -> e.id().startsWith("section.role.")));
    }

    // --- 高亮顺序 -----------------------------------------------------------

    @Test
    void highlightsControllerWithHighlightAndHatchesWithOutline() {
        SceneData scene = SceneGenerator.generate(medium());
        List<String> ids = scene.steps().stream().map(SceneStep::id).toList();

        int controller = ids.indexOf("highlight.controller");
        int hatchInput = ids.indexOf("outline.hatch.ITEM_INPUT");
        int hatchOutput = ids.indexOf("outline.hatch.FLUID_OUTPUT");
        assertTrue(controller >= 0, () -> "controller highlight missing: " + ids);
        assertTrue(hatchInput >= 0 && hatchOutput >= 0, () -> "hatch outlines missing: " + ids);
        assertTrue(controller < hatchInput && controller < hatchOutput,
                "controller must be highlighted before hatches: " + ids);

        SceneStep controllerStep = step(scene, "highlight.controller").orElseThrow();
        assertEquals(StepType.HIGHLIGHT, controllerStep.type());
        assertEquals(List.of("controller"), controllerStep.targets());
        assertEquals(Boolean.TRUE, controllerStep.params().get("visible"));

        // 控制器与仓口用不同颜色（不同步骤类型：HIGHLIGHT 金 vs OUTLINE 蓝）。
        SceneStep inputStep = step(scene, "outline.hatch.ITEM_INPUT").orElseThrow();
        assertEquals(StepType.OUTLINE, inputStep.type());
        assertEquals(List.of("hatch.ITEM_INPUT"), inputStep.targets());
        assertEquals(Boolean.TRUE, inputStep.params().get("visible"));

        // 颜色含义移至播放屏常驻图例：不再有 legend 旁白步骤。
        assertTrue(step(scene, "text.legend").isEmpty(),
                "the color legend must be a persistent widget, not a narration step");
    }

    @Test
    void cameraDeclaresAspectAwareFraming() {
        SceneStep camera = step(SceneGenerator.generate(medium()), "focus.camera").orElseThrow();

        assertEquals(Boolean.TRUE, camera.params().get("fit"),
                "auto camera must request aspect-aware framing");
        assertEquals(SceneGenerator.FIT_MARGIN,
                ((Number) camera.params().get("margin")).doubleValue(), 1.0e-9d);
    }

    @Test
    void formedNarrationIsMachineSpecific() {
        SceneStep mediumFormed = step(SceneGenerator.generate(medium()), "formed.text").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_FORMED, mediumFormed.narration());
        assertEquals(List.of("gtceu:medium_machine", "5x5x5", "2", "ITEM_INPUT, FLUID_OUTPUT", "0"),
                mediumFormed.narrationArgs(),
                "formed narration must carry machine id / size / hatch count+roles / module count");

        SceneStep largeFormed = step(SceneGenerator.generate(large()), "formed.text").orElseThrow();
        assertNotEquals(mediumFormed.narrationArgs(), largeFormed.narrationArgs(),
                "different machines must produce different formed narration");

        SceneStep moduledFormed = step(SceneGenerator.generate(withModuleSlot()), "formed.text").orElseThrow();
        assertEquals("1", moduledFormed.narrationArgs().get(4),
                "module slot count must be part of the formed narration");
    }

    @Test
    void hatchOutlineCountIsConverged() {
        StructureSource source = manyHatches();
        SceneData scene = SceneGenerator.generate(source);
        SceneElementResolver resolver = new SceneElementResolver(source);

        int total = 0;
        for (SceneStep outline : scene.steps()) {
            if (outline.type() != StepType.OUTLINE) {
                continue;
            }
            assertEquals(1, outline.targets().size());
            total += resolver.resolve(scene.element(outline.targets().get(0)).orElseThrow()).size();
        }
        assertEquals(SceneGenerator.HATCH_OUTLINE_LIMIT, total,
                "hatch outlines must converge to the limit");
        assertTrue(source.hatches().size() > total,
                "outlines must be fewer than all hatch blocks (" + source.hatches().size() + ")");

        // 旁白仍如实报告完整的仓口块数。
        assertEquals("6", step(scene, "text.hatches").orElseThrow().narrationArgs().get(0));
    }

    @Test
    void narrationIsMachineSpecificAndCarriesTemplateArgs() {
        SceneData smallScene = SceneGenerator.generate(small());
        SceneStep intro = step(smallScene, "intro").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_INTRO, intro.narration());
        assertTrue(intro.narrationArgs().contains("gtceu:small_machine"), intro.narrationArgs().toString());
        assertTrue(intro.narrationArgs().contains("3x3x3"), intro.narrationArgs().toString());

        SceneStep largeIntro = step(SceneGenerator.generate(large()), "intro").orElseThrow();
        assertNotEquals(intro.narrationArgs(), largeIntro.narrationArgs(),
                "different machines must produce different narration args");

        SceneStep hatchText = step(SceneGenerator.generate(medium()), "text.hatches").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_HATCHES, hatchText.narration());
        assertEquals(List.of("2", "ITEM_INPUT, FLUID_OUTPUT"), hatchText.narrationArgs());
    }

    @Test
    void moduleSlotsAreNarratedWithTheirCount() {
        SceneData scene = SceneGenerator.generate(withModuleSlot());

        SceneStep moduleText = step(scene, "text.modules").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULES, moduleText.narration());
        assertEquals(List.of("1"), moduleText.narrationArgs());
    }

    @Test
    void narrationStatesMissingCapabilitiesExplicitly() {
        SceneData scene = SceneGenerator.generate(exactlyBudget());

        assertEquals(SceneGenerator.NARRATION_HATCHES_NONE,
                step(scene, "text.hatches").orElseThrow().narration());
        assertEquals(SceneGenerator.NARRATION_MODULES_NONE,
                step(scene, "text.modules").orElseThrow().narration());
    }

    @Test
    void cameraFramingScalesWithBoundingBoxAndPrecedesBuild() {
        SceneData smallScene = SceneGenerator.generate(small());
        SceneData largeScene = SceneGenerator.generate(large());

        SceneStep smallCamera = step(smallScene, "focus.camera").orElseThrow();
        SceneStep largeCamera = step(largeScene, "focus.camera").orElseThrow();
        assertEquals(StepType.CAMERA, smallCamera.type());
        double smallDistance = ((Number) smallCamera.params().get("distance")).doubleValue();
        double largeDistance = ((Number) largeCamera.params().get("distance")).doubleValue();
        assertTrue(smallDistance > 0.0d && largeDistance > 0.0d);
        assertTrue(largeDistance > smallDistance, "larger structure needs a larger camera distance");

        List<String> ids = smallScene.steps().stream().map(SceneStep::id).toList();
        assertTrue(ids.indexOf("focus.camera") < indexOfFirstBuild(ids),
                "camera framing must be scheduled before the build steps: " + ids);
    }

    @Test
    void hatchesAreGroupedByTheirStructureRole() {
        SceneData scene = SceneGenerator.generate(large());

        assertTrue(scene.elements().stream().anyMatch(e -> e.id().equals("hatch.MUFFLER")));
        assertTrue(scene.elements().stream().anyMatch(e -> e.id().equals("hatch.ENERGY_INPUT")));
        assertFalse(scene.elements().stream().anyMatch(e -> e.id().equals("hatch.ITEM_INPUT")),
                "roles not present in the structure must not be emitted");
    }

    // --- 成型演示 -----------------------------------------------------------

    @Test
    void formedDemonstrationHidesThenShowsThenPulses() {
        SceneData scene = SceneGenerator.generate(medium());
        List<String> sectionIds = buildSectionIds(scene);
        assertFalse(sectionIds.isEmpty());

        SceneStep hide = step(scene, "formed.hide").orElseThrow();
        SceneStep show = step(scene, "formed.show").orElseThrow();
        SceneStep pulse = step(scene, "formed.pulse").orElseThrow();

        assertEquals(StepType.HIDE_SECTION, hide.type());
        assertEquals(StepType.SHOW_SECTION, show.type());
        assertEquals(sectionIds, hide.targets());
        assertEquals(sectionIds, show.targets());
        assertEquals(StepType.FORMED_PULSE, pulse.type());
        assertEquals(List.of("controller"), pulse.targets());

        List<String> ids = scene.steps().stream().map(SceneStep::id).toList();
        assertTrue(ids.indexOf("formed.hide") < ids.indexOf("formed.show"), ids.toString());
        assertTrue(ids.indexOf("formed.show") < ids.indexOf("formed.pulse"), ids.toString());
    }

    // --- 确定性 / 可播放性 ---------------------------------------------------

    @Test
    void sameSourceProducesByteEqualJson() {
        for (StructureSource source : List.of(small(), medium(), large())) {
            String first = SceneDataWriter.toJson(SceneGenerator.generate(source));
            String second = SceneDataWriter.toJson(SceneGenerator.generate(source));
            assertEquals(first, second, "generation must be deterministic for " + source.id());
        }
    }

    @Test
    void everyGeneratedElementResolvesToAtLeastOneBlock() {
        for (StructureSource source : List.of(small(), medium(), large())) {
            SceneData scene = SceneGenerator.generate(source);
            SceneElementResolver resolver = new SceneElementResolver(source);
            for (SceneElement element : scene.elements()) {
                List<StructureBlock> resolved = resolver.resolve(element);
                assertFalse(resolved.isEmpty(),
                        "element '" + element.id() + "' resolves to no blocks in " + source.id());
            }
        }
    }

    @Test
    void everyStepTargetReferencesADeclaredElement() {
        for (StructureSource source : List.of(small(), medium(), large())) {
            SceneData scene = SceneGenerator.generate(source);
            List<String> elementIds = scene.elements().stream().map(SceneElement::id).toList();
            for (SceneStep step : scene.steps()) {
                for (String target : step.targets()) {
                    assertTrue(elementIds.contains(target),
                            "step '" + step.id() + "' targets undeclared element '" + target + "'");
                }
            }
        }
    }

    @Test
    void emptyStructureStillProducesAValidScene() {
        StructureSource empty = StructureSource.builder("gtceu:empty").size(1, 1, 1).build();

        SceneData scene = SceneGenerator.generate(empty);

        assertEquals(Source.AUTO, scene.source());
        assertEquals(SceneGenerator.GENERATOR_VERSION, scene.generatorVersion());
        assertTrue(scene.steps().stream().noneMatch(s -> s.type() == StepType.SHOW_SECTION),
                "nothing to build for an empty structure");
        assertTrue(scene.steps().stream().noneMatch(s -> s.type() == StepType.FORMED_PULSE));
    }
}
