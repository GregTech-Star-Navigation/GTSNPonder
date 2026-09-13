package com.gtsn.ponder.engine.director;

/**
 * 场景世界的不可变快照令牌（不透明）。
 *
 * <p>用于 {@link SceneWorld#snapshot()} / {@link SceneWorld#restore(SceneWorldState)} 实现
 * 确定性的 rewind / seek：导演核心先恢复基线快照，再从 0 重放效果。</p>
 */
public interface SceneWorldState {
}
