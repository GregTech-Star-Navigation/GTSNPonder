package com.gtsn.ponder.viewport;

/**
 * 取景自适应（纯 Java，可 headless 单测）：给定结构包围盒与视口纵横比，算出「让结构占满视口且
 * 居中」的相机距离（= LDLib {@code SceneWidget} 的 zoom）与视线中心偏移。
 *
 * <p><b>为什么要按纵横比算</b>：LDLib 的 {@code SceneWidget} 使用透视投影，垂直 FOV 固定
 * {@value #DEFAULT_FOV_Y_DEGREES}°，水平 FOV 由视口纵横比推出（{@code tan(fovX/2) =
 * aspect · tan(fovY/2)}）。同一个距离在不同视口尺寸下填充度不同；只有把纵横比纳入计算，才能
 * 在不同窗口 / GUI 缩放下得到一致的填充。</p>
 *
 * <h2>相机约定（与 LDLib {@code WorldSceneRenderer} 对齐）</h2>
 * <p>{@code SceneWidget.setCameraYawAndPitch(rotationYaw, rotationPitch)} 最终调用
 * {@code WorldSceneRenderer.setCameraLookAt(center, radius, rotationPitch, rotationYaw)}，其从
 * <b>场景中心指向相机</b>的单位方向为</p>
 * <pre>dir = normalize( (cos(rotationPitch), tan(rotationYaw), sin(rotationPitch)) )</pre>
 * <p>相机位于 {@code center + distance · dir}，看向 {@code center}，up 取世界 {@code (0,1,0)}；
 * 相机基（forward / right / up）按标准 {@code lookAt} 求。</p>
 *
 * <h2>求解</h2>
 * <ol>
 *   <li><b>距离（填满限制维）</b>：把包围盒 8 角投到相机空间；结构投影<b>跨度</b>只受距离影响（沿视线
 *       方向平移不改变各角深度），故以二分法求「投影跨度 ≤ {@code 2·target}」的最小距离，使结构占据
 *       视口<b>限制维</b>（两个投影跨度中较大的那个轴；宽视口下即高度）约 {@code target} 比例。
 *       {@code target} = 请求值钳制到 {@value #MIN_LIMITING_OCCUPANCY}..{@value #MAX_FILL}。</li>
 *   <li><b>居中</b>：透视下近端角比远端角放大更多，直接对准包围盒中心会让投影包围盒偏移
 *       （出现一侧大黑边）。本类再求一个沿相机 right / up 的视线中心偏移，使投影包围盒居中；
 *       偏移与投影偏移近似线性，迭代 {@value #CENTER_ITERATIONS} 次收敛（残余偏移 &lt; 1e-6）。</li>
 * </ol>
 *
 * <p>纯 Java、零 MC / LDLib 依赖；{@code com.gtsn.ponder.viewport} 包纪律由
 * {@code ViewportImportIsolationTest} 自证。</p>
 */
public final class CameraFraming {

    /** LDLib {@code WorldSceneRenderer} 的默认垂直 FOV（度）；{@code LdlibSceneViewport} 不改它。 */
    public static final double DEFAULT_FOV_Y_DEGREES = 60.0d;

    /** 填充目标上限（安全钳制）：1.0 表示恰好贴边，留一点余量更稳。 */
    public static final double MAX_FILL = 0.98d;

    /**
     * 取景自适应下「限制维」的最小占用比例（小余量下限）：限制维 = 视口的窄轴，宽视口即<b>高度</b>。
     * 请求值低于此下限时按下限取景，使结构始终填满限制维、只留约 {@code 1 - 0.92 = 8%} 的总余量
     * （每侧约 4%），消除「结构偏小 / 四周大片黑边」的观感。视口宽高比是唯一硬约束：宽视口下立方体
     * 结构按面积无法填满，故以「填满限制维 + 垂直居中」为可达目标。
     *
     * <p>生成器写入的 {@code margin=0.90} 是保守下界；取景层据此下限抬升到 0.92，保证无论场景请求
     * 多保守，画面都不会留下大片空白。</p>
     */
    public static final double MIN_LIMITING_OCCUPANCY = 0.92d;

    private static final double EPSILON = 1.0e-9d;
    private static final int BISECTION_STEPS = 80;
    /**
     * 居中定点迭代次数：占用越高，透视近大远小的非线性越强，直接对准包围盒中心的残余偏移越大
     * （16 次迭代在 0.92 占用下约 {@code 1e-3}，在 0.95 下约 {@code 2.4e-3}）。64 次迭代把残余
     * 偏移压到 {@code ~1e-9}，保证高占用下仍严格垂直 / 水平居中。
     */
    private static final int CENTER_ITERATIONS = 64;

    private CameraFraming() {
    }

    /** 取景结果：相机距离 + 视线中心相对包围盒中心的偏移（世界坐标）。 */
    public record Fit(double distance, double centerOffsetX, double centerOffsetY, double centerOffsetZ) {

        public static Fit none() {
            return new Fit(0.0d, 0.0d, 0.0d, 0.0d);
        }

