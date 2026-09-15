package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
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

    // --- 全量覆盖（工单 #13）：为无场景的注册多方块合成按需生成条目 ------------------

    @Test
    void coverageOverloadSynthesizesAutoEntriesForRegisteredTargetsWithoutAScene() {
        List<SceneData> scenes = List.of(scene("gtceu:coke_oven.scene", "gtceu:coke_oven", Source.HAND));

        SceneCatalog catalog = SceneCatalog.of(scenes,
                List.of("gtceu:coke_oven", "gtceu:steam_grinder"), WatchedProgress.empty());

        assertEquals(2, catalog.total(), "the covered target keeps its scene; the uncovered one is synthesized");
        CatalogEntry synthesized = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steam_grinder"))
                .findFirst().orElseThrow();
        assertEquals(Source.AUTO, synthesized.source());
        assertEquals(SceneGenerator.sceneIdFor("gtceu:steam_grinder"), synthesized.key(),
                "the synthesized key must equal the on-demand generated scene id");
        assertEquals(GeneratedKeys.machineTitleKey("gtceu:steam_grinder"), synthesized.titleKey());
        assertEquals(SceneCategories.STEAM, synthesized.category());
    }

    @Test
    void coverageOverloadDoesNotSynthesizeWhenASceneAlreadyCoversTheTarget() {
        List<SceneData> scenes = List.of(scene("gtceu:coke_oven.scene", "gtceu:coke_oven", Source.HAND));

        SceneCatalog catalog = SceneCatalog.of(scenes, List.of("gtceu:coke_oven"), WatchedProgress.empty());

        assertEquals(1, catalog.total(), "an already-covered registered target must not be duplicated");
        assertEquals(Source.HAND, catalog.entries().get(0).source());
        assertEquals("gtceu:coke_oven.scene", catalog.entries().get(0).key());
    }

    @Test
    void coverageOverloadDeduplicatesAndIgnoresBlankRegisteredTargets() {
        SceneCatalog catalog = SceneCatalog.of(List.of(),
                List.of("gtceu:a", "gtceu:a", "   ", "gtceu:b"), WatchedProgress.empty());

        assertEquals(2, catalog.total());
        assertEquals(List.of("gtceu:a", "gtceu:b"),
                catalog.entries().stream().map(CatalogEntry::target).toList());
    }

    @Test
    void synthesizedEntryWatchedKeyMatchesTheGeneratedSceneId() {
        // 播放按需生成的场景会把 WatchedProgress.keyOf(scene)（= SceneGenerator.sceneIdFor(target)）
        // 标记为已看；目录用同一键，故已看标记能点亮。
        WatchedProgress progress = WatchedProgress.empty().with(SceneGenerator.sceneIdFor("gtceu:steam_grinder"));

        SceneCatalog catalog = SceneCatalog.of(List.of(), List.of("gtceu:steam_grinder"), progress);

        assertEquals(1, catalog.total());
        assertTrue(catalog.entries().get(0).watched());
        assertEquals(1, catalog.watchedCount());
    }

    @Test
    void synthesizedEntriesAreClassifiedByTargetKeywords() {
        SceneCatalog catalog = SceneCatalog.of(List.of(),
                List.of("gtceu:steam_grinder", "gtceu:item_pipe", "gtceu:large_combustion_engine"),
                WatchedProgress.empty());

        assertEquals(SceneCategories.STEAM, catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steam_grinder")).findFirst().orElseThrow().category());
        assertEquals(SceneCategories.LOGISTICS, catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:item_pipe")).findFirst().orElseThrow().category());
        assertEquals(SceneCategories.POWER, catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:large_combustion_engine"))
                .findFirst().orElseThrow().category());
    }

    @Test
    void legacyOverloadIgnoresRegisteredTargets() {
        SceneCatalog catalog = SceneCatalog.of(
                List.of(scene("gtceu:coke_oven.scene", "gtceu:coke_oven", Source.HAND)), WatchedProgress.empty());
        assertEquals(1, catalog.total());
    }

    // --- 单方块机器使用场景（工单 #15）：合成使用场景条目 -----------------------------

    @Test
    void usageOverloadSynthesizesUsageEntriesForSingleBlockTargets() {
        SceneCatalog catalog = SceneCatalog.of(List.of(), List.of("gtceu:coke_oven"),
                List.of("gtceu:lp_steam_furnace"), WatchedProgress.empty());

        assertEquals(2, catalog.total());
        CatalogEntry usage = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:lp_steam_furnace"))
                .findFirst().orElseThrow();
        assertEquals(Source.AUTO, usage.source());
        assertEquals(SingleBlockUsageGenerator.sceneIdFor("gtceu:lp_steam_furnace"), usage.key(),
                "the usage entry key must equal the on-demand usage scene id");
        assertEquals(SingleBlockUsageGenerator.titleKeyFor("gtceu:lp_steam_furnace"), usage.titleKey(),
                "the usage entry title must reuse GT's own block name key");
        assertEquals(SceneCategories.STEAM, usage.category());
    }

    @Test
    void usageOverloadDeduplicatesAgainstLoadedScenesAndMultiblockTargets() {
        List<SceneData> scenes = List.of(
                scene(SingleBlockUsageGenerator.sceneIdFor("gtceu:lv_macerator"), "gtceu:lv_macerator", Source.HAND));

        SceneCatalog catalog = SceneCatalog.of(scenes, List.of("gtceu:lv_macerator"),
                List.of("gtceu:lv_macerator"), WatchedProgress.empty());

        assertEquals(1, catalog.total(), "an already-covered target must not be synthesized twice");
        assertEquals(Source.HAND, catalog.entries().get(0).source());
    }

    @Test
    void usageSynthesizedEntryWatchedKeyMatchesTheUsageSceneId() {
        WatchedProgress progress = WatchedProgress.empty()
                .with(SingleBlockUsageGenerator.sceneIdFor("gtceu:lv_centrifuge"));

        SceneCatalog catalog = SceneCatalog.of(List.of(), List.of(),
                List.of("gtceu:lv_centrifuge"), progress);

        assertEquals(1, catalog.total());
        assertTrue(catalog.entries().get(0).watched());
    }

    @Test
    void usageOverloadKeepsMultiblockSynthesisOnTheAutoKey() {
        SceneCatalog catalog = SceneCatalog.of(List.of(), List.of("gtceu:steam_grinder"),
                List.of("gtceu:lv_centrifuge"), WatchedProgress.empty());

        CatalogEntry multiblock = catalog.entries().stream()
                .filter(entry -> entry.target().equals("gtceu:steam_grinder")).findFirst().orElseThrow();
        assertEquals(SceneGenerator.sceneIdFor("gtceu:steam_grinder"), multiblock.key());
        assertEquals(GeneratedKeys.machineTitleKey("gtceu:steam_grinder"), multiblock.titleKey());
    }
}
