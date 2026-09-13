package com.gtsn.ponder.engine.director;

/**
 * 导演核心操作「场景世界」的抽象接缝。
 *
 * <p>接口刻意保持最小且可扩展：第一垂直切片只需方块增删 / 分段显隐 / 高亮 / 轮廓 /
 * 旁白 / 相机；后续视口层以 LDLib 虚世界实现，测试以内存假世界实现。</p>
 *
 * <p>实现须保证：<b>效果是状态设置（幂等）</b>，且 {@link #snapshot()} / {@link #restore(SceneWorldState)}
 * 能完整捕获 / 还原状态——这是 rewind / seek 确定性的基础。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）：不得出现任何 {@code net.minecraft} 类型。</p>
 */
public interface SceneWorld {

    /** 显示 / 隐藏一个分段（{@code sectionId} 为元素稳定 ID）。 */
    void setSectionVisible(String sectionId, boolean visible);

    /** 把某元素的方块替换为给定方块（客户端由世界桥解析元素 → 实际坐标）。 */
    void replaceBlocks(String elementId, String blockId);

    /** 开启 / 关闭高亮。 */
    void setHighlight(String targetId, boolean active);

    /** 开启 / 关闭轮廓。 */
    void setOutline(String targetId, boolean active);

    /** 设置当前旁白文案（本地化键）。 */
    void setNarration(String narrationKey);

    /** 设置相机位姿。 */
    void setCamera(CameraState camera);

    /** 在某模块位安装模块。 */
    void installModule(String slotId, String moduleId);

    /** 触发一次「成型」脉冲（控制器 ID）。 */
    void pulseFormed(String controllerId);

    /** 在目标处发射粒子。 */
    void emitParticles(String targetId);

    /** 捕获当前世界状态的不可变快照。 */
    SceneWorldState snapshot();

    /** 用快照覆盖当前世界状态。 */
    void restore(SceneWorldState state);
}
