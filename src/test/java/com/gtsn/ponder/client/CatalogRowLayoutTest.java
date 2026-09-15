package com.gtsn.ponder.client;

import com.gtsn.lib.ui.widget.TextMetrics;
import org.junit.jupiter.api.Test;

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
}
