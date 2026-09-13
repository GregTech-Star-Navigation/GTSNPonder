package com.gtsn.ponder.engine.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主缝 DTO 校验：声明式 JSON → {@link SceneData}，只断言外部可观察的解析结果与告警。
 */
class SceneDataParserTest {

    private static String fullScene() {
        return """
                {
                  "formatVersion": 1,
                  "id": "gtceu:test_scene",
                  "title": "ponder.gtceu.test.title",
                  "target": "gtceu:test_machine",
                  "variant": "default",
                  "source": "hand",
                  "generatorVersion": "1.0.0",
                  "futureTopLevelKey": { "anything": [1, 2, 3] },
                  "elements": [
                    { "id": "section_a", "kind": "section", "futureElementKey": true },
                    { "id": "anchor_ctrl", "kind": "anchor" }
                  ],
                  "steps": [
                    {
                      "id": "s1", "type": "showSection", "duration": 10,
                      "targets": ["section_a"],
                      "params": { "futureParam": "ignored" },
                      "futureStepKey": 42
                    },
                    { "id": "s2", "type": "totallyUnknownFutureStep", "duration": 5, "targets": ["anchor_ctrl"] },
                    { "id": "s3", "type": "text", "duration": 0, "narration": "ponder.gtceu.test.narration",
                      "narrationArgs": ["Test Machine", "3x4x3"] }
                  ]
                }
                """;
    }

    @Test
    void parsesHeaderElementsAndKnownSteps() {
        SceneParseResult result = SceneDataParser.parse(fullScene());
        SceneData scene = result.scene();

        assertEquals(1, scene.formatVersion());
        assertEquals("gtceu:test_scene", scene.id());
        assertEquals("ponder.gtceu.test.title", scene.title());
        assertEquals("gtceu:test_machine", scene.target());
        assertEquals("default", scene.variant());
        assertEquals(Source.HAND, scene.source());
        assertEquals("1.0.0", scene.generatorVersion());

        assertEquals(2, scene.elements().size());
        assertEquals("section_a", scene.elements().get(0).id());
        assertEquals("section", scene.elements().get(0).kind());

        assertEquals(2, scene.steps().size());
        SceneStep first = scene.steps().get(0);
        assertEquals("s1", first.id());
        assertEquals(StepType.SHOW_SECTION, first.type());
        assertEquals(10, first.duration());
        assertEquals(List.of("section_a"), first.targets());
        SceneStep second = scene.steps().get(1);
        assertEquals("s3", second.id());
        assertEquals(StepType.TEXT, second.type());
        assertEquals("ponder.gtceu.test.narration", second.narration());
        assertEquals(List.of("Test Machine", "3x4x3"), second.narrationArgs());
    }

    @Test
    void rejectsMissingFormatVersion() {
        SceneFormatException exception = assertThrows(SceneFormatException.class,
                () -> SceneDataParser.parse("{ \"id\": \"no-version\" }"));
        assertTrue(exception.getMessage().contains("formatVersion"), exception.getMessage());
    }

    @Test
    void rejectsNonIntegerFormatVersion() {
        SceneFormatException exception = assertThrows(SceneFormatException.class,
                () -> SceneDataParser.parse("{ \"formatVersion\": \"1\" }"));
        assertTrue(exception.getMessage().contains("formatVersion"), exception.getMessage());
    }

    @Test
    void rejectsNonPositiveFormatVersion() {
        SceneFormatException exception = assertThrows(SceneFormatException.class,
                () -> SceneDataParser.parse("{ \"formatVersion\": 0 }"));
        assertTrue(exception.getMessage().contains("formatVersion"), exception.getMessage());
    }

    @Test
    void skipsUnknownStepTypeAndWarns() {
        SceneParseResult result = SceneDataParser.parse(fullScene());

        assertTrue(result.hasWarnings(), "expected a warning for the unknown step type");
        assertTrue(result.warnings().stream()
                        .anyMatch(warning -> warning.contains("s2") && warning.contains("totallyUnknownFutureStep")),
                result.warnings().toString());
        assertTrue(result.scene().steps().stream().noneMatch(step -> step.id().equals("s2")),
                "unknown step type must be skipped");
    }

    @Test
    void ignoresUnknownKeys() {
        SceneParseResult result = SceneDataParser.parse(fullScene());

        assertEquals(1, result.scene().formatVersion());
        assertEquals(2, result.scene().steps().size());
    }

    @Test
    void appliesDefaultsForOptionalFields() {
        SceneData scene = SceneDataParser.parse("{ \"formatVersion\": 1 }").scene();

        assertEquals(1, scene.formatVersion());
        assertNull(scene.id());
        assertNull(scene.target());
        assertEquals(Source.AUTO, scene.source());
        assertTrue(scene.elements().isEmpty());
        assertTrue(scene.steps().isEmpty());
    }
}
