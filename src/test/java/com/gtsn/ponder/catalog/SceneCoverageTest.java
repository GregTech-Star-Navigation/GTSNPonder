package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.Source;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全量覆盖（工单 #13）纯逻辑的 headless 行为测试：{@link SceneCoverage} 把「注册多方块集合 + 解析缝」
 * 折叠为可断言数字（注册 / 可解析 / 手作 / 生成 / 精选手作讲解 / 死链）。
 *
 * <p>精选关键机器的断言<b>读取真实随包场景文件</b>（{@code src/main/resources/assets/gtsnponder/ponder/}），
 * 与客户端自动测试 / GameTest 共用同一份 {@link SceneCoverage.CURATED} 常量，故「清单漂移」可被检出。
 * 纯逻辑、零 MC / GT。</p>
 */
class SceneCoverageTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static SceneData scene(String target, Source source) {
        return SceneData.builder().formatVersion(1).id("scene." + target).target(target).source(source).build();
    }

    private static Function<String, Optional<SceneData>> resolver(Map<String, SceneData> scenes) {
        return target -> Optional.ofNullable(scenes.get(target));
    }

    @Test
    void analyzeSplitsResolvedIntoHandAuthoredAndGeneratedAndReportsDeadLinks() {
        Map<String, SceneData> scenes = Map.of(
                "gtceu:hand_machine", scene("gtceu:hand_machine", Source.HAND),
                "gtceu:auto_machine", scene("gtceu:auto_machine", Source.AUTO));

        SceneCoverage.Report report = SceneCoverage.analyze(
                List.of("gtceu:hand_machine", "gtceu:auto_machine", "gtceu:dead_machine"),
                resolver(scenes));

        assertEquals(3, report.registered());
        assertEquals(2, report.resolved());
        assertEquals(1, report.handAuthored());
        assertEquals(1, report.generated());
        assertEquals(List.of("gtceu:dead_machine"), report.deadLinks());
        assertEquals(1, report.deadLinkCount());
        assertFalse(report.coversEveryMultiblock());
        assertFalse(report.isComplete(), "a dead link makes coverage incomplete");
    }

    @Test
    void mixedSourceCountsAsHandAuthored() {
        Map<String, SceneData> scenes = Map.of(
                "gtceu:mixed_machine", scene("gtceu:mixed_machine", Source.MIXED));

        SceneCoverage.Report report = SceneCoverage.analyze(List.of("gtceu:mixed_machine"), resolver(scenes));

        assertEquals(1, report.handAuthored());
        assertEquals(0, report.generated());
    }

    @Test
    void analyzeDeduplicatesAndIgnoresBlankTargets() {
        Map<String, SceneData> scenes = Map.of("gtceu:a", scene("gtceu:a", Source.AUTO));

        List<String> registered = new ArrayList<>();
        registered.add("gtceu:a");
        registered.add("gtceu:a");
        registered.add("   ");
        registered.add(null);
        registered.add("gtceu:b");

        SceneCoverage.Report report = SceneCoverage.analyze(registered, resolver(scenes));

        assertEquals(2, report.registered(), "duplicates and blank/null targets are collapsed");
        assertEquals(1, report.resolved());
        assertEquals(List.of("gtceu:b"), report.deadLinks());
    }

    @Test
    void analyzeTreatsNullResolverResultAsDeadLink() {
        SceneCoverage.Report report = SceneCoverage.analyze(List.of("gtceu:x"), target -> null);
        assertEquals(1, report.deadLinkCount());
    }

    @Test
    void curatedKeyMachinesHaveBundledHandOrMixedScenes() throws IOException {
        Map<String, SceneData> bundled = new LinkedHashMap<>();
        for (SceneCoverage.CuratedScene curated : SceneCoverage.CURATED) {
            Path file = RESOURCES.resolve(curated.resourcePath());
            assertTrue(Files.isRegularFile(file),
                    "curated scene file not found: " + file.toAbsolutePath());
            SceneData scene = SceneDataParser.parseOrThrow(Files.readString(file, StandardCharsets.UTF_8));
            assertEquals(curated.target(), scene.target(),
                    curated.resourcePath() + " declares a different target than its curated entry");
            assertTrue(SceneCoverage.isHandNarrated(scene.source()),
                    curated.resourcePath() + " must be source=hand|mixed, got " + scene.source());
            bundled.put(scene.target(), scene);
        }

        SceneCoverage.Report report = SceneCoverage.analyze(
                SceneCoverage.curatedTargets(), resolver(bundled));

        assertEquals(SceneCoverage.CURATED.size(), report.curatedHandAuthored(),
                "every curated key machine must resolve to a hand-authored narration");
        assertTrue(report.curatedNarrationComplete());
        assertTrue(report.coversEveryMultiblock(), "curated targets must all resolve: " + report.deadLinks());
        assertTrue(report.isComplete());
    }

    /**
     * {@link SceneCoverage#CURATED} 必须与仓库里实际随包的手作 / 混合场景一一对应——防止「加了手作
     * 场景文件却忘了登记精选清单」（或反之）造成的覆盖数字漂移。
     */
    @Test
    void curatedListMatchesEveryBundledHandAuthoredScene() throws IOException {
        Set<String> bundledTargets = new java.util.TreeSet<>();
        try (var files = Files.list(RESOURCES.resolve(SceneCoverage.PONDER_RESOURCE_DIRECTORY))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                SceneData scene = SceneDataParser.parseOrThrow(Files.readString(file, StandardCharsets.UTF_8));
                if (scene.target() != null && !scene.target().isBlank()) {
                    bundledTargets.add(scene.target());
                }
            }
        }
        assertEquals(new java.util.TreeSet<>(SceneCoverage.curatedTargets()), bundledTargets,
                "the curated list must name exactly the bundled hand-authored scenes");
    }
}
