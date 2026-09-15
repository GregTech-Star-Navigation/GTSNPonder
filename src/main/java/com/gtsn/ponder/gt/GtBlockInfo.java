package com.gtsn.ponder.gt;

import com.gtsn.ponder.generate.GeneratedKeys;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * GT 方块附加信息（工单 #21 反馈 3）：为场景中被点击的方块提供 GT 特有的可显示信息（电压 / 机器
 * 等级），供方块名覆盖层显示「方块名 + 角色 + 等级」。
 *
 * <p>属于唯一允许 import {@code com.gregtechceu} 的适配包 {@code com.gtsn.ponder.gt}；客户端只与
 * 本门面打交道，GT 类型不越界。方块名本身走通用的 {@code block.<ns>.<path>} 键（纯部分见
 * {@code com.gtsn.ponder.bridge.BlockInfoResolver}），此处只补 GT 才能知道的等级信息。</p>
 *
 * <p><b>失败即降级</b>：非 GT 方块 / 非机器方块 / 无等级 / 定义内部抛错一律返回空
 * {@link Optional}，绝不抛出（与其它适配器同一纪律）。</p>
 */
public final class GtBlockInfo {

    private GtBlockInfo() {
    }

    /**
     * 该方块（若为 GT 机器方块）的电压等级本地化键，如 {@code ponder.gtsnponder.tier.lv}；
     * 非 GT / 非机器 / 无等级时为空。
     */
    public static Optional<String> tierKeyFor(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location == null) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (!(block instanceof MetaMachineBlock machineBlock)) {
            return Optional.empty();
        }
        MachineDefinition definition = machineBlock.definition;
        if (definition == null) {
            return Optional.empty();
        }
        try {
            int tier = definition.getTier();
            if (tier >= 0 && tier < GTValues.VN.length && GTValues.VN[tier] != null) {
                return Optional.of(GeneratedKeys.tierKey(GTValues.VN[tier]));
            }
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
