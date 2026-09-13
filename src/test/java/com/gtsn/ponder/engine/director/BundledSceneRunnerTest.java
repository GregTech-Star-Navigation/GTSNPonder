package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手作场景资源的「文件驱动」测试：直接读取随包发布的
 * {@code assets/gtsnponder/ponder/coke_oven.json}，锁定其步骤数量 / 顺序 / 时长，
 * 并用 {@link FakeSceneWorld} 驱动 {@link SceneRunner} 验证效果词汇按序触发、seek / rewind 可复现。
 *
 * <p>这保证「数据文件」而非内存夹具既被解析也被播放，是 ticket #5 的 headless 证据。</p>
 */
class BundledSceneRunnerTest {

    private static final Path SCENE_FILE =
            Path.of("src", "main", "resources", "assets", "gtsnponder", "ponder", "coke_oven.json");

    private static SceneData loadScene() throws IOException {
        assertTrue(Files.isRegularFile(SCENE_FILE), "bundled scene not found: " + SCENE_FILE.toAbsolutePath());
        return SceneDataParser.parseOrThrow(Files.readString(SCENE_FILE, StandardCharsets.UTF_8));
    }

    @Test
    void bundledSceneHasExpectedHeaderAndElements() throws IOException {
        SceneData scene = loadScene();

        assertEquals(1, scene.formatVersion());
        assertEquals("gtsnponder:coke_oven", scene.id());
        assertEquals("gtceu:coke_oven", scene.target());
        assertEquals("default", scene.variant());
        assertEquals(Source.HAND, scene.source());

        assertEquals(List.of("base", "shell", "controller"),
                scene.elements().stream().map(element -> element.id()).toList());
        assertEquals("anchor", scene.element("controller").orElseThrow().kind());
    }

    @Test
    void bundledSceneHasExpectedStepsOrderAndTiming() throws IOException {
        SceneData scene = loadScene();

        assertEquals(List.of(
                        "reveal_base", "talk_intro", "reveal_shell", "frame", "highlight_controller",
                        "talk_controller", "talk_usage", "formed"),
                scene.steps().stream().map(step -> step.id()).toList());
        assertEquals(List.of(
                        StepType.SHOW_SECTION, StepType.TEXT, StepType.SHOW_SECTION, StepType.CAMERA,
                        StepType.HIGHLIGHT, StepType.TEXT, StepType.TEXT, StepType.FORMED_PULSE),
                scene.steps().stream().map(step -> step.type()).toList());
        assertEquals(List.of(25, 40, 25, 0, 30, 50, 60, 20),
                scene.steps().stream().map(step -> step.duration()).toList());

        assertEquals(0.0d, SceneRunner.startTime(scene, 0), 1.0e-9d);
        assertEquals(25.0d, SceneRunner.startTime(scene, 1), 1.0e-9d);
        assertEquals(65.0d, SceneRunner.startTime(scene, 2), 1.0e-9d);
        assertEquals(90.0d, SceneRunner.startTime(scene, 3), 1.0e-9d);
        assertEquals(120.0d, SceneRunner.startTime(scene, 5), 1.0e-9d);
        assertEquals(250.0d, SceneRunner.totalTime(scene), 1.0e-9d);
    }

    @Test
    void runnerAppliesTheEffectVocabularyInOrder() throws IOException {
        SceneData scene = loadScene();
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);

        runner.play();
        runner.tick(SceneRunner.totalTime(scene));

        assertTrue(runner.isComplete());
        assertEquals("ponder.gtsnponder.coke_oven.narration.usage", world.narration());
        assertEquals(Boolean.TRUE, world.state().highlights().get("controller"));
        assertEquals(Boolean.TRUE, world.state().sections().get("base"));
        assertEquals(Boolean.TRUE, world.state().sections().get("shell"));
        assertEquals(List.of("controller"), world.state().formedPulses());

        assertOrderedSubsequence(List.of(
                        "section:base=true",
                        "narration:ponder.gtsnponder.coke_oven.narration.intro",
                        "section:shell=true",
                        "camera:controller",
                        "highlight:controller=true",
                        "narration:ponder.gtsnponder.coke_oven.narration.controller",
                        "narration:ponder.gtsnponder.coke_oven.narration.usage",
                        "formed:controller"),
                world.events());
    }

    @Test
    void rewindReproducesTheOpeningWorldState() throws IOException {
        SceneData scene = loadScene();
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);
        runner.play();
        runner.tick(SceneRunner.totalTime(scene));

        FakeSceneWorld.FakeState played = world.state();
        runner.rewind();

        assertEquals(0.0d, runner.time(), 1.0e-9d);
        assertEquals(0, runner.currentStepIndex());
        assertNotEquals(played, world.state(), "rewind must change world state back");
        assertEquals(Boolean.TRUE, world.state().sections().get("base"), "first step is replayed at t=0");
        assertFalse(world.state().sections().containsKey("shell"), "later sections are hidden again");
        assertFalse(world.state().highlights().containsKey("controller"), "highlight from a later step is cleared");

        // Seeking straight to the highlight step yields the same world state as replaying to it.
        FakeSceneWorld seekWorld = new FakeSceneWorld();
        SceneRunner seekRunner = new SceneRunner(scene, seekWorld);
        seekRunner.seekTo(SceneRunner.startTime(scene, 4));
        runner.seekTo(SceneRunner.startTime(scene, 4));
        assertEquals(seekWorld.state(), world.state(), "seek equals fresh replay to the same time");
    }

    /** 断言 {@code actual} 中按顺序出现 {@code expected} 的每一项（允许其间有其它事件）。 */
    private static void assertOrderedSubsequence(List<String> expected, List<String> actual) {
        int cursor = 0;
        for (String want : expected) {
            int found = actual.indexOf(want);
            assertTrue(found >= cursor,
                    "expected ordered event '" + want + "' after index " + cursor + ", actual=" + actual);
            cursor = found + 1;
        }
    }
}
