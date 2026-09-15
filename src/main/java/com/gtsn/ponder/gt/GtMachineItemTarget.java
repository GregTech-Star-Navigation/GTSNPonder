package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * GT <b>机器物品 → 思索目标 id</b> 解析（工单 #17）：EMI/JEI 页面悬停 / 机器物品 tooltip 入口的
 * GT 侧耦合点，属唯一允许 import {@code com.gregtechceu} 的适配包 {@code com.gtsn.ponder.gt}。
 *
 * <p>GT 的机器方块 / 物品与机器定义共用同一个注册 id（如 {@code gtceu:lv_macerator} /
 * {@code gtceu:coke_oven}），故「物品 id 是否是一台 GT 机器」= {@code GTRegistries.MACHINES} 是否含该 id。
 * 解析出定义即返回其 id 作为思索目标（该目标总能经解析缝解析到场景：多方块有随包 / 生成场景，
 * 单方块有使用场景）。非机器物品（如 {@code minecraft:stone}、GT 材料 / 部件）返回空 → 不绘制入口。</p>
 *
 * <p><b>失败即降级</b>：id 非法 / 注册表异常一律返回空（绝不抛出）。</p>
 */
public final class GtMachineItemTarget {

    private GtMachineItemTarget() {
    }

    /** 物品注册 id → GT 机器定义 id；非机器物品 / 非法 id 为空。 */
    public static Optional<String> targetForItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return Optional.empty();
        }
        MachineDefinition definition;
        try {
            definition = GTRegistries.MACHINES.get(location);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        if (definition == null || definition.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(definition.getId().toString());
    }

    /** 该物品 id 是否为可思索的 GT 机器物品。 */
    public static boolean isMachineItem(String itemId) {
        return targetForItem(itemId).isPresent();
    }
}
