package com.gtsn.ponder.client;

import com.gtsn.lib.ui.widget.TextMetrics;

/**
 * 图鉴目录「条目行」的纯布局计算（工单 #16 缺陷 B）：目录行原先把本地化名放进一个不裁剪的填充宽度，
 * 名称 / 原始 id 在窄窗口或长文案下互相叠印（截图 {@code 低压蒸汽合金炉steam_alloy_smelter}）。
 *
 * <p>本类做三件可 headless 断言的事：</p>
 * <ol>
 *   <li>给定行<b>可用</b>宽度，算出一整组「必定放得下」的响应式列宽（{@link #columnsFor}，工单 #19）；
 *       宽敞时 id / 按钮保持设计宽度、名称列吸收余量；窄屏按 id → 相关 → 播放 → 名称 收缩；</li>
 *   <li>算出「名称列」可用宽度（{@link #nameWidth}，扣除状态标记 / id / 按钮与间距）；</li>
 *   <li>把过长文本按像素宽度截断并追加省略号（{@value #ELLIPSIS}），使其<b>不超过</b>预算宽度。</li>
 * </ol>
 *
 * <p>渲染侧把各列放进 {@code ClipWidget}（名称 / id）或固定宽度按钮，故即便极端窄屏也不会溢出到
 * 相邻列或屏幕外。纯 Java、零 MC。</p>
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
    /** id 列最小宽度（窄屏第一个收缩的固定列；够放下省略号）。 */
    public static final int MIN_ID_WIDTH = 40;
    /** 「相关机器」按钮最小宽度（容纳「相关机器」/「Related」文案）。 */
    public static final int MIN_RELATED_WIDTH = 48;
    /** 「播放」按钮最小宽度（容纳「播放」/「Play」文案）。 */
    public static final int MIN_PLAY_WIDTH = 32;
    /** 截断省略号。 */
    public static final String ELLIPSIS = "…";

    private CatalogRowLayout() {
    }

    /**
     * 目录行的一组响应式列宽（单位：像素）。{@link #used()} 含 4 个列间距，即该组列宽在行内实际
     * 占用的整行宽度——自动测试据此断言「恒不超过可用行宽」。
     */
    public record Columns(int mark, int name, int id, int related, int play) {

        public Columns {
            if (mark < 0 || name < 0 || id < 0 || related < 0 || play < 0) {
                throw new IllegalArgumentException("column widths must be non-negative: mark=" + mark
                        + " name=" + name + " id=" + id + " related=" + related + " play=" + play);
            }
        }

        /** 本组列宽 + 4 个列间距占用的整行像素（= mark + name + id + related + play + 4×GAP）。 */
        public int used() {
            return mark + name + id + related + play + 4 * GAP;
        }
    }

    /**
     * 给定行可用宽度，返回一整组<b>必定放得下</b>的列宽（工单 #19）：宽敞时 id / 按钮保持设计宽度、
     * 名称列吸收全部余量；窄屏时按 id → 相关 → 播放 → 名称 的次序收缩（各自先收缩到可读最小），
     * 极端窄屏再从右向左归零，保证 {@code used() ≤ availableWidth}、尾部按钮永不被挤出可视区。
     *
     * <p>与修复前「名称列 {@code Sizing.fill()} + 固定列溢出」的差异：旧布局在余量 ≤ 0 时把名称列
     * 撑满整行、把 id / 按钮排到视口外（1280x720 @ GUI 缩放 3 → 行可用 ~262px，症状为只见标记 + 机器名）。</p>
     */
    public static Columns columnsFor(int availableWidth) {
        int budget = Math.max(0, availableWidth) - 4 * GAP;
        if (budget <= 0) {
            return new Columns(0, 0, 0, 0, 0);
        }
        // 期望宽度：名称列吸收全部余量（不小于最小宽度）。
        int[] cells = {
                MARK_WIDTH,
                Math.max(MIN_NAME_WIDTH, budget - MARK_WIDTH - ID_WIDTH - RELATED_WIDTH - PLAY_WIDTH),
                ID_WIDTH,
                RELATED_WIDTH,
                PLAY_WIDTH};
        // 第一轮收窄：id → 相关 → 播放 → 名称，先各自收到可读最小（标记不动）。
        int[] minimums = {0, MIN_NAME_WIDTH, MIN_ID_WIDTH, MIN_RELATED_WIDTH, MIN_PLAY_WIDTH};
        int over = cells[0] + cells[1] + cells[2] + cells[3] + cells[4] - budget;
        for (int index : new int[] {2, 3, 4, 1}) {
            over = shrink(cells, index, minimums[index], over);
        }
        // 第二轮：仍超预算（极端窄屏）→ 从右向左归零，标记最后。
        for (int index : new int[] {2, 3, 4, 1, 0}) {
            over = shrink(cells, index, 0, over);
        }
        return new Columns(cells[0], cells[1], cells[2], cells[3], cells[4]);
    }

    /** 从 {@code cells[index]} 再削减至多 {@code excess} 像素、但不低于 {@code floor}，返回剩余超出量。 */
    private static int shrink(int[] cells, int index, int floor, int excess) {
        if (excess <= 0) {
            return excess;
        }
        int cut = Math.min(excess, cells[index] - floor);
        cells[index] -= cut;
        return excess - cut;
    }

    /**
     * 给定整行宽度，返回名称列的可用像素宽度（宽敞时 = 行宽 − 固定列，不小于 {@link #MIN_NAME_WIDTH}；
     * 整行窄到连最小列都放不下时按 {@link #columnsFor} 的收缩次序继续变小）。
     */
    public static int nameWidth(int rowWidth) {
        return columnsFor(rowWidth).name();
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
