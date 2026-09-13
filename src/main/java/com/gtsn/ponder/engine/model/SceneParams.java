package com.gtsn.ponder.engine.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字面量参数映射的读取工具（反 DSL：仅字面量，无表达式 / 算术）。
 *
 * <p>解析器把 JSON 基元转换为 {@code String} / {@code Double} / {@code Boolean} / {@code null}，
 * 嵌套结构以原始 JSON 文本保存（不参与求值）。本类提供不可变拷贝与带默认值的类型化读取。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneParams {

    private SceneParams() {
    }

    /** 返回不可变拷贝；{@code null} / 空 → 空映射。 */
    public static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static String string(Map<String, Object> params, String key, String fallback) {
        Object value = params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static boolean bool(Map<String, Object> params, String key, boolean fallback) {
        Object value = params.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0.0d;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return fallback;
    }

    public static double number(Map<String, Object> params, String key, double fallback) {
        Object value = params.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
