package com.gtsn.ponder.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 观看进度的持久化（纯 Java，零 MC）：写出 / 读回 {@code gtsnponder-progress.json}。
 *
 * <p>缺省存储位置由客户端包装层决定：{@code <gameDir>/}{@value #FILE_NAME}
 * （见 {@code com.gtsn.ponder.client.PonderProgress}）。</p>
 *
 * <p><b>稳健性</b>：文件缺失 / 空白 / 非法 JSON / 结构不符一律返回空进度（绝不抛出）；
 * 写出为按键排序的规范化 JSON，故同一进度字节相等（可 diff）。</p>
 */
public final class ProgressStore {

    /** 进度文件名（相对 gameDir）。 */
    public static final String FILE_NAME = "gtsnponder-progress.json";
    /** 进度文件格式版本。 */
    public static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProgressStore() {
    }

    /** 读回进度；任何缺失 / 非法内容都退化为空进度。 */
    public static WatchedProgress load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return WatchedProgress.empty();
        }
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return WatchedProgress.empty();
        }
        if (json.isBlank()) {
            return WatchedProgress.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return WatchedProgress.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement watched = root.get("watched");
            if (watched == null || !watched.isJsonArray()) {
                return WatchedProgress.empty();
            }
            List<String> keys = new ArrayList<>();
            for (JsonElement element : watched.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    keys.add(element.getAsString());
                }
            }
            return WatchedProgress.of(keys);
        } catch (RuntimeException failure) {
            return WatchedProgress.empty();
        }
    }

    /** 写出进度（自动创建父目录）；写出为规范化 JSON。 */
    public static void save(Path file, WatchedProgress progress) throws IOException {
        if (file == null) {
            throw new IOException("progress file must not be null");
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", CURRENT_VERSION);
        JsonArray watched = new JsonArray();
        for (String key : (progress == null ? WatchedProgress.empty() : progress).keys()) {
            watched.add(key);
        }
        root.add("watched", watched);
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
