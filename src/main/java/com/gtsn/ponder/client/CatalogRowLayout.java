package com.gtsn.ponder.client;

import com.gtsn.lib.ui.widget.TextMetrics;

/**
 * 图鉴目录「条目行」的纯布局计算（工单 #16 缺陷 B）：目录行原先把本地化名放进一个不裁剪的填充宽度，
 * 名称 / 原始 id 在窄窗口或长文案下互相叠印（截图 {@code 低压蒸汽合金炉steam_alloy_smelter}）。
 *
 * <p>本类只做两件可 headless 断言的事：</p>
 * <ol>
 *   <li>给定行宽，算出「名称列」可用宽度（扣除状态标记 / id / 按钮与间距）；</li>
 *   <li>把过长文本按像素宽度截断并追加省略号（{@value #ELLIPSIS}），使其<b>不超过</b>预算宽度。</li>
 * </ol>
 *
 * <p>渲染侧再把名称 / id 放进 {@code ClipWidget}，故即便极端窄屏也不会溢出到相邻列。纯 Java、零 MC。</p>
 */
public final class CatalogRowLayout {

    /** 已看 / 未看标记列宽。 */
    public static final int MARK_WIDTH = 40;
    /** 原始 id（次要信息）列宽。 */
    public static final int ID_WIDTH = 168;
    /** 「相关机器」按钮宽。 */
    public static final int RELATED_WIDTH = 56;
    /** 「播放」按钮宽。 */
    public static final int PLAY_WIDTH = 52;
    /** 列间距。 */
    public static final int GAP = 6;
    /** 名称列最小宽度（再窄则宁可裁切按钮区，也不让名称列消失）。 */
    public static final int MIN_NAME_WIDTH = 72;
    /** 截断省略号。 */
    public static final String ELLIPSIS = "…";

    /** 行内固定列（不含名称）占用的像素：标记 + id + 相关 + 播放 + 5 列间 4 个间距。 */
    private static final int FIXED_COLUMNS = MARK_WIDTH + ID_WIDTH + RELATED_WIDTH + PLAY_WIDTH + 4 * GAP;

    private CatalogRowLayout() {
    }

    /** 给定整行宽度，返回名称列的可用像素宽度（不小于 {@link #MIN_NAME_WIDTH}）。 */
    public static int nameWidth(int rowWidth) {
        return Math.max(MIN_NAME_WIDTH, rowWidth - FIXED_COLUMNS);
    }

    /**
     * 把 {@code text} 截断到不超过 {@code maxWidth} 像素：放得下则原样返回；否则保留尽可能长的前缀并
     * 追加 {@link #ELLIPSIS}；若连省略号都放不下则返回空串。{@code null} 视为空串。
     */
    public static String truncate(String text, int maxWidth, TextMetrics metrics) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (metrics.width(text) <= maxWidth) {
            return text;
        }
        if (metrics.width(ELLIPSIS) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (metrics.width(text.substring(0, mid) + ELLIPSIS) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + ELLIPSIS;
    }
}
