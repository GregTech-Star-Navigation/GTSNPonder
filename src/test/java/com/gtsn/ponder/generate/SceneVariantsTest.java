package com.gtsn.ponder.generate;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneParseResult;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多变体枚举（{@link SceneVariants}）的外部可观察行为（工单 #21 反馈 2）：给定多个结构页夹具，
 * 断言变体 id / 标签确定、每个变体可生成可解析的场景，且不同变体的结构 / 步骤数确实不同。
 *
 * <p>同时锁定「不改变冻结 schema 形状」：变体场景仍是合法 v1（解析往返稳定，仅 {@code variant} /
 * {@code id} 不同）。</p>
 */
class SceneVariantsTest {

    // --- 夹具 ---------------------------------------------------------------

    /** 一个实心 {@code sx×sy×sz} 结构（控制器在原点）。尺寸即「节数」。 */
    private static StructureSource box(String id, int sx, int sy, int sz) {
        StructureSource.Builder builder = StructureSource.builder(id).size(sx, sy, sz).controller(0, 0, 0);
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    boolean controller = x == 0 && y == 0 && z == 0;
                    builder.addBlock(x, y, z,
                            controller ? "gtceu:test_controller" : "gtceu:test_casing",
                            controller ? StructureRole.CONTROLLER : StructureRole.PLAIN);
                }
            }
        }
        return builder.build();
    }

    private static int buildStepCount(SceneData scene) {
        return (int) scene.steps().stream().filter(s -> s.id().startsWith("build.")).count();
    }

    // --- 命名 ---------------------------------------------------------------

    @Test
    void singleShapeIsTheDefaultVariant() {
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(box("gtceu:one", 3, 3, 3)));

        assertEquals(1, specs.size());
        assertEquals(SceneVariants.VARIANT_DEFAULT, specs.get(0).id());
        assertEquals(SceneVariants.LABEL_DEFAULT, specs.get(0).labelKey());
        assertTrue(specs.get(0).labelArgs().isEmpty());
    }

    @Test
    void emptyShapeListIsAlsoASingleDefaultVariant() {
        assertEquals(1, SceneVariants.specs(List.of()).size());
        assertEquals(SceneVariants.VARIANT_DEFAULT, SceneVariants.specs(null).get(0).id());
    }

    @Test
    void twoShapesAreLabelledShortAndLong() {
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(
                box("gtceu:aisle", 4, 3, 3),
                box("gtceu:aisle", 12, 3, 3)));

        assertEquals(2, specs.size());
        assertEquals(SceneVariants.VARIANT_DEFAULT, specs.get(0).id());
        assertEquals(SceneVariants.LABEL_SHORT, specs.get(0).labelKey());
        assertEquals(SceneVariants.VARIANT_LONG, specs.get(1).id());
        assertEquals(SceneVariants.LABEL_LONG, specs.get(1).labelKey());
    }

    @Test
    void manyShapesAreNamedByTheirSliceCount() {
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(
                box("gtceu:aisle", 5, 3, 3),
                box("gtceu:aisle", 8, 3, 3),
                box("gtceu:aisle", 12, 3, 3)));

        assertEquals(3, specs.size());
        assertEquals(SceneVariants.VARIANT_DEFAULT, specs.get(0).id());
        assertEquals(SceneVariants.LABEL_SLICES, specs.get(0).labelKey());
        assertEquals(List.of("5"), specs.get(0).labelArgs());
        assertEquals(SceneVariants.SLICES_PREFIX + "8", specs.get(1).id());
        assertEquals(List.of("8"), specs.get(1).labelArgs());
        assertEquals(SceneVariants.SLICES_PREFIX + "12", specs.get(2).id());
        assertEquals(List.of("12"), specs.get(2).labelArgs());
    }

    @Test
    void equalSizedShapesAreNumberedInsteadOfMislabeledAsSlices() {
        // 线圈 / 机壳等「部件变体」结构尺寸相同，不能标成「n 节」；退化为通用编号。
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(
                box("gtceu:coil", 3, 3, 3),
                box("gtceu:coil", 3, 3, 3),
                box("gtceu:coil", 3, 3, 3)));

        assertEquals(SceneVariants.LABEL_NUMBERED, specs.get(0).labelKey());
        assertEquals(List.of("1"), specs.get(0).labelArgs());
        assertEquals(SceneVariants.VARIANT_DEFAULT, specs.get(0).id());
        assertEquals("v2", specs.get(1).id());
        assertEquals(List.of("2"), specs.get(1).labelArgs());
        assertEquals("v3", specs.get(2).id());
    }

    @Test
    void variantSpecsAreDeterministic() {
        List<StructureSource> shapes = List.of(box("gtceu:aisle", 5, 3, 3), box("gtceu:aisle", 12, 3, 3));
        assertEquals(SceneVariants.specs(shapes), SceneVariants.specs(shapes));
    }

    // --- 场景生成 -----------------------------------------------------------

    @Test
    void variantSceneIdsAreStableAndDistinct() {
        StructureSource shortShape = box("gtceu:aisle", 4, 3, 3);
        StructureSource longShape = box("gtceu:aisle", 12, 3, 3);
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(shortShape, longShape));

        SceneData shortScene = SceneVariants.sceneFor(shortShape, specs.get(0));
        SceneData longScene = SceneVariants.sceneFor(longShape, specs.get(1));

        assertEquals(SceneGenerator.sceneIdFor("gtceu:aisle"), shortScene.id(),
                "the default variant must keep the existing scene id (catalog / progress key compatibility)");
        assertEquals(SceneVariants.VARIANT_DEFAULT, shortScene.variant());
        assertEquals(SceneGenerator.sceneIdFor("gtceu:aisle") + "_long", longScene.id());
        assertEquals(SceneVariants.VARIANT_LONG, longScene.variant());
        assertNotEquals(shortScene.id(), longScene.id());
    }

    @Test
    void longVariantHasALargerStructureThanTheShortOne() {
        StructureSource shortShape = box("gtceu:aisle", 4, 3, 3);
        StructureSource longShape = box("gtceu:aisle", 12, 6, 6);
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(shortShape, longShape));

        SceneData shortScene = SceneVariants.sceneFor(shortShape, specs.get(0));
        SceneData longScene = SceneVariants.sceneFor(longShape, specs.get(1));

        assertTrue(longShape.blockCount() > shortShape.blockCount());
        assertTrue(longShape.sizeX() > shortShape.sizeX() && longShape.sizeY() > shortShape.sizeY());
        assertNotEquals(shortScene.id(), longScene.id());
        assertTrue(longScene.steps().stream().anyMatch(s -> s.type() == StepType.SHOW_SECTION));
    }

    @Test
    void tallerVariantRevealsMoreLayersWhenUnderTheCellBudget() {
        StructureSource shortShape = box("gtceu:tower", 4, 2, 2); // 16 blocks, 2 layers
        StructureSource tallShape = box("gtceu:tower", 4, 5, 2); // 40 blocks, 5 layers
        List<SceneVariants.Spec> specs = SceneVariants.specs(List.of(shortShape, tallShape));

        SceneData shortScene = SceneVariants.sceneFor(shortShape, specs.get(0));
        SceneData tallScene = SceneVariants.sceneFor(tallShape, specs.get(1));

        assertTrue(!SceneGenerator.isRoleGrouped(tallShape), "fixture must stay layer-by-layer");
        assertEquals(2, buildStepCount(shortScene));
        assertEquals(5, buildStepCount(tallScene));
        assertTrue(tallScene.steps().size() > shortScene.steps().size(),
                "the taller variant must reveal more layers -> more steps (short="
                        + shortScene.steps().size() + ", tall=" + tallScene.steps().size() + ")");
    }

    @Test
    void sameShapeAndSpecProduceByteEqualJson() {
        StructureSource shape = box("gtceu:aisle", 8, 3, 3);
        SceneVariants.Spec spec = SceneVariants.specs(List.of(shape, box("gtceu:aisle", 12, 3, 3))).get(0);

        String first = SceneDataWriter.toJson(SceneVariants.sceneFor(shape, spec));
        String second = SceneDataWriter.toJson(SceneVariants.sceneFor(shape, spec));
        assertEquals(first, second);
    }

    // --- 冻结 schema 形状不变 -------------------------------------------------

    @Test
    void variantScenesRemainFrozenV1AndRoundTrip() {
        assertEquals(1, SceneFormat.CURRENT_VERSION, "the scene schema must not be re-versioned for variants");

        StructureSource shape = box("gtceu:aisle", 8, 3, 3);
        SceneVariants.Spec spec = SceneVariants.specs(List.of(shape, box("gtceu:aisle", 12, 3, 3))).get(1);

        SceneData scene = SceneVariants.sceneFor(shape, spec);
        String json = SceneDataWriter.toJson(scene);
        String rewritten = SceneDataWriter.toJson(SceneDataParser.parse(json).scene());

        assertEquals(json, rewritten, "variant scenes must round-trip (schema shape unchanged)");
        SceneParseResult parsed = SceneDataParser.parse(json);
        assertEquals(SceneFormat.CURRENT_VERSION, parsed.scene().formatVersion());
        assertEquals(spec.id(), parsed.scene().variant());
        assertEquals(scene.steps().size(), parsed.scene().steps().size());
        assertFalse(parsed.scene().steps().isEmpty());
    }
}
