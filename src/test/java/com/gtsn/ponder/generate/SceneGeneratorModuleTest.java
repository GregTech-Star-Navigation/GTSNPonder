package com.gtsn.ponder.generate;

import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.ModuleEffectInfo;
import com.gtsn.ponder.structure.ModuleOption;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动生成器的<b>模块系统演示</b>外部可观察行为：给定声明模块位的 {@link StructureSource} 夹具，
 * 断言产出的 {@link SceneData}——模块位区域高亮、可接受模块叙述、安装演示与效果汇总的顺序与内容、
 * 以及确定性。
 *
 * <p>这是规格 §Testing Decisions 的「自动生成缝」（{@code StructureSource → SceneGenerator →
 * SceneData}），不依赖 GT / MC 运行时。模块位数据来自真实 GT 的注入缝由 GameTest 覆盖。</p>
 */
class SceneGeneratorModuleTest {

    private static ModuleEffectInfo effect(int parallel, double speed, double energy,
            double input, double output, int tier) {
        return new ModuleEffectInfo(parallel, speed, energy, input, output, tier);
    }

    /**
     * 含两个模块位的夹具：槽 0（2×1×2）接受两个具名模块（含效果），槽 1（1×1×1）接受任意模块。
     * 区域坐标处不放结构方块（以空穴表达「空模块位」），验证安装后模块才出现。
     */
    private static StructureSource withModules() {
        return StructureSource.builder("gtceu:modular_machine")
                .displayName("Modular Machine")
                .size(4, 2, 4)
                .controller(3, 0, 3)
                .addBlock(3, 0, 3, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addBlock(0, 1, 0, "gtceu:test_casing")
                .addBlock(3, 0, 0, "gtceu:test_casing")
                .addModuleSlot(new ModuleSlot(0, 0, 0, 2, 1, 2, false,
                        List.of("gtceu:speed_module", "gtceu:parallel_module"),
                        List.of(
                                new ModuleOption("gtceu:parallel_module", effect(4, 1.0d, 1.0d, 1.0d, 1.0d, 0)),
                                new ModuleOption("gtceu:speed_module", effect(0, 0.5d, 1.0d, 1.0d, 1.0d, 0)))))
                .addModuleSlot(new ModuleSlot(2, 0, 0, 1, 1, 1, true, List.of()))
                .build();
    }

    /** 含一个「不接受任何模块」退化模块位的夹具。 */
    private static StructureSource withUninstallableSlot() {
        return StructureSource.builder("gtceu:sealed_machine")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(1, 1, 1, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addModuleSlot(new ModuleSlot(0, 0, 0, 1, 1, 1, false, List.of()))
                .build();
    }

    private static Optional<SceneStep> step(SceneData scene, String id) {
        return scene.steps().stream().filter(step -> step.id().equals(id)).findFirst();
    }

    private static List<String> stepIds(SceneData scene) {
        return scene.steps().stream().map(SceneStep::id).toList();
    }

    // --- 模块位区域高亮 -------------------------------------------------------

    @Test
    void moduleSlotsProduceRegionElementsAndOutlineSteps() {
        SceneData scene = SceneGenerator.generate(withModules());

        SceneElement slot0 = scene.element(SceneGenerator.ELEMENT_MODULE_SLOT_PREFIX + "0").orElseThrow();
        assertEquals("region", slot0.kind());
        assertEquals(SceneElementResolver.SELECTOR_MODULE_SLOT, slot0.params().get("selector"));
        assertEquals(0.0d, ((Number) slot0.params().get("index")).doubleValue(), 1.0e-9d);
        assertTrue(scene.element(SceneGenerator.ELEMENT_MODULE_SLOT_PREFIX + "1").isPresent());

        SceneStep outline0 = step(scene, "outline.moduleslot.0").orElseThrow();
        assertEquals(StepType.OUTLINE, outline0.type());
        assertEquals(List.of("moduleslot.0"), outline0.targets());
        assertEquals(Boolean.TRUE, outline0.params().get("visible"));
        assertTrue(step(scene, "outline.moduleslot.1").isPresent());

        // 区域覆盖全部单元（空穴也计入），供世界桥以区域形式高亮。
        SceneElementResolver resolver = new SceneElementResolver(withModules());
        assertEquals(4, resolver.resolve(slot0).size(), "2x1x2 slot region must highlight 4 cells");
    }

    // --- 可接受模块叙述 -------------------------------------------------------

    @Test
    void moduleSlotNarrationSurfacesAcceptableModules() {
        SceneData scene = SceneGenerator.generate(withModules());

        SceneStep slot0 = step(scene, "text.moduleslot.0").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULE_SLOT, slot0.narration());
        assertEquals(List.of("1", "gtceu:parallel_module, gtceu:speed_module"), slot0.narrationArgs(),
                "slot narration must list the acceptable modules deterministically");

        SceneStep slot1 = step(scene, "text.moduleslot.1").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULE_SLOT_ANY, slot1.narration());
        assertEquals(List.of("2"), slot1.narrationArgs());
    }

