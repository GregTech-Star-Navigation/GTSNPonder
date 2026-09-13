package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主缝行为测试：{@link SceneData} × {@link FakeSceneWorld} → {@link SceneRunner}。
 * 只断言外部可观察行为（世界状态 / 时间 / 当前步骤）。
 */
class SceneRunnerTest {

    private static final double EPS = 1.0e-9d;

    private static SceneData scene(SceneStep... steps) {
        SceneData.Builder builder = SceneData.builder()
                .formatVersion(1)
                .id("test_scene")
                .source(Source.HAND);
        for (SceneStep step : steps) {
            builder.addStep(step);
        }
        return builder.build();
    }

    private static SceneStep step(String id, StepType type, int duration, String... targets) {
        return SceneStep.builder()
                .id(id)
                .type(type)
                .duration(duration)
                .targets(List.of(targets))
                .build();
    }

    @Test
    void totalTimeSumsStepDurations() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 20, "a"),
                step("s2", StepType.TEXT, 0),
                step("s3", StepType.CAMERA, 40, "a"));

        assertEquals(60.0d, SceneRunner.totalTime(scene), EPS);
        assertEquals(0.0d, SceneRunner.startTime(scene, 0), EPS);
        assertEquals(20.0d, SceneRunner.startTime(scene, 1), EPS);
        assertEquals(20.0d, SceneRunner.startTime(scene, 2), EPS);
    }

    @Test
    void blockingIsDurationBased() {
        assertFalse(StepBehaviors.isBlocking(step("x", StepType.TEXT, 0)));
        assertTrue(StepBehaviors.isBlocking(step("y", StepType.TEXT, 5)));
    }

    @Test
    void stepsApplyInOrderAndBlockingHoldsTimeline() {
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene(
                step("s1", StepType.SHOW_SECTION, 10, "section_a"),
                step("s2", StepType.HIGHLIGHT, 0, "ctrl"),
                step("s3", StepType.OUTLINE, 20, "ctrl")), world);

        // At t=0 the first blocking step is already scheduled.
        assertEquals(0.0d, runner.time(), EPS);
        assertEquals(Boolean.TRUE, world.state().sections().get("section_a"));
        assertFalse(world.state().highlights().containsKey("ctrl"));

        runner.play();
        assertTrue(runner.isPlaying());
        runner.tick(5);
        assertEquals(5.0d, runner.time(), EPS);
        assertEquals(0, runner.currentStepIndex());
        assertTrue(runner.isCurrentStepBlocking());
        assertFalse(world.state().highlights().containsKey("ctrl"));

        // Crossing t=10 ends s1, fires the zero-duration s2, and starts s3.
        runner.tick(5);
        assertEquals(10.0d, runner.time(), EPS);
        assertEquals(Boolean.TRUE, world.state().highlights().get("ctrl"));
        assertEquals(2, runner.currentStepIndex());
        assertTrue(runner.isCurrentStepBlocking());

        runner.tick(20);
        assertEquals(30.0d, runner.time(), EPS);
        assertTrue(runner.isComplete());
        assertFalse(runner.isPlaying(), "runner must auto-pause at the end");
    }

    @Test
    void pauseStopsTimeAndResumeContinues() {
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene(step("s1", StepType.SHOW_SECTION, 20, "a")), world);

        runner.play();
        runner.tick(4);
        assertEquals(4.0d, runner.time(), EPS);

        runner.pause();
        assertFalse(runner.isPlaying());
        runner.tick(4);
        assertEquals(4.0d, runner.time(), EPS);

        runner.resume();
        assertTrue(runner.isPlaying());
        runner.tick(6);
        assertEquals(10.0d, runner.time(), EPS);
    }

    @Test
    void seekToEqualsSequentialPlayback() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 10, "section_a"),
                step("s2", StepType.HIGHLIGHT, 5, "ctrl"),
                step("s3", StepType.OUTLINE, 15, "ctrl"),
                SceneStep.builder().id("s4").type(StepType.TEXT).narration("n").build());

        FakeSceneWorld sequentialWorld = new FakeSceneWorld();
        SceneRunner sequential = new SceneRunner(scene, sequentialWorld);
        sequential.play();
        sequential.tick(12);

        FakeSceneWorld seekWorld = new FakeSceneWorld();
        SceneRunner seek = new SceneRunner(scene, seekWorld);
        seek.seekTo(12);

        assertEquals(12.0d, sequential.time(), EPS);
        assertEquals(12.0d, seek.time(), EPS);
        assertEquals(sequentialWorld.state(), seekWorld.state());
    }

    @Test
    void seekBackwardsMatchesFreshForwardRun() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 10, "section_a"),
                step("s2", StepType.HIGHLIGHT, 5, "ctrl"),
                step("s3", StepType.OUTLINE, 15, "ctrl"));

        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);
        runner.seekTo(20);
        runner.seekTo(7);

        FakeSceneWorld freshWorld = new FakeSceneWorld();
        SceneRunner fresh = new SceneRunner(scene, freshWorld);
        fresh.seekTo(7);

        assertEquals(7.0d, runner.time(), EPS);
        assertEquals(freshWorld.state(), world.state());
    }

    @Test
    void rewindReturnsToInitialStateAndIsRepeatable() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 10, "section_a"),
                step("s2", StepType.HIGHLIGHT, 5, "ctrl"));

        FakeSceneWorld initialWorld = new FakeSceneWorld();
        new SceneRunner(scene, initialWorld);
        FakeSceneWorld.FakeState initialState = initialWorld.state();

        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);
        runner.seekTo(SceneRunner.totalTime(scene));
        assertNotEquals(initialState, world.state(), "playback must have changed world state before rewind");
        runner.rewind();
        assertEquals(0.0d, runner.time(), EPS);
        assertEquals(initialState, world.state());

        runner.seekTo(12);
        runner.rewind();
        assertEquals(initialState, world.state(), "rewind must be repeatable");
    }

    @Test
    void effectReplayAndSnapshotRestoreAreIdempotent() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 10, "section_a"),
                SceneStep.builder().id("s2").type(StepType.REPLACE_BLOCKS).duration(5).targets(List.of("elem"))
                        .param("block", "gtceu:steel_casing").build(),
                step("s3", StepType.FORMED_PULSE, 5, "controller"));

        FakeSceneWorld firstWorld = new FakeSceneWorld();
        SceneRunner first = new SceneRunner(scene, firstWorld);
        first.seekTo(SceneRunner.totalTime(scene));

        FakeSceneWorld secondWorld = new FakeSceneWorld();
        SceneRunner second = new SceneRunner(scene, secondWorld);
        second.seekTo(SceneRunner.totalTime(scene));

        assertEquals(firstWorld.state(), secondWorld.state(), "same timeline -> same world state");

        // Re-seeking to the same time must not duplicate effects (formed pulse count stays 1).
        first.seekTo(SceneRunner.totalTime(scene));
        assertEquals(secondWorld.state(), firstWorld.state(), "re-seek must be idempotent");
        assertEquals(1, firstWorld.state().formedPulses().size());

        // Snapshot / restore round trip is stable.
        SceneWorldState snapshot = firstWorld.snapshot();
        first.seekTo(0);
        firstWorld.restore(snapshot);
        assertEquals(secondWorld.state(), firstWorld.state(), "restore must reproduce the snapshot");
    }

    @Test
    void seekClampsOutOfRangeAndCompletes() {
        SceneData scene = scene(step("s1", StepType.SHOW_SECTION, 30, "a"));
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);

        runner.seekTo(-100);
        assertEquals(0.0d, runner.time(), EPS);

        runner.seekTo(9999);
        assertEquals(30.0d, runner.time(), EPS);
        assertTrue(runner.isComplete());
    }

    @Test
    void appliesTheEffectVocabularyToTheWorld() {
        SceneData scene = scene(
                step("s1", StepType.SHOW_SECTION, 1, "section_a"),
                step("s2", StepType.HIDE_SECTION, 0, "section_b"),
                SceneStep.builder().id("s3").type(StepType.REPLACE_BLOCKS).targets(List.of("elem"))
                        .param("block", "gtceu:steel_casing").build(),
                SceneStep.builder().id("s4").type(StepType.HIGHLIGHT).targets(List.of("ctrl"))
                        .param("visible", true).build(),
                SceneStep.builder().id("s5").type(StepType.OUTLINE).targets(List.of("ctrl"))
                        .param("visible", false).build(),
                SceneStep.builder().id("s6").type(StepType.TEXT).narration("ponder.test.narration").build(),
                SceneStep.builder().id("s7").type(StepType.CAMERA).targets(List.of("ctrl"))
                        .param("yaw", 90.0d).param("pitch", 45.0d).param("distance", 8.0d).build(),
                SceneStep.builder().id("s8").type(StepType.INSTALL_MODULE).targets(List.of("slot1"))
                        .param("module", "gtceu:test_module").build(),
                step("s9", StepType.FORMED_PULSE, 0, "controller"),
                step("s10", StepType.PARTICLES, 0, "ctrl"),
                step("s11", StepType.IDLE, 0));

        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);
        runner.seekTo(SceneRunner.totalTime(scene));

        FakeSceneWorld.FakeState state = world.state();
        assertEquals(Boolean.TRUE, state.sections().get("section_a"));
        assertEquals(Boolean.FALSE, state.sections().get("section_b"));
        assertEquals("gtceu:steel_casing", state.blocks().get("elem"));
        assertEquals(Boolean.TRUE, state.highlights().get("ctrl"));
        assertEquals(Boolean.FALSE, state.outlines().get("ctrl"));
        assertEquals("ponder.test.narration", state.narration());
        assertEquals(CameraState.of("ctrl", 90.0d, 45.0d, 8.0d), state.camera());
        assertEquals("gtceu:test_module", state.modules().get("slot1"));
        assertEquals(List.of("controller"), state.formedPulses());
        assertEquals(List.of("ctrl"), state.particles());
    }
}
