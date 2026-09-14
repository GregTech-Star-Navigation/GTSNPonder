package com.gtsn.ponder.client;

import com.gtsn.lib.ui.client.ThemeFontMetrics;
import com.gtsn.lib.ui.layout.CrossAxisAlign;
import com.gtsn.lib.ui.layout.Direction;
import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.MainAxisAlign;
import com.gtsn.lib.ui.layout.Sizing;
import com.gtsn.lib.ui.screen.GtsnScreen;
import com.gtsn.lib.ui.theme.ThemeColorRole;
import com.gtsn.lib.ui.widget.ButtonWidget;
import com.gtsn.lib.ui.widget.CheckboxWidget;
import com.gtsn.lib.ui.widget.PanelWidget;
import com.gtsn.lib.ui.widget.ScrollPanelWidget;
import com.gtsn.lib.ui.widget.SpacerWidget;
import com.gtsn.lib.ui.widget.Stack;
import com.gtsn.lib.ui.widget.TextMetrics;
import com.gtsn.lib.ui.widget.TextWidget;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.editor.EditorSession;
import com.gtsn.ponder.editor.SceneDraft;
import com.gtsn.ponder.engine.director.CameraState;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneParams;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.presenter.ScenePlayback;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.ViewportWidget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 游戏内可视化编辑器（GTSN UI，ADR-0003 / ADR-0004）：左侧 3D 场景预览，右侧「边做边录」动作 +
 * 属性表单，底部保存 / 导出 / 热重载重放。
 *
 * <p>编辑对象是运行时<b>同一 DTO</b>：{@link EditorSession} 持有可变 {@link SceneDraft}，
 * {@link #scene()} 产出冻结 v1 {@link SceneData}；保存经 {@link SceneLibrary#saveAuthorScene} 写到
 * 可写作者目录并<b>热重载</b>，故编辑后的场景无需重启即可重放。</p>
 *
 * <p>「录制」打开后，动作按钮把动作录成受封闭 {@code StepType} 约束的步骤并即时应用到预览；
 * 属性表单可微调时长 / 目标 / 旁白键 / 相机（反 DSL：只写字段值，无表达式）。</p>
 *
 * <p>客户端专用类。</p>
 */
public final class SceneEditorScreen extends GtsnScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SIDE_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 18;
    private static final int STEP_NUDGE = 5;
    private static final int CAMERA_NUDGE = 15;
    private static final double DISTANCE_NUDGE = 1.0d;
    private static final double DEFAULT_YAW = 25.0d;
    private static final double DEFAULT_PITCH = -135.0d;
    private static final double DEFAULT_DISTANCE = 6.0d;

    private final StructureSource structure;
    private final EditorSession session;
    private final LdlibSceneViewport viewport;
    private final DummySceneWorld bridge;
    private final ScenePlayback playback;
    private final TextMetrics metrics;

    private final ViewportWidget viewportWidget;
    private final TextWidget statusLabel;
    private final EditorTextField idField;
    private final EditorTextField titleField;
    private final EditorTextField targetField;
    private final CheckboxWidget recordToggle;
    private final ButtonWidget showButton;
    private final ButtonWidget hideButton;
    private final ButtonWidget highlightButton;
    private final ButtonWidget narrateButton;
    private final ButtonWidget cameraButton;
    private final ButtonWidget prevStepButton;
    private final ButtonWidget nextStepButton;
    private final TextWidget stepLabel;
    private final ButtonWidget durationDownButton;
    private final ButtonWidget durationUpButton;
    private final TextWidget durationValue;
    private final ButtonWidget yawDownButton;
    private final ButtonWidget yawUpButton;
    private final TextWidget yawValue;
    private final ButtonWidget pitchDownButton;
    private final ButtonWidget pitchUpButton;
    private final TextWidget pitchValue;
    private final ButtonWidget distanceDownButton;
    private final ButtonWidget distanceUpButton;
    private final TextWidget distanceValue;
    private final EditorTextField narrationField;
    private final ButtonWidget saveButton;
    private final ButtonWidget exportButton;
    private final ButtonWidget reloadButton;
    private final ButtonWidget closeButton;

    private int selectedStepIndex;
    private int renderedFrames;

    public SceneEditorScreen(StructureSource structure, Level proxyLevel, EditorSession session) {
        super(Component.translatable(EditorKeys.TITLE), new PanelWidget().fill());
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.metrics = new ThemeFontMetrics(Minecraft.getInstance().font);

        this.viewport = LdlibSceneViewport.create(
                Objects.requireNonNull(proxyLevel, "proxyLevel must not be null"), structure);
        this.bridge = new DummySceneWorld(structure, session.snapshot(), viewport);
        this.playback = ScenePlayback.of(session.snapshot(), bridge);
        // 预览停在结构完全揭示的终态（编辑时看见完整结构），不自动播放。
        this.playback.seekFraction(1.0d);

        this.selectedStepIndex = session.draft().steps().isEmpty() ? -1 : session.draft().steps().size() - 1;

        PanelWidget root = root();
        root.node().params().padding(Insets.all(6)).gap(6);
        root.align(MainAxisAlign.START, CrossAxisAlign.STRETCH);

        Stack header = root.add(hstack());
        header.add(new TextWidget(localized(EditorKeys.TITLE), metrics).colorRole(ThemeColorRole.TEXT_STRONG));
        header.add(new SpacerWidget().weight(1));
        this.statusLabel = header.add(new TextWidget(localized(EditorKeys.STATUS_READY), metrics)
                .colorRole(ThemeColorRole.TEXT_MUTED));

        Stack main = root.add(new Stack(Direction.HORIZONTAL).fillWidth().weight(1).gap(6)
                .crossAxisAlign(CrossAxisAlign.STRETCH));
        PanelWidget viewportPanel = main.add(new PanelWidget()
                .background(ThemeColorRole.PANEL_BACKGROUND)
                .border(ThemeColorRole.PANEL_BORDER, 1)
                .weight(1));
        this.viewportWidget = viewportPanel.add(new ViewportWidget(viewport, viewport));
        viewportWidget.node().params().fill();

        ScrollPanelWidget side = main.add(new ScrollPanelWidget()
                .size(Sizing.fixed(SIDE_WIDTH), Sizing.fill()));
        Stack sideContent = side.add(new Stack(Direction.VERTICAL).fillWidth().gap(4).padding(Insets.all(4)));

        sideContent.add(sectionTitle(EditorKeys.SECTION_HEADER));
        this.idField = sideContent.add(new EditorTextField(nn(session.draft().id()), metrics, 128,
                value -> session.draft().id(blankToNull(value))).fillWidth());
        idField.node().params().height(Sizing.fixed(BUTTON_HEIGHT));
        this.titleField = sideContent.add(new EditorTextField(nn(session.draft().title()), metrics, 128,
                value -> session.draft().title(blankToNull(value))).fillWidth());
        titleField.node().params().height(Sizing.fixed(BUTTON_HEIGHT));
        this.targetField = sideContent.add(new EditorTextField(nn(session.draft().target()), metrics, 128,
                value -> session.draft().target(blankToNull(value))).fillWidth());
        targetField.node().params().height(Sizing.fixed(BUTTON_HEIGHT));

        sideContent.add(sectionTitle(EditorKeys.SECTION_RECORD));
        this.recordToggle = sideContent.add(new CheckboxWidget(localized(EditorKeys.RECORD), metrics,
                checked -> setStatus(checked ? EditorKeys.STATUS_RECORD_ON : EditorKeys.STATUS_RECORD_OFF)));

        Stack actionRow1 = sideContent.add(hstack());
        this.showButton = actionRow1.add(new ButtonWidget(localized(EditorKeys.ACTION_SHOW), metrics,
                this::onRecordShow).fixedSize(92, BUTTON_HEIGHT));
        this.hideButton = actionRow1.add(new ButtonWidget(localized(EditorKeys.ACTION_HIDE), metrics,
                this::onRecordHide).fixedSize(92, BUTTON_HEIGHT));
        Stack actionRow2 = sideContent.add(hstack());
        this.highlightButton = actionRow2.add(new ButtonWidget(localized(EditorKeys.ACTION_HIGHLIGHT), metrics,
                this::onRecordHighlight).fixedSize(126, BUTTON_HEIGHT));
        this.narrateButton = actionRow2.add(new ButtonWidget(localized(EditorKeys.ACTION_NARRATE), metrics,
                this::onRecordNarrate).fixedSize(126, BUTTON_HEIGHT));
        Stack actionRow3 = sideContent.add(hstack());
        this.cameraButton = actionRow3.add(new ButtonWidget(localized(EditorKeys.ACTION_CAMERA), metrics,
                this::onRecordCamera).fixedSize(126, BUTTON_HEIGHT));

        sideContent.add(sectionTitle(EditorKeys.SECTION_STEPS));
        Stack stepRow = sideContent.add(hstack());
        this.prevStepButton = stepRow.add(new ButtonWidget(localized(EditorKeys.STEP_PREV), metrics,
                this::onPreviousStep).fixedSize(36, BUTTON_HEIGHT));
        this.stepLabel = stepRow.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(BUTTON_HEIGHT)));
        this.nextStepButton = stepRow.add(new ButtonWidget(localized(EditorKeys.STEP_NEXT), metrics,
                this::onNextStep).fixedSize(36, BUTTON_HEIGHT));

        sideContent.add(sectionTitle(EditorKeys.SECTION_PROPERTIES));
        Stack durationRow = sideContent.add(hstack());
        durationRow.add(propertyName(EditorKeys.FIELD_DURATION));
        this.durationDownButton = durationRow.add(new ButtonWidget("-", metrics,
                () -> onDuration(-STEP_NUDGE)).fixedSize(20, BUTTON_HEIGHT));
        this.durationValue = durationRow.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(BUTTON_HEIGHT)));
        this.durationUpButton = durationRow.add(new ButtonWidget("+", metrics,
                () -> onDuration(STEP_NUDGE)).fixedSize(20, BUTTON_HEIGHT));

        Stack yawRow = sideContent.add(hstack());
        yawRow.add(propertyName(EditorKeys.FIELD_YAW));
        this.yawDownButton = yawRow.add(new ButtonWidget("-", metrics,
                () -> onCameraNudge(-CAMERA_NUDGE, 0, 0)).fixedSize(20, BUTTON_HEIGHT));
        this.yawValue = yawRow.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(BUTTON_HEIGHT)));
        this.yawUpButton = yawRow.add(new ButtonWidget("+", metrics,
                () -> onCameraNudge(CAMERA_NUDGE, 0, 0)).fixedSize(20, BUTTON_HEIGHT));

        Stack pitchRow = sideContent.add(hstack());
        pitchRow.add(propertyName(EditorKeys.FIELD_PITCH));
        this.pitchDownButton = pitchRow.add(new ButtonWidget("-", metrics,
                () -> onCameraNudge(0, -CAMERA_NUDGE, 0)).fixedSize(20, BUTTON_HEIGHT));
        this.pitchValue = pitchRow.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(BUTTON_HEIGHT)));
        this.pitchUpButton = pitchRow.add(new ButtonWidget("+", metrics,
                () -> onCameraNudge(0, CAMERA_NUDGE, 0)).fixedSize(20, BUTTON_HEIGHT));

        Stack distanceRow = sideContent.add(hstack());
        distanceRow.add(propertyName(EditorKeys.FIELD_DISTANCE));
        this.distanceDownButton = distanceRow.add(new ButtonWidget("-", metrics,
                () -> onCameraNudge(0, 0, (int) -DISTANCE_NUDGE)).fixedSize(20, BUTTON_HEIGHT));
        this.distanceValue = distanceRow.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(BUTTON_HEIGHT)));
        this.distanceUpButton = distanceRow.add(new ButtonWidget("+", metrics,
                () -> onCameraNudge(0, 0, (int) DISTANCE_NUDGE)).fixedSize(20, BUTTON_HEIGHT));

        sideContent.add(propertyName(EditorKeys.FIELD_NARRATION));
        this.narrationField = sideContent.add(new EditorTextField("", metrics, 160,
                this::onNarrationChanged).fillWidth());
        narrationField.node().params().height(Sizing.fixed(BUTTON_HEIGHT));

        Stack footer = root.add(hstack());
        this.saveButton = footer.add(new ButtonWidget(localized(EditorKeys.SAVE), metrics,
                this::onSave).fixedSize(60, BUTTON_HEIGHT));
        this.exportButton = footer.add(new ButtonWidget(localized(EditorKeys.EXPORT), metrics,
                this::onExport).fixedSize(72, BUTTON_HEIGHT));
        this.reloadButton = footer.add(new ButtonWidget(localized(EditorKeys.RELOAD), metrics,
                this::onReloadAndReplay).fixedSize(98, BUTTON_HEIGHT));
        footer.add(new SpacerWidget().weight(1));
        this.closeButton = footer.add(new ButtonWidget(localized(EditorKeys.CLOSE), metrics,
                this::onClose).fixedSize(60, BUTTON_HEIGHT));

        syncWidgets();
    }

    private PanelWidget root() {
        return (PanelWidget) host().root();
    }

    private static Stack hstack() {
        return new Stack(Direction.HORIZONTAL).fillWidth().gap(4).crossAxisAlign(CrossAxisAlign.CENTER);
    }

    private TextWidget sectionTitle(String key) {
        return new TextWidget(localized(key), metrics).colorRole(ThemeColorRole.TEXT_STRONG);
    }

    private TextWidget propertyName(String key) {
        return new TextWidget(localized(key), metrics).colorRole(ThemeColorRole.TEXT_MUTED)
                .size(Sizing.fixed(60), Sizing.fixed(BUTTON_HEIGHT));
    }

    private static String localized(String key) {
        return key == null ? "" : Component.translatable(key).getString();
    }

    private static String localized(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // --- 录制 ----------------------------------------------------------------

    private void onRecordShow() {
        if (!requireRecording()) {
            return;
        }
        String id = firstElementId();
        session.recorder().recordShowSection(id, 12);
        bridge.setSectionVisible(id, true);
        afterRecord();
    }

    private void onRecordHide() {
        if (!requireRecording()) {
            return;
        }
        String id = firstElementId();
        session.recorder().recordHideSection(id, 10);
        bridge.setSectionVisible(id, false);
        afterRecord();
    }

    private void onRecordHighlight() {
        if (!requireRecording()) {
            return;
        }
        String id = firstElementId();
        session.recorder().recordHighlight(id, 25);
        bridge.setHighlight(id, true);
        afterRecord();
    }

    private void onRecordNarrate() {
        if (!requireRecording()) {
            return;
        }
        session.recorder().recordNarration(EditorKeys.RECORD_NARRATION, 40);
        bridge.setNarration(EditorKeys.RECORD_NARRATION);
        afterRecord();
    }

    private void onRecordCamera() {
        if (!requireRecording()) {
            return;
        }
        String id = firstElementId();
        session.recorder().recordCamera(DEFAULT_YAW, DEFAULT_PITCH, DEFAULT_DISTANCE);
        bridge.setCamera(CameraState.of(id, DEFAULT_YAW, DEFAULT_PITCH, DEFAULT_DISTANCE));
        afterRecord();
    }

    private boolean requireRecording() {
        if (!recordToggle.checked()) {
            setStatus(EditorKeys.STATUS_RECORD_OFF);
            return false;
        }
        return true;
    }

    private void afterRecord() {
        selectedStepIndex = session.draft().steps().size() - 1;
        setStatus(EditorKeys.STATUS_RECORDED, session.recorder().recordedCount(),
                session.draft().steps().size());
        syncWidgets();
    }

    private String firstElementId() {
        SceneDraft draft = session.draft();
        if (!draft.elements().isEmpty()) {
            return draft.elements().get(0).id();
        }
        return SceneGenerator.ELEMENT_CONTROLLER;
    }

    // --- 属性表单 ------------------------------------------------------------

    private void onDuration(int delta) {
        SceneStep step = selectedStep();
        if (step == null) {
            return;
        }
        int duration = Math.max(0, step.duration() + delta);
        replaceSelected(copy(step, duration, null, null, null, null));
    }

    private void onCameraNudge(int dyaw, int dpitch, int dDistance) {
        SceneStep step = selectedStep();
        if (step == null) {
            return;
        }
        double yaw = SceneParams.number(step.params(), "yaw", DEFAULT_YAW) + dyaw;
        double pitch = SceneParams.number(step.params(), "pitch", DEFAULT_PITCH) + dpitch;
        double distance = Math.max(0.5d,
                SceneParams.number(step.params(), "distance", DEFAULT_DISTANCE) + dDistance);
        replaceSelected(copy(step, null, null, yaw, pitch, distance));
        bridge.setCamera(CameraState.of(firstElementId(), yaw, pitch, distance));
    }

    private void onNarrationChanged(String value) {
        SceneStep step = selectedStep();
        if (step == null) {
            return;
        }
        replaceSelected(copy(step, null, blankToNull(value), null, null, null));
    }

    private SceneStep selectedStep() {
        List<SceneStep> steps = session.draft().steps();
        if (selectedStepIndex < 0 || selectedStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(selectedStepIndex);
    }

    private void replaceSelected(SceneStep step) {
        session.draft().replaceStep(selectedStepIndex, step);
        syncWidgets();
    }

    private void onPreviousStep() {
        if (session.draft().steps().isEmpty()) {
            return;
        }
        selectedStepIndex = selectedStepIndex <= 0
                ? session.draft().steps().size() - 1 : selectedStepIndex - 1;
        syncWidgets();
    }

    private void onNextStep() {
        if (session.draft().steps().isEmpty()) {
            return;
        }
        selectedStepIndex = selectedStepIndex >= session.draft().steps().size() - 1
                ? 0 : selectedStepIndex + 1;
        syncWidgets();
    }

    /** 不可变复制并按需替换字段（{@link SceneStep} 无 toBuilder，此处集中处理）。 */
    private static SceneStep copy(SceneStep step, Integer duration, String narration,
            Double yaw, Double pitch, Double distance) {
        Map<String, Object> params = new LinkedHashMap<>(step.params());
        if (yaw != null) {
            params.put("yaw", yaw);
        }
        if (pitch != null) {
            params.put("pitch", pitch);
        }
        if (distance != null) {
            params.put("distance", distance);
        }
        return SceneStep.builder()
                .id(step.id())
                .type(step.type())
                .duration(duration != null ? duration : step.duration())
                .targets(step.targets())
                .params(params)
                .narration(narration != null ? narration : step.narration())
                .narrationArgs(step.narrationArgs())
                .keyframe(step.keyframe())
                .build();
    }

    // --- 保存 / 导出 / 热重载 ------------------------------------------------

    private void onSave() {
        try {
            Path file = session.save();
            setStatus(EditorKeys.STATUS_SAVED, file.toString());
            LOGGER.info("[GTSNPonder] editor saved draft ({} steps) -> {}",
                    session.draft().steps().size(), file);
        } catch (IOException failure) {
            setStatus(EditorKeys.STATUS_SAVE_FAILED, String.valueOf(failure.getMessage()));
            LOGGER.warn("[GTSNPonder] editor save failed", failure);
        }
    }

    private void onExport() {
        session.load(SceneGenerator.generate(structure));
        selectedStepIndex = session.draft().steps().isEmpty() ? -1 : session.draft().steps().size() - 1;
        idField.value(nn(session.draft().id()));
        titleField.value(nn(session.draft().title()));
        targetField.value(nn(session.draft().target()));
        setStatus(EditorKeys.STATUS_EXPORTED, nn(session.draft().target()));
        syncWidgets();
    }

    /** 保存 → 场景库热重载 → 用重载后的手作场景打开播放屏（无需重启即重放）。 */
    private void onReloadAndReplay() {
        try {
            Path file = session.save();
            SceneLibrary.get().reload();
            LOGGER.info("[GTSNPonder] editor hot-reloaded scene library after saving {}", file);
        } catch (IOException failure) {
            setStatus(EditorKeys.STATUS_SAVE_FAILED, String.valueOf(failure.getMessage()));
            return;
        }
        setStatus(EditorKeys.STATUS_SAVED, session.fileStem());
        PonderEntrypoints.openForTarget(nn(session.draft().target()));
    }

    // --- 同步 ----------------------------------------------------------------

    private void setStatus(String key, Object... args) {
        statusLabel.text(localized(key, args));
    }

    private void syncWidgets() {
        SceneStep step = selectedStep();
        int count = session.draft().steps().size();
        if (step == null) {
            stepLabel.text(localized(EditorKeys.STEP_NONE));
            durationValue.text("-");
            yawValue.text("-");
            pitchValue.text("-");
            distanceValue.text("-");
            if (!narrationField.isFocused()) {
                narrationField.value("");
            }
            return;
        }
        stepLabel.text((selectedStepIndex + 1) + " / " + count + "  " + step.type().jsonName());
        durationValue.text(Integer.toString(step.duration()));
        yawValue.text(format(SceneParams.number(step.params(), "yaw", 0.0d)));
        pitchValue.text(format(SceneParams.number(step.params(), "pitch", 0.0d)));
        distanceValue.text(format(SceneParams.number(step.params(), "distance", 0.0d)));
        if (!narrationField.isFocused()) {
            narrationField.value(nn(step.narration()));
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT,
                "%.1f", value);
    }

    /** 每客户端 tick 推进预览（若在播放）；由 {@code PonderClientEvents} 调用。 */
    public void advance() {
        if (playback.isPlaying()) {
            playback.tick(1.0d);
        }
    }

    @Override
    protected void init() {
        super.init();
        host().resize(width, height);
        LOGGER.info("[GTSNPonder] scene editor init: screen={}x{} viewport={} steps={}",
                width, height, viewportWidget.bounds(), session.draft().steps().size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        viewport.partialTick(partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderedFrames++;
        if (renderedFrames == 1 || renderedFrames % 120 == 0) {
            LOGGER.info("[GTSNPonder] scene editor frame={} steps={} selected={} visibleBlocks={}",
                    renderedFrames, session.draft().steps().size(), selectedStepIndex,
                    bridge.visibleBlockCount());
        }
    }

    // --- 自动测试 / 外部访问 -------------------------------------------------

    public EditorSession session() {
        return session;
    }

    public SceneDraft draft() {
        return session.draft();
    }

    /** 当前草稿的不可变 v1 快照。 */
    public SceneData scene() {
        return session.snapshot();
    }

    public StructureSource structure() {
        return structure;
    }

    public ScenePlayback playback() {
        return playback;
    }

    public DummySceneWorld bridge() {
        return bridge;
    }

    public TextWidget statusLabel() {
        return statusLabel;
    }

    public CheckboxWidget recordToggle() {
        return recordToggle;
    }

    public ButtonWidget showButton() {
        return showButton;
    }

    public ButtonWidget hideButton() {
        return hideButton;
    }

    public ButtonWidget highlightButton() {
        return highlightButton;
    }

    public ButtonWidget narrateButton() {
        return narrateButton;
    }

    public ButtonWidget cameraButton() {
        return cameraButton;
    }

    public ButtonWidget durationUpButton() {
        return durationUpButton;
    }

    public ButtonWidget durationDownButton() {
        return durationDownButton;
    }

    public ButtonWidget prevStepButton() {
        return prevStepButton;
    }

    public ButtonWidget nextStepButton() {
        return nextStepButton;
    }

    public ButtonWidget saveButton() {
        return saveButton;
    }

    public ButtonWidget exportButton() {
        return exportButton;
    }

    public ButtonWidget reloadButton() {
        return reloadButton;
    }

    public ButtonWidget closeButton() {
        return closeButton;
    }

    public int selectedStepIndex() {
        return selectedStepIndex;
    }

    public int renderedFrames() {
        return renderedFrames;
    }
}
