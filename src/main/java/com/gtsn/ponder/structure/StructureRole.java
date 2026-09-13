package com.gtsn.ponder.structure;

/**
 * 结构源中方块扮演的角色（{@link StructureBlock#role()}）：普通方块、控制器，或仓口 / 总线。
 *
 * <p><b>纯 Java 词汇</b>：GT 的角色特征（{@code PartAbility}、模块系统）由唯一适配包
 * {@code com.gtsn.ponder.gt} 翻译为本枚举，其余包只与本枚举打交道。</p>
 *
 * <p>具体 I/O 角色（物品 / 流体 / 能量进出的区分）来自 GT 的 {@code PartAbility} 特征；
 * 无法判定时退化为 {@link #OTHER_HATCH}（是仓口 / 总线，但具体角色未知）。</p>
 */
public enum StructureRole {

    /** 普通结构方块（机壳 / 线圈等），无特殊角色。 */
    PLAIN,

    /** 多方块控制器（主机方块）。 */
    CONTROLLER,

    /** 物品输入总线。 */
    ITEM_INPUT,

    /** 物品输出总线。 */
    ITEM_OUTPUT,

    /** 流体输入仓。 */
    FLUID_INPUT,

    /** 流体输出仓。 */
    FLUID_OUTPUT,

    /** 能量输入仓。 */
    ENERGY_INPUT,

    /** 能量输出仓。 */
    ENERGY_OUTPUT,

    /** 消声仓。 */
    MUFFLER,

    /** 维护仓。 */
    MAINTENANCE,

    /** 物品 / 流体直通仓（passthrough）。 */
    PASSTHROUGH,

    /** 已识别为仓口 / 总线，但具体 I/O 角色未知（如特殊部件 {@code coke_oven_hatch}）。 */
    OTHER_HATCH;

    /** 是否为仓口 / 总线（相对普通过方块与控制器）。 */
    public boolean isHatch() {
        return this != PLAIN && this != CONTROLLER;
    }

    /** 是否为多方块控制器。 */
    public boolean isController() {
        return this == CONTROLLER;
    }
}
