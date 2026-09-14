package com.gtsn.ponder.content;

import java.util.List;

/**
 * 内容工单 #10（发电·能量网 / 物流管网）手作 / 混合场景所用的<b>本地化键</b>（单一事实源）。
 *
 * <p>随包场景的 {@code title} / {@code narration} 直接引用这些字面量键；datagen
 * {@code GtsnPonderLanguageProvider} 与 {@code GeneratedLangKeysTest} 共享本常量集，保证
 * 「随包 JSON 引用的键 → datagen 产出中英文案」不漂移。概念键（电压 / 超压 / 熔断、物品 / 流体
 * 管道 / 线缆 / 覆盖板）与机器特定键（每台真实 GT 多方块的 title / intro）分开命名。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（可 headless 单测）。</p>
 */
public final class SystemSceneKeys {

    private SystemSceneKeys() {
    }

    /** 发电：燃烧发电机场景标题。 */
    public static final String POWER_COMBUSTION_TITLE = "ponder.gtsnponder.power.combustion.title";
    /** 发电：有源变压器场景标题。 */
    public static final String POWER_TRANSFORMER_TITLE = "ponder.gtsnponder.power.transformer.title";
    /** 发电：燃烧发电机机器特定开场旁白（参数：机器 id）。 */
    public static final String POWER_COMBUSTION_INTRO = "ponder.gtsnponder.power.combustion.narration.intro";
    /** 发电：有源变压器机器特定开场旁白（参数：机器 id）。 */
    public static final String POWER_TRANSFORMER_INTRO = "ponder.gtsnponder.power.transformer.narration.intro";
    /** 发电：控制器概念旁白。 */
    public static final String POWER_CONTROLLER = "ponder.gtsnponder.power.narration.controller";
    /** 发电：电压等级概念旁白。 */
    public static final String POWER_VOLTAGE = "ponder.gtsnponder.power.narration.voltage";
    /** 发电：超压概念旁白。 */
    public static final String POWER_OVERVOLTAGE = "ponder.gtsnponder.power.narration.overvoltage";
    /** 发电：线缆熔断概念旁白。 */
    public static final String POWER_BURNING = "ponder.gtsnponder.power.narration.burning";

    /** 物流：钢制多方块储罐场景标题。 */
    public static final String LOGISTICS_TANK_TITLE = "ponder.gtsnponder.logistics.tank.title";
    /** 物流：原始水泵场景标题。 */
    public static final String LOGISTICS_PUMP_TITLE = "ponder.gtsnponder.logistics.pump.title";
    /** 物流：钢制多方块储罐机器特定开场旁白（参数：机器 id）。 */
    public static final String LOGISTICS_TANK_INTRO = "ponder.gtsnponder.logistics.tank.narration.intro";
    /** 物流：原始水泵机器特定开场旁白（参数：机器 id）。 */
    public static final String LOGISTICS_PUMP_INTRO = "ponder.gtsnponder.logistics.pump.narration.intro";
    /** 物流：控制器概念旁白。 */
    public static final String LOGISTICS_CONTROLLER = "ponder.gtsnponder.logistics.narration.controller";
    /** 物流：物品管道概念旁白。 */
    public static final String LOGISTICS_ITEM_PIPES = "ponder.gtsnponder.logistics.narration.item_pipes";
    /** 物流：流体管道概念旁白。 */
    public static final String LOGISTICS_FLUID_PIPES = "ponder.gtsnponder.logistics.narration.fluid_pipes";
    /** 物流：线缆概念旁白。 */
    public static final String LOGISTICS_CABLES = "ponder.gtsnponder.logistics.narration.cables";
    /** 物流：覆盖板概念旁白。 */
    public static final String LOGISTICS_COVERS = "ponder.gtsnponder.logistics.narration.covers";

    /** 全部随包系统场景键（供 datagen 与产物守卫遍历）。 */
    public static final List<String> ALL = List.of(
            POWER_COMBUSTION_TITLE,
            POWER_TRANSFORMER_TITLE,
            POWER_COMBUSTION_INTRO,
            POWER_TRANSFORMER_INTRO,
            POWER_CONTROLLER,
            POWER_VOLTAGE,
            POWER_OVERVOLTAGE,
            POWER_BURNING,
            LOGISTICS_TANK_TITLE,
            LOGISTICS_PUMP_TITLE,
            LOGISTICS_TANK_INTRO,
            LOGISTICS_PUMP_INTRO,
            LOGISTICS_CONTROLLER,
            LOGISTICS_ITEM_PIPES,
            LOGISTICS_FLUID_PIPES,
            LOGISTICS_CABLES,
            LOGISTICS_COVERS);
}
