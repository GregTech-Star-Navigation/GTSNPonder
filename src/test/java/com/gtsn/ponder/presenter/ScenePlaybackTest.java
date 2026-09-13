package com.gtsn.ponder.presenter;

import com.gtsn.ponder.engine.director.FakeSceneWorld;
import com.gtsn.ponder.engine.director.SceneRunner;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯逻辑行为测试：{@link ScenePlayback}（表现层的时间轴 / 控制条绑定）。
 *
 * <p>只断言外部可观察量：时间、进度、当前步骤、旁白键、播放状态，以及控制操作
 * （播放 / 暂停 / 上一步 / 下一步 / 重播 / seek）之后的可观察结果。</p>
 */
class ScenePlaybackTest {

    private static final double EPS = 1.0e-9d;

    private static ScenePlayback playback() {
        SceneData scene = SceneData.builder()
                .formatVersion(1)
                .id("pb_test")
                .source(Source.HAND)
                .addStep(SceneStep.builder().id("s0").type(StepType.SHOW_SECTION)
                        .duration(10).addTarget("shell").build())
                .addStep(SceneStep.builder().id("s1").type(StepType.TEXT)
                        .duration(20).narration("ponder.test.n1").build())
                .addStep(SceneStep.builder().id("s2").type(StepType.TEXT)
                        .duration(30).narration("ponder.test.n2").build())
                .build();
        return new ScenePlayback(new SceneRunner(scene, new FakeSceneWorld()));
    }

    @Test
    void startsAtZeroWithNoProgressAndFirstStepActive() {
        ScenePlayback playback = playback();

        assertEquals(0.0d, playback.time(), EPS);
        assertEquals(0.0d, playback.progress(), EPS);
        assertEquals(0, playback.stepIndex());
        assertEquals(3, playback.stepCount());
        assertEquals(60.0d, playback.totalTime(), EPS);
        assertNull(playback.narrationKey(), "no narration before the first text step");
    }

    @Test
    void seekFractionMapsToAbsoluteTimeAndProgress() {
        ScenePlayback playback = playback();

        playback.seekFraction(0.5d);

        assertEquals(30.0d, playback.time(), EPS);
        assertEquals(0.5d, playback.progress(), EPS);
        assertEquals(2, playback.stepIndex());
        assertEquals("ponder.test.n2", playback.narrationKey());
    }

    @Test
    void seekFractionClampsOutOfRange() {
        ScenePlayback playback = playback();

        playback.seekFraction(-2.0d);
        assertEquals(0.0d, playback.time(), EPS);

        playback.seekFraction(9.0d);
        assertEquals(60.0d, playback.time(), EPS);
        assertEquals(1.0d, playback.progress(), EPS);
        assertTrue(playback.isComplete());
    }

    @Test
    void narrationKeyTracksTheMostRecentNarratedStep() {
        ScenePlayback playback = playback();

        playback.seekFraction(0.2d); // t=12, inside s1
        assertEquals("ponder.test.n1", playback.narrationKey());

        playback.seekFraction(0.5d); // t=30, s2 start
        assertEquals("ponder.test.n2", playback.narrationKey());

        playback.seekFraction(0.0d);
        assertNull(playback.narrationKey(), "rewinding before any narration clears the key");
    }

    @Test
    void pauseFreezesTimeAndResumeContinues() {
        ScenePlayback playback = playback();

        playback.play();
        assertTrue(playback.isPlaying());
        playback.tick(5.0d);
        assertEquals(5.0d, playback.time(), EPS);

        playback.pause();
        assertFalse(playback.isPlaying());
        playback.tick(5.0d);
        assertEquals(5.0d, playback.time(), EPS, "paused playback must not advance");

        playback.play();
        playback.tick(5.0d);
        assertEquals(10.0d, playback.time(), EPS);
    }

    @Test
    void nextStepJumpsToTheFollowingStepStart() {
        ScenePlayback playback = playback();

        playback.nextStep();
        assertEquals(10.0d, playback.time(), EPS);
        assertEquals(1, playback.stepIndex());

        playback.nextStep();
        assertEquals(30.0d, playback.time(), EPS);
        assertEquals(2, playback.stepIndex());

        playback.nextStep();
        assertEquals(30.0d, playback.time(), EPS, "next on the last step is a no-op");
    }

    @Test
    void previousStepRewindsToCurrentStartThenToThePriorStep() {
        ScenePlayback playback = playback();

        playback.seekFraction(0.5d); // t=30 == s2 start
        playback.previousStep();
        assertEquals(10.0d, playback.time(), EPS, "at a step boundary, go to the previous step");

        playback.seekFraction(0.9d); // t=54, mid s2
        playback.previousStep();
        assertEquals(30.0d, playback.time(), EPS, "mid-step, rewind to the current step start");

        playback.seekFraction(0.0d);
        playback.previousStep();
        assertEquals(0.0d, playback.time(), EPS, "previous on the first step is a no-op");
    }

    @Test
    void replayRestartsFromZeroAndPlays() {
        ScenePlayback playback = playback();
        playback.play();
        playback.tick(60.0d);
        assertTrue(playback.isComplete());
        assertFalse(playback.isPlaying(), "runner auto-pauses at the end");

        playback.replay();

        assertEquals(0.0d, playback.time(), EPS);
        assertEquals(0, playback.stepIndex());
        assertTrue(playback.isPlaying(), "replay resumes playback");
    }

    @Test
    void narrationKeyForExplicitStepIndex() {
        ScenePlayback playback = playback();

        assertNull(playback.narrationKeyAt(0));
        assertEquals("ponder.test.n1", playback.narrationKeyAt(1));
        assertEquals("ponder.test.n2", playback.narrationKeyAt(2));
        assertEquals(List.of("s0", "s1", "s2"), playback.stepIds());
    }
}
