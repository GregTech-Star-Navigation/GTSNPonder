package com.gtsn.ponder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XEI（JEI/EMI）页面内「思索」入口的纯几何测试（工单 #20）：入口必须落在<b>页面左侧的页面按钮列</b>
 * （EMI {@code RecipeScreen} 左侧翻页箭头之下、配方内容左内缩之内），而不是屏幕右上角悬浮。
 *
 * <p>几何策略（零 MC）在此 headless 锁定；EMI/JEI 适配器只负责把各自屏幕布局换算成「配方面板左上角」，
 * 故「画在哪」可在无客户端环境被自动覆盖（fail-able）。</p>
 */
class XeiPageEntryLayoutTest {

    /** 1280x720 @ GUI 缩放 2 下的真实 EMI 值：配方面板左上角 = (235, 57)。 */
    private static final int PAGE_LEFT = 235;
    private static final int PAGE_TOP = 57;
    private static final int SCREEN_W = 640;
    private static final int SCREEN_H = 360;
    /** EMI 配方内容的左内缩：入口不得越过它（否则压住配方）。 */
    private static final int CONTENT_LEFT_INSET = 25;

    @Test
    void entrySitsInTheLeftPageButtonColumn() {
        MachinePonderButton.Box box =
                XeiPageEntryLayout.leftColumnBox(PAGE_LEFT, PAGE_TOP, SCREEN_W, SCREEN_H);

        assertTrue(box.x() >= PAGE_LEFT, "the entry starts at the recipe panel's left edge");
        assertTrue(box.x() <= PAGE_LEFT + 8, "the entry hugs the left edge, not the page centre");
        assertTrue(box.right() <= PAGE_LEFT + CONTENT_LEFT_INSET,
                "the entry must not overlap the recipe content which starts " + CONTENT_LEFT_INSET
                        + "px in from the panel's left edge");
        assertTrue(box.y() > PAGE_TOP + 20,
                "the entry sits below the page navigation arrows stacked at the panel's top-left");
        assertTrue(box.y() <= PAGE_TOP + 60, "the entry stays in the top block of the page column");
        assertTrue(box.x() < SCREEN_W / 2, "the entry is on the left half of the screen");
    }

    @Test
    void entryIsNotTheTopRightCornerButton() {
        MachinePonderButton.Box box =
                XeiPageEntryLayout.leftColumnBox(PAGE_LEFT, PAGE_TOP, SCREEN_W, SCREEN_H);
        MachinePonderButton.Box corner = MachinePonderButton.bounds(SCREEN_W, SCREEN_H);

        assertTrue(box.x() + box.width() <= corner.x(),
                "the page entry must lie entirely left of the corner button (工单 #20 位置整改)");
        assertEquals(XeiPageEntryLayout.WIDTH, box.width());
        assertEquals(XeiPageEntryLayout.HEIGHT, box.height());
        assertTrue(box.height() >= 8 && box.height() <= 24, "a page-button-sized entry");
    }

    @Test
    void entryStaysInsideANarrowScreen() {
        MachinePonderButton.Box box = XeiPageEntryLayout.leftColumnBox(
                4, 4, XeiPageEntryLayout.WIDTH, XeiPageEntryLayout.HEIGHT);

        assertTrue(box.x() >= 0 && box.y() >= 0, "clamped to the screen's top-left");
        assertTrue(box.right() <= XeiPageEntryLayout.WIDTH, "clamped horizontally");
        assertTrue(box.bottom() <= XeiPageEntryLayout.HEIGHT, "clamped vertically");
    }

    @Test
    void labelIsScaledDownOnlyWhenItWouldOverflow() {
        // 中文「思索」= 2 个 CJK 字形 ≈ 18px，20 宽的入口里无需缩放。
        assertEquals(1.0f, XeiPageEntryLayout.labelScale(18, 9,
                XeiPageEntryLayout.WIDTH, XeiPageEntryLayout.HEIGHT), 0.001f);
        // 英文 "Ponder" ≈ 36px，必须缩进 20 宽（留 2px 边距 → 0.5）。
        assertEquals(0.5f, XeiPageEntryLayout.labelScale(36, 9,
                XeiPageEntryLayout.WIDTH, XeiPageEntryLayout.HEIGHT), 0.001f);
        // 极端宽度仍不小于下限，保证可读（不缩成 0）。
        assertTrue(XeiPageEntryLayout.labelScale(400, 9, XeiPageEntryLayout.WIDTH,
                XeiPageEntryLayout.HEIGHT) >= XeiPageEntryLayout.MIN_LABEL_SCALE);
    }
}
