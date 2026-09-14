package com.gtsn.ponder.engine.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema 白名单（反 DSL 铁律可测）：给定 JSON 文档，断言解析器<b>只</b>接受冻结的 v1 节点形状，
 * 拒绝未知 / 畸形形状；未知步骤类型仍按前向兼容规则跳过并告警（不崩）。
 *
 * <p>只通过公开缝 {@link SceneDataParser#parse(String)} 断言外部可观察行为
 * （异常 / 告警 / 解析结果），不触碰内部实现。</p>
 */
class SceneSchemaWhitelistTest {

    private static SceneFormatException reject(String json) {
        return assertThrows(SceneFormatException.class, () -> SceneDataParser.parse(json));
    }

    @Test
    void rejectsNonObjectRoot() {
        assertTrue(reject("[]").getMessage().contains("object"));
    }

    @Test
    void rejectsNewerThanCurrentFormatVersion() {
        SceneFormatException exception = reject("{\"formatVersion\":" + (SceneFormat.CURRENT_VERSION + 1) + "}");
        assertTrue(exception.getMessage().contains("formatVersion"), exception.getMessage());
        assertTrue(exception.getMessage().contains(String.valueOf(SceneFormat.CURRENT_VERSION + 1)),
                exception.getMessage());
    }

    @Test
    void rejectsElementsThatAreNotAnArray() {
        reject("{\"formatVersion\":1,\"elements\":{}}");
    }

    @Test
    void rejectsElementThatIsNotAnObject() {
        reject("{\"formatVersion\":1,\"elements\":[\"section\"]}");
    }

    @Test
    void rejectsElementWithoutId() {
        reject("{\"formatVersion\":1,\"elements\":[{\"kind\":\"section\"}]}");
    }

    @Test
    void rejectsElementWithBlankId() {
        reject("{\"formatVersion\":1,\"elements\":[{\"id\":\"  \"}]}");
    }

    @Test
    void rejectsStepsThatAreNotAnArray() {
        reject("{\"formatVersion\":1,\"steps\":{}}");
    }

    @Test
    void rejectsStepThatIsNotAnObject() {
        reject("{\"formatVersion\":1,\"steps\":[42]}");
    }

    @Test
    void rejectsStepWithoutId() {
        reject("{\"formatVersion\":1,\"steps\":[{\"type\":\"text\"}]}");
    }

    @Test
    void rejectsTargetsThatAreNotAnArray() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\",\"targets\":\"section\"}]}");
    }

    @Test
    void rejectsTargetsContainingNonStrings() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\",\"targets\":[1,2]}]}");
    }

    @Test
    void rejectsFractionalDuration() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\",\"duration\":1.5}]}");
    }

    @Test
    void rejectsNegativeDuration() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\",\"duration\":-1}]}");
    }

    @Test
    void rejectsParamsThatAreNotAnObject() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\",\"params\":[1]}]}");
    }

    @Test
    void rejectsNestedObjectParamValue() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\","
                + "\"params\":{\"nested\":{\"a\":1}}}]}");
    }

    @Test
    void rejectsNestedArrayParamValue() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"text\","
                + "\"params\":{\"nested\":[1,2]}}]}");
    }

    @Test
    void rejectsNestedKeyframeValue() {
        reject("{\"formatVersion\":1,\"steps\":[{\"id\":\"s\",\"type\":\"camera\","
                + "\"keyframe\":{\"nested\":{\"a\":1}}}]}");
    }

    @Test
    void rejectsNestedElementParamValue() {
        reject("{\"formatVersion\":1,\"elements\":[{\"id\":\"e\",\"params\":{\"nested\":[1]}}]}");
    }

    @Test
    void skipsUnknownStepTypeEvenWhenItsFieldsAreMalformed() {
        // Forward-compat: an unknown step type is skipped (with a warning) before shape validation,
        // so a future step type cannot break an older parser even if it carries new shapes.
        SceneParseResult result = SceneDataParser.parse("""
                {
                  "formatVersion": 1,
                  "steps": [
                    { "id": "future", "type": "futureStep", "duration": -5, "targets": "not-an-array",
                      "params": { "nested": { "a": 1 } } }
                  ]
                }
                """);

        assertTrue(result.hasWarnings(), "unknown step type must warn");
        assertTrue(result.scene().steps().isEmpty(), "unknown step type must be skipped");
    }

    @Test
    void acceptsLiteralOnlyParamsAndKeyframe() {
        SceneParseResult result = SceneDataParser.parse("""
                {
                  "formatVersion": 1,
                  "steps": [
                    { "id": "s", "type": "camera", "duration": 0,
                      "params": { "yaw": 25.0, "fit": true, "note": "x", "empty": null },
                      "keyframe": { "frame": 3 } }
                  ]
                }
                """);

        assertEquals(1, result.scene().steps().size());
    }
}
