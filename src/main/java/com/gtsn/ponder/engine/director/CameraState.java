package com.gtsn.ponder.engine.director;

/**
 * 相机位姿（值对象）：目标锚点 + 球坐标（yaw / pitch / distance）+ 可选取景自适应参数。
 *
 * <p>{@code fitMargin > 0} 表示「视口层应忽略 {@code distance}，改按结构包围盒与视口纵横比
 * 计算恰好容纳结构的相机距离」（{@code margin} 为目标填充比例）。这是自动生成器让结构占满
 * 视口的方式；手作场景用 {@code distance} 精确指定、{@code fitMargin == 0}。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。视口层负责将其映射到实际渲染相机。</p>
 */
public record CameraState(String targetId, double yaw, double pitch, double distance, double fitMargin) {

    /** 兼容构造：不启用取景自适应（{@code fitMargin == 0}）。 */
    public CameraState(String targetId, double yaw, double pitch, double distance) {
        this(targetId, yaw, pitch, distance, 0.0d);
    }

    public static CameraState of(String targetId, double yaw, double pitch, double distance) {
        return new CameraState(targetId, yaw, pitch, distance, 0.0d);
    }

    /** 是否请求按包围盒 + 纵横比自适应取景。 */
    public boolean fits() {
        return fitMargin > 0.0d;
    }
}
