package com.gtsn.ponder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GT 机器界面覆盖按钮的纯几何测试（零 MC）：位置在屏幕右上角、矩形在屏内、命中测试边界正确。
 * 使 #12 覆盖层的「画在哪 / 点到了没」在无客户端环境仍被自动覆盖（fail-able）。
 */
class MachinePonderButtonTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    void buttonSitsAtTheTopRightCornerWithMargin() {
        MachinePonderButton.Box box = MachinePonderButton.bounds(WIDTH, HEIGHT);

        assertEquals(WIDTH - MachinePonderButton.WIDTH - MachinePonderButton.MARGIN, box.x());
        assertEquals(MachinePonderButton.MARGIN, box.y());
        assertEquals(MachinePonderButton.WIDTH, box.width());
        assertEquals(MachinePonderButton.HEIGHT, box.height());
        assertTrue(box.right() <= WIDTH, "button must stay inside the screen horizontally");
        assertTrue(box.bottom() <= HEIGHT, "button must stay inside the screen vertically");
        assertFalse(box.contains(box.x() - 1, box.y()), "just left of the box is a miss");
        assertTrue(box.contains(box.x(), box.y()), "top-left corner is inside");
    }

    @Test
    void rightAndBottomEdgesAreExclusive() {
        MachinePonderButton.Box box = MachinePonderButton.bounds(WIDTH, HEIGHT);

        assertTrue(box.contains(box.right() - 0.5d, box.y() + 1), "inside near the right edge");
        assertFalse(box.contains(box.right(), box.y() + 1), "right edge is exclusive");
        assertFalse(box.contains(box.x() + 1, box.bottom()), "bottom edge is exclusive");
        assertTrue(box.contains(box.x() + box.width() / 2.0d, box.y() + box.height() / 2.0d), "center is inside");
    }

    @Test
    void narrowScreenClampsTheButtonInside() {
        MachinePonderButton.Box box = MachinePonderButton.bounds(40, 40);

        assertEquals(MachinePonderButton.MARGIN, box.x(), "button is clamped to the left margin on a narrow screen");
        assertTrue(box.x() >= 0 && box.y() >= 0);
    }
}
