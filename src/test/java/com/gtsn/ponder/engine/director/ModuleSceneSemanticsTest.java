package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块安装效果的<b>快照 / 重放语义</b>（导演核心 × 内存假世界，headless）：
 * {@code INSTALL_MODULE} 在场景世界里留下「槽位 → 模块」占用状态，且该状态必须能被
 * {@code snapshot} / {@code restore} 完整捕获 / 还原——即 seek / rewind 之后安装效果与
 * 「顺序播放到该时刻」一致。世界桥（{@link com.gtsn.ponder.client.DummySceneWorld}）以此为契约。
 */
class ModuleSceneSemanticsTest {

    private static SceneData moduleScene() {
        return SceneData.builder()
                .formatVersion(1)
                .id("gtceu:module_demo")
                .source(Source.AUTO)
                .addStep(SceneStep.builder().id("intro").type(StepType.TEXT).duration(10)
                        .narration("ponder.test.intro").build())
                .addStep(SceneStep.builder().id("install").type(StepType.INSTALL_MODULE).duration(10)
                        .targets(List.of("moduleslot.0")).param("module", "gtceu:parallel_module").build())
                .addStep(SceneStep.builder().id("effect").type(StepType.TEXT).duration(20)
                        .narration("ponder.test.effect").build())
                .build();
    }

    @Test
    void installLeavesModuleOccupancyInTheWorldState() {
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(moduleScene(), world);
        runner.seekTo(SceneRunner.totalTime(moduleScene()));

        assertEquals("gtceu:parallel_module", world.state().modules().get("moduleslot.0"));
    }

    @Test
    void rewindClearsTheInstalledModuleAndReSeekRestoresIt() {
        SceneData scene = moduleScene();
        FakeSceneWorld world = new FakeSceneWorld();
        SceneRunner runner = new SceneRunner(scene, world);

        runner.seekTo(SceneRunner.totalTime(scene));
        assertEquals(1, world.state().modules().size(), "install must occupy the slot");

        runner.rewind();
        assertTrue(world.state().modules().isEmpty(), "rewind must clear the installed module");

        runner.seekTo(SceneRunner.totalTime(scene));
        assertEquals("gtceu:parallel_module", world.state().modules().get("moduleslot.0"),
                "re-seeking must replay the install effect deterministically");
        assertEquals(1, world.state().modules().size(), "replay must not duplicate the module entry");
    }

    @Test
    void seekToTheInstallFrameEqualsSequentialPlayback() {
        SceneData scene = moduleScene();
        double installFrame = SceneRunner.startTime(scene, 2); // after the install step completed

        FakeSceneWorld sequentialWorld = new FakeSceneWorld();
        SceneRunner sequential = new SceneRunner(scene, sequentialWorld);
        sequential.play();
        sequential.tick(installFrame);

        FakeSceneWorld seekWorld = new FakeSceneWorld();
        SceneRunner seek = new SceneRunner(scene, seekWorld);
        seek.seekTo(installFrame);

        assertEquals(sequentialWorld.state(), seekWorld.state(),
                "seek to the install frame must equal sequential playback");
    }
}
