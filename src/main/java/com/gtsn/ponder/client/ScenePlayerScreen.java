package com.gtsn.ponder.client;

import com.gtsn.lib.ui.client.ThemeFontMetrics;
import com.gtsn.lib.ui.layout.CrossAxisAlign;
import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.MainAxisAlign;
import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.layout.Sizing;
import com.gtsn.lib.ui.screen.GtsnScreen;
import com.gtsn.lib.ui.theme.ThemeColorRole;
import com.gtsn.lib.ui.widget.ButtonWidget;
import com.gtsn.lib.ui.widget.PanelWidget;
import com.gtsn.lib.ui.widget.ProgressBarWidget;
import com.gtsn.lib.ui.widget.SpacerWidget;
import com.gtsn.lib.ui.widget.Stack;
import com.gtsn.lib.ui.widget.TextMetrics;
import com.gtsn.lib.ui.widget.TextWidget;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.bridge.BlockInfoResolver;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.SceneVariants;
import com.gtsn.ponder.gt.GtBlockInfo;
import com.gtsn.ponder.presenter.NarrationLocalization;
import com.gtsn.ponder.presenter.ScenePlayback;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.ViewportWidget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 鎬濈储鍦烘櫙鎾斁灞忥紙Presenter锛孏TSN UI锛夛細鍦?GTSNLib {@link GtsnScreen} 涓祵鍏?LDLib 3D 瑙嗗彛锛? * 骞跺彔鏀炬梺鐧芥銆佹帶鍒舵潯锛堟殏鍋?/ 涓婁竴姝?/ 涓嬩竴姝?/ 閲嶆挱锛変笌鍙?seek 鐨勮繘搴︽潯銆? *
 * <p>缁戝畾锛氬睆骞曞彧渚濊禆绾€昏緫 {@link ScenePlayback}锛堟椂闂磋酱锛変笌 {@link DummySceneWorld}锛堜笘鐣屾ˉ锛夛紱
 * 姣忓鎴风 tick 鐢?{@code PonderClientEvents} 璋冪敤 {@link #advance()} 鎺ㄨ繘瀵兼紨锛屽苟鍒锋柊缁戝畾鎺т欢銆? * 杩涘害鏉＄偣鍑?/ 鎷栨嫿 鈫?{@link ScenePlayback#seekFraction(double)}銆傛墍鏈夋梺鐧芥枃妗堣蛋鏈湴鍖栭敭銆?/p>
 *
 * <p>瀹㈡埛绔笓鐢ㄧ被锛堝紩鐢?{@link GuiGraphics} 涓?LDLib 瑙嗗彛锛夈€?/p>
 */
public final class ScenePlayerScreen extends GtsnScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 灞忓箷鍥涘懆鐣欑櫧銆?*/
    public static final int SCREEN_PADDING = 8;
    private static final int NARRATION_LINES = 3;
    private static final int BUTTON_HEIGHT = 18;
    private static final String DEFAULT_TITLE_KEY = "ponder.gtsnponder.player.title";

    /** 常驻颜色图例（金色 = 控制器，蓝色 = 仓口 / 总线）的本地化键（单一事实源见 {@link GeneratedKeys}）。 */
    private static final String LEGEND_TITLE_KEY = GeneratedKeys.LEGEND_TITLE;
    private static final String LEGEND_CONTROLLER_KEY = GeneratedKeys.LEGEND_CONTROLLER;
    private static final String LEGEND_HATCH_KEY = GeneratedKeys.LEGEND_HATCH;
    private static final String LEGEND_MODULE_SLOT_KEY = GeneratedKeys.LEGEND_MODULE_SLOT;
    private static final int LEGEND_SWATCH = 8;
    private static final int LEGEND_PAD = 4;
    private static final int LEGEND_LINE = 10;
    private static final int LEGEND_ROWS = 4;
    private static final int LEGEND_MARGIN = 6;

    private final StructureSource structure;
    private final SceneData scene;
    private final LdlibSceneViewport viewport;
    private final DummySceneWorld bridge;
    private final ScenePlayback playback;

    private final TextMetrics metrics;
    private final ViewportWidget viewportWidget;
    private final TextWidget narrationText;
    private final TextWidget stepLabel;
    private final ProgressBarWidget progressBar;
    private final ButtonWidget prevButton;
    private final ButtonWidget playPauseButton;
    private final ButtonWidget nextButton;
    private final ButtonWidget replayButton;

    private String lastNarrationKey;
    private int lastNarrationArgsHash;
    private boolean lastPlaying;
    private int lastStepIndex = Integer.MIN_VALUE;
    private boolean seeking;
    private int renderedFrames;

    /** 变体选择（工单 #21 反馈 2）：目标 id 与全部变体描述；≤1 时不显示按钮。 */
    private final String target;
    private final List<SceneVariants.Spec> variants;
    private final int variantIndex;
    private final ButtonWidget variantButton;

    /** 方块名覆盖层（工单 #21 反馈 3）：最近指针位置、按下 / 拖拽标记与当前选中单元。 */
    private double pointerX = Double.NaN;
    private double pointerY = Double.NaN;
    private boolean viewportPress;
    private boolean viewportDrag;
    private BlockInfoResolver.BlockInfo selectedBlock;
    private double selectedAtX;
    private double selectedAtY;

    public ScenePlayerScreen(SceneData scene, StructureSource structure, Level proxyLevel) {
        this(scene, structure, proxyLevel, null, List.of(), 0);
    }

    /**
     * 变体感知构造（工单 #21 反馈 2）：{@code variants} 为同一目标的全部变体描述（按
     * {@link SceneVariants.Spec} 顺序），{@code variantIndex} 为当前展示的变体下标；>1 时控制条多出
     * 一个「变体」按钮，点击循环切换到下一个变体（重新打开本屏）。目标无变体时传空列表。
     */
    public ScenePlayerScreen(SceneData scene, StructureSource structure, Level proxyLevel,
            String target, List<SceneVariants.Spec> variants, int variantIndex) {
        super(Component.translatable(scene.title() != null ? scene.title() : DEFAULT_TITLE_KEY),
                new PanelWidget().fill());
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.target = target;
        this.variants = List.copyOf(variants == null ? List.of() : variants);
        this.variantIndex = Math.max(0, Math.min(variantIndex, Math.max(0, this.variants.size() - 1)));
        this.viewport = LdlibSceneViewport.create(proxyLevel, structure);
        this.bridge = new DummySceneWorld(structure, scene, viewport);
        this.playback = ScenePlayback.of(scene, bridge);
        this.metrics = new ThemeFontMetrics(Minecraft.getInstance().font);
        // 打开播放屏即视为「已看」：图鉴目录据此渲染进度（幂等、随即持久化）。
        PonderProgress.get().markWatched(scene);

        PanelWidget root = root();
        root.node().params().padding(Insets.all(SCREEN_PADDING)).gap(6);
        root.align(MainAxisAlign.START, CrossAxisAlign.STRETCH);

        Stack header = root.add(Stack.horizontal().fillWidth().gap(8).crossAxisAlign(CrossAxisAlign.CENTER));
        header.add(new TextWidget(localized(scene.title() != null ? scene.title() : DEFAULT_TITLE_KEY), metrics)
                .colorRole(ThemeColorRole.TEXT_STRONG));
        header.add(new SpacerWidget().weight(1));
        this.stepLabel = header.add(new TextWidget("", metrics).colorRole(ThemeColorRole.TEXT_MUTED));

        PanelWidget viewportPanel = root.add(new PanelWidget()
                .background(ThemeColorRole.PANEL_BACKGROUND)
                .border(ThemeColorRole.PANEL_BORDER, 1)
                .fillWidth()
                .weight(1));
        this.viewportWidget = viewportPanel.add(new ViewportWidget(viewport, viewport));
        viewportWidget.node().params().fill();

        PanelWidget narrationPanel = root.add(new PanelWidget()
                .background(ThemeColorRole.PANEL_BACKGROUND)
                .border(ThemeColorRole.PANEL_BORDER, 1)
                .padding(Insets.all(6))
                .fillWidth());
        narrationPanel.node().params().height(Sizing.fixed(metrics.lineHeight() * NARRATION_LINES + 14));
        this.narrationText = narrationPanel.add(new TextWidget("", metrics)
                .wrap(560)
                .lineSpacing(2)
                .colorRole(ThemeColorRole.TEXT));

        this.progressBar = root.add(new ProgressBarWidget()
                .range(0.0d, 1.0d)
                .value(0.0d)
                .gradient(true)
                .label(progress -> Math.round(progress * 100.0d) + "%")
                .size(Sizing.fill(), Sizing.fixed(16)));

        Stack controls = root.add(Stack.horizontal().fillWidth().gap(6).crossAxisAlign(CrossAxisAlign.CENTER));
        this.prevButton = controls.add(new ButtonWidget(localized("ponder.gtsnponder.control.prev"), metrics,
                this::onPrevious).fixedSize(48, BUTTON_HEIGHT));
        this.playPauseButton = controls.add(new ButtonWidget(localized("ponder.gtsnponder.control.play"), metrics,
                this::onTogglePlay).fixedSize(64, BUTTON_HEIGHT));
        this.nextButton = controls.add(new ButtonWidget(localized("ponder.gtsnponder.control.next"), metrics,
                this::onNext).fixedSize(48, BUTTON_HEIGHT));
        this.replayButton = controls.add(new ButtonWidget(localized("ponder.gtsnponder.control.replay"), metrics,
                this::onReplay).fixedSize(64, BUTTON_HEIGHT));
        this.variantButton = this.variants.size() > 1
                ? controls.add(new ButtonWidget(variantLabel(), metrics, this::onNextVariant)
                        .fixedSize(132, BUTTON_HEIGHT))
                : null;
        controls.add(new SpacerWidget().weight(1));
        controls.add(new TextWidget(localized("ponder.gtsnponder.player.seekhint"), metrics)
                .colorRole(ThemeColorRole.TEXT_MUTED));

        playback.play();
        syncWidgets();
    }

    private PanelWidget root() {
        return (PanelWidget) host().root();
    }

    private static String localized(String key) {
        return key == null ? "" : Component.translatable(key).getString();
    }

    /** 本地化并把生成器等注入的模板参数插值进键文案（机器特定旁白）。 */
    private static String localized(String key, java.util.List<String> args) {
        if (key == null) {
            return "";
        }
        if (args == null || args.isEmpty()) {
            return Component.translatable(key).getString();
        }
        java.util.List<java.util.List<NarrationLocalization.Part>> resolved =
                NarrationLocalization.resolveArgs(args, Language.getInstance()::has);
        Object[] substituents = new Object[resolved.size()];
        for (int index = 0; index < resolved.size(); index++) {
            substituents[index] = componentOf(resolved.get(index));
        }
        return Component.translatable(key, substituents).getString();
    }

    /**
     * 把解析后的旁白参数片段拼成一个组件（工单 #16 缺陷 A1/A2）：可翻译片段按当前语言的机器标题 /
     * 角色名渲染，字面量片段原样渲染，从而旁白不再露出原始注册 id 与枚举名。
     */
    private static Component componentOf(java.util.List<NarrationLocalization.Part> parts) {
        MutableComponent component = Component.empty();
        for (NarrationLocalization.Part part : parts) {
            component.append(part.translatable()
                    ? Component.translatable(part.value())
                    : Component.literal(part.value()));
        }
        return component;
    }

    /** 姣忓鎴风 tick 鎺ㄨ繘瀵兼紨骞跺埛鏂版帶浠剁粦瀹氾紙鐢?{@code PonderClientEvents} 璋冪敤锛夈€?*/
    public void advance() {
        if (playback.isPlaying()) {
            playback.tick(1.0d);
        }
        syncWidgets();
    }

    /** 鎶婂婕旂姸鎬佸埛鏂板埌鏃佺櫧 / 杩涘害 / 鎸夐挳 / 姝ラ鏍囩锛堜粎鍦ㄥ彉鍖栨椂鏀瑰瓧绗︿覆锛屽噺灏戞棤璋撻噸寤猴級銆?*/
    private void syncWidgets() {
        String narrationKey = playback.narrationKey();
        java.util.List<String> narrationArgs = playback.narrationArgs();
        int narrationArgsHash = narrationArgs.hashCode();
        if (!Objects.equals(narrationKey, lastNarrationKey) || narrationArgsHash != lastNarrationArgsHash) {
            lastNarrationKey = narrationKey;
            lastNarrationArgsHash = narrationArgsHash;
            narrationText.text(localized(narrationKey, narrationArgs));
        }
        progressBar.value(playback.progress());
        boolean playing = playback.isPlaying();
        if (playing != lastPlaying) {
            lastPlaying = playing;
            playPauseButton.label(localized(
                    playing ? "ponder.gtsnponder.control.pause" : "ponder.gtsnponder.control.play"));
        }
        int index = playback.stepIndex();
        if (index != lastStepIndex) {
            lastStepIndex = index;
            stepLabel.text((index + 1) + " / " + playback.stepCount());
        }
    }

    private void onPrevious() {
        playback.previousStep();
        syncWidgets();
    }

    private void onTogglePlay() {
        playback.togglePlay();
        syncWidgets();
    }

    private void onNext() {
        playback.nextStep();
        syncWidgets();
    }

    private void onReplay() {
        playback.replay();
        syncWidgets();
    }

    /** 「变体」按钮文案：本地化变体标签 + (i/n)（工单 #21 反馈 2）。 */
    private String variantLabel() {
        SceneVariants.Spec spec = variants.get(variantIndex);
        String label = localized(spec.labelKey(), spec.labelArgs());
        String counter = "(" + (variantIndex + 1) + "/" + variants.size() + ")";
        return label.isEmpty() ? counter : label + " " + counter;
    }

    /** 循环切换到下一个变体（重新打开本屏，结构 / 步骤随之更换）。 */
    private void onNextVariant() {
        if (target == null || variants.size() <= 1) {
            return;
        }
        PonderEntrypoints.openVariant(target, (variantIndex + 1) % variants.size());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    private void seekAt(double guiX) {
        Rect bounds = progressBar.bounds();
        if (bounds.width() <= 0) {
            return;
        }
        playback.seekFraction((guiX - bounds.x()) / bounds.width());
        syncWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pointerX = mouseX;
        pointerY = mouseY;
        if (button == 0 && progressBar.bounds().contains(mouseX, mouseY)) {
            seeking = true;
            seekAt(mouseX);
            return true;
        }
        if (button == 0) {
            if (viewportWidget.bounds().contains(mouseX, mouseY)) {
                // 视口内按下可能是「点击方块」也可能是「拖拽旋转」，在释放时（未拖拽）才判定。
                viewportPress = true;
                viewportDrag = false;
            } else {
                clearBlockSelection();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (seeking && button == 0) {
            seekAt(mouseX);
            return true;
        }
        if (button == 0 && viewport.isDragging()) {
            viewportDrag = true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (seeking && button == 0) {
            seeking = false;
            seekAt(mouseX);
            return true;
        }
        if (button == 0 && viewportPress) {
            boolean dragged = viewportDrag;
            viewportPress = false;
            viewportDrag = false;
            if (!dragged) {
                pickBlockAt(mouseX, mouseY);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 点击方块看名称（工单 #21 反馈 3）：读取视口当前指针下的方块（结构局部坐标 == 虚世界坐标），
     * 映射为结构单元并解析本地化键。再次点击同一方块 / 点击空处即关闭覆盖层。
     */
    private void pickBlockAt(double guiX, double guiY) {
        Optional<BlockPos> picked = viewport.pickedBlock();
        if (picked.isEmpty()) {
            clearBlockSelection();
            return;
        }
        BlockPos pos = picked.get();
        BlockInfoResolver.BlockInfo info = BlockInfoResolver
                .blockAt(structure, pos.getX(), pos.getY(), pos.getZ())
                .flatMap(BlockInfoResolver::resolve)
                .orElse(null);
        if (info == null) {
            clearBlockSelection();
            return;
        }
        if (info.equals(selectedBlock)) {
            clearBlockSelection();
            return;
        }
        selectedBlock = info;
        selectedAtX = guiX;
        selectedAtY = guiY;
        LOGGER.info("[GTSNPonder] block info: block={} role={} pos={} label='{}'",
                info.blockId(), info.roleKey(), pos, blockOverlayText().orElse(""));
    }

    private void clearBlockSelection() {
        if (selectedBlock != null) {
            LOGGER.info("[GTSNPonder] block info dismissed (was {})", selectedBlock.blockId());
        }
        selectedBlock = null;
    }

    /** 覆盖层文本：方块名（+ 角色名 + GT 电压等级，若有）。无选中时为空。 */
    public Optional<String> blockOverlayText() {
        if (selectedBlock == null) {
            return Optional.empty();
        }
        return Optional.of(composeBlockLabel(selectedBlock));
    }

    private static String composeBlockLabel(BlockInfoResolver.BlockInfo info) {
        StringBuilder builder = new StringBuilder(Component.translatable(info.nameKey()).getString());
        if (info.hasRole()) {
            builder.append(" · ").append(Component.translatable(info.roleKey()).getString());
        }
        GtBlockInfo.tierKeyFor(info.blockId())
                .map(key -> Component.translatable(key).getString())
                .ifPresent(tier -> builder.append(" · ").append(tier));
        return builder.toString();
    }

    /** 当前选中的结构单元（诊断 / 自动测试）；无选中为空。 */
    public Optional<BlockInfoResolver.BlockInfo> selectedBlock() {
        return Optional.ofNullable(selectedBlock);
    }

    @Override
    protected void init() {
        super.init();
        int wrap = Math.max(160, width - 2 * SCREEN_PADDING - 16);
        narrationText.wrap(wrap);
        host().resize(width, height);
        LOGGER.info("[GTSNPonder] scene player init: screen={}x{} viewport={} scene={} steps={} duration={}",
                width, height, viewportWidget.bounds(), scene.id(), playback.stepCount(), playback.totalTime());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        viewport.partialTick(partialTick);
        // 指针位置作为视口拾取坐标：优先用屏幕记录的指针（mouseMoved / mouseClicked 写入，自动测试可确定
        // 性注入）；尚未记录时回退渲染参数（真实光标）。
        viewport.setPointer(Double.isFinite(pointerX) ? pointerX : mouseX,
                Double.isFinite(pointerY) ? pointerY : mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLegend(graphics);
        renderBlockInfoOverlay(graphics);
        renderedFrames++;
        if (renderedFrames == 1 || renderedFrames % 120 == 0) {
            LOGGER.info("[GTSNPonder] scene player frame={} step={} time={} playing={} visibleBlocks={}",
                    renderedFrames, playback.stepIndex(), playback.time(), playback.isPlaying(),
                    bridge.visibleBlockCount());
        }
    }

    /**
     * 常驻颜色图例：在视口左上角绘制「金色 = 控制器、蓝色 = 仓口 / 总线」的小面板，始终可见，
     * 从而颜色含义不再挤占旁白句子（见 ticket #7 round-3 反馈 2）。
     */
    private void renderLegend(GuiGraphics graphics) {
        Rect bounds = viewportWidget.bounds();
        if (bounds.width() < 140 || bounds.height() < 64) {
            return;
        }
        var font = Minecraft.getInstance().font;
        String title = localized(LEGEND_TITLE_KEY);
        String controller = localized(LEGEND_CONTROLLER_KEY);
        String hatch = localized(LEGEND_HATCH_KEY);
        String moduleSlot = localized(LEGEND_MODULE_SLOT_KEY);
        int textWidth = Math.max(font.width(title),
                Math.max(font.width(controller), Math.max(font.width(hatch), font.width(moduleSlot))));
        int boxWidth = LEGEND_PAD * 2 + LEGEND_SWATCH + 4 + textWidth;
        int boxHeight = LEGEND_PAD * 2 + LEGEND_LINE * LEGEND_ROWS;
        int x = bounds.x() + LEGEND_MARGIN;
        int y = bounds.y() + LEGEND_MARGIN;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xC0101418);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF3A424C);
        int textX = x + LEGEND_PAD;
        int rowY = y + LEGEND_PAD;
        graphics.drawString(font, title, textX, rowY, 0xFFE0E6EE, true);
        rowY += LEGEND_LINE;
        drawLegendRow(graphics, font, controller, textX, rowY, DummySceneWorld.HIGHLIGHT_COLOR);
        rowY += LEGEND_LINE;
        drawLegendRow(graphics, font, hatch, textX, rowY, DummySceneWorld.OUTLINE_COLOR);
        rowY += LEGEND_LINE;
        drawLegendRow(graphics, font, moduleSlot, textX, rowY, DummySceneWorld.MODULE_SLOT_COLOR);
    }

    private static void drawLegendRow(GuiGraphics graphics, Font font,
            String label, int x, int y, int color) {
        graphics.fill(x, y + 1, x + LEGEND_SWATCH, y + 1 + LEGEND_SWATCH, color);
        graphics.drawString(font, label, x + LEGEND_SWATCH + 4, y, 0xFFFFFFFF, true);
    }

    /**
     * 方块名覆盖层（工单 #21 反馈 3）：在点击位置附近绘制一个小面板，显示该方块的本地化名称
     * （+ 角色 + GT 电压等级）。点击同一方块 / 点击空处即关闭。
     */
    private void renderBlockInfoOverlay(GuiGraphics graphics) {
        if (selectedBlock == null) {
            return;
        }
        Rect bounds = viewportWidget.bounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = composeBlockLabel(selectedBlock);
        int pad = 4;
        int boxWidth = font.width(text) + pad * 2;
        int boxHeight = font.lineHeight + pad * 2;
        int x = (int) Math.round(selectedAtX) + 10;
        int y = (int) Math.round(selectedAtY) - boxHeight - 4;
        x = Math.max(bounds.x() + 2, Math.min(x, bounds.right() - boxWidth - 2));
        y = Math.max(bounds.y() + 2, Math.min(y, bounds.bottom() - boxHeight - 2));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xE0101418);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF8AB4E8);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF8AB4E8);
        graphics.drawString(font, text, x + pad, y + pad, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public ScenePlayback playback() {
        return playback;
    }

    public DummySceneWorld bridge() {
        return bridge;
    }

    public LdlibSceneViewport viewport() {
        return viewport;
    }

    public StructureSource structure() {
        return structure;
    }

    public SceneData scene() {
        return scene;
    }

    public TextWidget narrationText() {
        return narrationText;
    }

    public ProgressBarWidget progressBar() {
        return progressBar;
    }

    public ButtonWidget prevButton() {
        return prevButton;
    }

    public ButtonWidget playPauseButton() {
        return playPauseButton;
    }

    public ButtonWidget nextButton() {
        return nextButton;
    }

    public ButtonWidget replayButton() {
        return replayButton;
    }

    /** 变体切换按钮；目标无多变体时为 {@code null}（不显示）。 */
    public ButtonWidget variantButton() {
        return variantButton;
    }

    /** 当前目标的全部变体描述（无变体时为空列表）。 */
    public List<SceneVariants.Spec> variants() {
        return variants;
    }

    /** 当前展示的变体下标。 */
    public int variantIndex() {
        return variantIndex;
    }

    public int renderedFrames() {
        return renderedFrames;
    }

    /** 渚涜嚜鍔ㄦ祴璇曟柇瑷€锛氳鍙ｅ綋鍓嶇煩褰紙甯冨眬鍚庯級銆?*/
    public Rect viewportBounds() {
        return viewportWidget.bounds();
    }
}
