package com.gtsn.ponder.gt;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.structure.HatchClassifier;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.module.ModuleDefinition;
import com.gregtechceu.gtceu.api.machine.module.ModuleRegion;
import com.gregtechceu.gtceu.api.machine.module.ModuleSlot;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * GT 结构适配的 GameTest（真实加载环境：GT 已注册、结构页可展开）。放在
 * {@code com.gtsn.ponder.gt} 适配包内，使 {@code com.gregtechceu} import 仍被
 * {@code GtImportIsolationTest} 允许。
 *
 * <p>锁定 {@code BlockInfo[][][]} 的索引约定（见 {@link GtStructureAdapter} 类 javadoc 的陷阱
 * 说明）：index0 = X、index1 = Y、index2 = Z。用非对称尺寸夹具证明「按 index0 迭代」而非转置。</p>
 *
 * <p>另验证仓口 / 总线分类（能力特征优先 + 名称兜底）、模块位提取与坐标锚定，以及
 * 无控制器 / 畸形结构的守卫分支。</p>
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

    /**
     * 真实多方块（焦炉）产出结构、含控制器单元，且仓口视图与方块角色一致；任何按名称可判为
     * 仓口的方块都必须被实际标记为仓口（证明名称兜底已应用）。
     */
    @GameTest(template = "empty")
    public static void adapterRealMultiblockRolesAreConsistent(GameTestHelper helper) {
        StructureSource source = GtStructureAdapter.byId("gtceu:coke_oven").orElse(null);
        if (source == null) {
            helper.fail("gtceu:coke_oven did not resolve to a structure source");
            return;
        }
        helper.assertTrue(source.blockCount() > 0, "coke oven has no blocks: " + source);
        helper.assertTrue(source.hasController(), "coke oven should expose a controller cell: " + source);
        long hatchBlockCount = source.blocks().stream().filter(block -> block.role().isHatch()).count();
        helper.assertTrue(source.hatches().size() == hatchBlockCount,
                "hatches() view disagrees with block roles: " + source);
        for (StructureBlock block : source.blocks()) {
            if (HatchClassifier.classifyByName(block.blockId()).isHatch()) {
                helper.assertTrue(block.isHatch(), "hatch-named block was not classified as a hatch: " + block);
            }
        }
        helper.succeed();
    }

    /** 能力特征优先：真实 GT 仓口方块被映射为精确的 I/O 角色（而非模糊的 OTHER_HATCH）。 */
    @GameTest(template = "empty")
    public static void adapterClassifiesHatchesFromPartAbilities(GameTestHelper helper) {
        Block itemInput = firstBlock(PartAbility.IMPORT_ITEMS);
        Block fluidOutput = firstBlock(PartAbility.EXPORT_FLUIDS);
        Block energyInput = firstBlock(PartAbility.INPUT_ENERGY);
        Block muffler = firstBlock(PartAbility.MUFFLER);
        if (itemInput == null || fluidOutput == null || energyInput == null || muffler == null) {
            helper.fail("PartAbility registry is empty; cannot verify ability-based hatch classification");
            return;
        }
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("ABCDE")
                .where('A', Blocks.IRON_BLOCK)
                .where('B', itemInput)
                .where('C', fluidOutput)
                .where('D', energyInput)
                .where('E', muffler)
                .build();

        StructureSource source = toSynthetic("gtsnponder:test_hatches", shape, List.of());
        helper.assertTrue(source.blockCount() == 5, "expected 5 blocks, got " + source.blockCount());
        helper.assertTrue(source.blocks().get(1).role() == StructureRole.ITEM_INPUT,
                "item bus not classified as ITEM_INPUT: " + source.blocks().get(1));
        helper.assertTrue(source.blocks().get(2).role() == StructureRole.FLUID_OUTPUT,
                "fluid hatch not classified as FLUID_OUTPUT: " + source.blocks().get(2));
        helper.assertTrue(source.blocks().get(3).role() == StructureRole.ENERGY_INPUT,
                "energy hatch not classified as ENERGY_INPUT: " + source.blocks().get(3));
        helper.assertTrue(source.blocks().get(4).role() == StructureRole.MUFFLER,
                "muffler hatch not classified as MUFFLER: " + source.blocks().get(4));
        helper.assertTrue(source.hatches().size() == 4, "expected 4 hatches, got " + source.hatches().size());
        helper.succeed();
    }

    /** 名称兜底：未注册任何 PartAbility 的特殊部件（焦炉仓）仍被识别为仓口。 */
    @GameTest(template = "empty")
    public static void adapterUsesNameFallbackForAbilityLessHatch(GameTestHelper helper) {
        Block cokeOvenHatch = GTMachines.COKE_OVEN_HATCH.getBlock();
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("AB")
                .where('A', Blocks.IRON_BLOCK)
                .where('B', cokeOvenHatch)
                .build();

        StructureSource source = toSynthetic("gtsnponder:test_name_fallback", shape, List.of());
        helper.assertTrue(source.blocks().get(1).role() == StructureRole.OTHER_HATCH,
                "coke oven hatch not classified via name fallback: " + source.blocks().get(1));
        helper.succeed();
    }

    /** 模块位提取：区域坐标按 GT 预览映射（控制器 + (-x,+y,-z)）锚定到结构页坐标系。 */
    @GameTest(template = "empty")
    public static void adapterExtractsModuleSlotsAnchoredAtController(GameTestHelper helper) {
        Block controllerBlock = controllerDefinition().getBlock();
        ModuleDefinition module = new ModuleDefinition(new ResourceLocation("gtsnponder:test_module"));
        ModuleRegion region = ModuleRegion.at(BlockPos.ZERO, 1, 1, 1);
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("BBBBBBBA")
                .where('A', controllerBlock)
                .where('B', Blocks.IRON_BLOCK)
                .build();

        StructureSource source = toSynthetic("gtsnponder:test_modules", shape,
                List.of(ModuleSlot.of(region, module)));
        helper.assertTrue(source.hasController(), "synthetic multiblock has no controller: " + source);
        helper.assertTrue(source.moduleSlotCount() == 1, "expected 1 module slot, got " + source.moduleSlotCount());
        var slot = source.moduleSlots().get(0);
        helper.assertTrue(slot.offsetX() == 7 && slot.offsetY() == 0 && slot.offsetZ() == 0,
                "module slot anchored wrong: " + slot);
        helper.assertTrue(slot.sizeX() == 1 && slot.sizeY() == 1 && slot.sizeZ() == 1,
                "module slot size wrong: " + slot);
        helper.assertTrue(!slot.acceptsAnyModule(), "restricted slot should not accept any module: " + slot);
        helper.assertTrue(slot.acceptableModuleIds().equals(List.of("gtsnponder:test_module")),
                "acceptable modules wrong: " + slot);
        helper.assertTrue(slot.accepts("gtsnponder:test_module"), "slot should accept its declared module");
        helper.succeed();
    }

    /** 任意模块模块位：acceptsAnyModule 为真且仍被视为有可安装模块。 */
    @GameTest(template = "empty")
    public static void adapterKeepsAnyModuleSlot(GameTestHelper helper) {
        Block controllerBlock = controllerDefinition().getBlock();
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("BBBBBABB")
                .where('A', controllerBlock)
                .where('B', Blocks.IRON_BLOCK)
                .build();

        StructureSource source = toSynthetic("gtsnponder:test_any_module", shape,
                List.of(ModuleSlot.any(ModuleRegion.at(BlockPos.ZERO, 2, 1, 1))));
        helper.assertTrue(source.moduleSlotCount() == 1, "expected 1 module slot, got " + source.moduleSlotCount());
        var slot = source.moduleSlots().get(0);
        helper.assertTrue(slot.offsetX() == 5 && slot.offsetY() == 0 && slot.offsetZ() == 0,
                "any-module slot anchored wrong: " + slot);
        helper.assertTrue(slot.acceptsAnyModule(), "any-module slot lost its flag: " + slot);
        helper.assertTrue(slot.hasAcceptableModules(), "any-module slot must have acceptable modules: " + slot);
        helper.assertTrue(slot.sizeX() == 2, "any-module slot size wrong: " + slot);
        helper.succeed();
    }

    /** 守卫：无控制器的结构页不产出模块位（无法锚定区域），但结构本身照常产出。 */
    @GameTest(template = "empty")
    public static void adapterDropsModuleSlotsWithoutController(GameTestHelper helper) {
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("BBBBBBBB")
                .where('B', Blocks.IRON_BLOCK)
                .build();

        StructureSource source = toSynthetic("gtsnponder:test_no_controller", shape,
                List.of(ModuleSlot.any(ModuleRegion.at(BlockPos.ZERO, 1, 1, 1))));
        helper.assertTrue(source.blockCount() == 8, "structure blocks missing: " + source.blockCount());
        helper.assertTrue(!source.hasController(), "structure unexpectedly has a controller: " + source);
        helper.assertTrue(!source.hasModuleSlots(),
                "module slots must be dropped without a controller: " + source);
        helper.succeed();
    }

    /** 守卫：换算后越出结构包围盒的模块位被跳过，结构本身不受影响。 */
    @GameTest(template = "empty")
    public static void adapterDropsOutOfBoundsModuleSlot(GameTestHelper helper) {
        Block controllerBlock = controllerDefinition().getBlock();
        MultiblockShapeInfo shape = MultiblockShapeInfo.builder()
                .aisle("ABBBBBBB")
                .where('A', controllerBlock)
                .where('B', Blocks.IRON_BLOCK)
                .build();

        // offset (5,0,0) with controller at x=0 maps to grid x = -5 → outside the structure page.
        StructureSource source = toSynthetic("gtsnponder:test_oob_module", shape,
                List.of(ModuleSlot.any(ModuleRegion.at(new BlockPos(5, 0, 0), 1, 1, 1))));
        helper.assertTrue(source.hasController(), "synthetic multiblock lost its controller: " + source);
        helper.assertTrue(source.moduleSlotCount() == 0,
                "out-of-bounds module slot must be dropped: " + source.moduleSlots());
        helper.succeed();
    }

    /** 守卫：无结构页 / 空结构页 / 空方块数组 / 未知或畸形 id 一律返回空而不抛异常。 */
    @GameTest(template = "empty")
    public static void adapterGuardsMalformedStructures(GameTestHelper helper) {
        MultiblockMachineDefinition noShapes =
                new MultiblockMachineDefinition(new ResourceLocation("gtsnponder:test_no_shapes"));
        helper.assertTrue(GtStructureAdapter.toSource(noShapes).isEmpty(),
                "definition with unset shapes must yield empty");

        noShapes.setShapes(List::of);
        helper.assertTrue(GtStructureAdapter.toSource(noShapes).isEmpty(),
                "definition with empty shapes must yield empty");

        MultiblockShapeInfo nullGrid = new MultiblockShapeInfo((BlockInfo[][][]) null);
        MultiblockMachineDefinition nullGridDefinition =
                new MultiblockMachineDefinition(new ResourceLocation("gtsnponder:test_null_grid"));
        nullGridDefinition.setShapes(() -> Collections.singletonList(nullGrid));
        helper.assertTrue(GtStructureAdapter.toSource(nullGridDefinition).isEmpty(),
                "definition with a null grid must yield empty");

        helper.assertTrue(GtStructureAdapter.byId("gtceu:does_not_exist").isEmpty(),
                "unknown machine id must yield empty");
        helper.assertTrue(GtStructureAdapter.byId("not a resource location").isEmpty(),
                "malformed machine id must yield empty");
        helper.succeed();
    }

    /**
     * 自动生成器在真实 GT 多方块上可产出<b>可播放</b>（每个元素都解析到方块）且<b>确定性</b>
     * （两次生成字节相等 JSON）的场景。选取小 / 中 / 大三台代表机器，验证自动生成缝在真实数据上成立。
     */
    @GameTest(template = "empty")
    public static void generatorProducesDeterministicSceneForRealMultiblocks(GameTestHelper helper) {
        List<String> ids = List.of(
                "gtceu:coke_oven", "gtceu:electric_blast_furnace", "gtceu:large_chemical_reactor");
        int checked = 0;
        for (String id : ids) {
            StructureSource source = GtStructureAdapter.byId(id).orElse(null);
            if (source == null || source.isEmpty()) {
                continue;
            }
            SceneData scene = SceneGenerator.generate(source);
            helper.assertTrue(scene.source() == Source.AUTO, "generated source must be auto for " + id);
            helper.assertTrue(SceneGenerator.GENERATOR_VERSION.equals(scene.generatorVersion()),
                    "generatorVersion missing for " + id);
            helper.assertTrue(!scene.steps().isEmpty(), "no steps generated for " + id);
            helper.assertTrue(SceneDataWriter.toJson(scene)
                            .equals(SceneDataWriter.toJson(SceneGenerator.generate(source))),
                    "generation is not deterministic for " + id);

            SceneElementResolver resolver = new SceneElementResolver(source);
            for (SceneElement element : scene.elements()) {
                helper.assertTrue(!resolver.resolve(element).isEmpty(),
                        "element '" + element.id() + "' resolves to nothing for " + id);
            }
            // 逐层 / 角色分组由方块数决定，两者都必须产出非空构建序列。
            long buildSteps = scene.steps().stream()
                    .filter(step -> step.id().startsWith("build.layer.") || step.id().startsWith("build.role."))
                    .count();
            helper.assertTrue(buildSteps > 0, "no build step generated for " + id);
            checked++;
        }
        helper.assertTrue(checked > 0, "no real multiblock could be auto-generated (GT loaded?)");
        helper.succeed();
    }

    /** 取焦炉定义用于承载控制器方块（其方块是带 MultiblockMachineDefinition 的 MetaMachineBlock）。 */
    private static MultiblockMachineDefinition controllerDefinition() {
        MachineDefinition definition = GTRegistries.MACHINES.get(ResourceLocation.tryParse("gtceu:coke_oven"));
        if (!(definition instanceof MultiblockMachineDefinition multiblock)) {
            throw new IllegalStateException("gtceu:coke_oven is not a multiblock definition");
        }
        return multiblock;
    }

    /** 构造一个未注册的合成多方块定义，注入结构页与模块位，再交给适配器。 */
    private static StructureSource toSynthetic(String id, MultiblockShapeInfo shape, List<ModuleSlot> moduleSlots) {
        MultiblockMachineDefinition definition = new MultiblockMachineDefinition(new ResourceLocation(id));
        definition.setShapes(() -> List.of(shape));
        definition.setModuleSlots(moduleSlots);
        return GtStructureAdapter.toSource(definition)
                .orElseThrow(() -> new IllegalStateException("synthetic structure '" + id + "' produced no source"));
    }

    private static Block firstBlock(PartAbility ability) {
        for (Block block : ability.getAllBlocks()) {
            return block;
        }
        return null;
    }
}
