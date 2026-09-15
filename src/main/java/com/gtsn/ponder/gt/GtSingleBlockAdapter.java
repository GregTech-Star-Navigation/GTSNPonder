package com.gtsn.ponder.gt;

import com.gtsn.ponder.structure.SingleBlockMachineSource;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.TreeSet;

/**
 * GT <b>单方块机器</b>适配器（入口 #15）：把 {@link MachineDefinition} 的机器元数据翻译成本 mod 自有的
 * 中立 {@link SingleBlockMachineSource}，供「使用场景」生成器（{@code SingleBlockUsageGenerator}）消费。
 *
 * <p>本类属于唯一允许 import {@code com.gregtechceu} 的适配包 {@code com.gtsn.ponder.gt}。与
 * {@link GtStructureAdapter}（多方块结构页）不同，它<b>不需要结构页</b>：单方块机器没有形状要搭，只有
 * 机器本身的元数据。可推导的信息有：</p>
 * <ul>
 *   <li><b>身份</b>：机器定义 id、方块注册名、GT 随包名键（{@code getLangValue()}）；</li>
 *   <li><b>等级</b>：{@link MachineDefinition#getTier()} 与 {@link GTValues#VN} 等级名（如 {@code LV}）；</li>
 *   <li><b>能力</b>：机器声明的配方类型（{@link MachineDefinition#getRecipeTypes()}）的
 *       {@code maxInputs} / {@code maxOutputs} 按能力族聚合——物品输入 / 输出槽、流体输入 / 输出罐、
 *       是否接电（EU）——即「槽位 / 罐 / 电网」能力（{@link GTRecipeCapabilities}）。</li>
 * </ul>
 *
 * <p><b>失败即降级</b>：id 非法 / 定义缺失 / 是多方块 / 无方块 / 定义内部抛错一律返回空 {@link Optional}，
 * 能力读取异常退化为 0，绝不抛出（与 {@link GtStructureAdapter} 相同的适配层纪律）。</p>
 *
 * <p><b>线程 / 端</b>：只读 {@code GTRegistries} 与机器定义，均为公共（非客户端）类型，专职服务端
 * （GameTest / datagen）可安全调用。</p>
 */
public final class GtSingleBlockAdapter {

    private GtSingleBlockAdapter() {
    }

    /**
     * 按机器 id 解析单方块机器源；不是单方块（多方块 / 未知 id / 无方块）时为空。
     */
    public static Optional<SingleBlockMachineSource> byId(String machineId) {
        ResourceLocation location = ResourceLocation.tryParse(machineId);
        if (location == null) {
            return Optional.empty();
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(location);
        if (definition == null || definition instanceof MultiblockMachineDefinition
                || definition.getId() == null) {
            return Optional.empty();
        }
        Block block;
        try {
            block = definition.getBlock();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        if (block == null) {
            return Optional.empty();
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (blockId == null || blockId.isBlank()) {
            return Optional.empty();
        }

        int tier = safeTier(definition);
        Abilities abilities = abilities(definition);
        return Optional.of(SingleBlockMachineSource.builder(definition.getId().toString())
                .displayName(definition.getLangValue())
                .blockId(blockId)
                .tier(tier, tierName(tier))
                .itemInputs(abilities.itemInputs())
                .itemOutputs(abilities.itemOutputs())
                .fluidInputs(abilities.fluidInputs())
                .fluidOutputs(abilities.fluidOutputs())
                .energy(abilities.energy())
                .recipeTypeIds(abilities.recipeTypeIds())
                .build());
    }

    /** 该 id 是否为可解析的 GT 单方块机器（多方块 / 未知 / 无方块 → {@code false}）。 */
    public static boolean isSingleBlockMachine(String machineId) {
        return byId(machineId).isPresent();
    }

    private static int safeTier(MachineDefinition definition) {
        try {
            return Math.max(0, definition.getTier());
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    /** 等级名（{@link GTValues#VN}）；越界 / 未知时退化为 {@code T<tier>}。 */
    private static String tierName(int tier) {
        if (tier >= 0 && tier < GTValues.VN.length && GTValues.VN[tier] != null) {
            return GTValues.VN[tier];
        }
        return "T" + tier;
    }

    /**
     * 聚合机器声明的全部配方类型的能力上限：物品 / 流体输入输出槽罐数、是否接电（EU），以及配方类型
     * id 列表（去重、升序）。任何读取异常退化为 0 / 忽略该类型（失败即降级）。
     */
    private static Abilities abilities(MachineDefinition definition) {
        GTRecipeType[] types;
        try {
            types = definition.getRecipeTypes();
        } catch (RuntimeException failure) {
            return Abilities.EMPTY;
        }
        if (types == null || types.length == 0) {
            return Abilities.EMPTY;
        }
        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        boolean energy = false;
        TreeSet<String> recipeTypeIds = new TreeSet<>();
        for (GTRecipeType type : types) {
            if (type == null) {
                continue;
            }
            if (type.registryName != null) {
                recipeTypeIds.add(type.registryName.toString());
            }
            itemInputs = Math.max(itemInputs, max(type, true, GTRecipeCapabilities.ITEM));
            itemOutputs = Math.max(itemOutputs, max(type, false, GTRecipeCapabilities.ITEM));
            fluidInputs = Math.max(fluidInputs, max(type, true, GTRecipeCapabilities.FLUID));
            fluidOutputs = Math.max(fluidOutputs, max(type, false, GTRecipeCapabilities.FLUID));
            energy = energy || max(type, true, GTRecipeCapabilities.EU) > 0
                    || max(type, false, GTRecipeCapabilities.EU) > 0;
        }
        return new Abilities(itemInputs, itemOutputs, fluidInputs, fluidOutputs, energy,
                recipeTypeIds.stream().toList());
    }

    private static int max(GTRecipeType type, boolean input,
            com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?> capability) {
        try {
            int value = input ? type.getMaxInputs(capability) : type.getMaxOutputs(capability);
            return Math.max(0, value);
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    /** 机器能力聚合（中性计数，绝不携带 GT 类型）。 */
    private record Abilities(int itemInputs, int itemOutputs, int fluidInputs, int fluidOutputs,
            boolean energy, java.util.List<String> recipeTypeIds) {

        private static final Abilities EMPTY = new Abilities(0, 0, 0, 0, false, java.util.List.of());
    }
}
