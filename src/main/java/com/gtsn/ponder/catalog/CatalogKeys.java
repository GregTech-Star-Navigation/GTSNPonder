package com.gtsn.ponder.catalog;

/**
 * 思索图鉴目录（#11）与 JEI/EMI 入口所用的<b>本地化键</b>（单一事实源）：目录标题 / 搜索 / 类别 /
 * 进度 / 观看标记 / 空态 / 关闭，以及 XEI「思索」按钮文案。供目录屏幕与 datagen 的 lang provider 共用
 * （全部文案走本地化键，中英双语）。
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class CatalogKeys {

    private CatalogKeys() {
    }

    /** 目录屏幕标题。 */
    public static final String TITLE = "ponder.gtsnponder.catalog.title";
    /** 搜索框占位 / 标签。 */
    public static final String SEARCH = "ponder.gtsnponder.catalog.search";
    /** 「全部类别」按钮。 */
    public static final String ALL = "ponder.gtsnponder.catalog.all";
    /** 条目的「播放」按钮。 */
    public static final String PLAY = "ponder.gtsnponder.catalog.play";
    /** 已看标记。 */
    public static final String WATCHED = "ponder.gtsnponder.catalog.watched";
    /** 未看标记。 */
    public static final String UNWATCHED = "ponder.gtsnponder.catalog.unwatched";
    /** 总进度模板（已看 / 总数）。 */
    public static final String PROGRESS = "ponder.gtsnponder.catalog.progress";
    /** 无匹配条目时的空态。 */
    public static final String EMPTY = "ponder.gtsnponder.catalog.empty";
    /** 关闭按钮。 */
    public static final String CLOSE = "ponder.gtsnponder.catalog.close";

    /** XEI（JEI/EMI）机器页「思索」按钮的悬浮提示。 */
    public static final String XEI_OPEN = "ponder.gtsnponder.xei.open";
    /** XEI 机器页「思索」按钮的短标签。 */
    public static final String XEI_OPEN_SHORT = "ponder.gtsnponder.xei.open.short";

    /** 类别显示名键前缀（{@code ...category.<key>}）。 */
    public static final String CATEGORY_PREFIX = "ponder.gtsnponder.catalog.category.";

    /** 类别键 → 本地化键。 */
    public static String categoryKey(String category) {
        return CATEGORY_PREFIX + category;
    }
}
