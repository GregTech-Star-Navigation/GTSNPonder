package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;

import java.util.List;
import java.util.Locale;

/**
 * 图鉴目录的类别分类器（纯 Java，零 MC）。
 *
 * <p>场景格式 v1 已冻结，没有类别字段，因此类别由目标的稳定 id（{@code gtceu:<path>}）
 * <b>关键词派生</b>：按固定优先级匹配（模块 → 蒸汽 → 发电 → 物流），无命中归入「机器」。
 * 无 {@code target} 的场景（概念课）归入「概念」。规则集中于此、由单测锁定，避免分类静默漂移。</p>
 *
 * <p><b>已知局限</b>：分类基于命名启发式，命名不规范的机器会落入「机器」桶；这是冻结格式下的
 * 确定、可测的降级，而非静默错误。</p>
 */
public final class SceneCategories {

    /** 模块系统（组织 fork 独有）。 */
    public static final String MODULE = "module";
    /** 概念课（无目标）。 */
    public static final String CONCEPT = "concept";
    /** 蒸汽时代机器。 */
    public static final String STEAM = "steam";
    /** 发电与能量网。 */
    public static final String POWER = "power";
    /** 物流与管网（物品 / 流体 / 线缆 / 部件）。 */
    public static final String LOGISTICS = "logistics";
    /** 其余多方块机器（默认桶）。 */
    public static final String MACHINES = "machines";

    /** 类别显示顺序（同时也是整理排序键）。 */
    public static final List<String> ORDER = List.of(MODULE, CONCEPT, STEAM, POWER, LOGISTICS, MACHINES);

    /** 按优先级排列的关键词表：每个条目是「类别 + 词元」。 */
    private static final List<Rule> RULES = List.of(
            new Rule(MODULE, List.of("module")),
            new Rule(STEAM, List.of("steam")),
            new Rule(POWER, List.of("generator", "turbine", "boiler", "dynamo", "energy", "battery",
                    "transformer", "reactor", "fusion", "solar", "combustion", "engine")),
            new Rule(LOGISTICS, List.of("pipe", "cable", "wire", "conveyor", "pump", "tank", "hatch",
                    "bus", "logistics", "item_", "fluid_", "ae2", "pattern", "modem", "interface")));

    private SceneCategories() {
    }

    /** 场景 → 类别键：无目标即概念，否则按目标 id 关键词分类。 */
    public static String categoryOf(SceneData scene) {
        if (scene == null || scene.target() == null || scene.target().isBlank()) {
            return CONCEPT;
        }
        return classify(scene.target());
    }

    /** 文本 → 类别键；空白 / null 归入「机器」。 */
    public static String classify(String text) {
        if (text == null || text.isBlank()) {
            return MACHINES;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String token : rule.tokens()) {
                if (haystack.contains(token)) {
                    return rule.category();
                }
            }
        }
        return MACHINES;
    }

    /** 类别的显示 / 排序下标；未知类别排在最后。 */
    public static int orderIndex(String category) {
        int index = ORDER.indexOf(category);
        return index < 0 ? ORDER.size() : index;
    }

    /** 类别键 → 本地化键。 */
    public static String langKey(String category) {
        return CatalogKeys.categoryKey(category);
    }

    private record Rule(String category, List<String> tokens) {
    }
}
