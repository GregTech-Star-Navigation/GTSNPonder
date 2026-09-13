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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0005 的自动化纪律检查（外部可观察行为）：{@code com.gregtechceu} 只能出现在
 * 适配包 {@code com.gtsn.ponder.gt}。扫描 {@code src/main/java} 下所有 {@code .java}，
 * 任何越界引用即失败。当前无生产 GT 引用，故零违规即通过。
 */
class GtImportIsolationTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path ADAPTER_PACKAGE_ROOT =
            MAIN_SOURCE_ROOT.resolve(Path.of("com", "gtsn", "ponder", "gt")).normalize();
    /** 仅匹配 import 语句（约束的措辞即「import com.gregtechceu」）；注释 / 文档提及不算越界。 */
    private static final Pattern GT_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?com\\.gregtechceu\\b");

    @Test
    void gtReferencesAreConfinedToAdapterPackage() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCE_ROOT),
                "main source root not found: " + MAIN_SOURCE_ROOT.toAbsolutePath());

        List<Path> javaSources;
        try (Stream<Path> walked = Files.walk(MAIN_SOURCE_ROOT)) {
            javaSources = walked
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        assertTrue(!javaSources.isEmpty(),
                "no java sources found under " + MAIN_SOURCE_ROOT.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        for (Path source : javaSources) {
            if (source.normalize().startsWith(ADAPTER_PACKAGE_ROOT)) {
                continue;
            }
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (GT_IMPORT.matcher(text).find()) {
                violations.add(source.toString());
            }
        }

        assertTrue(violations.isEmpty(),
                "com.gregtechceu referenced outside com.gtsn.ponder.gt: " + violations);
    }
}
