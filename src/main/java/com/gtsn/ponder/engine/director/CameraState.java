package com.gtsn.ponder.engine.director;

/**
 * 相机位姿（值对象）：目标锚点 + 球坐标（yaw / pitch / distance）。
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。视口层负责将其映射到实际渲染相机。</p>
 */
public record CameraState(String targetId, double yaw, double pitch, double distance) {

    public static CameraState of(String targetId, double yaw, double pitch, double distance) {
        return new CameraState(targetId, yaw, pitch, distance);
    }
}
