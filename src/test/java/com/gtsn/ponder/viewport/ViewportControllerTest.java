package com.gtsn.ponder.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ViewportController} 的相机交互状态机：拖拽旋转、未按下不旋转、
 * 滚轮缩放与钳制、复位。纯 Java、零 MC 依赖。
 */
class ViewportControllerTest {

    @Test
    void startsAtLdlibDefaults() {
        ViewportController controller = new ViewportController();
        assertEquals(ViewportController.DEFAULT_YAW, controller.rotationYaw());
        assertEquals(ViewportController.DEFAULT_PITCH, controller.rotationPitch());
        assertEquals(ViewportController.DEFAULT_ZOOM, controller.zoom());
        assertFalse(controller.isDragging());
    }

    @Test
    void dragBeforePressDoesNothing() {
        ViewportController controller = new ViewportController();
        double yaw = controller.rotationYaw();
        double pitch = controller.rotationPitch();

        assertFalse(controller.dragged(5, 5, 10, 10));

        assertEquals(yaw, controller.rotationYaw());
        assertEquals(pitch, controller.rotationPitch());
        assertFalse(controller.isDragging());
    }

    @Test
    void horizontalDragRotatesPitchAndWraps() {
        ViewportController controller = new ViewportController();
        assertTrue(controller.press(10, 10, 0));

        assertTrue(controller.dragged(20, 10, 30, 0));

        assertEquals(ViewportController.wrap360(ViewportController.DEFAULT_PITCH + 30), controller.rotationPitch());
        assertEquals(ViewportController.DEFAULT_YAW, controller.rotationYaw(), "水平拖拽不改 yaw");
    }

    @Test
    void verticalDragChangesYawAndClamps() {
        ViewportController controller = new ViewportController();
        controller.press(10, 10, 0);

        controller.dragged(10, 1000, 0, 1000);
        assertEquals(ViewportController.YAW_LIMIT, controller.rotationYaw());

        controller.dragged(10, -1000, 0, -1000);
        assertEquals(-ViewportController.YAW_LIMIT, controller.rotationYaw());
    }

    @Test
    void releaseEndsDragCapture() {
        ViewportController controller = new ViewportController();
        controller.press(10, 10, 0);
        assertTrue(controller.isDragging());

        assertTrue(controller.released(10, 10, 0));

        assertFalse(controller.isDragging());
        assertFalse(controller.dragged(10, 10, 50, 50), "释放后拖拽不再生效");
    }

    @Test
    void nonPrimaryButtonDoesNotCapture() {
        ViewportController controller = new ViewportController();
        assertFalse(controller.press(10, 10, 1));
        assertFalse(controller.isDragging());
    }

    @Test
    void scrollUpZoomsInAndDownZoomsOut() {
        ViewportController controller = new ViewportController();
        double start = controller.zoom();

        assertTrue(controller.scrolled(10, 10, 1.0));
        assertEquals(start - ViewportController.ZOOM_STEP, controller.zoom(), "向上滚轮拉近");

        controller.scrolled(10, 10, -1.0);
        assertEquals(start, controller.zoom(), "向下滚轮恢复距离");
    }

    @Test
    void zoomIsClampedToRange() {
        ViewportController controller = new ViewportController();
        for (int i = 0; i < 10_000; i++) {
            controller.scrolled(0, 0, -1.0);
        }
        assertEquals(ViewportController.MAX_ZOOM, controller.zoom());

        for (int i = 0; i < 10_000; i++) {
            controller.scrolled(0, 0, 1.0);
        }
        assertEquals(ViewportController.MIN_ZOOM, controller.zoom());
    }

    @Test
    void resetRestoresCamera() {
        ViewportController controller = new ViewportController();
        controller.press(0, 0, 0);
        controller.dragged(0, 0, 100, 50);
        controller.scrolled(0, 0, 1.0);

        controller.reset();

        assertEquals(ViewportController.DEFAULT_YAW, controller.rotationYaw());
        assertEquals(ViewportController.DEFAULT_PITCH, controller.rotationPitch());
        assertEquals(ViewportController.DEFAULT_ZOOM, controller.zoom());
    }
}
