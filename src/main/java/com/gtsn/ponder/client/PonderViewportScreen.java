package com.gtsn.ponder.client;

import com.gtsn.lib.ui.client.ThemeFontMetrics;
import com.gtsn.lib.ui.layout.Anchor;
import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.screen.GtsnScreen;
import com.gtsn.lib.ui.theme.ThemeColorRole;
import com.gtsn.lib.ui.widget.ButtonWidget;
import com.gtsn.lib.ui.widget.PanelWidget;
import com.gtsn.lib.ui.widget.TextMetrics;
import com.gtsn.lib.ui.widget.TextWidget;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.ViewportWidget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 视口嵌入 spike 的宿主界面：GTSN UI {@link GtsnScreen} 中嵌入一台真实 GT 多方块的
 * LDLib 3D 视口，并叠放一个与本视口重叠的 GTSN UI 覆盖面板，用于验证
 * 「视口 × 覆盖控件」的 z 序与输入路由（见 {@code SceneViewport} 契约）。
 *
 * <p>布局语义（响应式，用于验证 resize）：根面板留 {@value #SCREEN_PADDING}px 内边距；视口控件
 * {@code fill()} 填满根内容区，因此窗口尺寸 / GUI 缩放变化后其矩形随之改变；覆盖面板绝对锚定在
 * 右下角，与视口右下区域刻意重叠。视口先添加、覆盖层后添加 → 渲染时覆盖层在上、命中时覆盖层优先。</p>
 *
 * <p>客户端专用类（引用 {@link GuiGraphics} 与 LDLib 视口）。</p>
 */
public final class PonderViewportScreen extends GtsnScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 根内容区四周留白（视口即填满该内容区）。 */
    public static final int SCREEN_PADDING = 16;
    /** 覆盖面板尺寸；锚定右下角、与视口右下区域重叠。 */
    public static final int OVERLAY_WIDTH = 150;
    public static final int OVERLAY_HEIGHT = 84;
    private static final int OVERLAY_OFFSET = -8;

    private final StructureSource structure;
    private final LdlibSceneViewport viewport;
    private final ViewportWidget viewportWidget;
    private final ButtonWidget overlayButton;
    private final int[] overlayClicks = { 0 };
    private int renderedFrames;

    public PonderViewportScreen(StructureSource structure, Level proxyLevel) {
        super(Component.literal("GTSN Ponder — viewport spike"), new PanelWidget().fill());
        this.structure = structure;
        this.viewport = LdlibSceneViewport.create(proxyLevel, structure);
        TextMetrics metrics = new ThemeFontMetrics(Minecraft.getInstance().font);

        PanelWidget root = root();
        root.node().params().padding(Insets.all(SCREEN_PADDING));

        this.viewportWidget = new ViewportWidget(viewport, viewport);
        this.viewportWidget.node().params().fill();

        PanelWidget overlay = new PanelWidget()
                .background(ThemeColorRole.PANEL_BACKGROUND)
                .border(ThemeColorRole.BORDER, 1)
                .padding(Insets.all(6))
                .fixedSize(OVERLAY_WIDTH, OVERLAY_HEIGHT);
        overlay.node().params().absolute(Anchor.BOTTOM_RIGHT, OVERLAY_OFFSET, OVERLAY_OFFSET);
        overlay.add(new TextWidget("Overlay (on top of viewport)", metrics).shadow(false));
        this.overlayButton = overlay.add(new ButtonWidget("Click me", metrics, () -> overlayClicks[0]++));
        this.overlayButton.fixedSize(OVERLAY_WIDTH - 12, 18);

        // 视口先添加、覆盖面板后添加 → 渲染时覆盖层在上、命中时覆盖层优先。
        root.add(viewportWidget);
        root.add(overlay);
    }

    /** 根面板（{@link GtsnScreen} 构造时传入，此处取回以挂接子控件）。 */
    private PanelWidget root() {
        return (PanelWidget) host().root();
    }

    public LdlibSceneViewport viewport() {
        return viewport;
    }

    public StructureSource structure() {
        return structure;
    }

    public ButtonWidget overlayButton() {
        return overlayButton;
    }

    public int overlayClicks() {
        return overlayClicks[0];
    }

    public int renderedFrames() {
        return renderedFrames;
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.info("[GTSNPonder] viewport screen init: screen={}x{} viewport={} structure={} blocks={}",
                width, height, viewportWidget.bounds(), structure.id(), structure.blockCount());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // partial-tick 先于渲染传入视口（SceneViewport 契约 §5）。
        viewport.partialTick(partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderedFrames++;
        if (renderedFrames == 1 || renderedFrames % 120 == 0) {
            LOGGER.info("[GTSNPonder] viewport rendered frame={} ldcZoom={} ldcYaw={} blocks={}",
                    renderedFrames, viewport.ldlibZoom(), viewport.ldlibRotationYaw(),
                    viewport.renderedBlockCount());
        }
    }

    /** 供自动测试断言：视口当前矩形（布局后）。 */
    public Rect viewportBounds() {
        return viewportWidget.bounds();
    }

    /** 供自动测试断言：覆盖按钮当前矩形（布局后）。 */
    public Rect overlayButtonBounds() {
        return overlayButton.bounds();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
