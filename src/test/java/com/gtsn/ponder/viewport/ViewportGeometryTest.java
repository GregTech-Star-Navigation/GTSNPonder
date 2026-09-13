package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ViewportGeometry} 的坐标映射、命中测试与裁剪矩形推导。纯几何断言。
 */
class ViewportGeometryTest {

    private static final Rect BOUNDS = Rect.of(100, 50, 200, 150);

    @Test
    void localCoordinatesAreRelativeToTopLeft() {
        assertEquals(0.0, ViewportGeometry.localX(BOUNDS, 100));
        assertEquals(0.0, ViewportGeometry.localY(BOUNDS, 50));
        assertEquals(25.5, ViewportGeometry.localX(BOUNDS, 125.5));
        assertEquals(Math.PI, ViewportGeometry.localY(BOUNDS, 50 + Math.PI), 1.0e-9);
    }

    @Test
    void containsUsesHalfOpenBounds() {
        assertTrue(ViewportGeometry.contains(BOUNDS, 100, 50), "左上角在内");
        assertTrue(ViewportGeometry.contains(BOUNDS, 299.99, 199.99), "内部点在内");
        assertFalse(ViewportGeometry.contains(BOUNDS, 300, 100), "右边界在外");
        assertFalse(ViewportGeometry.contains(BOUNDS, 100, 200), "下边界在外");
        assertFalse(ViewportGeometry.contains(BOUNDS, 99.99, 100), "左侧在外");
    }

    @Test
    void clipRectDefaultsToBounds() {
        assertEquals(BOUNDS, ViewportGeometry.clipRect(BOUNDS));
    }

    @Test
    void clipRectSupportsInsetForBorders() {
        assertEquals(Rect.of(104, 54, 192, 142), ViewportGeometry.clipRect(BOUNDS, Insets.all(4)));
    }

    @Test
    void relayoutKeepsLocalMappingRelativeToNewOrigin() {
        Rect resized = Rect.of(20, 10, 300, 200);
        assertEquals(5.0, ViewportGeometry.localX(resized, 25));
        assertEquals(15.0, ViewportGeometry.localY(resized, 25));
        assertTrue(ViewportGeometry.contains(resized, 25, 25));
        assertFalse(ViewportGeometry.contains(BOUNDS, 25, 25), "重排后旧矩形坐标不再命中");
    }
}
