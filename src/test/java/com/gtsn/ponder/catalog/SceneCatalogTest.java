package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

    @Test
    void relatedNavigationFindsSameCategorySiblings() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        CatalogEntry turbine = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:large_gas_turbine"))
                .findFirst().orElseThrow();

        assertTrue(catalog.relatedTo(turbine).isEmpty(),
                "the sole power entry has no same-category sibling in this fixture");

        SceneData second = scene("gtceu:gas_large_turbine.scene", "gtceu:gas_large_turbine", Source.AUTO);
        SceneCatalog extended = SceneCatalog.of(
                List.of(scene("gtceu:large_gas_turbine.scene", "gtceu:large_gas_turbine", Source.AUTO), second),
                WatchedProgress.empty());
        CatalogEntry first = extended.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:large_gas_turbine"))
                .findFirst().orElseThrow();
        List<CatalogEntry> related = extended.relatedTo(first);

        assertEquals(1, related.size());
        assertEquals("gtceu:gas_large_turbine", related.get(0).target());
        assertTrue(related.stream().noneMatch(entry -> entry.key().equals(first.key())),
                "related must exclude the anchor");
    }

    @Test
    void relatedNavigationFindsSharedMachineFamilyAcrossCategories() {
        SceneCatalog catalog = SceneCatalog.of(List.of(
                scene("gtceu:steam_grinder.scene", "gtceu:steam_grinder", Source.AUTO),
                scene("gtceu:grinder_factory.scene", "gtceu:grinder_factory", Source.HAND)),
                WatchedProgress.empty());
        CatalogEntry steam = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steam_grinder"))
                .findFirst().orElseThrow();
        CatalogEntry factory = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:grinder_factory"))
                .findFirst().orElseThrow();

        assertEquals(SceneCategories.STEAM, steam.category());
        assertEquals(SceneCategories.MACHINES, factory.category());
        assertTrue(catalog.relatedTo(steam).contains(factory),
                "entries sharing the 'grinder' machine token must relate across categories");
        assertTrue(catalog.relatedTo(factory).contains(steam));
    }

    @Test
    void relatedNavigationIsOrderedAndDeterministic() {
        SceneCatalog catalog = SceneCatalog.of(List.of(
                scene("gtceu:wooden_multiblock_tank.scene", "gtceu:wooden_multiblock_tank", Source.AUTO),
                scene("gtceu:bronze_multiblock_tank.scene", "gtceu:bronze_multiblock_tank", Source.AUTO),
                scene("gtceu:steel_multiblock_tank.scene", "gtceu:steel_multiblock_tank", Source.HAND)),
                WatchedProgress.empty());
        CatalogEntry anchor = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steel_multiblock_tank"))
                .findFirst().orElseThrow();

        List<String> first = catalog.relatedTo(anchor).stream().map(CatalogEntry::target).toList();
        List<String> second = catalog.relatedTo(anchor).stream().map(CatalogEntry::target).toList();
        assertEquals(first, second, "related navigation must be deterministic");
        assertTrue(catalog.entries().indexOf(anchor) >= 0);
        assertEquals(List.of("gtceu:bronze_multiblock_tank", "gtceu:wooden_multiblock_tank"), first,
                "related entries follow the catalog's stable category/target order");
    }

    @Test
    void relatedNavigationHandlesNullAndBlankTargets() {
        SceneCatalog catalog = fixture(WatchedProgress.empty());
        assertTrue(catalog.relatedTo(null).isEmpty());
        assertEquals(SceneCategories.MACHINES, SceneCategories.classify("   "));
    }

    @Test
    void targetTokensDropNamespaceAndGenericWords() {
        assertEquals(Set.of("tank"), SceneCatalog.targetTokens("gtceu:large_multiblock_tank"));
        assertEquals(Set.of("coke", "oven"), SceneCatalog.targetTokens("gtceu:coke_oven"));
        assertTrue(SceneCatalog.targetTokens(null).isEmpty());
        assertTrue(SceneCatalog.targetTokens("gtceu:multi").isEmpty());
    }
}
