package com.gtsn.ponder.gt;

import com.gtsn.ponder.structure.HatchClassifier;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.module.ModuleDefinition;
import com.gregtechceu.gtceu.api.machine.module.ModuleRegion;
import com.gregtechceu.gtceu.api.machine.module.ModuleSlotInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 逐格转换成方块注册名，并附上角色与模块位。</p>
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
 * <h2>仓口 / 总线分类（含启发式与边界）</h2>
 * <p>角色判定分两级，且只在方块确为 GT 机器方块（{@link MetaMachineBlock}）时进行：</p>
 * <ol>
 *   <li><b>精确（首选）</b>：用 GT 的 {@link PartAbility} 特征判定——若方块属于某一能力
 *       （物品 / 流体 / 能量进出、消声、维护、直通等），则映射为对应的
 *       {@link StructureRole}；属于其余能力（转子、并行、激光、数据、光学、HPCA、对象架等）
 *       则归为 {@link StructureRole#OTHER_HATCH}。这是 GT 自己的「部件能力」真相，故为精确判定。</li>
 *   <li><b>保守兜底</b>：未命中任何能力时，用 {@link HatchClassifier#classifyByName(String)}
 *       按方块注册名里的 {@code hatch}/{@code bus} 词元判定为 {@link StructureRole#OTHER_HATCH}。
 *       需要它是因为 GT 并非所有部件都注册能力——例如 {@code gtceu:coke_oven_hatch} 未声明任何
 *       {@code PartAbility}。</li>
 * </ol>
 * <p><b>局限（已知且接受）</b>：① 兜底依赖命名规范，命名不规范的部件会被漏判为
 * {@link StructureRole#PLAIN}，反之名含 {@code hatch}/{@code bus} 词元的非部件机器方块会被误判为仓口
 * （概率极低，且仅在方块是 GT 机器方块时才可能）；② 本适配器只看
 * {@link MultiblockMachineDefinition#getMatchingShapes()} 产出的结构页，而 GT 的
 * {@code BlockPattern#getPreview} 对「机壳 <i>或</i> 仓口」这类或谓词会**坍缩为第一个候选**
 * （通常为机壳），因此按图案自动生成的结构页可能显示不出真实仓口位置——这类机器需作者显式
 * {@code shapeInfos(...)}（如放置真实仓口方块）才能被识别。识别到的仓口角色在方块级如实表达。</p>
 *
 * <h2>模块位（组织 fork 独有）</h2>
 * <p>{@link MultiblockMachineDefinition#getModuleSlotInfos()} 给出模块位区域与其可接受模块。
 * 区域坐标相对**控制器**；结构页坐标相对包围盒原点。本适配器按 GT 自己预览用的映射
 * （{@code ModulePreviewPlacement.gridOffset = (-x, +y, -z)}，即
 * {@code grid = controller + (-offsetX, +offsetY, -offsetZ)}）把区域换算到结构页坐标系，
 * 与方块同系。守卫：结构页无控制器时无法锚定模块区域，直接略过（不产出模块位）；区域退化
 * （尺寸非正）或换算后越出结构包围盒的模块位同样略过。异常（无结构页、结构页为空、定义内部
 * 抛错）一律返回空 {@link Optional} 或不含该信息，绝不抛出。</p>
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

    /** 能力 → 精确角色的分组；顺序即优先级（同一方块命中多组时取先命中者）。 */
    private static final List<AbilityGroup> ABILITY_GROUPS = List.of(
            new AbilityGroup(StructureRole.ITEM_INPUT,
                    PartAbility.IMPORT_ITEMS, PartAbility.STEAM_IMPORT_ITEMS),
            new AbilityGroup(StructureRole.ITEM_OUTPUT,
                    PartAbility.EXPORT_ITEMS, PartAbility.STEAM_EXPORT_ITEMS),
            new AbilityGroup(StructureRole.FLUID_INPUT,
                    PartAbility.IMPORT_FLUIDS, PartAbility.IMPORT_FLUIDS_1X, PartAbility.IMPORT_FLUIDS_4X,
                    PartAbility.IMPORT_FLUIDS_9X, PartAbility.PUMP_FLUID_HATCH, PartAbility.STEAM),
            new AbilityGroup(StructureRole.FLUID_OUTPUT,
                    PartAbility.EXPORT_FLUIDS, PartAbility.EXPORT_FLUIDS_1X, PartAbility.EXPORT_FLUIDS_4X,
                    PartAbility.EXPORT_FLUIDS_9X, PartAbility.TANK_VALVE),
            new AbilityGroup(StructureRole.ENERGY_INPUT,
                    PartAbility.INPUT_ENERGY, PartAbility.SUBSTATION_INPUT_ENERGY),
            new AbilityGroup(StructureRole.ENERGY_OUTPUT,
                    PartAbility.OUTPUT_ENERGY, PartAbility.SUBSTATION_OUTPUT_ENERGY),
            new AbilityGroup(StructureRole.MUFFLER, PartAbility.MUFFLER),
            new AbilityGroup(StructureRole.MAINTENANCE, PartAbility.MAINTENANCE),
            new AbilityGroup(StructureRole.PASSTHROUGH, PartAbility.PASSTHROUGH_HATCH));

    /** 其余「是仓口 / 总线但具体角色未细分」的能力。 */
    private static final List<PartAbility> OTHER_HATCH_ABILITIES = List.of(
            PartAbility.ROTOR_HOLDER,
            PartAbility.PARALLEL_HATCH,
            PartAbility.INPUT_LASER,
            PartAbility.OUTPUT_LASER,
            PartAbility.COMPUTATION_DATA_RECEPTION,
            PartAbility.COMPUTATION_DATA_TRANSMISSION,
            PartAbility.OPTICAL_DATA_RECEPTION,
            PartAbility.OPTICAL_DATA_TRANSMISSION,
            PartAbility.DATA_ACCESS,
            PartAbility.HPCA_COMPONENT,
            PartAbility.OBJECT_HOLDER);

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
     * 空气 / 空单元跳过；控制器单元记录其局部坐标并打上 {@link StructureRole#CONTROLLER}；
     * 其余机器方块按 {@link #classifyBlock(Block)} 分类。最后在控制器存在时追加模块位。
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
        int boundedX = Math.max(sizeX, 1);
        int boundedY = Math.max(sizeY, 1);
        int boundedZ = Math.max(sizeZ, 1);

        StructureSource.Builder builder = StructureSource.builder(definition.getId().toString())
                .displayName(displayName(definition))
                .size(boundedX, boundedY, boundedZ);

        Map<Block, StructureRole> roleCache = new HashMap<>();
        StructureSource.ControllerCell controllerCell = null;

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
                    Block block = state.getBlock();
                    String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                    if (blockId == null || blockId.isEmpty()) {
                        continue;
                    }
                    StructureRole role = roleCache.computeIfAbsent(block, GtStructureAdapter::classifyBlock);
                    builder.addBlock(x, y, z, blockId, role);
                    if (role == StructureRole.CONTROLLER && controllerCell == null) {
                        controllerCell = new StructureSource.ControllerCell(x, y, z);
                    }
                }
            }
        }

        if (controllerCell != null) {
            builder.controller(controllerCell.x(), controllerCell.y(), controllerCell.z());
        }
        addModuleSlots(definition, builder, controllerCell, boundedX, boundedY, boundedZ);
        return builder.build();
    }

    /**
     * 判定一个 GT 机器方块的仓口 / 总线角色（见类 javadoc 的两级分类）。
     * 非 GT 机器方块一律 {@link StructureRole#PLAIN}。
     */
    private static StructureRole classifyBlock(Block block) {
        if (!(block instanceof MetaMachineBlock machineBlock)) {
            return StructureRole.PLAIN;
        }
        if (machineBlock.definition instanceof MultiblockMachineDefinition) {
            return StructureRole.CONTROLLER;
        }
        for (AbilityGroup group : ABILITY_GROUPS) {
            for (PartAbility ability : group.abilities()) {
                if (ability.isApplicable(block)) {
                    return group.role();
                }
            }
        }
        for (PartAbility ability : OTHER_HATCH_ABILITIES) {
            if (ability.isApplicable(block)) {
                return StructureRole.OTHER_HATCH;
            }
        }
        return HatchClassifier.classifyByName(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /**
     * 追加模块位。守卫：无控制器（无法锚定区域）直接返回；定义无模块位 / 定义内部抛错时静默跳过；
     * 区域退化（尺寸非正）或换算到结构页后越出包围盒的模块位同样跳过。坐标映射见
     * {@link #moduleGridOffset(StructureSource.ControllerCell, BlockPos)}。
     */
    private static void addModuleSlots(MultiblockMachineDefinition definition, StructureSource.Builder builder,
            StructureSource.ControllerCell controllerCell, int sizeX, int sizeY, int sizeZ) {
        if (controllerCell == null) {
            return;
        }
        List<ModuleSlotInfo> infos;
        try {
            infos = definition.getModuleSlotInfos();
        } catch (RuntimeException failure) {
            return;
        }
        if (infos == null || infos.isEmpty()) {
            return;
        }
        for (ModuleSlotInfo info : infos) {
            if (info == null || info.slot() == null) {
                continue;
            }
            ModuleRegion region = info.slot().getRegion();
            if (region == null || region.width() <= 0 || region.height() <= 0 || region.depth() <= 0) {
                continue;
            }
            BlockPos offset = region.offset();
            if (offset == null) {
                continue;
            }
            BlockPos gridOffset = moduleGridOffset(controllerCell, offset);
            int gridX = gridOffset.getX();
            int gridY = gridOffset.getY();
            int gridZ = gridOffset.getZ();
            int slotX = region.width();
            int slotY = region.height();
            int slotZ = region.depth();
            if (gridX < 0 || gridY < 0 || gridZ < 0
                    || gridX + slotX > sizeX || gridY + slotY > sizeY || gridZ + slotZ > sizeZ) {
                continue;
            }
            List<String> moduleIds = new ArrayList<>();
            if (info.acceptable() != null) {
                for (ModuleDefinition module : info.acceptable()) {
                    if (module != null && module.getId() != null) {
                        moduleIds.add(module.getId().toString());
                    }
                }
            }
            builder.addModuleSlot(new ModuleSlot(gridX, gridY, gridZ, slotX, slotY, slotZ,
                    info.slot().acceptsAnyModule(), moduleIds.stream().distinct().sorted().toList()));
        }
    }

    /**
     * 模块位区域相对控制器的偏移 → 结构页网格坐标。采用 GT 预览自身使用的映射
     * （{@code ModulePreviewPlacement.gridOffset}：X / Z 取反、Y 不变），叠加控制器单元坐标。
     */
    private static BlockPos moduleGridOffset(StructureSource.ControllerCell controllerCell, BlockPos regionOffset) {
        return new BlockPos(
                controllerCell.x() - regionOffset.getX(),
                controllerCell.y() + regionOffset.getY(),
                controllerCell.z() - regionOffset.getZ());
    }

    private static String displayName(MultiblockMachineDefinition definition) {
        String langValue = definition.getLangValue();
        return langValue != null && !langValue.isBlank() ? langValue : definition.getId().toString();
    }

    /** 一组能力映射到同一精确角色。 */
    private record AbilityGroup(StructureRole role, List<PartAbility> abilities) {
        private AbilityGroup(StructureRole role, PartAbility... abilities) {
            this(role, List.of(abilities));
        }
    }
}
