package com.gtsn.ponder.viewport;

/**
 * 视口相机交互状态机（纯 Java，可 headless 单测）：拖拽旋转 + 滚轮缩放。
 *
 * <p>刻意把「输入 → 相机状态」这一步与 LDLib 渲染解耦：状态机是唯一事实源，视口实现只负责
 * 把这里的角度 / 缩放写进 LDLib {@code SceneWidget}。因此「矩形内拖拽改变朝向、矩形外拖拽不
 * 改变、滚轮改变缩放」等行为可在无 MC 环境断言，而渲染侧只做机械映射。</p>
 *
 * <p><b>角度语义对齐 LDLib</b>：字段命名与 {@code SceneWidget} 的 protected 字段一致
 * （{@code rotationYaw} 默认 {@value #DEFAULT_YAW}、{@code rotationPitch} 默认 {@value #DEFAULT_PITCH}、
 * {@code zoom} 默认 {@value #DEFAULT_ZOOM}），映射规则也与 {@code SceneWidget#mouseDragged} /
 * {@code mouseWheelMove} 相同（水平位移改 {@code rotationPitch} 并 360° 回绕；垂直位移改
 * {@code rotationYaw} 并钳制到 ±{@value #YAW_LIMIT}），从而与 LDLib 的坐标约定保持视觉一致。</p>
 */
public final class ViewportController {

    /** 默认水平朝向（与 {@code SceneWidget} 初值一致）。 */
    public static final double DEFAULT_YAW = 25.0d;
    /** 默认俯仰（与 {@code SceneWidget} 初值一致）。 */
    public static final double DEFAULT_PITCH = -135.0d;
    /** 默认缩放 / 相机距离（与 {@code SceneWidget} 初值一致）。 */
    public static final double DEFAULT_ZOOM = 5.0d;

    /** 垂直拖拽可用的俯仰范围（度），与 {@code SceneWidget} 的钳制一致。 */
    public static final double YAW_LIMIT = 89.9d;
    /** 缩放下限 / 上限与步长（与 {@code SceneWidget} 一致）。 */
    public static final double MIN_ZOOM = 0.1d;
    public static final double MAX_ZOOM = 999.0d;
    public static final double ZOOM_STEP = 0.5d;

    private double rotationYaw = DEFAULT_YAW;
    private double rotationPitch = DEFAULT_PITCH;
    private double zoom = DEFAULT_ZOOM;
    private boolean dragging;
    private double lastLocalX;
    private double lastLocalY;

    /** 复位相机到默认朝向 / 缩放（不影响拖拽态——调用方通常先释放）。 */
    public void reset() {
        rotationYaw = DEFAULT_YAW;
        rotationPitch = DEFAULT_PITCH;
        zoom = DEFAULT_ZOOM;
    }

    /**
     * 直接设定相机位姿（场景 {@code CameraState} → 视口相机）。角度 / 缩放按与拖拽 / 滚轮
     * 相同的规则钳制，随后用户重新拖拽 / 缩放会从该位姿继续。
     */
    public void set(double rotationYaw, double rotationPitch, double zoom) {
        this.rotationYaw = clamp(rotationYaw, -YAW_LIMIT, YAW_LIMIT);
        this.rotationPitch = rotationPitch;
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /** 指针按下：主键（0）开始拖拽并返回 {@code true}，其它键返回 {@code false}。 */
    public boolean press(double localX, double localY, int button) {
        lastLocalX = localX;
        lastLocalY = localY;
        if (button != 0) {
            return false;
        }
        dragging = true;
        return true;
    }

    /** 指针移动：记录局部位置；不改变相机。 */
    public boolean moved(double localX, double localY) {
        lastLocalX = localX;
        lastLocalY = localY;
        return true;
    }

    /** 拖拽：仅在拖拽态下旋转相机（水平 → pitch 回绕，垂直 → yaw 钳制）。 */
    public boolean dragged(double localX, double localY, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        lastLocalX = localX;
        lastLocalY = localY;
        rotationPitch = wrap360(rotationPitch + dragX);
        rotationYaw = clamp(rotationYaw + dragY, -YAW_LIMIT, YAW_LIMIT);
        return true;
    }

    /** 指针释放：结束拖拽态。 */
    public boolean released(double localX, double localY, int button) {
        lastLocalX = localX;
        lastLocalY = localY;
        dragging = false;
        return true;
    }

    /**
     * 滚轮缩放：向上（{@code scrollDelta > 0}）拉近（zoom 减小），向下拉远；钳制到
     * {@code [MIN_ZOOM, MAX_ZOOM]}。
     */
    public boolean scrolled(double localX, double localY, double scrollDelta) {
        lastLocalX = localX;
        lastLocalY = localY;
        double step = scrollDelta < 0 ? ZOOM_STEP : -ZOOM_STEP;
        zoom = clamp(zoom + step, MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    public boolean isDragging() {
        return dragging;
    }

    public double rotationYaw() {
        return rotationYaw;
    }

    public double rotationPitch() {
        return rotationPitch;
    }

    public double zoom() {
        return zoom;
    }

    public double lastLocalX() {
        return lastLocalX;
    }

    public double lastLocalY() {
        return lastLocalY;
    }

    /** 角度回绕到 {@code [0,360)}（与 {@code SceneWidget} 的 {@code +360 % 360} 等价）。 */
    public static double wrap360(double degrees) {
        double wrapped = degrees % 360.0d;
        return wrapped < 0.0d ? wrapped + 360.0d : wrapped;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
