package com.gtsn.ponder.engine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 场景数据（声明式 JSON DTO 的运行时形态，单一事实源）。
 *
 * <p>头部携带 {@code formatVersion}（强制）与 {@code generatorVersion}（可重生成产物标识），
 * 加稳定字符串 ID 的 {@code elements[]} 与有序 {@code steps[]}。</p>
 *
 * <p>不可变值对象；纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneData {

    private final int formatVersion;
    private final String id;
    private final String title;
    private final String target;
    private final String variant;
    private final Source source;
    private final String generatorVersion;
    private final List<SceneElement> elements;
    private final List<SceneStep> steps;

    private SceneData(Builder builder) {
        this.formatVersion = builder.formatVersion;
        this.id = builder.id;
        this.title = builder.title;
        this.target = builder.target;
        this.variant = builder.variant;
        this.source = builder.source;
        this.generatorVersion = builder.generatorVersion;
        this.elements = List.copyOf(builder.elements);
        this.steps = List.copyOf(builder.steps);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int formatVersion() {
        return formatVersion;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String target() {
        return target;
    }

    public String variant() {
        return variant;
    }

    public Source source() {
        return source;
    }

    public String generatorVersion() {
        return generatorVersion;
    }

    public List<SceneElement> elements() {
        return elements;
    }

    public List<SceneStep> steps() {
        return steps;
    }

    public Optional<SceneElement> element(String elementId) {
        if (elementId == null) {
            return Optional.empty();
        }
        return elements.stream().filter(element -> element.id().equals(elementId)).findFirst();
    }

    public static final class Builder {
        private int formatVersion;
        private String id;
        private String title;
        private String target;
        private String variant;
        private Source source = Source.AUTO;
        private String generatorVersion;
        private final List<SceneElement> elements = new ArrayList<>();
        private final List<SceneStep> steps = new ArrayList<>();

        private Builder() {
        }

        public Builder formatVersion(int formatVersion) {
            this.formatVersion = formatVersion;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder variant(String variant) {
            this.variant = variant;
            return this;
        }

        public Builder source(Source source) {
            this.source = Objects.requireNonNull(source, "source must not be null");
            return this;
        }

        public Builder generatorVersion(String generatorVersion) {
            this.generatorVersion = generatorVersion;
            return this;
        }

        public Builder elements(List<SceneElement> elements) {
            this.elements.clear();
            if (elements != null) {
                this.elements.addAll(elements);
            }
            return this;
        }

        public Builder addElement(SceneElement element) {
            this.elements.add(Objects.requireNonNull(element, "element must not be null"));
            return this;
        }

        public Builder steps(List<SceneStep> steps) {
            this.steps.clear();
            if (steps != null) {
                this.steps.addAll(steps);
            }
            return this;
        }

        public Builder addStep(SceneStep step) {
            this.steps.add(Objects.requireNonNull(step, "step must not be null"));
            return this;
        }

        public SceneData build() {
            return new SceneData(this);
        }
    }
}
