package com.gtsn.ponder.gametest;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.catalog.SceneCoverage;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.gt.GtMultiblockCatalog;
import com.gtsn.ponder.gt.GtStructureAdapter;
import com.gtsn.ponder.structure.StructureSource;

import com.mojang.logging.LogUtils;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工单 #13（全量覆盖）的 GameTest：在真实加载环境（GT 已注册、结构页可展开）中枚举<b>全部注册
 * 多方块</b>，断言每一台都能经唯一适配包 {@link GtStructureAdapter} 解析并产出一份<b>可播放</b>
 * （每个元素解析到方块）且<b>确定性</b>（两次生成字节相等 JSON）的自动生成场景——即「无机器无场景」。
 *
 * <p>覆盖数字同时经共享纯分析器 {@link SceneCoverage#analyze}（随包手作场景优先、否则生成）核算，
 * 故与本工单的 headless 单测 / 客户端自动测试（{@code GTSNPONDER_UI_AUTOTEST=coverage}）断言同一套
 * 数字：注册数 / 可解析数 / 手作数 / 生成数 / 精选手作讲解 / 死链数。</p>
 *
 * <p>本类只 import {@code com.gtsn.ponder.gt} 的门面（{@link GtMultiblockCatalog} /
 * {@link GtStructureAdapter}），不直接 import {@code com.gregtechceu}——GT import 隔离纪律照旧。</p>
 */
@GameTestHolder(GTSNPonder.MODID)
@PrefixGameTestTemplate(false)
public final class CoverageGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CoverageGameTests() {
    }

    /**
     * 全量覆盖验收：① 每台注册多方块都有可解析、可播放、确定性的场景；② 精选关键机器有手作讲解；
     * ③ 零死链。任何一台失败即列出其 id。
     */
    @GameTest(template = "empty")
    public static void everyRegisteredMultiblockHasAPlayableScene(GameTestHelper helper) {
        List<GtMultiblockCatalog.Multiblock> registered = GtMultiblockCatalog.all();
        if (registered.isEmpty()) {
            helper.fail("no GT multiblocks are registered; cannot verify coverage");
            return;
        }

        // ① 逐台：结构源可解析 → 生成场景每个元素都能解析到方块（可播放）→ 生成确定性。
        for (GtMultiblockCatalog.Multiblock machine : registered) {
            StructureSource source = GtStructureAdapter.byId(machine.id()).orElse(null);
            if (source == null) {
                helper.fail("registered multiblock has no structure source (dead link): " + machine.id());
                return;
            }
            SceneData scene = SceneGenerator.generate(source);
            SceneElementResolver resolver = new SceneElementResolver(source);
            for (SceneElement element : scene.elements()) {
                if (resolver.resolve(element).isEmpty()) {
                    helper.fail("generated scene element '" + element.id() + "' is unplayable for "
                            + machine.id());
                    return;
                }
            }
            if (!SceneDataWriter.toJson(scene)
                    .equals(SceneDataWriter.toJson(SceneGenerator.generate(source)))) {
                helper.fail("auto generation is not deterministic for " + machine.id());
                return;
            }
        }

        // ②③ 共享分析器核算覆盖数字（手作随包场景优先，否则按需生成）。
        List<String> targets = new ArrayList<>(registered.size());
        for (GtMultiblockCatalog.Multiblock machine : registered) {
            targets.add(machine.id());
        }
        SceneCoverage.Report report = SceneCoverage.analyze(targets, CoverageGameTests::resolveBundledOrGenerated);
        LOGGER.info("[GTSNPonder] coverage gametest: {}", report.summary());

        helper.assertTrue(report.coversEveryMultiblock(),
                "registered multiblocks without a resolvable scene (dead links): " + report.deadLinks());
        helper.assertTrue(report.curatedNarrationComplete(),
                "curated key machines without hand-authored narration: "
                        + report.curatedWithoutHandNarration());
        helper.assertTrue(report.handAuthored() > 0,
                "no hand-authored scene found among registered multiblocks: " + report.summary());
        helper.assertTrue(report.isComplete(), "coverage incomplete: " + report.summary());
        helper.succeed();
    }

    /**
     * 解析缝（GameTest 侧）：随包手作 / 混合场景优先（{@link SceneCoverage#CURATED} 的资源），
     * 否则经结构适配器生成。两者皆无 → 空（死链）。
     */
    private static Optional<SceneData> resolveBundledOrGenerated(String target) {
        Optional<SceneData> bundled = bundledScene(target);
        if (bundled.isPresent()) {
            return bundled;
        }
        return GtStructureAdapter.byId(target).map(SceneGenerator::generate);
    }

    /** 从 classpath 读取某目标的随包手作 / 混合场景（仅精选目标有随包文件）。 */
    private static Optional<SceneData> bundledScene(String target) {
        for (SceneCoverage.CuratedScene curated : SceneCoverage.CURATED) {
            if (!curated.target().equals(target)) {
                continue;
            }
            try (InputStream stream = CoverageGameTests.class.getClassLoader()
                    .getResourceAsStream(curated.resourcePath())) {
                if (stream == null) {
                    return Optional.empty();
                }
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(SceneDataParser.parseOrThrow(json));
            } catch (Exception failure) {
                LOGGER.warn("[GTSNPonder] could not read bundled scene {} for {}", curated.resourcePath(),
                        target, failure);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