        public boolean isValid() {
            return distance > 0.0d;
        }
    }

    /**
     * 计算让结构包围盒投影「居中并填充视口至 {@code fill}」的相机参数。
     *
     * @param sizeX 结构包围盒 X 尺寸（格，&gt; 0）
     * @param sizeY 结构包围盒 Y 尺寸（格，&gt; 0）
     * @param sizeZ 结构包围盒 Z 尺寸（格，&gt; 0）
     * @param rotationYawDeg {@code SceneWidget} 的 rotationYaw（度；控制相机高度角分量）
     * @param rotationPitchDeg {@code SceneWidget} 的 rotationPitch（度；控制相机水平方位）
     * @param viewportWidth 视口像素宽（&gt; 0）
     * @param viewportHeight 视口像素高（&gt; 0）
     * @param fovYDegrees 垂直 FOV（度）
     * @param fill 目标填充比例（{@code (0,1]}）；钳制到 {@value #MIN_LIMITING_OCCUPANCY}..{@value #MAX_FILL}，
     *             即请求低于小余量下限时按 {@value #MIN_LIMITING_OCCUPANCY} 取景（限制维始终填满）
     * @return 取景结果；输入退化（尺寸 / 视口非正）时返回 {@link Fit#none()}
     */
    public static Fit fit(double sizeX, double sizeY, double sizeZ,
            double rotationYawDeg, double rotationPitchDeg,
            double viewportWidth, double viewportHeight,
            double fovYDegrees, double fill) {
        if (!(sizeX > 0.0d) || !(sizeY > 0.0d) || !(sizeZ > 0.0d)
                || !(viewportWidth > 0.0d) || !(viewportHeight > 0.0d)) {
            return Fit.none();
        }
        double clampedFill = clamp(fill, MIN_LIMITING_OCCUPANCY, MAX_FILL);
        Basis basis = basis(rotationYawDeg, rotationPitchDeg);
        double tanY = Math.tan(Math.toRadians(fovYDegrees) / 2.0d);
        if (tanY < EPSILON) {
            return Fit.none();
        }
        double tanX = (viewportWidth / viewportHeight) * tanY;
        double[][] corners = corners(sizeX, sizeY, sizeZ);

        double offsetX = 0.0d;
        double offsetY = 0.0d;
        double offsetZ = 0.0d;
        double distance = 0.0d;
        for (int iteration = 0; iteration < CENTER_ITERATIONS; iteration++) {
            distance = distanceForSpan(corners, basis, tanX, tanY, clampedFill,
                    offsetX, offsetY, offsetZ);
            double[] center = projectedBoundingCenter(corners, basis, tanX, tanY, distance,
                    offsetX, offsetY, offsetZ);
            // 投影偏移 ≈ -(s·up)/(z·tanY)（竖直）/ -(s·right)/(z·tanX)（水平）；反解视线平移。
            double vertical = center[0] * distance * tanY;
            double horizontal = center[1] * distance * tanX;
            offsetX += vertical * basis.upX + horizontal * basis.rightX;
            offsetY += vertical * basis.upY + horizontal * basis.rightY;
            offsetZ += vertical * basis.upZ + horizontal * basis.rightZ;
        }
        distance = distanceForSpan(corners, basis, tanX, tanY, clampedFill, offsetX, offsetY, offsetZ);
        if (!(distance > 0.0d)) {
            return Fit.none();
        }
        return new Fit(distance, offsetX, offsetY, offsetZ);
    }

    /** 便捷重载：固定垂直 FOV（{@link #DEFAULT_FOV_Y_DEGREES}）。 */
    public static Fit fit(double sizeX, double sizeY, double sizeZ,
            double rotationYawDeg, double rotationPitchDeg,
            double viewportWidth, double viewportHeight, double fill) {
        return fit(sizeX, sizeY, sizeZ, rotationYawDeg, rotationPitchDeg,
                viewportWidth, viewportHeight, DEFAULT_FOV_Y_DEGREES, fill);
    }

    /** 相机方向（center → eye）的单位向量分量，供测试 / 诊断与渲染层复用。 */
    public static double[] cameraDirection(double rotationYawDeg, double rotationPitchDeg) {
        Basis basis = basis(rotationYawDeg, rotationPitchDeg);
        return new double[] { basis.dirX, basis.dirY, basis.dirZ };
    }

    // --- 内部 ---------------------------------------------------------------

    /** 相机基：dir（center→eye）、right、up（forward = -dir）。 */
    private record Basis(double dirX, double dirY, double dirZ,
            double rightX, double rightY, double rightZ,
            double upX, double upY, double upZ) {
    }

