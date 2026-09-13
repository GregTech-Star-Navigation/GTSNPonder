package com.gtsn.ponder.presenter;

import com.gtsn.ponder.engine.director.SceneRunner;
import com.gtsn.ponder.engine.director.SceneWorld;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 表现层的时间轴绑定（纯 Java，可 headless 单测）：把 {@link SceneRunner} 的原始状态
 * 换算成旁白 / 控制条 / 进度条需要的可观察量，并提供播放控制与 seek。
 *
 * <p>Presenter（GTSN UI 屏幕）只依赖本类，不直接触摸导演核心；因此「暂停冻结进度、上一步 /
 * 下一步跳转、seek 到分数、重播」等交互行为可在无 MC 环境断言（ticket #5 的单测覆盖）。</p>
 *
 * <p>旁白取「当前步骤及之前最近一个声明了 {@code narration} 的步骤」的本地化键——与导演核心
 * 只对 {@code text} 步骤设置旁白的语义一致。</p>
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class ScenePlayback {

    private static final double EPSILON = 1.0e-9d;

    private final SceneRunner runner;

    public ScenePlayback(SceneRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    /** 便捷构造：用场景数据与场景世界新建导演并包一层表现层绑定。 */
    public static ScenePlayback of(SceneData scene, SceneWorld world) {
        return new ScenePlayback(new SceneRunner(scene, world));
    }

    public SceneRunner runner() {
        return runner;
    }

    public SceneData scene() {
        return runner.scene();
    }

    public SceneWorld world() {
        return runner.world();
    }

    /** 当前时间（tick）。 */
    public double time() {
        return runner.time();
    }

    /** 总时长（tick）。 */
    public double totalTime() {
        return SceneRunner.totalTime(scene());
    }

    /** 归一化进度 {@code [0,1]}；总时长为 0 时返回 0。 */
    public double progress() {
        double total = totalTime();
        if (total <= 0.0d) {
            return 0.0d;
        }
        return clamp01(runner.time() / total);
    }

    public boolean isPlaying() {
        return runner.isPlaying();
    }

    public boolean isComplete() {
        return runner.isComplete();
    }

    /** 当前活动步骤下标；无则 {@code -1}。 */
    public int stepIndex() {
        return runner.currentStepIndex();
    }

    public int stepCount() {
        return scene().steps().size();
    }

    /** 全部步骤 ID（有序），供步骤列表绑定。 */
    public List<String> stepIds() {
        return scene().steps().stream().map(SceneStep::id).toList();
    }

    /** 第 {@code index} 个步骤的起始时间（越界返回 0 / 总时长之外由调用方保证）。 */
    public double stepStartTime(int index) {
        return SceneRunner.startTime(scene(), index);
    }

    /** 当前应显示的旁白本地化键；尚无旁白时为 {@code null}。 */
    public String narrationKey() {
        return narrationKeyAt(stepIndex());
    }

    /** 第 {@code index} 步及之前最近一次旁白键；无则 {@code null}。 */
    public String narrationKeyAt(int index) {
        return narrationStepAt(index).map(SceneStep::narration).orElse(null);
    }

    /** 当前旁白键的模板参数（无旁白时为 <b>空列表</b>）。 */
    public List<String> narrationArgs() {
        return narrationArgsAt(stepIndex());
    }

    /** 第 {@code index} 步及之前最近一次旁白键的模板参数；无则空列表。 */
    public List<String> narrationArgsAt(int index) {
        return narrationStepAt(index).map(SceneStep::narrationArgs).orElse(List.of());
    }

    /** 第 {@code index} 步及之前最近一个声明了旁白键的步骤。 */
    private Optional<SceneStep> narrationStepAt(int index) {
        List<SceneStep> steps = scene().steps();
        for (int i = Math.min(index, steps.size() - 1); i >= 0; i--) {
            if (steps.get(i).narration() != null) {
                return Optional.of(steps.get(i));
            }
        }
        return Optional.empty();
    }

    public void play() {
        runner.play();
    }

    public void pause() {
        runner.pause();
    }

    public void resume() {
        runner.resume();
    }

    public void togglePlay() {
        if (runner.isPlaying()) {
            runner.pause();
        } else {
            runner.play();
        }
    }

    /** 从起点重新开始并播放。 */
    public void replay() {
        runner.rewind();
        runner.play();
    }

    /** 推进 {@code deltaTicks}（仅在播放中生效）。 */
    public void tick(double deltaTicks) {
        runner.tick(deltaTicks);
    }

    /** 按归一化分数 seek（越界钳制到 {@code [0,1]}）。 */
    public void seekFraction(double fraction) {
        runner.seekTo(clamp01(fraction) * totalTime());
    }

    /** 跳到下一步骤的起始时刻；已是最后一步则不动。 */
    public void nextStep() {
        int index = stepIndex();
        int last = stepCount() - 1;
        if (index < 0 || index >= last) {
            return;
        }
        runner.seekTo(stepStartTime(index + 1));
    }

    /** 回到本步起点；已在本步起点时回到上一步起点（媒体控制语义）。 */
    public void previousStep() {
        int index = stepIndex();
        if (index <= 0) {
            runner.seekTo(0.0d);
            return;
        }
        double currentStart = stepStartTime(index);
        if (runner.time() > currentStart + EPSILON) {
            runner.seekTo(currentStart);
        } else {
            runner.seekTo(stepStartTime(index - 1));
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
