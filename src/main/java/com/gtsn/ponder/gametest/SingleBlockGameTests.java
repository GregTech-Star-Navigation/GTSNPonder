package com.gtsn.ponder.gametest;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.catalog.SingleBlockScenes;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
import com.gtsn.ponder.gt.GtSingleBlockAdapter;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import com.gtsn.ponder.structure.StructureSource;

import com.mojang.logging.LogUtils;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 工单 #15（单方块机器使用场景）的 GameTest：在真实加载环境（GT 已注册）中校验代表性单方块机器可经
 * 唯一适配包 {@link GtSingleBlockAdapter} 解析为机器源，且 {@link SingleBlockUsageGenerator} 为其产出
 * <b>可播放</b>（每个元素解析到方块）且<b>确定性</b>（两次生成字节相等 JSON）的使用场景；精选手作机器
 * 有随包手作场景，覆盖分析器 {@link SingleBlockScenes#analyze} 核算的数字达标。
 *
 * <p>本类只 import {@code com.gtsn.ponder.gt} 的门面，不直接 import {@code com.gregtechceu}——GT import
 * 隔离纪律照旧。</p>
 */
@GameTestHolder(GTSNPonder.MODID)
@PrefixGameTestTemplate(false)
public final class SingleBlockGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SingleBlockGameTests() {
    }

    /**
     * 代表性单方块机器：① 每台都经适配器解析为机器源（无死链）；② 使用场景 source=auto 且确定性
     * （两次生成字节相等）；③ 场景每个元素解析到方块（可播放）。任何一台失败即列出其 id。
     */
    @GameTest(template = "empty")
    public static void representativeSingleBlockMachinesResolveAndGenerate(GameTestHelper helper) {
        if (SingleBlockScenes.REPRESENTATIVE.size() < SingleBlockScenes.MIN_REPRESENTATIVE) {
            helper.fail("fewer than " + SingleBlockScenes.MIN_REPRESENTATIVE + " representative machines declared");
            return;
        }
        for (String id : SingleBlockScenes.REPRESENTATIVE) {
            SingleBlockMachineSource machine = GtSingleBlockAdapter.byId(id).orElse(null);
            if (machine == null) {
                helper.fail("representative single-block machine did not resolve (dead link): " + id);
                return;
            }
            if (machine.blockId() == null || machine.blockId().isBlank()) {
                helper.fail("machine resolved without a block id: " + id);
                return;
            }

            StructureSource structure = SingleBlockUsageGenerator.structureOf(machine);
            SceneData scene = SingleBlockUsageGenerator.generate(machine);
            if (scene.source() != Source.AUTO) {
                helper.fail(id + " usage scene is not source=auto");
                return;
            }
            if (!SingleBlockUsageGenerator.GENERATOR_VERSION.equals(scene.generatorVersion())) {
                helper.fail(id + " usage scene is missing generatorVersion");
                return;
            }
            if (scene.steps().isEmpty()) {
                helper.fail(id + " usage scene generated no steps");
                return;
            }
            if (!SingleBlockUsageGenerator.sceneIdFor(id).equals(scene.id())) {
                helper.fail(id + " usage scene id does not match sceneIdFor(): " + scene.id());
                return;
            }
            if (!SceneDataWriter.toJson(scene)
                    .equals(SceneDataWriter.toJson(SingleBlockUsageGenerator.generate(machine)))) {
                helper.fail(id + " usage generation is not deterministic");
                return;
            }

            SceneElementResolver resolver = new SceneElementResolver(structure);
            for (SceneElement element : scene.elements()) {
                if (resolver.resolve(element).isEmpty()) {
                    helper.fail(id + " usage scene element '" + element.id() + "' is unplayable");
                    return;
                }
            }
        }
        LOGGER.info("[GTSNPonder] single-block gametest: {} representative machines resolve and generate "
                + "playable, deterministic usage scenes", SingleBlockScenes.REPRESENTATIVE.size());
        helper.succeed();
    }

    /**
     * 精选单方块机器有随包手作场景；覆盖分析器（随包手作优先、否则按需生成）核算「零死链 + 手作讲解齐全 +
     * 数量达标」。
     */
    @GameTest(template = "empty")
    public static void curatedSingleBlockMachinesAreHandAuthoredAndCoverageIsComplete(GameTestHelper helper) {
        for (SingleBlockScenes.CuratedScene curated : SingleBlockScenes.CURATED) {
            Optional<SceneData> bundled = bundledScene(curated.resourcePath());
            if (bundled.isEmpty()) {
                helper.fail("curated usage scene resource is missing: " + curated.resourcePath());
                return;
            }
            SceneData scene = bundled.get();
            if (!curated.target().equals(scene.target())) {
                helper.fail(curated.resourcePath() + " declares target " + scene.target()
                        + " instead of " + curated.target());
                return;
            }
            if (!SingleBlockScenes.isHandNarrated(scene.source())) {
                helper.fail(curated.resourcePath() + " must be source=hand|mixed, got " + scene.source());
                return;
            }
        }

        SingleBlockScenes.Report report = SingleBlockScenes.analyze(SingleBlockGameTests::resolveBundledOrGenerated);
        LOGGER.info("[GTSNPonder] single-block gametest: {}", report.summary());
        helper.assertTrue(report.coversEveryRepresentative(),
                "representative machines without a usage scene (dead links): " + report.deadLinks());
        helper.assertTrue(report.curatedNarrationComplete(),
                "curated machines without hand-authored narration: " + report.curatedWithoutHandNarration());
        helper.assertTrue(report.handAuthored() >= SingleBlockScenes.MIN_HAND_AUTHORED, report.summary());
        helper.assertTrue(report.isComplete(), "single-block usage coverage incomplete: " + report.summary());
        helper.succeed();
    }

    /** 解析缝（GameTest 侧）：随包手作场景优先，否则经适配器生成使用场景；两者皆无 → 空（死链）。 */
    private static Optional<SceneData> resolveBundledOrGenerated(String target) {
        for (SingleBlockScenes.CuratedScene curated : SingleBlockScenes.CURATED) {
            if (curated.target().equals(target)) {
                Optional<SceneData> bundled = bundledScene(curated.resourcePath());
                if (bundled.isPresent()) {
                    return bundled;
                }
            }
        }
        return GtSingleBlockAdapter.byId(target).map(SingleBlockUsageGenerator::generate);
    }

    /** 从 classpath 读取某资源的随包场景 JSON。 */
    private static Optional<SceneData> bundledScene(String resourcePath) {
        try (InputStream stream = SingleBlockGameTests.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Optional.empty();
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(SceneDataParser.parseOrThrow(json));
        } catch (Exception failure) {
            LOGGER.warn("[GTSNPonder] could not read bundled usage scene {}", resourcePath, failure);
            return Optional.empty();
        }
    }
}
