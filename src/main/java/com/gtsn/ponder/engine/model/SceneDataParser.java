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
 * <p>使用 GSON（MC 无关、随 MC 类路径提供）。校验规则（Ticket #3 / #8）：</p>
 * <ul>
 *   <li>{@code formatVersion} 强制：缺失 / 非整数 / {@code < 1} / 高于当前版本
 *       → {@link SceneFormatException}；合法旧版本先经 {@link SceneMigrations} 逐级迁移；</li>
 *   <li>schema 白名单（反 DSL 铁律）：根 / 元素 / 步骤必须是对象，{@code targets} / {@code narrationArgs}
 *       必须是字符串数组，{@code duration} 必须是非负整数，{@code params} / {@code keyframe} 必须
 *       是对象且<b>仅含字面量</b>（嵌套对象 / 数组被拒绝）；</li>
 *   <li>未知步骤 {@code type} → 跳过该步骤并在 {@link SceneParseResult#warnings()} 中告警（前向兼容，
 *       且在其字段做形状校验之前跳过，使未来步骤类型不会拖垮旧解析器）；</li>
 *   <li>未知顶层 / 元素 / 步骤键 → 忽略。</li>
 * </ul>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneDataParser {

    /** 当前格式版本（单一事实源见 {@link SceneFormat}；保留此别名以兼容既有引用）。 */
    public static final int CURRENT_FORMAT_VERSION = SceneFormat.CURRENT_VERSION;

    private SceneDataParser() {
    }

    /**
     * 解析并校验场景 JSON。
     *
     * @throws SceneFormatException 当 JSON 结构非法、{@code formatVersion} 缺失 / 非法 / 高于当前版本，
     *         或节点形状不在 schema 白名单内
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
        // 版本迁移：旧文档逐级升级到当前版本；未来版本在此清晰拒绝。之后一律按当前结构解析。
        JsonObject migrated = SceneMigrations.migrateToCurrent(root, formatVersion);
        List<String> warnings = new ArrayList<>();

        List<SceneElement> elements = readElements(migrated);
        List<SceneStep> steps = readSteps(migrated, warnings);

        SceneData scene = SceneData.builder()
                .formatVersion(SceneFormat.CURRENT_VERSION)
                .id(optionalString(migrated, "id"))
                .title(optionalString(migrated, "title"))
                .target(optionalString(migrated, "target"))
                .variant(optionalString(migrated, "variant"))
                .source(Source.fromJson(optionalString(migrated, "source"), Source.AUTO))
                .generatorVersion(optionalString(migrated, "generatorVersion"))
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
            elements.add(SceneElement.of(id, optionalString(element, "kind"),
                    readParams(element.get("params"), "scene element '" + id + "' field 'params'")));
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
                    .params(readParams(step.get("params"), "step '" + stepId + "' field 'params'"))
                    .narration(optionalString(step, "narration"))
                    .narrationArgs(readStringArray(step, "narrationArgs", stepId))
                    .keyframe(readParams(step.get("keyframe"), "step '" + stepId + "' field 'keyframe'"))
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

    private static Map<String, Object> readParams(JsonElement raw, String context) {
        if (raw == null || raw.isJsonNull()) {
            return Map.of();
        }
        if (!raw.isJsonObject()) {
            throw new SceneFormatException(context + " must be a JSON object");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
            params.put(entry.getKey(), toLiteral(entry.getKey(), entry.getValue(), context));
        }
        return params;
    }

    /**
     * 反 DSL：字面量参数<b>只</b>接受 {@code string} / {@code number} / {@code boolean} / {@code null}；
     * 嵌套对象 / 数组被拒绝（schema 白名单，格式不得退化为编程语言）。
     */
    private static Object toLiteral(String key, JsonElement element, String context) {
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
        throw new SceneFormatException(context + " value '" + key
                + "' must be a literal (string / number / boolean / null); nested objects and arrays are not allowed");
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
