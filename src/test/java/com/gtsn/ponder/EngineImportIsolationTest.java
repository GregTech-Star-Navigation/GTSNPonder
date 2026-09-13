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
 * 导演核心纪律检查（约束 §3「导演核心不得 import net.minecraft」）。
 *
 * <p>扫描 {@code src/main/java/com/gtsn/ponder/engine} 下所有 {@code .java}，任何
 * {@code net.minecraft} import 即失败。以可失败的断言自证纯 Java 内核。</p>
 */
class EngineImportIsolationTest {

    private static final Path ENGINE_ROOT =
            Path.of("src", "main", "java", "com", "gtsn", "ponder", "engine");
    /** 仅匹配 import 语句；注释 / 文档中的措辞不算越界。 */
    private static final Pattern MINECRAFT_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?net\\.minecraft\\b");

    @Test
    void directorCoreHasNoMinecraftImports() throws IOException {
        assertTrue(Files.isDirectory(ENGINE_ROOT),
                "engine source root not found: " + ENGINE_ROOT.toAbsolutePath());

        List<Path> javaSources;
        try (Stream<Path> walked = Files.walk(ENGINE_ROOT)) {
            javaSources = walked
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        assertFalse(javaSources.isEmpty(),
                "no java sources found under " + ENGINE_ROOT.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        for (Path source : javaSources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (MINECRAFT_IMPORT.matcher(text).find()) {
                violations.add(source.toString());
            }
        }

        assertTrue(violations.isEmpty(),
                "director core imported net.minecraft (must stay pure Java): " + violations);
    }
}
