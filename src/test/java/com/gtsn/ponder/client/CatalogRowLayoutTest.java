package com.gtsn.ponder.client;

import com.gtsn.lib.ui.widget.TextMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目录行布局测试（纯 Java，零 MC，工单 #16 缺陷 B）：行内「本地化名（主）/ 原始 id（次）」原先共用
 * 一个无裁剪的宽度，长名 / 长 id 会互相叠印。本测试锁定可失败的截断 / 列宽计算行为，保证行内各列
 * 有界、不溢出（配合 {@code ClipWidget}，渲染期亦不越界）。
 */
class CatalogRowLayoutTest {

    /** 假字体：每字符 {@code charWidth} 像素宽。 */
    private static TextMetrics metrics(int charWidth) {
        return new TextMetrics() {
            @Override
            public int width(String text) {
                return text.length() * charWidth;
            }

            @Override
            public int lineHeight() {
                return 9;
            }
        };
    }

    @Test
    void truncateKeepsShortTextUnchanged() {
        assertEquals("abc", CatalogRowLayout.truncate("abc", 100, metrics(10)));
    }

    @Test
    void truncateAppendsEllipsisWhenTextIsTooLong() {
        // 6 字 * 10 = 60 > 45；「…」宽 10，故最多保留 3 字：abc…（40 ≤ 45）。
        String result = CatalogRowLayout.truncate("abcdef", 45, metrics(10));
        assertEquals("abc" + CatalogRowLayout.ELLIPSIS, result);
        assertTrue(metrics(10).width(result) <= 45, "truncated text must fit the budget");
    }

    @Test
    void truncateToExactlyTheEllipsisWidthKeepsOnlyTheEllipsis() {
        assertEquals(CatalogRowLayout.ELLIPSIS, CatalogRowLayout.truncate("abcdef", 10, metrics(10)));
    }

    @Test
    void truncateReturnsEmptyWhenEvenTheEllipsisDoesNotFit() {
        assertEquals("", CatalogRowLayout.truncate("abcdef", 5, metrics(10)));
        assertEquals("", CatalogRowLayout.truncate("abcdef", 0, metrics(10)));
    }

    @Test
    void truncateHandlesNullAndEmpty() {
        assertEquals("", CatalogRowLayout.truncate(null, 100, metrics(10)));
        assertEquals("", CatalogRowLayout.truncate("", 100, metrics(10)));
    }

    @Test
    void nameWidthReservesTheFixedColumnsAndStaysPositive() {
        int wide = CatalogRowLayout.nameWidth(800);
        int narrow = CatalogRowLayout.nameWidth(180);

        assertTrue(wide > narrow, "a wider row must give the name more room");
        assertTrue(narrow >= CatalogRowLayout.MIN_NAME_WIDTH,
                "the name column must never collapse below the minimum");
        assertTrue(wide < 800 - CatalogRowLayout.ID_WIDTH,
                "the fixed id/button columns must be reserved");
    }

    @Test
    void fixedColumnsFitAnOrdinaryRow() {
        int fixed = CatalogRowLayout.MARK_WIDTH + CatalogRowLayout.ID_WIDTH
                + CatalogRowLayout.RELATED_WIDTH + CatalogRowLayout.PLAY_WIDTH
                + 4 * CatalogRowLayout.GAP;
        assertTrue(fixed < 480, "fixed columns must leave room for the name at a normal width: " + fixed);
    }

    // ---- 工单 #19：响应式列宽（窄窗口 / 高 GUI 缩放不再把尾部按钮挤出可视区） ----

    /**
     * 自动测试三尺寸的行可用宽（屏幕宽 − 164 = 根内边距 8×2 + 侧栏 132 + 主列间距 6 + 列表滚动条 6 +
     * 列表内边距 2×2）：1280x720@2 → 476；1920x1080@2 → 796；1280x720@3 → 262。
     */
    private static final List<Integer> AUTOTEST_ROW_WIDTHS = List.of(262, 476, 796);

    @Test
    void columnsAtEveryAutotestSizeFitTheRow() {
        for (int available : AUTOTEST_ROW_WIDTHS) {
            CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(available);
            assertTrue(columns.used() <= available,
                    "cells + gaps must fit " + available + "px, got " + columns);
        }
    }

    @Test
    void columnsAtAComfortableWidthUseTheDesignedColumns() {
        // 476 − 4×6 间距 − 40 标记 − 168 id − 56 相关 − 52 播放 = 136 名称列。
        CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(476);

        assertEquals(CatalogRowLayout.MARK_WIDTH, columns.mark());
        assertEquals(CatalogRowLayout.ID_WIDTH, columns.id());
        assertEquals(CatalogRowLayout.RELATED_WIDTH, columns.related());
        assertEquals(CatalogRowLayout.PLAY_WIDTH, columns.play());
        assertEquals(136, columns.name());
        assertEquals(476, columns.used(), "a comfortable row fills exactly");
    }

    @Test
    void columnsAt1920AtScale2FillTheWiderRow() {
        // 796 − 24 − 316 = 456 名称列；其余列保持设计宽度。
        CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(796);

        assertEquals(456, columns.name());
        assertEquals(796, columns.used());
    }

    @Test
    void columnsAtANarrowWidthShrinkTheIdColumnBeforeTheButtons() {
        CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(262);

        assertEquals(CatalogRowLayout.MIN_NAME_WIDTH, columns.name(),
                "the name column keeps its minimum before the buttons give up space");
        assertEquals(CatalogRowLayout.MIN_ID_WIDTH, columns.id(),
                "the id column is the first fixed column to shrink");
        assertTrue(columns.related() >= CatalogRowLayout.MIN_RELATED_WIDTH,
                "the related button keeps its readable minimum: " + columns);
        assertTrue(columns.play() >= CatalogRowLayout.MIN_PLAY_WIDTH,
                "the play button keeps its readable minimum: " + columns);
        assertEquals(262, columns.used(), "a narrow row still fills exactly without overflowing");
    }

    @Test
    void columnsAlwaysFitAcrossTheSupportedWidthRange() {
        for (int available = 160; available <= 1600; available += 7) {
            CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(available);
            assertTrue(columns.used() <= available,
                    "cells + gaps must never exceed " + available + "px, got " + columns);
            assertTrue(columns.mark() >= 0 && columns.name() >= 0 && columns.id() >= 0
                            && columns.related() >= 0 && columns.play() >= 0,
                    "no cell may go negative: " + columns);
        }
    }
}
