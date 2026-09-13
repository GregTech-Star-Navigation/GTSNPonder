package com.gtsn.ponder.engine.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 声明式场景 JSON → {@link SceneData} 的解析与显式校验。
 *
 * <p>使用 GSON（MC 无关、随 MC 类路径提供）。校验规则（Ticket #3）：</p>
 * <ul>
 *   <li>{@code formatVersion} 强制：缺失 / 非整数 / {@code < 1} → {@link SceneFormatException}；</li>
 *   <li>未知步骤 {@code type} → 跳过该步骤并在 {@link SceneParseResult#warnings()} 中告警（前向兼容）；</li>
 *   <li>未知顶层 / 元素 / 步骤键 → 忽略。</li>
 * </ul>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneDataParser {

    public static final int CURRENT_FORMAT_VERSION = 1;

    private SceneDataParser() {
    }

    /**
     * 解析并校验场景 JSON。
     *
     * @throws SceneFormatException 当 JSON 结构非法或 {@code formatVersion} 缺失 / 非法
     */
    public static SceneParseResult parse(String json) {
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(json);
        } catch (JsonSyntaxException exception) {
            throw new SceneFormatException("scene is not valid JSON: " + exception.getMessage(), exception);
        }
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new SceneFormatException("scene root must be a JSON object");
        }
        JsonObject root = rootElement.getAsJsonObject();

        int formatVersion = readFormatVersion(root);
        List<String> warnings = new ArrayList<>();

        List<SceneElement> elements = readElements(root);
        List<SceneStep> steps = readSteps(root, warnings);

        SceneData scene = SceneData.builder()
                .formatVersion(formatVersion)
                .id(optionalString(root, "id"))
                .title(optionalString(root, "title"))
                .target(optionalString(root, "target"))
                .variant(optionalString(root, "variant"))
                .source(Source.fromJson(optionalString(root, "source"), Source.AUTO))
                .generatorVersion(optionalString(root, "generatorVersion"))
                .elements(elements)
                .steps(steps)
                .build();

        return new SceneParseResult(scene, warnings);
    }

    /** 便捷入口：解析并直接返回 {@link SceneData}（忽略告警）。 */
    public static SceneData parseOrThrow(String json) {
        return parse(json).scene();
    }

    private static int readFormatVersion(JsonObject root) {
        if (!root.has("formatVersion")) {
            throw new SceneFormatException("scene is missing mandatory 'formatVersion'");
        }
        JsonElement value = root.get("formatVersion");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new SceneFormatException("'formatVersion' must be an integer number, got: " + value);
        }
        double raw = value.getAsDouble();
        if (raw != Math.rint(raw)) {
            throw new SceneFormatException("'formatVersion' must be an integer number, got: " + raw);
        }
        int version = (int) raw;
        if (version < 1) {
            throw new SceneFormatException("'formatVersion' must be >= 1, got: " + version);
        }
        return version;
    }

    private static List<SceneElement> readElements(JsonObject root) {
        List<SceneElement> elements = new ArrayList<>();
        if (!root.has("elements")) {
            return elements;
        }
        JsonElement raw = root.get("elements");
        if (!raw.isJsonArray()) {
            throw new SceneFormatException("'elements' must be a JSON array");
        }
        JsonArray array = raw.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonObject()) {
                throw new SceneFormatException("each element must be a JSON object");
            }
            JsonObject element = item.getAsJsonObject();
            String id = optionalString(element, "id");
            if (id == null || id.isBlank()) {
                throw new SceneFormatException("scene element is missing a non-blank 'id'");
            }
            elements.add(SceneElement.of(id, optionalString(element, "kind"), readParams(element.get("params"))));
        }
        return elements;
    }

    private static List<SceneStep> readSteps(JsonObject root, List<String> warnings) {
        List<SceneStep> steps = new ArrayList<>();
        if (!root.has("steps")) {
            return steps;
        }
        JsonElement raw = root.get("steps");
        if (!raw.isJsonArray()) {
            throw new SceneFormatException("'steps' must be a JSON array");
        }
        JsonArray array = raw.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonObject()) {
                throw new SceneFormatException("each step must be a JSON object");
            }
            JsonObject step = item.getAsJsonObject();
            String stepId = optionalString(step, "id");
            if (stepId == null || stepId.isBlank()) {
                throw new SceneFormatException("scene step is missing a non-blank 'id'");
            }
            String typeName = optionalString(step, "type");
            Optional<StepType> type = StepType.fromJson(typeName);
            if (type.isEmpty()) {
                warnings.add("skipping step '" + stepId + "': unknown step type '" + typeName + "'");
                continue;
            }
            steps.add(SceneStep.builder()
                    .id(stepId)
                    .type(type.get())
                    .duration(readDuration(step, stepId))
                    .targets(readStringArray(step, "targets", stepId))
                    .params(readParams(step.get("params")))
                    .narration(optionalString(step, "narration"))
                    .keyframe(readParams(step.get("keyframe")))
                    .build());
        }
        return steps;
    }

    private static int readDuration(JsonObject step, String stepId) {
        if (!step.has("duration")) {
            return 0;
        }
        JsonElement value = step.get("duration");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new SceneFormatException("step '" + stepId + "' has a non-numeric 'duration'");
        }
        double raw = value.getAsDouble();
        if (raw != Math.rint(raw) || raw < 0) {
            throw new SceneFormatException("step '" + stepId + "' has an invalid 'duration': " + raw);
        }
        return (int) raw;
    }

    private static List<String> readStringArray(JsonObject owner, String key, String stepId) {
        List<String> values = new ArrayList<>();
        if (!owner.has(key)) {
            return values;
        }
        JsonElement raw = owner.get(key);
        if (!raw.isJsonArray()) {
            throw new SceneFormatException("step '" + stepId + "' field '" + key + "' must be a JSON array");
        }
        for (JsonElement item : raw.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new SceneFormatException("step '" + stepId + "' field '" + key + "' must contain strings");
            }
            values.add(item.getAsString());
        }
        return values;
    }

    private static Map<String, Object> readParams(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) {
            return Map.of();
        }
        if (!raw.isJsonObject()) {
            throw new SceneFormatException("'params' / 'keyframe' must be a JSON object");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
            params.put(entry.getKey(), toLiteral(entry.getValue()));
        }
        return params;
    }

    /**
     * 反 DSL：仅接受字面量。嵌套对象 / 数组以原始 JSON 文本保存，不参与任何求值。
     */
    private static Object toLiteral(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsDouble();
            }
            return primitive.getAsString();
        }
        return element.toString();
    }

    private static String optionalString(JsonObject owner, String key) {
        if (!owner.has(key)) {
            return null;
        }
        JsonElement value = owner.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new SceneFormatException("'" + key + "' must be a string");
        }
        return value.getAsString();
    }
}
