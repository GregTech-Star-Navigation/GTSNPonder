package com.gtsn.ponder.engine.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SceneDataWriter} 的外部可观察行为：与 {@link SceneDataParser} 往返稳定、输出确定性
 * （同一 {@link SceneData} 两次序列化字节相等），且头部 / 元素 / 步骤字段完整。
 *
 * <p>这是自动生成器「两次生成字节相等 JSON」证据的序列化基础。</p>
 */
class SceneDataWriterTest {

    private static SceneData fixture() {
        return SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .id("gtsnponder:auto_gtceu_test")
                .title("Test Machine")
                .target("gtceu:test_machine")
                .variant("default")
                .source(Source.AUTO)
                .generatorVersion("auto-1")
                .addElement(SceneElement.of("section.layer.0", "section",
                        Map.of("selector", "layer", "y", 0.0d)))
                .addElement(SceneElement.of("controller", "anchor",
                        Map.of("selector", "controller")))
                .addStep(SceneStep.builder()
                        .id("intro")
                        .type(StepType.TEXT)
                        .duration(40)
                        .narration("ponder.gtsnponder.generated.narration.intro")
                        .narrationArgs(List.of("Test Machine", "gtceu:test_machine", "3x3x3"))
                        .build())
                .addStep(SceneStep.builder()
                        .id("build.layer.0")
                        .type(StepType.SHOW_SECTION)
                        .duration(12)
                        .targets(List.of("section.layer.0"))
                        .param("lod", "grouped")
                        .param("fadeIn", Boolean.TRUE)
                        .build())
                .addStep(SceneStep.builder()
                        .id("focus.controller")
                        .type(StepType.CAMERA)
                        .duration(0)
                        .targets(List.of("controller"))
                        .param("yaw", 25.0d)
                        .param("pitch", -135.0d)
                        .param("distance", 6.0d)
                        .build())
                .build();
    }

    @Test
    void writesHeaderElementsAndSteps() {
        String json = SceneDataWriter.toJson(fixture());

        assertTrue(json.contains("\"formatVersion\""), json);
        assertTrue(json.contains("\"gtsnponder:auto_gtceu_test\""), json);
        assertTrue(json.contains("\"gtceu:test_machine\""), json);
        assertTrue(json.contains("\"source\": \"auto\""), json);
        assertTrue(json.contains("\"generatorVersion\": \"auto-1\""), json);
        assertTrue(json.contains("\"section.layer.0\""), json);
        assertTrue(json.contains("\"showSection\""), json);
        assertTrue(json.contains("\"narration\""), json);
        assertTrue(json.contains("\"narrationArgs\""), json);
        assertTrue(json.contains("\"3x3x3\""), json);
    }

    @Test
    void roundTripsThroughParserWithStableBytes() {
        String json = SceneDataWriter.toJson(fixture());

        SceneData parsed = SceneDataParser.parseOrThrow(json);

        assertEquals(fixture().id(), parsed.id());
        assertEquals(fixture().target(), parsed.target());
        assertEquals(fixture().source(), parsed.source());
        assertEquals(fixture().generatorVersion(), parsed.generatorVersion());
        assertEquals(fixture().elements().size(), parsed.elements().size());
        assertEquals(fixture().steps().size(), parsed.steps().size());
        assertEquals(json, SceneDataWriter.toJson(parsed),
                "writing a parsed scene must produce identical bytes (round-trip stable)");
    }

    @Test
    void isDeterministicForTheSameScene() {
        assertEquals(SceneDataWriter.toJson(fixture()), SceneDataWriter.toJson(fixture()));
    }

    @Test
    void writesStepParamsAndKeyframeUnderTheirOwnKeys() {
        SceneData scene = SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .addStep(SceneStep.builder()
                        .id("focus")
                        .type(StepType.CAMERA)
                        .duration(0)
                        .param("yaw", 25.0d)
                        .keyframe(Map.of("frame", 3.0d))
                        .build())
                .build();

        String json = SceneDataWriter.toJson(scene);

        assertTrue(json.contains("\"params\""), json);
        assertTrue(json.contains("\"keyframe\""), json);
        SceneData parsed = SceneDataParser.parseOrThrow(json);
        assertEquals(Map.of("yaw", 25.0d), parsed.steps().get(0).params(), json);
        assertEquals(Map.of("frame", 3.0d), parsed.steps().get(0).keyframe(), json);
        assertEquals(json, SceneDataWriter.toJson(parsed), "keyframe round-trip must be byte-stable");
    }

    @Test
    void writesMinimalSceneThatParses() {
        SceneData minimal = SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .build();

        String json = SceneDataWriter.toJson(minimal);
        SceneData parsed = SceneDataParser.parseOrThrow(json);

        assertEquals(SceneDataParser.CURRENT_FORMAT_VERSION, parsed.formatVersion());
        assertEquals(Source.AUTO, parsed.source());
        assertTrue(parsed.elements().isEmpty());
        assertTrue(parsed.steps().isEmpty());
    }
}
