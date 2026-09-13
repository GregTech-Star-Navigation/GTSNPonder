package com.gtsn.ponder.gt;

import com.gtsn.ponder.structure.StructureSource;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GT 结构适配器：把格雷科技的多方块结构数据翻译成 GTSNPonder 自有的中立
 * {@link StructureSource} DTO。本类是 {@code com.gtsn.ponder.gt} 适配包的一员——是唯一
 * 允许引用 {@code com.gregtechceu} 的地方（ADR-0005）；其余包只与 {@link StructureSource}
 * 打交道，因此 GT 上游升级的影响收敛于此。
 *
 * <p><b>数据来源</b>：{@link GTRegistries#MACHINES} 枚举所有机器定义，筛出
 * {@link MultiblockMachineDefinition}；{@link MultiblockMachineDefinition#getMatchingShapes()}
 * 给出其结构页（每个结构页是一个 {@link BlockInfo}{@code [][][]}，由
 * {@code BlockPattern#getPreview} 或作者显式 {@code shapeInfos(...)} 产出）。本适配器取第一页，
 * 逐格转换成方块注册名。</p>
 *
 * <h2>索引约定（重要陷阱）</h2>
 * <p>{@link MultiblockShapeInfo} 源码把字段注释写成 {@code BlockInfo[][][] blocks; // [z][y][x]}，
 * 但该注释是<b>错的</b>：产出侧（{@code BlockPattern#getPreview}）按
 * {@code result[pos.x-minX][pos.y-minY][pos.z-minZ]} 填充，消费侧（GT 自己的
 * {@code PatternPreviewWidget.initializePattern}）也按
 * {@code for (x = 0..blocks.length) for (y = 0..blocks[x].length) for (z = 0..blocks[x][y].length)}
 * 并以 {@code (x, y, z)} 偏移。因此实际约定是 <b>index0 = X、index1 = Y、index2 = Z</b>。
 * 本适配器严格按此约定遍历；若整体转置会静默产出错误结构，故由 GameTest
 * {@code GtStructureAdapterGameTests} 用非对称尺寸夹具锁定。</p>
 *
 * <p><b>线程 / 端</b>：只读 {@code GTRegistries} 与结构定义，均为公共（非客户端）类型，
 * 专职服务端（GameTest）可安全调用。</p>
 */
public final class GtStructureAdapter {

    /**
     * 优先候选：已知的小型多方块（按结构体积从小到大、且为完整三维结构）。
     * 先按此列表尝试，避免在客户端首次打开视口时枚举并展开全部（含 15×3×15 的聚变堆）
     * 结构页；任一命中即用。都不在时回退为「扫描全部取最小」。
     */
    private static final List<String> PREFERRED_SMALL_MULTIBLOCKS = List.of(
            "gtceu:coke_oven",
            "gtceu:steam_grinder",
            "gtceu:steam_oven",
            "gtceu:large_chemical_reactor",
            "gtceu:multi_smelter",
            "gtceu:implosion_compressor",
            "gtceu:vacuum_freezer",
            "gtceu:electric_blast_furnace");

    private GtStructureAdapter() {
    }

    /**
     * 选取一个适合预览的小型多方块结构：先试 {@link #PREFERRED_SMALL_MULTIBLOCKS}，
     * 都不可用时扫描 {@link GTRegistries#MACHINES} 取结构包围盒体积最小者。
     *
     * @return 结构源；GT 缺席 / 无任何可用多方块时为空
     */
    public static Optional<StructureSource> smallestMultiblock() {
        for (String id : PREFERRED_SMALL_MULTIBLOCKS) {
            Optional<StructureSource> source = byId(id);
            if (source.isPresent()) {
                return source;
            }
        }
        return scanForSmallest();
    }

    /** 按机器 id 解析结构源；不是多方块 / 不存在 / 无结构页时为空。 */
    public static Optional<StructureSource> byId(String machineId) {
        ResourceLocation location = ResourceLocation.tryParse(machineId);
        if (location == null) {
            return Optional.empty();
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(location);
        if (!(definition instanceof MultiblockMachineDefinition multiblock)) {
            return Optional.empty();
        }
        return toSource(multiblock);
    }

    /** 把某个多方块定义翻译为结构源（其第一个结构页）。 */
    public static Optional<StructureSource> toSource(MultiblockMachineDefinition definition) {
        List<MultiblockShapeInfo> shapes;
        try {
            shapes = definition.getMatchingShapes();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        if (shapes == null || shapes.isEmpty()) {
            return Optional.empty();
        }
        BlockInfo[][][] grid = shapes.get(0).getBlocks();
        if (grid == null || grid.length == 0) {
            return Optional.empty();
        }
        return Optional.of(convert(definition, grid));
    }

    /**
     * 扫描全部机器定义，取结构包围盒体积最小的多方块。体积用
     * {@code getPatternDimensions()}（{@code [z, y, x]}，乘积即体积）廉价估算，再对最小者
     * 构建结构页，避免为每台机器展开完整预览。
     */
    private static Optional<StructureSource> scanForSmallest() {
        MultiblockMachineDefinition smallest = null;
        long smallestVolume = Long.MAX_VALUE;
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            if (!(definition instanceof MultiblockMachineDefinition multiblock)) {
                continue;
            }
            long volume = patternVolume(multiblock);
            if (volume <= 0L || volume >= smallestVolume) {
                continue;
            }
            if (toSource(multiblock).isEmpty()) {
                continue;
            }
            smallest = multiblock;
            smallestVolume = volume;
        }
        return smallest == null ? Optional.empty() : toSource(smallest);
    }

    /** 结构体积估算；结构页不可展开（异常 / 无 pattern）时返回 {@code -1}。 */
    private static long patternVolume(MultiblockMachineDefinition definition) {
        try {
            BlockPattern pattern = definition.getPatternFactory().get();
            if (pattern == null) {
                return -1L;
            }
            int[] dimensions = pattern.getPatternDimensions();
            if (dimensions.length < 3) {
                return -1L;
            }
            return (long) dimensions[0] * dimensions[1] * dimensions[2];
        } catch (RuntimeException failure) {
            return -1L;
        }
    }

    /**
     * 逐格转换结构页。索引约定：index0 = X、index1 = Y、index2 = Z（见类 javadoc 的陷阱说明）。
     * 空气 / 空单元跳过；控制器单元（{@link MetaMachineBlock} 且其定义是多方块）记录其局部坐标。
     */
    private static StructureSource convert(MultiblockMachineDefinition definition, BlockInfo[][][] grid) {
        int sizeX = grid.length;
        int sizeY = 0;
        int sizeZ = 0;
        for (BlockInfo[][] aisle : grid) {
            if (aisle == null) {
                continue;
            }
            sizeY = Math.max(sizeY, aisle.length);
            for (BlockInfo[] column : aisle) {
                if (column != null) {
                    sizeZ = Math.max(sizeZ, column.length);
                }
            }
        }

        List<StructureSource.ControllerCell> controller = new ArrayList<>();
        StructureSource.Builder builder = StructureSource.builder(definition.getId().toString())
                .displayName(displayName(definition))
                .size(Math.max(sizeX, 1), Math.max(sizeY, 1), Math.max(sizeZ, 1));

        for (int x = 0; x < sizeX; x++) {
            BlockInfo[][] aisle = grid[x];
            if (aisle == null) {
                continue;
            }
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (column == null) {
                    continue;
                }
                for (int z = 0; z < column.length; z++) {
                    BlockInfo info = column[z];
                    if (info == null) {
                        continue;
                    }
                    BlockState state = info.getBlockState();
                    if (state == null || state.isAir() || state.getBlock() == Blocks.AIR) {
                        continue;
                    }
                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (blockId == null || blockId.isEmpty()) {
                        continue;
                    }
                    builder.addBlock(x, y, z, blockId);
                    if (isController(state) && controller.isEmpty()) {
                        controller.add(new StructureSource.ControllerCell(x, y, z));
                    }
                }
            }
        }

        if (!controller.isEmpty()) {
            StructureSource.ControllerCell cell = controller.get(0);
            builder.controller(cell.x(), cell.y(), cell.z());
        }
        return builder.build();
    }

    /** 方块是否为多方块控制器（{@link MetaMachineBlock} 且定义是 {@link MultiblockMachineDefinition}）。 */
    private static boolean isController(BlockState state) {
        return state.getBlock() instanceof MetaMachineBlock machineBlock
                && machineBlock.definition instanceof MultiblockMachineDefinition;
    }

    private static String displayName(MultiblockMachineDefinition definition) {
        String langValue = definition.getLangValue();
        return langValue != null && !langValue.isBlank() ? langValue : definition.getId().toString();
    }
}
