package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图鉴目录核心的行为测试：把场景列表 + 观看进度投影成可浏览 / 可搜索 / 可统计的目录。
 *
 * <p>只断言外部可观察输出（条目、类别、搜索结果、进度计数），不触碰屏幕实现。</p>
 */
class SceneCatalogTest {

    private static SceneData scene(String id, String target, Source source) {
        return SceneData.builder().formatVersion(1).id(id).target(target).source(source).build();
    }

    private static SceneCatalog fixture(WatchedProgress progress) {
        List<SceneData> scenes = List.of(
                scene("gtceu:coke_oven.scene", "gtceu:coke_oven", Source.HAND),
                scene("gtceu:steam_grinder.scene", "gtceu:steam_grinder", Source.AUTO),
                scene("gtceu:large_gas_turbine.scene", "gtceu:large_gas_turbine", Source.AUTO),
                SceneData.builder().formatVersion(1).id("gtsnponder:concept").source(Source.HAND).build());
        return SceneCatalog.of(scenes, progress);
    }

    @Test
    void entriesRequireATargetAndDropScenesWithoutOne() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        assertEquals(3, catalog.total(), "scenes without a target cannot be opened and are excluded");
        assertTrue(catalog.entries().stream().allMatch(entry -> entry.target() != null));
    }

    @Test
    void entriesAreSortedByCategoryThenTarget() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        List<String> categories = catalog.entries().stream().map(CatalogEntry::category).toList();
        List<String> targets = catalog.entries().stream().map(CatalogEntry::target).toList();

        // 类别顺序：steam(self) 先于 power，最后是 machines。
        assertEquals(List.of(SceneCategories.STEAM, SceneCategories.POWER, SceneCategories.MACHINES), categories);
        assertEquals("gtceu:steam_grinder", targets.get(0));
        assertEquals("gtceu:large_gas_turbine", targets.get(1));
    }

    @Test
    void categoriesReflectOnlyPresentBucketsInDisplayOrder() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        assertEquals(List.of(SceneCategories.STEAM, SceneCategories.POWER, SceneCategories.MACHINES),
                catalog.categories());
        assertEquals(List.of("gtceu:steam_grinder"), catalog.inCategory(SceneCategories.STEAM).stream()
                .map(CatalogEntry::target).toList());
        assertTrue(catalog.inCategory(SceneCategories.MODULE).isEmpty());
    }

    @Test
    void searchMatchesTargetCaseInsensitively() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        List<CatalogEntry> hits = catalog.search("STEAM");
        assertEquals(1, hits.size());
        assertEquals("gtceu:steam_grinder", hits.get(0).target());
    }

    @Test
    void searchMatchesCategoryAndTitleAndId() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        assertEquals(1, catalog.search("turbine").size());
        assertEquals(1, catalog.search("coke_oven.scene").size(), "id is searchable");
        assertEquals(3, catalog.search("gtceu").size(), "namespace matches every entry");
    }

    @Test
    void blankSearchReturnsEverythingAndNoMatchReturnsNothing() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        assertEquals(3, catalog.search("").size());
        assertEquals(3, catalog.search("   ").size());
        assertTrue(catalog.search("definitely-absent").isEmpty());
    }

    @Test
    void progressCountsAndMarksFollowWatchedKeys() {
        WatchedProgress progress = WatchedProgress.empty()
                .with(WatchedProgress.keyOf(scene("gtceu:coke_oven.scene", "gtceu:coke_oven", Source.HAND)))
                .with(WatchedProgress.keyOf(scene("gtceu:steam_grinder.scene", "gtceu:steam_grinder", Source.AUTO)));

        SceneCatalog catalog = fixture(progress);

        assertEquals(2, catalog.watchedCount());
        assertEquals(2.0 / 3.0, catalog.progressFraction(), 1.0e-9);
        assertTrue(catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steam_grinder"))
                .allMatch(CatalogEntry::watched));
        assertFalse(catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:large_gas_turbine"))
                .findFirst().orElseThrow().watched());
    }

    @Test
    void emptyCatalogHasZeroProgressAndNoCategories() {
        SceneCatalog catalog = SceneCatalog.of(List.of(), WatchedProgress.empty());
        assertEquals(0, catalog.total());
        assertEquals(0, catalog.watchedCount());
        assertEquals(0.0d, catalog.progressFraction());
        assertTrue(catalog.categories().isEmpty());
    }
}
