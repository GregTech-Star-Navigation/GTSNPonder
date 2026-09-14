package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.Source;

import java.util.Locale;

/**
 * 目录中的一个场景条目（不可变值）：稳定键、目标、标题键、来源、类别与观看态。
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public record CatalogEntry(String key, String target, String titleKey, Source source,
        String category, boolean watched) {

    /** 条目是否命中搜索词：大小写不敏感，匹配键 / 目标 / 标题键 / 类别。空白 → 全部命中。 */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return contains(key, needle) || contains(target, needle)
                || contains(titleKey, needle) || contains(category, needle);
    }

    /** 条目的稳定排序键（类别内按目标，再按键）。 */
    public String sortKey() {
        return (target == null ? "" : target) + '\u0000' + (key == null ? "" : key);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
