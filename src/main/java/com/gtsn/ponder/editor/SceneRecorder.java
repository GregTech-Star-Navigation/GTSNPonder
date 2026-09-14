package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.StepType;

import java.util.List;
import java.util.Objects;

/**
 * 「边做边录」记录器：把作者在场景世界里执行的动作录制成有序 {@link SceneStep}，追加进草稿。
 *
 * <p>每个录制动作只映射到<b>封闭 {@link StepType} 枚举</b>中的一个类型（反 DSL 铁律：无表达式 /
 * 条件 / 循环 / 算术；参数只接受字面量）。录制只产出数据，不执行任何脚本语义。</p>
 *
 * <p>纯 Java、零 MC 依赖（可在 headless 单测中驱动）。</p>
 */
public final class SceneRecorder {

    private final SceneDraft draft;
    private int recorded;

    public SceneRecorder(SceneDraft draft) {
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
    }

    public SceneDraft draft() {
        return draft;
    }

    /** 录制「显示分段」。 */
    public SceneRecorder recordShowSection(String sectionId, int duration) {
        return append(StepType.SHOW_SECTION, List.of(sectionId), duration, "show");
    }

    /** 录制「隐藏分段」。 */
    public SceneRecorder recordHideSection(String sectionId, int duration) {
        return append(StepType.HIDE_SECTION, List.of(sectionId), duration, "hide");
    }

    /** 录制「高亮」（金色线框盒）。 */
    public SceneRecorder recordHighlight(String targetId, int duration) {
        return append(StepType.HIGHLIGHT, List.of(targetId), duration, "highlight");
    }

    /** 录制「轮廓」（蓝色线框盒）。 */
    public SceneRecorder recordOutline(String targetId, int duration) {
        return append(StepType.OUTLINE, List.of(targetId), duration, "outline");
    }

    /** 录制「旁白字幕」（本地化键）。 */
    public SceneRecorder recordNarration(String narrationKey, int duration) {
        return recordNarration(narrationKey, List.of(), duration);
    }

    /** 录制「旁白字幕」（本地化键 + 字面量模板参数）。 */
    public SceneRecorder recordNarration(String narrationKey, List<String> narrationArgs, int duration) {
        Objects.requireNonNull(narrationKey, "narration key must not be null");
        SceneStep step = SceneStep.builder()
                .id(draft.nextStepId("text"))
                .type(StepType.TEXT)
                .duration(requireDuration(duration))
                .narration(narrationKey)
                .narrationArgs(narrationArgs)
                .build();
        draft.addStep(step);
        recorded++;
        return this;
    }

    /** 录制「相机」（瞬时步骤，进度不阻塞）。 */
    public SceneRecorder recordCamera(double yaw, double pitch, double distance) {
        SceneStep step = SceneStep.builder()
                .id(draft.nextStepId("camera"))
                .type(StepType.CAMERA)
                .duration(0)
                .param("yaw", yaw)
                .param("pitch", pitch)
                .param("distance", distance)
                .build();
        draft.addStep(step);
        recorded++;
        return this;
    }

    /** 最近一次录制的步骤；尚未录制任何步骤时为 {@code null}。 */
    public SceneStep lastRecorded() {
        List<SceneStep> steps = draft.steps();
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    /** 已录制的步骤数（本次会话追加的步骤数）。 */
    public int recordedCount() {
        return recorded;
    }

    private SceneRecorder append(StepType type, List<String> targets, int duration, String prefix) {
        SceneStep step = SceneStep.builder()
                .id(draft.nextStepId(prefix))
                .type(type)
                .duration(requireDuration(duration))
                .targets(targets)
                .build();
        draft.addStep(step);
        recorded++;
        return this;
    }

    private static int requireDuration(int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("step duration must be >= 0, got " + duration);
        }
        return duration;
    }
}
