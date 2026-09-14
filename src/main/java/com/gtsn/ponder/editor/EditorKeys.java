package com.gtsn.ponder.editor;

/**
 * 游戏内编辑器所用的<b>本地化键</b>（单一事实源）：界面标签与「边做边录」默认旁白键集中于此，
 * 供编辑器屏幕与 datagen 的 lang provider 共用（全部文案走本地化键，中英双语）。
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class EditorKeys {

    private EditorKeys() {
    }

    /** 屏幕标题。 */
    public static final String TITLE = "ponder.gtsnponder.editor.title";

    /** 编辑器的按钮 / 状态 / 字段键。 */
    public static final String RECORD = "ponder.gtsnponder.editor.record";
    public static final String ACTION_SHOW = "ponder.gtsnponder.editor.action.show";
    public static final String ACTION_HIDE = "ponder.gtsnponder.editor.action.hide";
    public static final String ACTION_HIGHLIGHT = "ponder.gtsnponder.editor.action.highlight";
    public static final String ACTION_NARRATE = "ponder.gtsnponder.editor.action.narrate";
    public static final String ACTION_CAMERA = "ponder.gtsnponder.editor.action.camera";

    public static final String SECTION_HEADER = "ponder.gtsnponder.editor.section.header";
    public static final String SECTION_RECORD = "ponder.gtsnponder.editor.section.record";
    public static final String SECTION_STEPS = "ponder.gtsnponder.editor.section.steps";
    public static final String SECTION_PROPERTIES = "ponder.gtsnponder.editor.section.properties";

    public static final String FIELD_ID = "ponder.gtsnponder.editor.field.id";
    public static final String FIELD_TITLE = "ponder.gtsnponder.editor.field.title";
    public static final String FIELD_TARGET = "ponder.gtsnponder.editor.field.target";
    public static final String FIELD_DURATION = "ponder.gtsnponder.editor.field.duration";
    public static final String FIELD_NARRATION = "ponder.gtsnponder.editor.field.narration";
    public static final String FIELD_YAW = "ponder.gtsnponder.editor.field.yaw";
    public static final String FIELD_PITCH = "ponder.gtsnponder.editor.field.pitch";
    public static final String FIELD_DISTANCE = "ponder.gtsnponder.editor.field.distance";

    public static final String STEP_PREV = "ponder.gtsnponder.editor.step.prev";
    public static final String STEP_NEXT = "ponder.gtsnponder.editor.step.next";
    public static final String STEP_NONE = "ponder.gtsnponder.editor.step.none";

    public static final String SAVE = "ponder.gtsnponder.editor.save";
    public static final String EXPORT = "ponder.gtsnponder.editor.export";
    public static final String RELOAD = "ponder.gtsnponder.editor.reload";
    public static final String CLOSE = "ponder.gtsnponder.editor.close";

    public static final String STATUS_READY = "ponder.gtsnponder.editor.status.ready";
    public static final String STATUS_SAVED = "ponder.gtsnponder.editor.status.saved";
    public static final String STATUS_SAVE_FAILED = "ponder.gtsnponder.editor.status.save_failed";
    public static final String STATUS_EXPORTED = "ponder.gtsnponder.editor.status.exported";
    public static final String STATUS_RECORD_ON = "ponder.gtsnponder.editor.status.record_on";
    public static final String STATUS_RECORD_OFF = "ponder.gtsnponder.editor.status.record_off";
    public static final String STATUS_RECORDED = "ponder.gtsnponder.editor.status.recorded";

    /** 编辑器门控关闭时的提示（命令 / 入口）。 */
    public static final String GATED = "ponder.gtsnponder.editor.gated";

    /** 「录制旁白」动作写入的默认旁白键（作者可随后用属性表单改成任意键）。 */
    public static final String RECORD_NARRATION = "ponder.gtsnponder.editor.record.narration";
}
