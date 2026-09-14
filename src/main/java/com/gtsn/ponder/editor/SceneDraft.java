package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 可变场景草稿：游戏内编辑器操作<b>与运行时同一个 DTO</b>（ADR-0003 单一事实源）。
 *
 * <p>草稿只是 {@link SceneData} 的可变镜像——头部字段 + 稳定 ID 的 {@code elements[]} + 有序
 * {@code steps[]}；{@link #toSceneData()} 以冻结的 {@link SceneFormat#CURRENT_VERSION} 产出
 * 不可变场景，再由 {@code SceneDataWriter} 写出为合法 v1 JSON。编辑器<b>绝不</b>维护平行模型。</p>
 *
 * <p>草稿被视为<b>手作（{@link Source#HAND}）</b>：从自动生成基线导出后编辑即成为手作覆盖
 * （ADR-0003 场景粒度覆盖语义）。</p>
 *
 * <p>纯 Java、零 MC 依赖（可在 headless 单测中构建与断言）。</p>
 */
public final class SceneDraft {

    /** 步骤 / 元素 ID 前缀的默认回退。 */
    private static final String DEFAULT_ID_PREFIX = "step";

    private String id;
    private String title;
    private String target;
    private String variant;
    private Source source;
    private String generatorVersion;

    private final List<SceneElement> elements = new ArrayList<>();
    private final List<SceneStep> steps = new ArrayList<>();

    private SceneDraft() {
        this.source = Source.HAND;
    }

    /**
     * 从已有场景（如自动生成产物）建立草稿：复制头部与内容，并把 {@link Source#AUTO} 转为
     * {@link Source#HAND}（导出自动场景为草稿后由作者编辑，属手作覆盖）。
     */
    public static SceneDraft from(SceneData scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        SceneDraft draft = new SceneDraft();
        draft.copyFrom(scene);
        return draft;
    }

    /** 新建空草稿，绑定目标（如 {@code gtceu:coke_oven}）。 */
    public static SceneDraft create(String target) {
        SceneDraft draft = new SceneDraft();
        draft.target = target;
        return draft;
    }

    /** 用另一场景整体替换草稿内容（头部 + 元素 + 步骤）。 */
    public void copyFrom(SceneData scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        this.id = scene.id();
        this.title = scene.title();
        this.target = scene.target();
        this.variant = scene.variant();
        this.source = scene.source() == Source.AUTO ? Source.HAND : scene.source();
        this.generatorVersion = scene.generatorVersion();
        this.elements.clear();
        this.elements.addAll(scene.elements());
        this.steps.clear();
        this.steps.addAll(scene.steps());
    }

    // --- 头部 ---------------------------------------------------------------

    public String id() {
        return id;
    }

    public SceneDraft id(String id) {
        this.id = id;
        return this;
    }

    public String title() {
        return title;
    }

    public SceneDraft title(String title) {
        this.title = title;
        return this;
    }

    public String target() {
        return target;
    }

    public SceneDraft target(String target) {
        this.target = target;
        return this;
    }

    public String variant() {
        return variant;
    }

    public SceneDraft variant(String variant) {
        this.variant = variant;
        return this;
    }

    public Source source() {
        return source;
    }

    public SceneDraft source(Source source) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        return this;
    }

    public String generatorVersion() {
        return generatorVersion;
    }

    public SceneDraft generatorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
        return this;
    }

    // --- 元素 ---------------------------------------------------------------

    public List<SceneElement> elements() {
        return List.copyOf(elements);
    }

    /** 追加元素；{@code id} 与已有元素重复时拒绝。 */
    public void addElement(SceneElement element) {
        Objects.requireNonNull(element, "element must not be null");
        if (element(element.id()).isPresent()) {
            throw new IllegalArgumentException("duplicate element id: " + element.id());
        }
        elements.add(element);
    }

    /** 便捷追加元素（稳定 ID / 类别 / 字面量参数）。 */
    public void addElement(String elementId, String kind, Map<String, Object> params) {
        addElement(SceneElement.of(elementId, kind, params));
    }

    public boolean removeElement(String elementId) {
        return elements.removeIf(element -> element.id().equals(elementId));
    }

    public Optional<SceneElement> element(String elementId) {
        if (elementId == null) {
            return Optional.empty();
        }
        return elements.stream().filter(element -> element.id().equals(elementId)).findFirst();
    }

    /** 元素 ID 是否已被占用。 */
    public boolean hasElement(String elementId) {
        return element(elementId).isPresent();
    }

    // --- 步骤 ---------------------------------------------------------------

    public List<SceneStep> steps() {
        return List.copyOf(steps);
    }

    public int stepCount() {
        return steps.size();
    }

    public void addStep(SceneStep step) {
        Objects.requireNonNull(step, "step must not be null");
        if (hasStep(step.id())) {
            throw new IllegalArgumentException("duplicate step id: " + step.id());
        }
        steps.add(step);
    }

    /** 原地替换第 {@code index} 步；越界抛 {@link IndexOutOfBoundsException}。 */
    public void replaceStep(int index, SceneStep step) {
        Objects.requireNonNull(step, "step must not be null");
        steps.set(index, step);
    }

    public SceneStep removeStep(int index) {
        return steps.remove(index);
    }

    public Optional<SceneStep> step(String stepId) {
        if (stepId == null) {
            return Optional.empty();
        }
        return steps.stream().filter(step -> step.id().equals(stepId)).findFirst();
    }

    public boolean hasStep(String stepId) {
        return step(stepId).isPresent();
    }

    /**
     * 生成对现有步骤唯一的 ID：{@code <prefix>.<n>}，{@code n} 从 0 起取第一个未占用者。
     * 前缀空白时用 {@value #DEFAULT_ID_PREFIX}。
     */
    public String nextStepId(String prefix) {
        String effective = prefix == null || prefix.isBlank() ? DEFAULT_ID_PREFIX : prefix;
        int index = 0;
        String candidate = effective + "." + index;
        while (hasStep(candidate)) {
            index++;
            candidate = effective + "." + index;
        }
        return candidate;
    }

    // --- 产出 ---------------------------------------------------------------

    /** 以冻结的 v1 版本产出不可变场景（编辑器写出的唯一形态）。 */
    public SceneData toSceneData() {
        return SceneData.builder()
                .formatVersion(SceneFormat.CURRENT_VERSION)
                .id(id)
                .title(title)
                .target(target)
                .variant(variant)
                .source(source == null ? Source.HAND : source)
                .generatorVersion(generatorVersion)
                .elements(elements)
                .steps(steps)
                .build();
    }
}
