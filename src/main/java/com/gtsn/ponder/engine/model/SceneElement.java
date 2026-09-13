package com.gtsn.ponder.engine.model;

import java.util.Map;
import java.util.Objects;

/**
 * 场景元素（分段 / 锚点）：以<b>稳定字符串 ID</b> 命名，供步骤的 {@code targets[]} 引用。
 *
 * <p>不可变值对象；纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneElement {

    private final String id;
    private final String kind;
    private final Map<String, Object> params;

    private SceneElement(String id, String kind, Map<String, Object> params) {
        this.id = id;
        this.kind = kind;
        this.params = params;
    }

    public static SceneElement of(String id, String kind, Map<String, Object> params) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("element id must be non-blank");
        }
        return new SceneElement(id, kind, SceneParams.immutableCopy(params));
    }

    public static SceneElement of(String id, String kind) {
        return of(id, kind, Map.of());
    }

    public String id() {
        return id;
    }

    /** 元素类别（如 {@code section} / {@code anchor}）；可为 {@code null}。 */
    public String kind() {
        return kind;
    }

    /** 不可变字面量参数映射。 */
    public Map<String, Object> params() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SceneElement other)) {
            return false;
        }
        return id.equals(other.id) && Objects.equals(kind, other.kind) && params.equals(other.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, params);
    }

    @Override
    public String toString() {
        return "SceneElement[id=" + id + ", kind=" + kind + ", params=" + params + "]";
    }
}
