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
import com.gtsn.ponder.presenter.ScenePlayback;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.ViewportWidget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Objects;

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
    private boolean lastPlaying;
    private int lastStepIndex = Integer.MIN_VALUE;
    private boolean seeking;
    private int renderedFrames;

    public ScenePlayerScreen(SceneData scene, StructureSource structure, Level proxyLevel) {
        super(Component.translatable(scene.title() != null ? scene.title() : DEFAULT_TITLE_KEY),
                new PanelWidget().fill());
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.viewport = LdlibSceneViewport.create(proxyLevel, structure);
        this.bridge = new DummySceneWorld(structure, scene, viewport);
        this.playback = ScenePlayback.of(scene, bridge);
        this.metrics = new ThemeFontMetrics(Minecraft.getInstance().font);

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
        if (!Objects.equals(narrationKey, lastNarrationKey)) {
            lastNarrationKey = narrationKey;
            narrationText.text(localized(narrationKey));
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
        if (button == 0 && progressBar.bounds().contains(mouseX, mouseY)) {
            seeking = true;
            seekAt(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (seeking && button == 0) {
            seekAt(mouseX);
            return true;
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
        return super.mouseReleased(mouseX, mouseY, button);
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
        super.render(graphics, mouseX, mouseY, partialTick);
        renderedFrames++;
        if (renderedFrames == 1 || renderedFrames % 120 == 0) {
            LOGGER.info("[GTSNPonder] scene player frame={} step={} time={} playing={} visibleBlocks={}",
                    renderedFrames, playback.stepIndex(), playback.time(), playback.isPlaying(),
                    bridge.visibleBlockCount());
        }
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

    public int renderedFrames() {
        return renderedFrames;
    }

    /** 渚涜嚜鍔ㄦ祴璇曟柇瑷€锛氳鍙ｅ綋鍓嶇煩褰紙甯冨眬鍚庯級銆?*/
    public Rect viewportBounds() {
        return viewportWidget.bounds();
    }
}
