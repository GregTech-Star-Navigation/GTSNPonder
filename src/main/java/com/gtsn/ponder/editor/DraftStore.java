package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneDataWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 草稿存储：把编辑器草稿（{@link SceneData}）写入一个<b>可写目录</b>（运行时作者目录）并读回。
 *
 * <p>写出走 {@link SceneDataWriter}（规范化 JSON），读回走
 * {@link SceneDataParser}（冻结的 v1 白名单）——与场景库扫描同一格式，故「保存 → 热重载」闭环
 * 无需重启。写入仅依赖 {@link Path}，不含 Minecraft 类型（纯 Java，可 headless 单测）。</p>
 */
public final class DraftStore {

    private static final String JSON_SUFFIX = ".json";

    private DraftStore() {
    }

    /**
     * 把场景写入 {@code root/<stem>.json}（自动创建目录）；返回写入的文件路径。
     *
     * @throws IOException 目录创建或写入失败
     */
    public static Path write(Path root, SceneData scene, String stem) throws IOException {
        Files.createDirectories(root);
        Path file = root.resolve(sanitizeStem(stem) + JSON_SUFFIX);
        Files.writeString(file, SceneDataWriter.toJson(scene), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * 读取单个草稿文件：缺失返回空；内容非法（非 JSON / 违反 v1 白名单）抛
     * {@link com.gtsn.ponder.engine.model.SceneFormatException}。
     */
    public static Optional<SceneData> read(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return Optional.of(SceneDataParser.parseOrThrow(json));
    }

    /** 列出目录中的草稿文件（{@code *.json}，按文件名排序）；目录缺失返回空列表。 */
    public static List<Path> list(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JSON_SUFFIX))
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareTo(right.getFileName().toString()))
                    .toList();
        } catch (IOException failure) {
            return List.of();
        }
    }

    /** 文件名词干：小写，非 {@code [a-z0-9_]} 的字符替换为 {@code _}；空白回退 {@code "scene"}。 */
    public static String sanitizeStem(String raw) {
        if (raw == null) {
            return "scene";
        }
        String trimmed = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "scene";
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (char character : trimmed.toCharArray()) {
            boolean alphanumeric = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9');
            builder.append(alphanumeric || character == '_' ? character : '_');
        }
        return builder.toString();
    }
}
