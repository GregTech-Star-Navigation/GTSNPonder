package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目录分类器的行为测试（外部可观察：给定目标文本 / 场景 → 稳定的类别键）。
 *
 * <p>场景格式（v1 已冻结）没有类别字段，故类别由目标的稳定 id 关键词派生；本测试锁定规则优先级
 * 与退化行为，防止分类静默漂移。</p>
 */
class SceneCategoriesTest {

    @Test
    void moduleKeywordWinsOverOtherWords() {
        assertEquals(SceneCategories.MODULE, SceneCategories.classify("gtceu:steam_module_hub"));
    }

    @Test
    void steamMachinesClassifyAsSteam() {
        assertEquals(SceneCategories.STEAM, SceneCategories.classify("gtceu:steam_grinder"));
    }

    @Test
    void powerMachinesClassifyAsPower() {
        assertEquals(SceneCategories.POWER, SceneCategories.classify("gtceu:large_gas_turbine"));
        assertEquals(SceneCategories.POWER, SceneCategories.classify("gtceu:large_combustion_engine"));
    }

    @Test
    void logisticsPartsClassifyAsLogistics() {
        assertEquals(SceneCategories.LOGISTICS, SceneCategories.classify("gtceu:item_pipe"));
        assertEquals(SceneCategories.LOGISTICS, SceneCategories.classify("gtceu:fluid_cable"));
    }

    @Test
    void plainMachinesFallBackToMachines() {
        assertEquals(SceneCategories.MACHINES, SceneCategories.classify("gtceu:coke_oven"));
        assertEquals(SceneCategories.MACHINES, SceneCategories.classify("gtceu:primitive_blast_furnace"));
    }

    @Test
    void nullOrBlankTextFallsBackToMachines() {
        assertEquals(SceneCategories.MACHINES, SceneCategories.classify(null));
        assertEquals(SceneCategories.MACHINES, SceneCategories.classify("   "));
    }

    @Test
    void sceneWithoutTargetIsAConcept() {
        SceneData concept = SceneData.builder().formatVersion(1).id("gtsnponder:energy_primer").build();
        assertEquals(SceneCategories.CONCEPT, SceneCategories.categoryOf(concept));
    }

    @Test
    void sceneWithTargetClassifiesByTarget() {
        SceneData scene = SceneData.builder().formatVersion(1)
                .id("gtceu:steam_grinder.scene").target("gtceu:steam_grinder").build();
        assertEquals(SceneCategories.STEAM, SceneCategories.categoryOf(scene));
    }

    @Test
    void categoryOrderIsStableAndCoversEveryKey() {
        assertEquals(SceneCategories.ORDER.size(), SceneCategories.ORDER.stream().distinct().count());
        for (String category : SceneCategories.ORDER) {
            assertTrue(SceneCategories.langKey(category).startsWith(CatalogKeys.CATEGORY_PREFIX),
                    "every category must map onto a localization key");
        }
    }
}
