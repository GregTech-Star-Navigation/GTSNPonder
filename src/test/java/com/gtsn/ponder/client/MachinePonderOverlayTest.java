package com.gtsn.ponder.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GT 机器界面覆盖层点击交接逻辑测试（纯 Java，零 MC）：渲染期登记「GT 机器屏目标 + 按钮矩形」，
 * 点击 / 按键处理器据此解析目标。本测试锁定该可失败行为，使覆盖层点击解析部分被自动覆盖。
 */
class MachinePonderOverlayTest {

    @AfterEach
    void reset() {
        MachinePonderOverlay.get().reset();
    }

    @Test
    void blankTargetIsInactiveAndConsumesNothing() {
        MachinePonderOverlay.get().present("  ", 1280, 720);

        assertFalse(MachinePonderOverlay.get().isActive());
        assertTrue(MachinePonderOverlay.get().target().isEmpty());
        assertTrue(MachinePonderOverlay.get().button().isEmpty());
        assertTrue(MachinePonderOverlay.get().click(10, 10).isEmpty());
    }

    @Test
    void nonGtScreenClearDropsTheButton() {
        MachinePonderOverlay.get().present("gtceu:coke_oven", 1280, 720);
        assertTrue(MachinePonderOverlay.get().isActive());

        MachinePonderOverlay.get().clear();
        assertFalse(MachinePonderOverlay.get().isActive());
        assertTrue(MachinePonderOverlay.get().click(10, 10).isEmpty());
    }

    @Test
    void clickResolvesOnlyInsideTheRegisteredBox() {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        overlay.present("gtceu:coke_oven", 1280, 720);
        MachinePonderButton.Box box = overlay.button().orElseThrow();

        assertEquals(Optional.of("gtceu:coke_oven"), overlay.click(box.x() + 1, box.y() + 1));
        assertEquals(1, overlay.clicks());
        assertTrue(overlay.click(box.x() - 1, box.y() + 1).isEmpty(), "left of the box is a miss");
        assertTrue(overlay.click(box.x() + 1, box.y() - 1).isEmpty(), "above the box is a miss");
        assertEquals(1, overlay.clicks(), "misses must not count as clicks");
    }

    @Test
    void hoverTracksTheRegisteredBox() {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        overlay.present("gtceu:coke_oven", 1280, 720);
        MachinePonderButton.Box box = overlay.button().orElseThrow();

        assertTrue(overlay.hovered(box.x() + 1, box.y() + 1));
        assertFalse(overlay.hovered(box.x() - 1, box.y() + 1));
        assertFalse(overlay.hovered(1, 1), "the opposite corner is not hovered");
    }

    @Test
    void presentOverwritesThePreviousTarget() {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        overlay.present("gtceu:coke_oven", 1280, 720);
        MachinePonderButton.Box first = overlay.button().orElseThrow();
        overlay.present("gtceu:steam_grinder", 640, 480);
        MachinePonderButton.Box second = overlay.button().orElseThrow();

        assertTrue(overlay.click(first.x() + 1, first.y() + 1).isEmpty(), "old box is forgotten");
        assertEquals(Optional.of("gtceu:steam_grinder"), overlay.click(second.x() + 1, second.y() + 1));
    }

    @Test
    void recordOpenCountsSuccessfulOpens() {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        overlay.present("gtceu:coke_oven", 1280, 720);

        assertEquals(0, overlay.opens());
        overlay.recordOpen();
        overlay.recordOpen();
        assertEquals(2, overlay.opens());
    }
}
