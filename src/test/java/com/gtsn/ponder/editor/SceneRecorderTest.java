package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SceneRecorder} 的外部可观察行为：把「在场景世界里做过的动作」按顺序录制成
 * 受封闭 {@link StepType} 枚举约束的 {@link SceneStep}，且能读出最后一步 / 计数。
 */
class SceneRecorderTest {

    @Test
    void recordsShowSectionAsOrderedStep() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");
        SceneRecorder recorder = new SceneRecorder(draft);

        recorder.recordShowSection("section.layer.0", 12);

        assertEquals(1, draft.steps().size());
        SceneStep step = draft.steps().get(0);
        assertEquals(StepType.SHOW_SECTION, step.type());
        assertEquals(List.of("section.layer.0"), step.targets());
        assertEquals(12, step.duration());
        assertFalse(step.id().isBlank());
        assertEquals(step, recorder.lastRecorded());
        assertEquals(1, recorder.recordedCount());
    }

    @Test
    void recordsNarrationWithArgs() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");
        SceneRecorder recorder = new SceneRecorder(draft);

        recorder.recordNarration("ponder.gtsnponder.editor.record.narration", List.of("A", "B"), 40);

        SceneStep step = draft.steps().get(0);
        assertEquals(StepType.TEXT, step.type());
        assertEquals("ponder.gtsnponder.editor.record.narration", step.narration());
        assertEquals(List.of("A", "B"), step.narrationArgs());
        assertEquals(40, step.duration());
    }

    @Test
    void recordsHighlightOutlineCameraAndHide() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");
        SceneRecorder recorder = new SceneRecorder(draft);

        recorder.recordHideSection("section.layer.0", 10)
                .recordHighlight("controller", 25)
                .recordOutline("hatch.ITEM_INPUT", 20)
                .recordCamera(25.0d, -135.0d, 6.0d);

        List<SceneStep> steps = draft.steps();
        assertEquals(4, steps.size());
        assertEquals(StepType.HIDE_SECTION, steps.get(0).type());
        assertEquals(StepType.HIGHLIGHT, steps.get(1).type());
        assertEquals(StepType.OUTLINE, steps.get(2).type());
        assertEquals(StepType.CAMERA, steps.get(3).type());
        assertEquals(25.0d, steps.get(3).params().get("yaw"));
        assertEquals(-135.0d, steps.get(3).params().get("pitch"));
        assertEquals(6.0d, steps.get(3).params().get("distance"));
    }

    @Test
    void recordedIdsAreUnique() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");
        SceneRecorder recorder = new SceneRecorder(draft);

        recorder.recordShowSection("a", 1).recordShowSection("b", 1).recordShowSection("c", 1);

        long distinct = draft.steps().stream().map(SceneStep::id).distinct().count();
        assertEquals(3, distinct);
    }

    @Test
    void rejectsNegativeDuration() {
        SceneDraft draft = SceneDraft.create("gtceu:test_machine");
        SceneRecorder recorder = new SceneRecorder(draft);

        assertThrows(IllegalArgumentException.class, () -> recorder.recordShowSection("a", -1));
        assertTrue(draft.steps().isEmpty());
    }
}
