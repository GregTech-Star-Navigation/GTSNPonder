package com.gtsn.ponder.gt;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.generate.SceneVariants;
import com.gtsn.ponder.structure.StructureSource;
import com.mojang.logging.LogUtils;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 多变体枚举 + 搭建序列覆盖的 GameTest（工单 #21 反馈 1 / 2）。放在 {@code com.gtsn.ponder.gt}
 * 适配包内，使 {@code com.gregtechceu} import 仍被 {@code GtImportIsolationTest} 允许。
 *
 * <p>锁定两件事：① 适配器把 {@code getMatchingShapes()} 的<b>每一页</b>翻译为变体，且真实机器中
 * 至少有一台可重复结构段的机器产出 ≥2 个变体（否则「短 / 长」功能不可验证，测试直接失败）；
 * ② 每一台注册多方块的<b>默认生成场景</b>都含搭建 / 揭示序列（工单 #21 反馈 1 的审计断言）。</p>
 */
@GameTestHolder(GTSNPonder.MODID)
@PrefixGameTestTemplate(false)
public final class VariantGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private VariantGameTests() {
    }

    /** 合成一个两页多方块，断言适配器产出两个尺寸不同的变体，且命名 / 场景随之不同。 */
    @GameTest(template = "empty")
    public static void syntheticMultiShapeDefinitionYieldsDistinctVariants(GameTestHelper helper) {
        MultiblockShapeInfo shortShape = box(2);
        MultiblockShapeInfo longShape = box(5);
        MultiblockMachineDefinition definition =
                new MultiblockMachineDefinition(new ResourceLocation("gtsnponder:test_aisle"));
        definition.setShapes(() -> List.of(shortShape, longShape));
        definition.setModuleSlots(List.of());

        List<StructureSource> variants = GtStructureAdapter.variantsOf(definition);
        if (variants.size() != 2) {
            helper.fail("expected 2 variants from a 2-page definition, got " + variants.size());
            return;
        }
        if (variants.get(0).sizeX() != 2 || variants.get(1).sizeX() != 5) {
            helper.fail("variant sizes wrong: " + variants.get(0).sizeX() + " / " + variants.get(1).sizeX());
            return;
        }
        List<SceneVariants.Spec> specs = SceneVariants.specs(variants);
        helper.assertTrue(SceneVariants.LABEL_SHORT.equals(specs.get(0).labelKey()), "first must be short");
        helper.assertTrue(SceneVariants.LABEL_LONG.equals(specs.get(1).labelKey()), "second must be long");

        SceneData shortScene = SceneVariants.sceneFor(variants.get(0), specs.get(0));
        SceneData longScene = SceneVariants.sceneFor(variants.get(1), specs.get(1));
        helper.assertTrue(!shortScene.id().equals(longScene.id()), "variant scene ids must differ");
        helper.assertTrue(longScene.id().endsWith("_long"), "long variant id suffix expected: " + longScene.id());
        helper.assertTrue(variants.get(1).blockCount() > variants.get(0).blockCount(),
                "the long variant must be a larger structure");
        helper.succeed();
    }

    /**
     * 审计：枚举全部注册多方块，记录每台的（结构页数 / 变体数 / 默认场景的搭建步骤数），并断言
     * <b>每台</b>的默认生成场景都含搭建 / 揭示序列；且至少一台机器有 ≥2 个变体。
     */
    @GameTest(template = "empty")
    public static void everyRegisteredMultiblockBuildsIncrementallyAndVariantsAreEnumerated(
            GameTestHelper helper) {
        Map<String, Integer> variantCounts = new LinkedHashMap<>();
        List<String> missingBuild = new ArrayList<>();
        int machinesWithVariants = 0;
        int total = 0;

        for (GtMultiblockCatalog.Multiblock machine : GtMultiblockCatalog.all()) {
            List<StructureSource> variants = GtStructureAdapter.variantsById(machine.id());
            if (variants.isEmpty()) {
                continue;
            }
            total++;
            variantCounts.put(machine.id(), variants.size());
            if (variants.size() > 1) {
                machinesWithVariants++;
            }
            SceneData scene = SceneGenerator.generate(variants.get(0));
            long buildSteps = scene.steps().stream().filter(step -> step.id().startsWith("build.")).count();
            if (buildSteps <= 0) {
                missingBuild.add(machine.id());
            }
        }

        LOGGER.info("[GTSNPonder] variant audit: {}/{} registered multiblocks resolved; {} with >1 variant",
                total, GtMultiblockCatalog.all().size(), machinesWithVariants);
        LOGGER.info("[GTSNPonder] variant counts: {}", new TreeMap<>(variantCounts));
        for (String id : List.of("gtceu:large_combustion_engine", "gtceu:active_transformer",
                "gtceu:steel_multiblock_tank", "gtceu:primitive_pump")) {
            GtStructureAdapter.byId(id).ifPresent(source -> LOGGER.info(
                    "[GTSNPonder] bundled scene structure {} = {}x{}x{} ({} blocks)",
                    id, source.sizeX(), source.sizeY(), source.sizeZ(), source.blockCount()));
        }

        if (missingBuild.isEmpty() && total > 0) {
            LOGGER.info("[GTSNPonder] every generated multiblock scene has a build/reveal sequence");
        }
        helper.assertTrue(missingBuild.isEmpty(),
                "generated scenes without a build sequence: " + missingBuild);
        helper.assertTrue(machinesWithVariants > 0,
                "no registered multiblock produces more than one shape page (variant feature unverifiable)");
        helper.succeed();
    }

    /** 每个注册多方块的每个变体都生成可播放、元素可解析、字节确定的场景。 */
    @GameTest(template = "empty")
    public static void everyVariantGeneratesAPlayableDeterministicScene(GameTestHelper helper) {
        int variantsChecked = 0;
        for (GtMultiblockCatalog.Multiblock machine : GtMultiblockCatalog.all()) {
            List<StructureSource> variants = GtStructureAdapter.variantsById(machine.id());
            List<SceneVariants.Spec> specs = SceneVariants.specs(variants);
            for (int i = 0; i < variants.size(); i++) {
                StructureSource shape = variants.get(i);
                SceneData scene = SceneVariants.sceneFor(shape, specs.get(i));
                if (scene.steps().isEmpty()) {
                    helper.fail("variant scene has no steps: " + machine.id() + " / " + specs.get(i).id());
                    return;
                }
                SceneElementResolver resolver = new SceneElementResolver(shape);
                for (SceneElement element : scene.elements()) {
                    if (resolver.resolve(element).isEmpty()) {
                        helper.fail("element '" + element.id() + "' resolves to no blocks in "
                                + machine.id() + " / " + specs.get(i).id());
                        return;
                    }
                }
                String first = SceneDataWriter.toJson(scene);
                String second = SceneDataWriter.toJson(SceneVariants.sceneFor(shape, specs.get(i)));
                if (!first.equals(second)) {
                    helper.fail("variant generation is not deterministic: " + machine.id());
                    return;
                }
                if (!scene.steps().stream().anyMatch(step -> step.type() == StepType.SHOW_SECTION)) {
                    helper.fail("variant scene has no reveal step: " + machine.id());
                    return;
                }
                variantsChecked++;
            }
        }
        LOGGER.info("[GTSNPonder] variant generation audit: {} variant scene(s) verified", variantsChecked);
        helper.assertTrue(variantsChecked > 0, "no variant scenes were verified");
        helper.succeed();
    }

    /** 一个 {@code x} 宽、3×3 截面、实心铁块的结构页（无控制器，纯用于变体枚举）。 */
    private static MultiblockShapeInfo box(int x) {
        String row = "C".repeat(x);
        MultiblockShapeInfo.ShapeInfoBuilder builder = MultiblockShapeInfo.builder();
        for (int z = 0; z < 3; z++) {
            builder.aisle(row, row, row);
        }
        return builder.where('C', Blocks.IRON_BLOCK).build();
    }
}
