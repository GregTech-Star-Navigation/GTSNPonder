package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EditorSession} 的外部可观察行为：把「录制 → 保存 → 重载 → 重放」这条编辑器主回路
 * 收敛为纯逻辑，可在 headless 单测中断言（客户端自动测试只驱动 UI 并复用本回路）。
 */
class EditorSessionTest {

    private static SceneData generatedBaseline() {
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
                        .duration(12).targets(java.util.List.of("section.layer.0")).build())
                .build();
    }

    @Test
    void recordsEditsSavesAndReloadsTheEditedScene(@TempDir Path root) throws IOException {
        EditorSession session = EditorSession.startingFrom(generatedBaseline(), root);
        int baseline = session.draft().steps().size();

        session.recorder().recordShowSection("section.layer.0", 12);
        session.recorder().recordNarration("ponder.gtsnponder.editor.record.narration", 40);

        Path file = session.save();

        assertTrue(Files.isRegularFile(file), "saved draft must exist: " + file);
        assertEquals(baseline + 2, session.draft().steps().size());

        // 热重载：从磁盘重新解析，必须是合法 v1 且包含录制的旁白（可重放）。
        SceneData reloaded = DraftStore.read(file).orElseThrow();
        assertEquals(SceneFormat.CURRENT_VERSION, reloaded.formatVersion());
        assertEquals("gtceu:test_machine", reloaded.target());
        assertEquals(Source.HAND, reloaded.source());
        assertEquals(baseline + 2, reloaded.steps().size());
        assertTrue(reloaded.steps().stream().anyMatch(step ->
                        "ponder.gtsnponder.editor.record.narration".equals(step.narration())),
                "the recorded narration must survive save + reload");
    }

    @Test
    void loadReplacesDraftFromAnotherScene(@TempDir Path root) {
        EditorSession session = EditorSession.startingFrom(generatedBaseline(), root);

        session.load(SceneDraft.create("gtceu:other").toSceneData());

        assertEquals("gtceu:other", session.draft().target());
        assertTrue(session.draft().steps().isEmpty());
    }

    @Test
    void fileStemDerivesFromTarget() {
        assertEquals("gtceu_test_machine", EditorSession.fileStemFor(generatedBaseline()));
        assertNotNull(EditorSession.startingFrom(generatedBaseline(), Path.of(".")).fileStem());
    }

    @Test
    void exportKeepsHandSourceAndEditorTag() {
        EditorSession session = EditorSession.startingFrom(generatedBaseline(), Path.of("."));

        SceneData snapshot = session.snapshot();

        assertEquals(Source.HAND, snapshot.source(),
                "an exported auto baseline becomes a hand draft authors can edit");
        assertEquals("gtceu:test_machine", snapshot.target());
    }
}
