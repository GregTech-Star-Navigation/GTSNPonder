package com.gtsn.ponder.engine.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 场景步骤：数据格式的原子，声明类型、时长、目标与字面量参数。
 *
 * <p>不可变值对象；{@code duration} 单位为 tick。{@code duration > 0} 即「阻塞」步骤（占据时间轴），
 * {@code duration == 0} 为瞬时非阻塞步骤（见 {@code StepBehaviors#isBlocking}）。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneStep {

    private final String id;
    private final StepType type;
    private final int duration;
    private final List<String> targets;
    private final Map<String, Object> params;
    private final String narration;
    private final List<String> narrationArgs;
    private final Map<String, Object> keyframe;

    private SceneStep(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.duration = builder.duration;
        this.targets = List.copyOf(builder.targets);
        this.params = SceneParams.immutableCopy(builder.params);
        this.narration = builder.narration;
        this.narrationArgs = List.copyOf(builder.narrationArgs);
        this.keyframe = SceneParams.immutableCopy(builder.keyframe);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public StepType type() {
        return type;
    }

    /** 时长（tick），恒 {@code >= 0}。 */
    public int duration() {
        return duration;
    }

    /** 引用的稳定元素 / 目标 ID 列表（不可变）。 */
    public List<String> targets() {
        return targets;
    }

    /** 不可变字面量参数映射。 */
    public Map<String, Object> params() {
        return params;
    }

    /** 旁白本地化键；可为 {@code null}。 */
    public String narration() {
        return narration;
    }

    /**
     * 旁白本地化键的模板参数（不可变、永不 {@code null}）：配合 {@link #narration()} 做
     * {@code Component.translatable(key, args)} 级别的插值（如机器名 / 结构尺寸 / 仓口数量），
     * 使同一模板产出机器特定文案。反 DSL：仅字面量字符串，无表达式。
     */
    public List<String> narrationArgs() {
        return narrationArgs;
    }

    /** 关键帧字面量（供视口插值，本工单不消费）；不可变，永不 {@code null}。 */
    public Map<String, Object> keyframe() {
        return keyframe;
    }

    public static final class Builder {
        private String id;
        private StepType type;
        private int duration;
        private final List<String> targets = new java.util.ArrayList<>();
        private final Map<String, Object> params = new java.util.LinkedHashMap<>();
        private String narration;
        private final List<String> narrationArgs = new java.util.ArrayList<>();
        private final Map<String, Object> keyframe = new java.util.LinkedHashMap<>();

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(StepType type) {
            this.type = type;
            return this;
        }

        public Builder duration(int duration) {
            this.duration = duration;
            return this;
        }

        public Builder targets(List<String> targets) {
            this.targets.clear();
            if (targets != null) {
                this.targets.addAll(targets);
            }
            return this;
        }

        public Builder addTarget(String target) {
            this.targets.add(target);
            return this;
        }

        public Builder param(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params.clear();
            if (params != null) {
                this.params.putAll(params);
            }
            return this;
        }

        public Builder narration(String narration) {
            this.narration = narration;
            return this;
        }

        public Builder narrationArgs(List<String> narrationArgs) {
            this.narrationArgs.clear();
            if (narrationArgs != null) {
                this.narrationArgs.addAll(narrationArgs);
            }
            return this;
        }

        public Builder addNarrationArg(String argument) {
            this.narrationArgs.add(Objects.requireNonNull(argument, "narration argument must not be null"));
            return this;
        }

        public Builder keyframe(Map<String, Object> keyframe) {
            this.keyframe.clear();
            if (keyframe != null) {
                this.keyframe.putAll(keyframe);
            }
            return this;
        }

        public SceneStep build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("step id must be non-blank");
            }
            Objects.requireNonNull(type, "step type must not be null");
            if (duration < 0) {
                throw new IllegalArgumentException("step duration must be >= 0, got " + duration);
            }
            return new SceneStep(this);
        }
    }

    @Override
    public String toString() {
        return "SceneStep[id=" + id + ", type=" + type + ", duration=" + duration
                + ", targets=" + targets + ", params=" + params + ", narration=" + narration + "]";
    }
}
