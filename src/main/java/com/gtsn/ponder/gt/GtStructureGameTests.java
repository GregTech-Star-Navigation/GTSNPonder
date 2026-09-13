package com.gtsn.ponder.gt;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.structure.StructureSource;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * GT 结构适配的 GameTest（真实加载环境：GT 已注册、结构页可展开）。放在
 * {@code com.gtsn.ponder.gt} 适配包内，使 {@code com.gregtechceu} import 仍被
 * {@code GtImportIsolationTest} 允许。
 *
 * <p>锁定 {@code BlockInfo[][][]} 的索引约定（见 {@link GtStructureAdapter} 类 javadoc 的陷阱
 * 说明）：index0 = X、index1 = Y、index2 = Z。用非对称尺寸夹具证明「按 index0 迭代」而非转置。</p>
 */
@GameTestHolder(GTSNPonder.MODID)
@PrefixGameTestTemplate(false)
public final class GtStructureGameTests {

    private GtStructureGameTests() {
    }

    /** 适配器能在真实 GT 上取到小型多方块并产出非空结构。 */
    @GameTest(template = "empty")
    public static void adapterProducesStructureFromGt(GameTestHelper helper) {
        StructureSource source = GtStructureAdapter.smallestMultiblock().orElse(null);
        if (source == null) {
            helper.fail("GtStructureAdapter.smallestMultiblock() returned no structure (GT multiblocks registered?)");
            return;
        }
        helper.assertTrue(source.blockCount() > 0, "structure has no blocks: " + source);
        helper.assertTrue(source.sizeX() > 0 && source.sizeY() > 0 && source.sizeZ() > 0,
                "structure has non-positive size: " + source);
        helper.succeed();
    }

    /**
     * 结构页数组按 index0 = X 索引：用 2×3×4 的非对称夹具，断言
     * {@code blocks.length == 2 && blocks[0].length == 3 && blocks[0][0].length == 4}，
     * 并断言特殊方块落在 index0 轴（X）上——若把数组转置就会失败。
     */
    @GameTest(template = "empty")
    public static void shapeArrayIsIndexedXFirst(GameTestHelper helper) {
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("AB", "AB", "AB")
                .aisle("AB", "AB", "AB")
                .aisle("AB", "AB", "AB")
                .aisle("AB", "AB", "AB")
                .where('A', Blocks.IRON_BLOCK)
                .where('B', Blocks.GOLD_BLOCK)
                .build();
        BlockInfo[][][] blocks = shape.getBlocks();
        helper.assertTrue(blocks.length == 2, "expected index0 size 2 (chars), got " + blocks.length);
        helper.assertTrue(blocks[0].length == 3, "expected index1 size 3 (rows), got " + blocks[0].length);
        helper.assertTrue(blocks[0][0].length == 4, "expected index2 size 4 (aisles), got " + blocks[0][0].length);
        // 'B' is the second character → index0 == 1; proves the char axis is index0 (GT consumer treats it as X).
        helper.assertTrue(blocks[1][0][0].getBlockState().getBlock() == Blocks.GOLD_BLOCK,
                "'B' did not land at index0 == 1 (array may be transposed)");
        helper.assertTrue(blocks[0][0][0].getBlockState().getBlock() == Blocks.IRON_BLOCK,
                "'A' did not land at index0 == 0");
        helper.succeed();
    }

    /**
     * 适配器不转置：产出结构的尺寸与原始结构页数组
     * {@code [blocks.length][blocks[0].length][blocks[0][0].length]} 一一对应。
     * 优先选用非立方的 EBF / 热解炉，使转置可被检出。
     */
    @GameTest(template = "empty")
    public static void adapterDimensionsMatchShapeArray(GameTestHelper helper) {
        String id = "gtceu:electric_blast_furnace";
        StructureSource source = GtStructureAdapter.byId(id).orElse(null);
        if (source == null) {
            id = "gtceu:pyrolyse_oven";
            source = GtStructureAdapter.byId(id).orElse(null);
        }
        if (source == null) {
            source = GtStructureAdapter.smallestMultiblock().orElse(null);
            id = source == null ? null : source.id();
        }
        if (source == null) {
            helper.fail("no multiblock structure available to check dimensions");
            return;
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(ResourceLocation.tryParse(id));
        if (!(definition instanceof MultiblockMachineDefinition multiblock)) {
            helper.fail("definition is not a multiblock: " + id);
            return;
        }
        BlockInfo[][][] grid = multiblock.getMatchingShapes().get(0).getBlocks();
        helper.assertTrue(source.sizeX() == grid.length,
                "sizeX " + source.sizeX() + " != index0 " + grid.length + " for " + id);
        helper.assertTrue(source.sizeY() == grid[0].length,
                "sizeY " + source.sizeY() + " != index1 " + grid[0].length + " for " + id);
        helper.assertTrue(source.sizeZ() == grid[0][0].length,
                "sizeZ " + source.sizeZ() + " != index2 " + grid[0][0].length + " for " + id);
        helper.succeed();
    }
}
