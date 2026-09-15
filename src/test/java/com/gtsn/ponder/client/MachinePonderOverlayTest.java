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
        MachinePonderOverlay.xei().reset();
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

    // --- XEI page entry (工单 #20)：入口属于「页面」，不随悬停消失，否则点不到 -------------------

    /** page-list 位置（左侧页面按钮列）的锚点矩形：与屏幕右上角按钮不同。 */
    private static final MachinePonderButton.Box PAGE_ANCHOR =
            MachinePonderButton.Box.of(240, 87, 20, 18);

    @Test
    void pageEntrySurvivesThePointerLeavingTheHoveredItem() {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        Object page = new Object();

        overlay.presentPage("gtceu:coke_oven", PAGE_ANCHOR, page);
        assertTrue(overlay.isActive(), "a machine on the page registers the entry");

        // 指针为点击入口而离开悬停物品：本帧不再有机器目标（真实 EMI 悬停解析返回空），但仍是同一页。
        overlay.presentPage(null, PAGE_ANCHOR, page);

        assertTrue(overlay.isActive(),
                "the entry must survive the pointer travelling from the hovered item to it");
        assertEquals(Optional.of("gtceu:coke_oven"), overlay.target(),
                "the latched target is what the click must open");
        assertEquals(Optional.of(PAGE_ANCHOR), overlay.button());
        assertEquals(Optional.of("gtceu:coke_oven"),
                overlay.click(PAGE_ANCHOR.x() + 1, PAGE_ANCHOR.y() + 1),
                "the click on the page-list entry resolves the machine");
    }

    @Test
    void pageEntryUsesThePageAnchorBoxNotTheScreenCorner() {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        overlay.presentPage("gtceu:coke_oven", PAGE_ANCHOR, new Object());

        assertEquals(Optional.of(PAGE_ANCHOR), overlay.button(),
                "the XEI page entry is drawn at the page anchor, never at the screen corner");
        assertFalse(MachinePonderButton.bounds(640, 360).contains(
                        PAGE_ANCHOR.x() + 1, PAGE_ANCHOR.y() + 1),
                "the page anchor must not coincide with the top-right corner button");
    }

    @Test
    void pageEntryClearsWhenThePageChanges() {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        overlay.presentPage("gtceu:coke_oven", PAGE_ANCHOR, new Object());

        overlay.presentPage(null, PAGE_ANCHOR, new Object());

        assertFalse(overlay.isActive(), "a different XEI page must not inherit the previous entry");
        assertTrue(overlay.click(PAGE_ANCHOR.x() + 1, PAGE_ANCHOR.y() + 1).isEmpty());
    }

    @Test
    void pageEntryClearsWhenTheScreenIsNoLongerAnXeiPage() {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        Object page = new Object();
        overlay.presentPage("gtceu:coke_oven", PAGE_ANCHOR, page);

        overlay.presentPage(null, null, page);

        assertFalse(overlay.isActive(), "leaving the XEI page (no anchor) drops the entry");
        assertTrue(overlay.button().isEmpty());
    }

    @Test
    void resetDropsTheLatchedPageEntry() {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        Object page = new Object();
        overlay.presentPage("gtceu:coke_oven", PAGE_ANCHOR, page);

        overlay.reset();

        assertFalse(overlay.isActive());
        overlay.presentPage(null, PAGE_ANCHOR, page);
        assertFalse(overlay.isActive(), "reset must also forget the page identity");
    }
}
