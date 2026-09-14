package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneFormatException;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DraftStore} 的外部可观察行为：把草稿 {@link SceneData} 写入一个可写目录（作者目录），
 * 再读回为合法 v1；列出目录中的 JSON；非法 JSON 清晰拒绝。文件 I/O 走 {@link SceneDataWriter}
 * 与 {@link com.gtsn.ponder.engine.model.SceneDataParser}（与运行时同一 DTO）。
 */
class DraftStoreTest {

    private static SceneData scene() {
        return SceneData.builder()
                .formatVersion(SceneFormat.CURRENT_VERSION)
                .id("gtsnponder:hand_gtceu_test_machine")
                .title("ponder.gtsnponder.editor.title")
                .target("gtceu:test_machine")
                .variant("default")
                .source(Source.HAND)
                .addElement(SceneElement.of("section.layer.0", "section", Map.of("selector", "layer", "y", 0.0d)))
                .addStep(SceneStep.builder().id("text.0").type(StepType.TEXT).duration(40)
                        .narration("ponder.gtsnponder.editor.record.narration").build())
                .build();
    }

    @Test
    void writesThenReadsBackValidV1(@TempDir Path root) throws IOException {
        Path file = DraftStore.write(root, scene(), "gtceu_test_machine");

        assertTrue(Files.isRegularFile(file), "draft file must exist: " + file);
        assertTrue(file.toString().endsWith(".json"));
        assertEquals(SceneDataWriter.toJson(scene()), Files.readString(file, StandardCharsets.UTF_8));

        SceneData read = DraftStore.read(file).orElseThrow();
        assertEquals(SceneFormat.CURRENT_VERSION, read.formatVersion());
        assertEquals("gtceu:test_machine", read.target());
        assertEquals(Source.HAND, read.source());
        assertEquals(1, read.steps().size());
        assertEquals("ponder.gtsnponder.editor.record.narration", read.steps().get(0).narration());
    }

    @Test
    void listsOnlyJsonFilesSorted(@TempDir Path root) throws IOException {
        DraftStore.write(root, scene(), "b_machine");
        DraftStore.write(root, scene(), "a_machine");
        Files.writeString(root.resolve("notes.txt"), "ignore me", StandardCharsets.UTF_8);

        List<Path> files = DraftStore.list(root);

        assertEquals(List.of("a_machine.json", "b_machine.json"),
                files.stream().map(path -> path.getFileName().toString()).toList());
    }

    @Test
    void missingDirectoryListsEmpty(@TempDir Path root) {
        assertTrue(DraftStore.list(root.resolve("absent")).isEmpty());
    }

    @Test
    void invalidJsonIsRejectedClearly(@TempDir Path root) throws IOException {
        Path file = root.resolve("broken.json");
        Files.writeString(file, "{ not valid", StandardCharsets.UTF_8);

        assertThrows(SceneFormatException.class, () -> DraftStore.read(file));
    }

    @Test
    void sanitizesStemForFilesystem() {
        assertEquals("gtceu_test_machine", DraftStore.sanitizeStem("gtceu:test_machine"));
        assertEquals("a_b", DraftStore.sanitizeStem("a/b"));
        assertEquals("scene", DraftStore.sanitizeStem("  "));
    }
}
