package com.gtsn.ponder.content;

import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.catalog.WatchedProgress;
import com.gtsn.ponder.engine.director.FakeSceneWorld;
import com.gtsn.ponder.engine.director.SceneRunner;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容工单 #10（发电·能量网 / 物流管网）的「文件驱动」行为测试：直接读取随包发布的
 * {@code assets/gtsnponder/ponder/*.json} 新场景，锁定其目标 / 来源 / 步骤 / 旁白键，
 * 并断言它们经 {@link SceneRunner} + {@link FakeSceneWorld} 可播、narration 按序触发、rewind 可复现，
 * 同时断言其经 {@link SceneCategories} 落入正确的目录类别、经 {@link SceneCatalog} 可浏览且
 * 「相关机器」导航可派生。
 *
 * <p>这保证「数据文件」而非内存夹具既被解析也被播放，是本工单的 headless 证据。纯逻辑、零 MC。</p>
 */
class SystemScenesTest {

    private static final Path PONDER_DIR =
            Path.of("src", "main", "resources", "assets", "gtsnponder", "ponder");

    /**
     * 一个随包系统场景的期望契约：文件、稳定 id、真实 GT 多方块目标、目录类别、概念旁白键与步骤数。
     */
    private record ExpectedScene(
            String file,
            String id,
            String target,
            String category,
            List<String> conceptNarrationKeys,
            int stepCount) {
    }

    /** 两类内容各两条完整场景（发电 / 物流各至少一条，另加一条同类别「相关机器」以支撑目录跳转）。 */
    private static final List<ExpectedScene> SCENES = List.of(
            new ExpectedScene("power_energy.json", "gtsnponder:power_energy",
                    "gtceu:large_combustion_engine", SceneCategories.POWER,
                    List.of("ponder.gtsnponder.power.narration.voltage",
                            "ponder.gtsnponder.power.narration.overvoltage",
                            "ponder.gtsnponder.power.narration.burning"),
                    9),
            new ExpectedScene("power_transformer.json", "gtsnponder:power_transformer",
                    "gtceu:active_transformer", SceneCategories.POWER,
                    List.of("ponder.gtsnponder.power.narration.voltage",
                            "ponder.gtsnponder.power.narration.overvoltage",
                            "ponder.gtsnponder.power.narration.burning"),
                    9),
            new ExpectedScene("logistics_network.json", "gtsnponder:logistics_network",
                    "gtceu:steel_multiblock_tank", SceneCategories.LOGISTICS,
                    List.of("ponder.gtsnponder.logistics.narration.item_pipes",
                            "ponder.gtsnponder.logistics.narration.fluid_pipes",
                            "ponder.gtsnponder.logistics.narration.cables",
                            "ponder.gtsnponder.logistics.narration.covers"),
                    10),
            new ExpectedScene("logistics_pump.json", "gtsnponder:logistics_pump",
                    "gtceu:primitive_pump", SceneCategories.LOGISTICS,
                    List.of("ponder.gtsnponder.logistics.narration.item_pipes",
                            "ponder.gtsnponder.logistics.narration.fluid_pipes",
                            "ponder.gtsnponder.logistics.narration.cables",
                            "ponder.gtsnponder.logistics.narration.covers"),
                    10));

    private static SceneData load(ExpectedScene expected) throws IOException {
        Path file = PONDER_DIR.resolve(expected.file());
        assertTrue(Files.isRegularFile(file), "bundled system scene not found: " + file.toAbsolutePath());
        return SceneDataParser.parseOrThrow(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void bothContentCategoriesHaveAtLeastOneBundledScene() {
        Map<String, Integer> perCategory = new LinkedHashMap<>();
        for (ExpectedScene scene : SCENES) {
            perCategory.merge(scene.category(), 1, Integer::sum);
        }
        assertTrue(perCategory.getOrDefault(SceneCategories.POWER, 0) >= 1,
                "power & energy must ship at least one scene: " + perCategory);
        assertTrue(perCategory.getOrDefault(SceneCategories.LOGISTICS, 0) >= 1,
                "logistics & pipes must ship at least one scene: " + perCategory);
        assertEquals(SCENES.size(), perCategory.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void bundledScenesParseWithExpectedHeadersAndSteps() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            assertEquals(1, scene.formatVersion(), expected.file());
            assertEquals(expected.id(), scene.id(), expected.file());
            assertEquals(expected.target(), scene.target(), expected.file());
            assertEquals("default", scene.variant(), expected.file());
            assertEquals(Source.MIXED, scene.source(),
                    expected.file() + " must declare the hybrid (auto structure + hand narration) source");
            assertEquals(expected.stepCount(), scene.steps().size(), expected.file());
            assertTrue(scene.steps().stream().anyMatch(step -> step.type() == StepType.SHOW_SECTION),
                    expected.file() + " must reveal the real structure");
            assertTrue(scene.steps().stream().anyMatch(step -> step.type() == StepType.FORMED_PULSE),
                    expected.file() + " must show the formed pulse");
            assertTrue(scene.element("controller").isPresent(),
                    expected.file() + " must anchor the controller element");
        }
    }

    @Test
    void sceneTargetsClassifyIntoTheExpectedCatalogCategories() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            assertEquals(expected.category(), SceneCategories.categoryOf(scene),
                    expected.file() + " target " + expected.target() + " classified into the wrong bucket");
        }
    }

    @Test
    void conceptNarrationKeysCoverTheRequiredTopics() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            List<String> keys = scene.steps().stream()
                    .map(step -> step.narration())
                    .filter(key -> key != null && !key.isBlank())
                    .toList();
            for (String required : expected.conceptNarrationKeys()) {
                assertTrue(keys.contains(required),
                        expected.file() + " is missing concept narration key " + required);
            }
            assertFalse(keys.isEmpty(), expected.file() + " has no narration at all");
        }
    }

    @Test
    void shippedScenesOnlyReferenceKnownLocalizationKeys() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            assertTrue(SystemSceneKeys.ALL.contains(scene.title()),
                    expected.file() + " title key is not registered for datagen: " + scene.title());
            for (var step : scene.steps()) {
                if (step.narration() != null) {
                    assertTrue(SystemSceneKeys.ALL.contains(step.narration()),
                            expected.file() + " references an unregistered narration key: " + step.narration());
                }
            }
        }
    }

    @Test
    void scenesPlayThroughTheDirectorAndReachTheFinalConceptNarration() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            FakeSceneWorld world = new FakeSceneWorld();
            SceneRunner runner = new SceneRunner(scene, world);

            runner.play();
            runner.tick(SceneRunner.totalTime(scene));

            assertTrue(runner.isComplete(), expected.file() + " did not finish playback");
            assertTrue(world.state().sections().get("shell"),
                    expected.file() + " never revealed the structure shell");
            assertEquals(Boolean.TRUE, world.state().highlights().get("controller"),
                    expected.file() + " never highlighted the controller");
            assertEquals(List.of("controller"), world.state().formedPulses(),
                    expected.file() + " never pulsed the formed structure");
            String lastConcept = expected.conceptNarrationKeys().get(expected.conceptNarrationKeys().size() - 1);
            assertEquals(lastConcept, world.narration(),
                    expected.file() + " did not end on its last concept narration");
        }
    }

    @Test
    void rewindReproducesTheOpeningWorldState() throws IOException {
        for (ExpectedScene expected : SCENES) {
            SceneData scene = load(expected);
            FakeSceneWorld world = new FakeSceneWorld();
            SceneRunner runner = new SceneRunner(scene, world);
            runner.play();
            runner.tick(SceneRunner.totalTime(scene));

            FakeSceneWorld.FakeState played = world.state();
            runner.rewind();

            assertEquals(0.0d, runner.time(), 1.0e-9d, expected.file());
            assertEquals(0, runner.currentStepIndex(), expected.file());
            assertNotEquals(played, world.state(), expected.file() + " rewind must change world state back");
            assertEquals(Boolean.TRUE, world.state().sections().get("shell"),
                    expected.file() + ": the reveal step is re-applied at t=0");
            assertFalse(world.state().highlights().containsKey("controller"),
                    expected.file() + ": a later highlight is cleared on rewind");
        }
    }

    @Test
    void catalogListsTheNewScenesAndDerivesRelatedMachineNavigation() throws IOException {
        List<SceneData> scenes = new ArrayList<>();
        for (ExpectedScene expected : SCENES) {
            scenes.add(load(expected));
        }
        SceneCatalog catalog = SceneCatalog.of(scenes, WatchedProgress.empty());

        assertEquals(SCENES.size(), catalog.total(), "every system scene must be catalogued");
        assertTrue(catalog.categories().contains(SceneCategories.POWER));
        assertTrue(catalog.categories().contains(SceneCategories.LOGISTICS));

        CatalogEntry power = catalog.inCategory(SceneCategories.POWER).stream()
                .filter(entry -> entry.target().equals("gtceu:large_combustion_engine"))
                .findFirst().orElseThrow();
        List<CatalogEntry> related = catalog.relatedTo(power);
        assertFalse(related.isEmpty(), "related-machine navigation must resolve a sibling for " + power.target());
        assertTrue(related.stream().noneMatch(entry -> entry.key().equals(power.key())),
                "related entries must exclude the anchor itself");
        assertTrue(related.stream().anyMatch(entry -> entry.target().equals("gtceu:active_transformer")),
                "same-category sibling must be reachable from " + power.target());
    }
}
