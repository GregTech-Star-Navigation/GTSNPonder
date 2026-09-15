package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单方块机器覆盖契约（{@link SingleBlockScenes}）的 headless 行为测试：代表性数量下限、精选手作场景
 * 的随包文件与旁白键，以及共享分析器的数字折叠。纯逻辑、零 MC / GT。
 */
class SingleBlockScenesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static SingleBlockMachineSource anySource(String id) {
        return SingleBlockMachineSource.builder(id)
                .displayName("block." + id.replace(':', '.'))
                .blockId(id)
                .tier(1, "LV")
                .itemInputs(1)
                .itemOutputs(1)
                .energy(true)
                .addRecipeType("gtceu:test")
                .build();
    }

    private static Function<String, Optional<SceneData>> bundledOrGenerated() {
        Map<String, SceneData> bundled = new HashMap<>();
        for (SingleBlockScenes.CuratedScene curated : SingleBlockScenes.CURATED) {
            Path file = RESOURCES.resolve(curated.resourcePath());
            try {
                bundled.put(curated.target(),
                        SceneDataParser.parseOrThrow(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException failure) {
                throw new IllegalStateException("could not read " + file, failure);
            }
        }
        return target -> Optional.ofNullable(bundled.get(target))
                .or(() -> Optional.of(SingleBlockUsageGenerator.generate(anySource(target))));
    }

    @Test
    void representativeListMeetsTheMinimumAndIncludesEveryCuratedMachine() {
        assertTrue(SingleBlockScenes.REPRESENTATIVE.size() >= SingleBlockScenes.MIN_REPRESENTATIVE,
                "at least " + SingleBlockScenes.MIN_REPRESENTATIVE + " representative machines are required");
        assertEquals(SingleBlockScenes.REPRESENTATIVE.size(),
                new LinkedHashSet<>(SingleBlockScenes.REPRESENTATIVE).size(),
                "representative ids must be unique");
        assertTrue(SingleBlockScenes.REPRESENTATIVE.containsAll(SingleBlockScenes.curatedTargets()),
                "every curated machine must be part of the representative set");
    }

    @Test
    void curatedMachinesMeetTheHandAuthoredMinimum() {
        assertTrue(SingleBlockScenes.CURATED.size() >= SingleBlockScenes.MIN_HAND_AUTHORED,
                "at least " + SingleBlockScenes.MIN_HAND_AUTHORED + " hand-authored single-block scenes are required");
    }

    @Test
    void curatedScenesAreBundledHandAuthoredAndUseKnownNarrationKeys() throws IOException {
        Set<String> knownKeys = new java.util.HashSet<>(SingleBlockScenes.HAND_NARRATION_KEYS);
        for (SingleBlockScenes.CuratedScene curated : SingleBlockScenes.CURATED) {
            Path file = RESOURCES.resolve(curated.resourcePath());
            assertTrue(Files.isRegularFile(file), "curated usage scene file not found: " + file.toAbsolutePath());
            SceneData scene = SceneDataParser.parseOrThrow(Files.readString(file, StandardCharsets.UTF_8));
            assertEquals(curated.target(), scene.target(),
                    curated.resourcePath() + " declares a different target than its curated entry");
            assertTrue(SceneCoverage.isHandNarrated(scene.source()),
                    curated.resourcePath() + " must be source=hand|mixed, got " + scene.source());
            assertEquals(SingleBlockUsageGenerator.sceneIdFor(scene.target()), scene.id(),
                    "the hand-authored scene id must match the catalog / progress key");
            boolean usesHandNarration = false;
            for (SceneStep step : scene.steps()) {
                if (step.narration() != null && knownKeys.contains(step.narration())) {
                    usesHandNarration = true;
                }
            }
            assertTrue(usesHandNarration,
                    curated.resourcePath() + " does not reference any hand-authored narration key");
        }
    }

    @Test
    void analyzeReportsDeadLinksAuthoringAndGeneratedCounts() {
        Map<String, SceneData> scenes = new HashMap<>();
        scenes.put("gtceu:lp_steam_furnace",
                SceneData.builder().formatVersion(1).id("gtsnponder:usage_x")
                        .target("gtceu:lp_steam_furnace").source(com.gtsn.ponder.engine.model.Source.HAND).build());
        scenes.put("gtceu:lp_steam_macerator",
                SceneData.builder().formatVersion(1).id("gtsnponder:usage_y")
                        .target("gtceu:lp_steam_macerator").source(com.gtsn.ponder.engine.model.Source.AUTO).build());

        SingleBlockScenes.Report report = SingleBlockScenes.analyze(
                target -> Optional.ofNullable(scenes.get(target)));

        assertEquals(SingleBlockScenes.REPRESENTATIVE.size(), report.representative());
        assertEquals(2, report.resolved());
        assertEquals(1, report.handAuthored());
        assertEquals(1, report.generated());
        assertEquals(SingleBlockScenes.REPRESENTATIVE.size() - 2, report.deadLinkCount());
        assertFalse(report.coversEveryRepresentative());
        assertFalse(report.isComplete());
        assertFalse(report.curatedNarrationComplete());
    }

    @Test
    void analyzeWithBundledCuratedAndGeneratedRepresentativesIsComplete() {
        SingleBlockScenes.Report report = SingleBlockScenes.analyze(bundledOrGenerated());

        assertTrue(report.coversEveryRepresentative(), "dead links: " + report.deadLinks());
        assertTrue(report.curatedNarrationComplete(),
                "curated machines without hand narration: " + report.curatedWithoutHandNarration());
        assertEquals(SingleBlockScenes.CURATED.size(), report.curatedHandAuthored());
        assertTrue(report.handAuthored() >= SingleBlockScenes.MIN_HAND_AUTHORED, report.summary());
        assertTrue(report.generated() >= 1, "the rest of the representatives must be generated: " + report.summary());
        assertTrue(report.isComplete(), report.summary());
    }
}
