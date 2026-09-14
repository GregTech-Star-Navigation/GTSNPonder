package com.gtsn.ponder.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 观看进度持久化的行为测试：缺失 / 非法文件优雅退化，保存 → 读回往返稳定。
 */
class ProgressStoreTest {

    @Test
    void missingFileLoadsAsEmpty(@TempDir Path dir) {
        WatchedProgress progress = ProgressStore.load(dir.resolve("absent.json"));
        assertTrue(progress.keys().isEmpty());
    }

    @Test
    void corruptFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("corrupt.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        assertTrue(ProgressStore.load(file).keys().isEmpty());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.json");
        WatchedProgress progress = WatchedProgress.empty().with("b").with("a");

        ProgressStore.save(file, progress);

        assertTrue(Files.isRegularFile(file), "save must create the file");
        WatchedProgress loaded = ProgressStore.load(file);
        assertEquals(progress.keys(), loaded.keys());
    }

    @Test
    void saveCreatesMissingParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested").resolve("deeper").resolve("progress.json");
        ProgressStore.save(file, WatchedProgress.empty().with("x"));
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).isBlank());
    }
}
