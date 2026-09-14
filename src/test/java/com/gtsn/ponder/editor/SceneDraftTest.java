package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SceneDraft} 的外部可观察行为：编辑同一 v1 DTO（单一事实源）、唯一 ID、增删改步骤，
 * 且 {@link SceneDraft#toSceneData()} 产出的场景能被冻结的 {@link SceneDataParser} 接受。
 */
class SceneDraftTest {

    private static SceneData seed() {
        return SceneData.builder()
                .formatVersion(SceneFormat.CURRENT_VERSION)
                .id("gtsnponder:auto_gtceu_test_machine")
                .title("ponder.gtsnponder.generated.machine.gtceu_test_machine.title")
                .target("gtceu:test_machine")
                .variant("default")
                .source(Source.AUTO)
                .generatorVersion("auto-1")
                .addElement(SceneElement.of("section.layer.0", "section", Map.of("selector", "layer", "y", 0.0d)))
                .addStep(SceneStep.builder().id("build.layer.0").type(StepType.SHOW_SECTION)
                        .duration(12).targets(List.of("section.layer.0")).build())
                .build();
    }

    @Test
    void fromCopiesHeaderElementsAndStepsAndSwitchesToHand() {
        SceneDraft draft = SceneDraft.from(seed());

        assertEquals("gtceu:test_machine", draft.target());
        assertEquals("default", draft.variant());
        assertEquals(1, draft.elements().size());
        assertEquals(1, draft.steps().size());
        assertEquals(Source.HAND, draft.source(),
                "an edited draft is hand-authored (it overrides the auto baseline)");
    }

    @Test
    void toSceneDataEmitsFrozenV1ThatParses() {
        SceneDraft draft = SceneDraft.from(seed());
        draft.addStep(SceneStep.builder().id("text.1").type(StepType.TEXT).duration(30)
                .narration("ponder.gtsnponder.editor.record.narration").build());

        SceneData scene = draft.toSceneData();

        assertEquals(SceneFormat.CURRENT_VERSION, scene.formatVersion());
        assertEquals(SceneFormat.V1, scene.formatVersion());
        assertEquals("gtceu:test_machine", scene.target());
        assertEquals(Source.HAND, scene.source());
        assertEquals(2, scene.steps().size());

        // 产出的 JSON 必须被冻结的解析器接受（v1 白名单）。
        SceneData reparsed = SceneDataParser.parseOrThrow(SceneDataWriter.toJson(scene));
        assertEquals(SceneFormat.CURRENT_VERSION, reparsed.formatVersion());
        assertEquals(2, reparsed.steps().size());
        assertEquals(1, reparsed.elements().size());
    }

    @Test
    void nextStepIdIsUniqueAgainstExistingSteps() {
        SceneDraft draft = SceneDraft.from(seed());
        assertEquals("record.0", draft.nextStepId("record"));

        draft.addStep(SceneStep.builder().id("record.0").type(StepType.IDLE).build());
        assertEquals("record.1", draft.nextStepId("record"));

        draft.addStep(SceneStep.builder().id("record.5").type(StepType.IDLE).build());
        assertEquals("record.1", draft.nextStepId("record"));
        assertNotEquals("record.5", draft.nextStepId("record"));
    }

    @Test
    void addElementRejectsDuplicateId() {
        SceneDraft draft = SceneDraft.from(seed());
        assertThrows(IllegalArgumentException.class,
                () -> draft.addElement(SceneElement.of("section.layer.0", "section")));
    }

    @Test
    void replaceAndRemoveStepEditInPlace() {
        SceneDraft draft = SceneDraft.from(seed());
        draft.addStep(SceneStep.builder().id("a").type(StepType.IDLE).build());
        draft.addStep(SceneStep.builder().id("b").type(StepType.IDLE).build());
        assertEquals(List.of("build.layer.0", "a", "b"), stepIds(draft));

        draft.replaceStep(1, SceneStep.builder().id("a").type(StepType.CAMERA).duration(5).build());
        assertEquals(StepType.CAMERA, draft.steps().get(1).type());
        assertEquals(5, draft.steps().get(1).duration());

        draft.removeStep(0);
        assertEquals(List.of("a", "b"), stepIds(draft));
    }

    @Test
    void createStartsWithEmptyDraftForTarget() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");

        assertEquals("gtceu:test_machine", draft.target());
        assertEquals(Source.HAND, draft.source());
        assertTrue(draft.steps().isEmpty());
        assertTrue(draft.elements().isEmpty());
        assertFalse(SceneDataWriter.toJson(draft.toSceneData()).isBlank());
    }

    private static List<String> stepIds(SceneDraft draft) {
        return draft.steps().stream().map(SceneStep::id).toList();
    }
}
