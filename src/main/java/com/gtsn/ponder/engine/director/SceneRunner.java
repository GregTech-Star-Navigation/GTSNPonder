package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneStep;

import java.util.Objects;
import java.util.Optional;

/**
 * 导演核心：按确定性顺序推进场景、处理 pause/resume、seek/rewind，并把步骤效果应用到
 * {@link SceneWorld}。
 *
 * <p><b>确定性策略（Ticket #3 的刻意修复）</b>：任何 seek 都先恢复<b>基线快照</b>再<b>从 0 重放</b>
 * 所有 {@code start <= t} 的步骤；tick 仅在当前状态上增量应用跨过起始时刻的步骤。由于效果均为
 * 幂等状态设置，两条路径产生一致的世界状态，因此「seek 到 t」严格等于「顺序播放到 t」，
 * rewind 可重复，重放幂等。</p>
 *
 * <p><b>时间轴语义</b>：步骤按数据顺序排布；{@code duration > 0} 为阻塞步骤（占据其时长），
 * {@code duration == 0} 为瞬时的非阻塞步骤（在其起始时刻生效，不占用时间）。总时长 = 各步骤时长之和。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneRunner {

    private static final double EPSILON = 1.0e-9d;

    private final SceneData scene;
    private final SceneWorld world;
    private final SceneWorldState baseState;
    private final double[] stepStarts;

    private int appliedSteps;
    private double time;
    private boolean playing;

    public SceneRunner(SceneData scene, SceneWorld world) {
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
        this.world = Objects.requireNonNull(world, "world must not be null");
        this.stepStarts = new double[scene.steps().size()];
        double cursor = 0.0d;
        for (int i = 0; i < scene.steps().size(); i++) {
            stepStarts[i] = cursor;
            cursor += scene.steps().get(i).duration();
        }
        this.baseState = world.snapshot();
        this.appliedSteps = 0;
        this.time = 0.0d;
        this.playing = false;
        // Apply every step scheduled at or before t=0 (e.g. a step starting at 0).
        applyStepsUpTo(0.0d);
    }

    public SceneData scene() {
        return scene;
    }

    public SceneWorld world() {
        return world;
    }

    /** 当前时间（tick）。 */
    public double time() {
        return time;
    }

    public boolean isPlaying() {
        return playing;
    }

    /** 是否已到达 {@link #totalTime(SceneData)}。 */
    public boolean isComplete() {
        return time + EPSILON >= totalTime(scene);
    }

    /** 当前活动步骤下标；无则 {@code -1}。 */
    public int currentStepIndex() {
        for (int i = scene.steps().size() - 1; i >= 0; i--) {
            if (stepStarts[i] <= time + EPSILON) {
                return i;
            }
        }
        return -1;
    }

    public Optional<SceneStep> currentStep() {
        int index = currentStepIndex();
        return index < 0 ? Optional.empty() : Optional.of(scene.steps().get(index));
    }

    /** 当前活动步骤是否为阻塞步骤。 */
    public boolean isCurrentStepBlocking() {
        return currentStep().map(StepBehaviors::isBlocking).orElse(false);
    }

    public void play() {
        playing = true;
    }

    public void resume() {
        play();
    }

    public void pause() {
        playing = false;
    }

    /** 推进 {@code deltaTicks}（仅在播放中生效）；到达终点自动暂停。 */
    public void tick(double deltaTicks) {
        if (!playing || deltaTicks <= 0.0d) {
            return;
        }
        double total = totalTime(scene);
        double target = Math.min(this.time + deltaTicks, total);
        applyStepsUpTo(target);
        this.time = target;
        if (this.time + EPSILON >= total) {
            this.playing = false;
        }
    }

    /** 跳到绝对时间 {@code time}（越界钳制到 {@code [0, totalTime]}）。 */
    public void seekTo(double time) {
        double target = Math.max(0.0d, Math.min(time, totalTime(scene)));
        world.restore(baseState);
        appliedSteps = 0;
        this.time = 0.0d;
        applyStepsUpTo(target);
        this.time = target;
    }

    /** 回到起点（等价 {@code seekTo(0)}）。 */
    public void rewind() {
        seekTo(0.0d);
    }

    /** 总时长 = 所有步骤 {@code duration} 之和。 */
    public static double totalTime(SceneData scene) {
        double total = 0.0d;
        for (SceneStep step : scene.steps()) {
            total += step.duration();
        }
        return total;
    }

    /** 第 {@code index} 个步骤的起始时间（= 前序步骤时长之和）。 */
    public static double startTime(SceneData scene, int index) {
        if (index <= 0) {
            return 0.0d;
        }
        double total = 0.0d;
        for (int i = 0; i < index && i < scene.steps().size(); i++) {
            total += scene.steps().get(i).duration();
        }
        return total;
    }

    /**
     * 增量应用所有起始时间 {@code <= upto} 且尚未应用的步骤（按数据顺序）。
     */
    private void applyStepsUpTo(double upto) {
        while (appliedSteps < scene.steps().size() && stepStarts[appliedSteps] <= upto + EPSILON) {
            SceneStep step = scene.steps().get(appliedSteps);
            StepBehaviors.forType(step.type()).onScheduled(step, world);
            appliedSteps++;
        }
    }
}