    private static Basis basis(double rotationYawDeg, double rotationPitchDeg) {
        double yaw = Math.toRadians(rotationYawDeg);
        double pitch = Math.toRadians(rotationPitchDeg);
        double vecX = Math.cos(pitch);
        double vecZ = Math.sin(pitch);
        double vecY = Math.tan(yaw) * Math.sqrt(vecX * vecX + vecZ * vecZ);
        double length = Math.sqrt(vecX * vecX + vecY * vecY + vecZ * vecZ);
        if (length < EPSILON) {
            length = 1.0d;
        }
        double dirX = vecX / length;
        double dirY = vecY / length;
        double dirZ = vecZ / length;
        // forward = -dir；right = normalize(cross(forward, (0,1,0))) = normalize(-fz, 0, fx)
        double fx = -dirX;
        double fy = -dirY;
        double fz = -dirZ;
        double rx = -fz;
        double rz = fx;
        double rightLength = Math.sqrt(rx * rx + rz * rz);
        if (rightLength < EPSILON) {
            rx = 1.0d;
            rz = 0.0d;
            rightLength = 1.0d;
        }
        rx /= rightLength;
        rz /= rightLength;
        // up = cross(right, forward)
        double ux = 0.0d * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - 0.0d * fx;
        return new Basis(dirX, dirY, dirZ, rx, 0.0d, rz, ux, uy, uz);
    }

    private static double[][] corners(double sizeX, double sizeY, double sizeZ) {
        double halfX = sizeX / 2.0d;
        double halfY = sizeY / 2.0d;
        double halfZ = sizeZ / 2.0d;
        double[][] corners = new double[8][];
        int index = 0;
        for (double ox : new double[] { -halfX, halfX }) {
            for (double oy : new double[] { -halfY, halfY }) {
                for (double oz : new double[] { -halfZ, halfZ }) {
                    corners[index++] = new double[] { ox, oy, oz };
                }
            }
        }
        return corners;
    }

    /** 二分求使投影跨度 ≤ {@code 2·fill} 的最小距离（跨度随距离单调下降）。 */
    private static double distanceForSpan(double[][] corners, Basis basis, double tanX, double tanY,
            double fill, double centerOffsetX, double centerOffsetY, double centerOffsetZ) {
        double minDir = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double ox = corner[0] - centerOffsetX;
            double oy = corner[1] - centerOffsetY;
            double oz = corner[2] - centerOffsetZ;
            minDir = Math.max(minDir, ox * basis.dirX + oy * basis.dirY + oz * basis.dirZ);
        }
        double lo = minDir + 1.0e-3d;
        double hi = minDir + 1.0e5d;
        if (spanAt(corners, basis, tanX, tanY, lo, centerOffsetX, centerOffsetY, centerOffsetZ)
                <= 2.0d * fill) {
            return lo;
        }
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = 0.5d * (lo + hi);
            if (spanAt(corners, basis, tanX, tanY, mid, centerOffsetX, centerOffsetY, centerOffsetZ)
                    <= 2.0d * fill) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /** 结构投影在该距离下的最大跨度（竖直 / 水平取大者，单位：NDC）。 */
    private static double spanAt(double[][] corners, Basis basis, double tanX, double tanY, double distance,
            double centerOffsetX, double centerOffsetY, double centerOffsetZ) {
        double[] bounds = projectedBounds(corners, basis, tanX, tanY, distance,
                centerOffsetX, centerOffsetY, centerOffsetZ);
        double verticalSpan = bounds[1] - bounds[0];
        double horizontalSpan = bounds[3] - bounds[2];
        return Math.max(verticalSpan, horizontalSpan);
    }

    /** 投影包围盒中心（竖直, 水平），单位：NDC。 */
    private static double[] projectedBoundingCenter(double[][] corners, Basis basis, double tanX, double tanY,
            double distance, double centerOffsetX, double centerOffsetY, double centerOffsetZ) {
        double[] bounds = projectedBounds(corners, basis, tanX, tanY, distance,
                centerOffsetX, centerOffsetY, centerOffsetZ);
        return new double[] { (bounds[0] + bounds[1]) / 2.0d, (bounds[2] + bounds[3]) / 2.0d };
    }

    /** 返回 {minV, maxV, minH, maxH}（NDC）。 */
    private static double[] projectedBounds(double[][] corners, Basis basis, double tanX, double tanY,
            double distance, double centerOffsetX, double centerOffsetY, double centerOffsetZ) {
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        double minH = Double.POSITIVE_INFINITY;
        double maxH = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double ox = corner[0] - centerOffsetX;
            double oy = corner[1] - centerOffsetY;
            double oz = corner[2] - centerOffsetZ;
            double depth = distance - (ox * basis.dirX + oy * basis.dirY + oz * basis.dirZ);
            if (depth < 1.0e-4d) {
                depth = 1.0e-4d;
            }
            double vertical = (ox * basis.upX + oy * basis.upY + oz * basis.upZ) / (depth * tanY);
            double horizontal = (ox * basis.rightX + oy * basis.rightY + oz * basis.rightZ) / (depth * tanX);
            minV = Math.min(minV, vertical);
            maxV = Math.max(maxV, vertical);
            minH = Math.min(minH, horizontal);
            maxH = Math.max(maxH, horizontal);
        }
        return new double[] { minV, maxV, minH, maxH };
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
