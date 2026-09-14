package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 思索图鉴目录核心（纯 Java，零 MC）：把已加载的场景列表 + 观看进度投影成可浏览 / 可搜索 /
 * 可统计的目录。屏幕（{@code SceneCatalogScreen}）只做渲染与输入，全部归类 / 过滤 / 计数逻辑在此，
 * 故可 headless 单测。
 *
 * <p>只收录<b>有目标</b>的场景（无目标者无法按 target 打开，无法从目录播放）；条目按
 * {@link SceneCategories} 的类别顺序、再按目标排序，保证目录稳定。</p>
 */
public final class SceneCatalog {

    private final List<CatalogEntry> entries;
    private final List<String> categories;

    private SceneCatalog(List<CatalogEntry> entries, List<String> categories) {
        this.entries = List.copyOf(entries);
        this.categories = List.copyOf(categories);
    }

    /** 由场景列表与观看进度构建目录。 */
    public static SceneCatalog of(List<SceneData> scenes, WatchedProgress progress) {
        WatchedProgress watched = progress == null ? WatchedProgress.empty() : progress;
        List<CatalogEntry> entries = new ArrayList<>();
        if (scenes != null) {
            for (SceneData scene : scenes) {
                if (scene == null || scene.target() == null || scene.target().isBlank()) {
                    continue;
                }
                String key = WatchedProgress.keyOf(scene);
                String category = SceneCategories.categoryOf(scene);
                entries.add(new CatalogEntry(key, scene.target(), scene.title(), scene.source(),
                        category, watched.isWatched(key)));
            }
        }
        entries.sort(Comparator.comparingInt((CatalogEntry entry) -> SceneCategories.orderIndex(entry.category()))
                .thenComparing(CatalogEntry::sortKey));
        return new SceneCatalog(entries, distinctCategories(entries));
    }

    /** 全部条目（稳定排序、不可变）。 */
    public List<CatalogEntry> entries() {
        return entries;
    }

    /** 当前存在的类别（按显示顺序）。 */
    public List<String> categories() {
        return categories;
    }

    /** 指定类别的条目。 */
    public List<CatalogEntry> inCategory(String category) {
        if (category == null) {
            return List.of();
        }
        return entries.stream().filter(entry -> category.equals(entry.category())).toList();
    }

    /** 搜索（键 / 目标 / 标题键 / 类别，大小写不敏感）；空白返回全部。 */
    public List<CatalogEntry> search(String query) {
        return entries.stream().filter(entry -> entry.matches(query)).toList();
    }

    public int total() {
        return entries.size();
    }

    public int watchedCount() {
        return (int) entries.stream().filter(CatalogEntry::watched).count();
    }

    /** 已看比例 {@code [0,1]}；无条目时 0。 */
    public double progressFraction() {
        return entries.isEmpty() ? 0.0d : (double) watchedCount() / entries.size();
    }

    private static List<String> distinctCategories(List<CatalogEntry> entries) {
        Set<String> present = new LinkedHashSet<>();
        for (CatalogEntry entry : entries) {
            present.add(entry.category());
        }
        List<String> ordered = new ArrayList<>();
        for (String category : SceneCategories.ORDER) {
            if (present.remove(category)) {
                ordered.add(category);
            }
        }
        ordered.addAll(present);
        return List.copyOf(ordered);
    }
}
