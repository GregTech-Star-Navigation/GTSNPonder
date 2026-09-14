package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /** 通用词元：出现频率高且不构成机器族标识，剔除以免产生虚假的「相关」关系。 */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "large", "small", "multiblock", "multi", "primitive", "basic");

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

    /**
     * 「相关机器」导航（纯 Java，零 MC；v1 冻结格式无 {@code related} 字段，故由可测规则派生）：
     * 与给定条目相关的其它条目——<b>同类别</b>，或目标 id 共享一个<b>显著词元</b>（机器族，
     * 见 {@link #targetTokens(String)}）。结果保持目录的稳定顺序、去重且排除自身。
     *
     * <p>这是跨场景「跳转」在冻结 schema 下的降级实现：目录内导航到相关条目 / 类别，
     * 而非场景内跳转（后者需新增 {@code related} 字段，须先开 issue + 落 ADR）。</p>
     */
    public List<CatalogEntry> relatedTo(CatalogEntry entry) {
        if (entry == null) {
            return List.of();
        }
        List<CatalogEntry> related = new ArrayList<>();
        for (CatalogEntry candidate : entries) {
            if (candidate.equals(entry)) {
                continue;
            }
            if (isRelated(entry, candidate)) {
                related.add(candidate);
            }
        }
        return List.copyOf(related);
    }

    /** 两个条目是否相关：同类别，或目标 id 共享显著词元。 */
    static boolean isRelated(CatalogEntry left, CatalogEntry right) {
        if (left.category() != null && left.category().equals(right.category())) {
            return true;
        }
        Set<String> shared = targetTokens(left.target());
        shared.retainAll(targetTokens(right.target()));
        return !shared.isEmpty();
    }

    /**
     * 目标 id 的<b>显著词元</b>：去掉命名空间后按 {@code _} 切分，保留长度 ≥ 3 且非通用词
     * （{@code large} / {@code small} / {@code multiblock} / {@code multi} / {@code primitive} /
     * {@code basic}）的词元。用于派生「同一机器族」关系（如 {@code pyrolyse_oven} 与
     * {@code coke_oven} 共享 {@code oven}）。确定性、可测。
     */
    static Set<String> targetTokens(String target) {
        Set<String> tokens = new HashSet<>();
        if (target == null || target.isBlank()) {
            return tokens;
        }
        String path = target;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        for (String token : path.toLowerCase(Locale.ROOT).split("_")) {
            if (token.length() >= 3 && !GENERIC_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
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
