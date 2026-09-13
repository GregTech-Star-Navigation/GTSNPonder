package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneStep;

/**
 * 单一 {@link com.gtsn.ponder.engine.model.StepType} 的行为契约。
 *
 * <p>契约借鉴 Create-Ponder 的 instruction 模型（{@code isBlocking} / {@code onScheduled}），
 * 是本工单的扩展点：未来的注册式 Java 步骤实现按类型替换默认行为。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public interface StepBehavior {

    /**
     * 该步骤是否阻塞时间轴。默认实现：{@code duration > 0}。
     */
    default boolean isBlocking(SceneStep step) {
        return step.duration() > 0;
    }

    /**
     * 步骤在其起始时刻被调度时应用的效果；须为幂等的状态设置（供重放）。
     */
    void onScheduled(SceneStep step, SceneWorld world);
}
