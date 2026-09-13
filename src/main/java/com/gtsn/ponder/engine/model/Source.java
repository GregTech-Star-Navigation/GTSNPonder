package com.gtsn.ponder.engine.model;

/**
 * 场景来源（头部 {@code source} 字段）：{@code auto} / {@code hand} / {@code mixed}。
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。未知取值按 {@code fallback} 处理，不抛异常
 * （仅 {@code formatVersion} 是强制校验项）。</p>
 */
public enum Source {
    AUTO("auto"),
    HAND("hand"),
    MIXED("mixed");

    private final String jsonName;

    Source(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    /**
     * 按 JSON 字面量解析；{@code null} 或未知取值返回 {@code fallback}。
     */
    public static Source fromJson(String value, Source fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        for (Source source : values()) {
            if (source.jsonName.equalsIgnoreCase(trimmed)) {
                return source;
            }
        }
        return fallback;
    }
}
