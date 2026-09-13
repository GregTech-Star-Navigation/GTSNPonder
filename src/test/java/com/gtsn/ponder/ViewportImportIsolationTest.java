package com.gtsn.ponder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯逻辑包纪律检查：{@code com.gtsn.ponder.viewport}（视口契约 / 相机状态机 / 几何）与
 * {@code com.gtsn.ponder.structure}（中立结构 DTO）必须保持纯 Java——不得 import
 * {@code net.minecraft}，也不得 import {@code com.gregtechceu}（GT 访问只属于
 * {@code com.gtsn.ponder.gt}）。以可失败断言自证可 headless 单测。
 */
class ViewportImportIsolationTest {

    private static final List<Path> PURE_PACKAGES = List.of(
            Path.of("src", "main", "java", "com", "gtsn", "ponder", "viewport"),
            Path.of("src", "main", "java", "com", "gtsn", "ponder", "structure"));

    private static final Pattern MINECRAFT_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?net\\.minecraft\\b");
    private static final Pattern GREGTECH_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?com\\.gregtechceu\\b");

    @Test
    void viewportAndStructurePackagesStayPure() throws IOException {
        List<String> sourceFiles = new ArrayList<>();
        for (Path root : PURE_PACKAGES) {
            assertTrue(Files.isDirectory(root), "pure package not found: " + root.toAbsolutePath());
            try (Stream<Path> walked = Files.walk(root)) {
                walked.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> sourceFiles.add(path.toString()));
            }
        }
        assertFalse(sourceFiles.isEmpty(), "no java sources found in the pure packages");

        List<String> minecraftViolations = new ArrayList<>();
        List<String> gtViolations = new ArrayList<>();
        for (String source : sourceFiles) {
            String text = Files.readString(Path.of(source), StandardCharsets.UTF_8);
            if (MINECRAFT_IMPORT.matcher(text).find()) {
                minecraftViolations.add(source);
            }
            if (GREGTECH_IMPORT.matcher(text).find()) {
                gtViolations.add(source);
            }
        }

        assertTrue(minecraftViolations.isEmpty(),
                "viewport/structure imported net.minecraft (must stay pure Java): " + minecraftViolations);
        assertTrue(gtViolations.isEmpty(),
                "viewport/structure imported com.gregtechceu (GT access belongs to com.gtsn.ponder.gt): "
                        + gtViolations);
    }
}
