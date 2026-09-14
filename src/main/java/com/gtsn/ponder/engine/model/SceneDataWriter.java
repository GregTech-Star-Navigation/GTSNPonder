package com.gtsn.ponder.engine.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * {@link SceneData} → 声明式场景 JSON 的规范化序列化（{@link SceneDataParser} 的逆操作）。
 *
 * <p><b>确定性</b>：输出对同一 {@link SceneData} 恒定——字段顺序由写入顺序固定；参数映射按键名
 * 排序（{@link TreeMap}）后再写出，因此不依赖底层 {@code Map} 实现的迭代顺序。这使自动生成器可用
 * 「两次生成字节相等 JSON」作为可重生成产物的证据，并支持把自动场景导出为草稿（dump）。</p>
 *
 * <p>与解析器互为逆：{@code write(parse(write(scene)))} 字节相等（往返稳定）。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneDataWriter {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private SceneDataWriter() {
    }

    /** 序列化为规范化 JSON 文本（含换行；对同一输入恒定）。 */
    public static String toJson(SceneData scene) {
        Objects.requireNonNull(scene, "scene must not be null");

        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", scene.formatVersion());
        addString(root, "id", scene.id());
        addString(root, "title", scene.title());
        addString(root, "target", scene.target());
        addString(root, "variant", scene.variant());
        if (scene.source() != null) {
            root.addProperty("source", scene.source().jsonName());
        }
        addString(root, "generatorVersion", scene.generatorVersion());

        JsonArray elements = new JsonArray();
        for (SceneElement element : scene.elements()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", element.id());
            addString(object, "kind", element.kind());
            addParams(object, "params", element.params());
            elements.add(object);
        }
        root.add("elements", elements);

        JsonArray steps = new JsonArray();
        for (SceneStep step : scene.steps()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", step.id());
            object.addProperty("type", step.type().jsonName());
            object.addProperty("duration", step.duration());
            JsonArray targets = new JsonArray();
            for (String target : step.targets()) {
                targets.add(target);
            }
            object.add("targets", targets);
            addString(object, "narration", step.narration());
            if (!step.narrationArgs().isEmpty()) {
                JsonArray narrationArgs = new JsonArray();
                for (String argument : step.narrationArgs()) {
                    narrationArgs.add(argument);
                }
                object.add("narrationArgs", narrationArgs);
            }
            addParams(object, "params", step.params());
            addParams(object, "keyframe", step.keyframe());
            steps.add(object);
        }
        root.add("steps", steps);

        return GSON.toJson(root);
    }

    private static void addString(JsonObject owner, String key, String value) {
        if (value != null) {
            owner.addProperty(key, value);
        }
    }

    /** 参数映射按键名排序写出（规范化）；空映射省略。{@code params} 与 {@code keyframe} 各有独立键。 */
    private static void addParams(JsonObject owner, String key, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Object> entry : new TreeMap<>(params).entrySet()) {
            object.add(entry.getKey(), toJsonElement(entry.getValue()));
        }
        owner.add(key, object);
    }

    /** 反 DSL：仅字面量（解析器已拒绝嵌套结构，故此处只处理基元）。 */
    private static JsonElement toJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