    @Test
    void slotWithoutAcceptableModulesIsNarratedButNotInstalled() {
        SceneData scene = SceneGenerator.generate(withUninstallableSlot());

        SceneStep slot0 = step(scene, "text.moduleslot.0").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULE_SLOT_NONE, slot0.narration());
        assertEquals(List.of("1"), slot0.narrationArgs());
        assertTrue(step(scene, "install.moduleslot.0").isEmpty(),
                "a slot that accepts no module must not gain an install step");
        assertTrue(step(scene, "text.installed.0").isEmpty());
        // 仍以区域形式高亮该模块位。
        assertTrue(step(scene, "outline.moduleslot.0").isPresent());
    }

    // --- 安装演示 + 效果汇总 --------------------------------------------------

    @Test
    void installDemonstrationPlaysAfterSlotNarrationAndCarriesModuleParam() {
        SceneData scene = SceneGenerator.generate(withModules());

        SceneStep install0 = step(scene, "install.moduleslot.0").orElseThrow();
        assertEquals(StepType.INSTALL_MODULE, install0.type());
        assertEquals(List.of("moduleslot.0"), install0.targets());
        assertEquals("gtceu:parallel_module", install0.params().get("module"),
                "the first deterministically ordered acceptable module is installed");

        SceneStep install1 = step(scene, "install.moduleslot.1").orElseThrow();
        assertEquals(SceneGenerator.GENERIC_MODULE_ID, install1.params().get("module"),
                "a slot accepting any module installs a documented representative module");

        List<String> ids = stepIds(scene);
        assertTrue(ids.indexOf("outline.moduleslot.0") < ids.indexOf("text.moduleslot.0"), ids.toString());
        assertTrue(ids.indexOf("text.moduleslot.0") < ids.indexOf("install.moduleslot.0"), ids.toString());
        assertTrue(ids.indexOf("install.moduleslot.0") < ids.indexOf("text.installed.0"), ids.toString());
    }

    @Test
    void installEffectSummaryIsNarrated() {
        SceneData scene = SceneGenerator.generate(withModules());

        SceneStep installed0 = step(scene, "text.installed.0").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULE_INSTALLED, installed0.narration());
        assertEquals(List.of("gtceu:parallel_module", "1", "4", "1.0", "1.0", "1.0", "1.0", "0"),
                installed0.narrationArgs(),
                "effect summary must carry module id / slot / parallel / speed / energy / input / output / tier");

        SceneStep installed1 = step(scene, "text.installed.1").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT, installed1.narration());
        assertEquals(List.of(SceneGenerator.GENERIC_MODULE_ID, "2"), installed1.narrationArgs());
    }

    @Test
    void moduleSlotCountNarrationStillPrecedesPerSlotDemo() {
        SceneData scene = SceneGenerator.generate(withModules());

        SceneStep count = step(scene, "text.modules").orElseThrow();
        assertEquals(SceneGenerator.NARRATION_MODULES, count.narration());
        assertEquals(List.of("2"), count.narrationArgs());

        List<String> ids = stepIds(scene);
        assertTrue(ids.indexOf("text.modules") < ids.indexOf("outline.moduleslot.0"), ids.toString());
    }

    // --- 确定性 / 可播放性 ---------------------------------------------------

    @Test
    void moduleDemoIsDeterministicAndPlayable() {
        StructureSource source = withModules();
        SceneData scene = SceneGenerator.generate(source);
        assertEquals(SceneDataWriter.toJson(scene), SceneDataWriter.toJson(SceneGenerator.generate(source)));

        SceneElementResolver resolver = new SceneElementResolver(source);
        for (SceneElement element : scene.elements()) {
            assertFalse(resolver.resolve(element).isEmpty(),
                    "element '" + element.id() + "' resolves to no cells");
        }
        List<String> elementIds = scene.elements().stream().map(SceneElement::id).toList();
        for (SceneStep step : scene.steps()) {
            for (String target : step.targets()) {
                assertTrue(elementIds.contains(target),
                        "step '" + step.id() + "' targets undeclared element '" + target + "'");
            }
        }
        assertNotNull(scene.target());
    }

    @Test
    void moduleEffectInfoReachesTheInstallNarration() {
        // 效果汇总直接来自夹具中的模块效果，与 adapter 解耦（adapter 侧的提取由 GameTest 锁定）。
        SceneData scene = SceneGenerator.generate(withModules());
        SceneStep speedInstall = step(scene, "text.installed.0").orElseThrow();
        assertEquals("gtceu:parallel_module", speedInstall.narrationArgs().get(0));

        StructureSource speedOnly = StructureSource.builder("gtceu:speed_only")
                .size(3, 3, 3)
                .controller(1, 1, 1)
                .addBlock(1, 1, 1, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addModuleSlot(new ModuleSlot(0, 0, 0, 1, 1, 1, false, List.of("gtceu:speed_module"),
                        List.of(new ModuleOption("gtceu:speed_module", effect(0, 0.5d, 0.8d, 1.0d, 1.0d, 1)))))
                .build();
        SceneStep installed = step(SceneGenerator.generate(speedOnly), "text.installed.0").orElseThrow();
        assertEquals(List.of("gtceu:speed_module", "1", "0", "0.5", "0.8", "1.0", "1.0", "1"),
                installed.narrationArgs());
    }
}
