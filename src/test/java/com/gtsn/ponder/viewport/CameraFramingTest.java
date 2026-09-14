package com.gtsn.ponder.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取景自适应（{@link CameraFraming}）的外部可观察行为：距离随尺寸线性缩放、随视口纵横比调整；
 * 按求解出的距离 + 中心偏移投影时，包围盒投影恰好填充目标比例且居中。
 */
class CameraFramingTest {

    private static final double FOV = CameraFraming.DEFAULT_FOV_Y_DEGREES;
    private static final double YAW = 25.0d;
    private static final double PITCH = -135.0d;
    private static final double VIEW_W = 624.0d;
    private static final double VIEW_H = 236.0d;

    @Test
    void distanceIsPositiveAndScalesLinearlyWithSize() {
        double small = CameraFraming.fit(3, 3, 3, YAW, PITCH, VIEW_W, VIEW_H, FOV, 0.9d).distance();
        double doubled = CameraFraming.fit(6, 6, 6, YAW, PITCH, VIEW_W, VIEW_H, FOV, 0.9d).distance();

        assertTrue(small > 0.0d);
        assertEquals(2.0d, doubled / small, 1.0e-6d, "same aspect → distance scales with structure size");
    }

    @Test
    void degenerateInputsReturnAnInvalidFit() {
        assertFalse(CameraFraming.fit(0, 3, 3, YAW, PITCH, VIEW_W, VIEW_H, FOV, 0.9d).isValid());
        assertFalse(CameraFraming.fit(3, 3, 3, YAW, PITCH, 0, VIEW_H, FOV, 0.9d).isValid());
    }

    @Test
    void fitCentersAndFillsTheProjectedBoundingBox() {
        double fill = 0.9d;
        CameraFraming.Fit fit = CameraFraming.fit(5, 5, 5, YAW, PITCH, VIEW_W, VIEW_H, FOV, fill);
        double[] bounds = projectedBounds(5, 5, 5, fit, VIEW_W, VIEW_H);

        double verticalSpan = bounds[1] - bounds[0];
        double horizontalSpan = bounds[3] - bounds[2];
        double maxSpan = Math.max(verticalSpan, horizontalSpan);

        assertEquals(2.0d * fill, maxSpan, 1.0e-3d,
                "the projected bounding box must span the target fill on its binding axis");
        assertTrue(Math.abs((bounds[0] + bounds[1]) / 2.0d) < 1.0e-3d,
                "the projected bounding box must be vertically centered");
        assertTrue(Math.abs((bounds[2] + bounds[3]) / 2.0d) < 1.0e-3d,
                "the projected bounding box must be horizontally centered");
    }

    @Test
    void wideStructureNeedsMoreDistanceInANarrowViewport() {
        // 宽扁结构：窄视口（纵横比 1）比宽视口（纵横比 3）需要更远的相机，证明纵横比参与计算。
        double wide = CameraFraming.fit(12, 3, 3, YAW, PITCH, 600, 200, FOV, 0.9d).distance();
        double square = CameraFraming.fit(12, 3, 3, YAW, PITCH, 200, 200, FOV, 0.9d).distance();

        assertTrue(square > wide, "narrower viewport must require a larger distance for a wide structure");
    }

    /** 按 fit（距离 + 中心偏移）把包围盒 8 角投影到 NDC，返回 {minV, maxV, minH, maxH}。 */
    private static double[] projectedBounds(double sx, double sy, double sz, CameraFraming.Fit fit,
            double viewportWidth, double viewportHeight) {
        double[] dir = CameraFraming.cameraDirection(YAW, PITCH);
        double fx = -dir[0];
        double fy = -dir[1];
        double fz = -dir[2];
        double rx = -fz;
        double rz = fx;
        double rightLength = Math.sqrt(rx * rx + rz * rz);
        rx /= rightLength;
        rz /= rightLength;
        double ux = 0.0d * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - 0.0d * fx;
        double tanY = Math.tan(Math.toRadians(FOV) / 2.0d);
        double tanX = (viewportWidth / viewportHeight) * tanY;
        double halfW = sx / 2.0d;
        double halfH = sy / 2.0d;
        double halfD = sz / 2.0d;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        double minH = Double.POSITIVE_INFINITY;
        double maxH = Double.NEGATIVE_INFINITY;
        for (double ox : new double[] { -halfW, halfW }) {
            for (double oy : new double[] { -halfH, halfH }) {
                for (double oz : new double[] { -halfD, halfD }) {
                    double dx = ox - fit.centerOffsetX();
                    double dy = oy - fit.centerOffsetY();
                    double dz = oz - fit.centerOffsetZ();
                    double depth = fit.distance() - (dx * dir[0] + dy * dir[1] + dz * dir[2]);
                    double vertical = (dx * ux + dy * uy + dz * uz) / (depth * tanY);
                    double horizontal = (dx * rx + dy * 0.0d + dz * rz) / (depth * tanX);
                    minV = Math.min(minV, vertical);
                    maxV = Math.max(maxV, vertical);
                    minH = Math.min(minH, horizontal);
                    maxH = Math.max(maxH, horizontal);
                }
            }
        }
        return new double[] { minV, maxV, minH, maxH };
    }
}
