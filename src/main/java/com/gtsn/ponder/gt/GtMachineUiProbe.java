package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.api.gui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * GT 机器界面自动测试的<b>服务端探针</b>（#12）：在开发 / 自动测试里放置一台真实 GT 机器并以
 * GT 的标准路径（{@link MachineUIFactory#openUI}）打开其 GUI，使客户端得以在<b>真实 GT 机器屏</b>上
 * 验证覆盖按钮。生产路径不使用本类。
 *
 * <p>属于唯一允许 import {@code com.gregtechceu} 的适配包，故 {@code client.GtGuiAutotest} 无需触碰
 * GT 类型。永不抛出：id / 定义 / 方块缺失或系统内部异常一律返回空 / {@code false}。</p>
 */
public final class GtMachineUiProbe {

    private GtMachineUiProbe() {
    }

    /**
     * 在 {@code pos} 放置目标机器定义的默认方块（服务端线程调用）。返回放置坐标；id / 定义 / 方块
     * 不可用时为空。
     */
    public static Optional<BlockPos> placeMachine(ServerLevel level, BlockPos pos, String machineId) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(machineId);
        if (id == null) {
            return Optional.empty();
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        Block block = definition.getBlock();
        if (block == null) {
            return Optional.empty();
        }
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        return Optional.of(pos);
    }

    /**
     * {@code pos} 处是否已有可用的 GT 机器（方块实体已创建）。接受任意 {@link Level}，故客户端
     * （{@code ClientLevel}）与服务端（{@code ServerLevel}）皆可探测——自动测试用它确认「打开 GUI 前
     * 客户端已持有机器方块实体」，因为 GT 的 {@code MachineUIFactory.readHolderFromSyncData} 在客户端
     * 正是从<b>客户端</b>世界的方块实体解析 UI holder。
     */
    public static boolean machinePresent(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof IMachineBlockEntity blockEntity
                && blockEntity.getMetaMachine() != null;
    }

    /**
     * 以 GT 标准路径打开 {@code pos} 处机器的 GUI（{@link MachineUIFactory#openUI}，会向客户端发包）。
     * 该机器不支持 UI（非 {@link IUIMachine}）时返回 {@code false}。
     */
    public static boolean openMachineUi(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof IMachineBlockEntity blockEntity)) {
            return false;
        }
        MetaMachine machine = blockEntity.getMetaMachine();
        if (!(machine instanceof IUIMachine)) {
            return false;
        }
        return MachineUIFactory.INSTANCE.openUI(machine, player);
    }
}
