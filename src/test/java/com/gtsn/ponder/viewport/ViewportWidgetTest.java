package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.input.InputEvent;
import com.gtsn.lib.ui.layout.Anchor;
import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.render.RenderContext;
import com.gtsn.lib.ui.render.TextureRef;
import com.gtsn.lib.ui.screen.WidgetHost;
import com.gtsn.lib.ui.widget.AbstractWidget;
import com.gtsn.lib.ui.widget.PanelWidget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成缝（GTSN UI × 视口）的 headless 行为测试：用真实 {@link WidgetHost} / {@code InputRouter}
 * 驱动 {@link ViewportWidget}，验证
 * <ul>
 *   <li>布局 / resize 把矩形下发给视口；</li>
 *   <li>矩形内输入被转发为视口局部坐标，矩形外不被吞；</li>
 *   <li>按下后越界拖拽仍被捕获转发；</li>
 *   <li>重叠覆盖控件优先命中与渲染（z 序）。</li>
 * </ul>
 * 全部纯 Java，不需要 Minecraft。
 */
class ViewportWidgetTest {

    private static final Rect VIEWPORT_RECT = Rect.of(10, 10, 100, 80);
    private static final Rect OVERLAY_RECT = Rect.of(80, 10, 60, 20);

    @Test
    void layoutPushesReservedRectToViewport() {
        Fixture fixture = new Fixture(320, 180);

        assertEquals(VIEWPORT_RECT, fixture.viewportWidget.bounds());
        assertEquals(VIEWPORT_RECT, fixture.viewport.bounds(), "布局完成后视口收到矩形");
    }

    @Test
    void resizeRelayoutsFillViewport() {
        FakeViewport viewport = new FakeViewport();
        RootFixture fixture = RootFixture.withFillingViewport(viewport, 320, 180);
        assertEquals(Rect.of(0, 0, 320, 180), viewport.bounds());

        fixture.host.resize(640, 360);

        assertEquals(Rect.of(0, 0, 640, 360), viewport.bounds(), "resize 后视口矩形随之更新");
    }

    @Test
    void pressInsideRectIsConsumedAndForwardedInLocalCoordinates() {
        Fixture fixture = new Fixture(320, 180);

        assertTrue(fixture.host.dispatch(new InputEvent.MousePressed(30, 60, 0)));

        assertEquals(1, fixture.viewport.presses);
        assertEquals(20.0, fixture.viewport.lastLocalX, "GUI x=30 相对 x=10 → 局部 20");
        assertEquals(50.0, fixture.viewport.lastLocalY, "GUI y=60 相对 y=10 → 局部 50");
        assertTrue(fixture.viewport.isDragging());
    }

    @Test
    void pressOutsideRectIsNotConsumed() {
        Fixture fixture = new Fixture(320, 180);

        // 在视口之外、覆盖层之外。
        assertFalse(fixture.host.dispatch(new InputEvent.MousePressed(250, 170, 0)));

        assertEquals(0, fixture.viewport.presses);
        assertFalse(fixture.viewport.isDragging());
    }

    @Test
    void dragOutsideRectIsStillForwardedWhileCaptured() {
        Fixture fixture = new Fixture(320, 180);
        fixture.host.dispatch(new InputEvent.MousePressed(30, 60, 0));

        // 指针移到视口外（250,170），拖拽仍应转发（鼠标捕获语义）。
        assertTrue(fixture.host.dispatch(new InputEvent.MouseDragged(250, 170, 0, 12, -4)));

        assertEquals(1, fixture.viewport.drags);
        assertEquals(12.0, fixture.viewport.lastDragX);
        assertEquals(-4.0, fixture.viewport.lastDragY);
        assertTrue(fixture.viewport.isDragging());
    }

    @Test
    void releaseEndsCapture() {
        Fixture fixture = new Fixture(320, 180);
        fixture.host.dispatch(new InputEvent.MousePressed(30, 60, 0));

        assertTrue(fixture.host.dispatch(new InputEvent.MouseReleased(30, 60, 0)));
        assertFalse(fixture.viewport.isDragging());

        // 释放后再拖拽不转发。
        assertFalse(fixture.host.dispatch(new InputEvent.MouseDragged(35, 65, 0, 5, 5)));
        assertEquals(0, fixture.viewport.drags);
    }

    @Test
    void scrollInsideForwardedOutsideNot() {
        Fixture fixture = new Fixture(320, 180);

        assertTrue(fixture.host.dispatch(new InputEvent.MouseScrolled(30, 60, 0, 1.0)));
        assertEquals(1, fixture.viewport.scrolls);

        assertFalse(fixture.host.dispatch(new InputEvent.MouseScrolled(250, 170, 0, 1.0)));
        assertEquals(1, fixture.viewport.scrolls, "视口外的滚轮不被吞");
    }

    @Test
    void overlappingOverlayWinsInputOverViewport() {
        Fixture fixture = new Fixture(320, 180);
        // (100,20) 同时落在视口 (10..110,10..90) 与覆盖层 (80..140,10..30) 内。
        double x = 100;
        double y = 20;
        assertTrue(fixture.viewportWidget.bounds().contains(x, y));
        assertTrue(fixture.overlay.bounds().contains(x, y));

        assertTrue(fixture.host.dispatch(new InputEvent.MousePressed(x, y, 0)));
        assertTrue(fixture.overlay.isPressed(), "重叠处覆盖控件优先命中");
        assertEquals(0, fixture.viewport.presses, "视口未收到被覆盖的按下");

        assertTrue(fixture.host.dispatch(new InputEvent.MouseReleased(x, y, 0)));
        assertEquals(1, fixture.overlay.clicks);
        assertEquals(0, fixture.viewport.presses);
    }

    @Test
    void renderOrderDrawsViewportBeforeOverlappingOverlay() {
        Fixture fixture = new Fixture(320, 180);
        List<String> order = new ArrayList<>();
        fixture.orderSink = order;

        fixture.host.render(new NoopRenderContext());

        assertEquals(List.of("viewport", "overlay"), order, "视口先于其上的覆盖控件渲染");
    }

    /** 标准布局：全屏根面板 + 绝对定位视口 + 与之重叠的覆盖控件。 */
    private static final class Fixture {

        final FakeViewport viewport = new FakeViewport();
        final RecordingOverlay overlay = new RecordingOverlay();
        final PanelWidget root = new PanelWidget().fill();
        final ViewportWidget viewportWidget;
        final WidgetHost host;
        List<String> orderSink;

        Fixture(int width, int height) {
            viewportWidget = new ViewportWidget(viewport, (context, bounds) -> {
                if (orderSink != null) {
                    orderSink.add("viewport");
                }
            });
            viewportWidget.node().params().absolute(Anchor.TOP_LEFT, VIEWPORT_RECT.x(), VIEWPORT_RECT.y())
                    .size(com.gtsn.lib.ui.layout.Sizing.fixed(VIEWPORT_RECT.width()),
                            com.gtsn.lib.ui.layout.Sizing.fixed(VIEWPORT_RECT.height()));
            overlay.node().params().absolute(Anchor.TOP_LEFT, OVERLAY_RECT.x(), OVERLAY_RECT.y())
                    .size(com.gtsn.lib.ui.layout.Sizing.fixed(OVERLAY_RECT.width()),
                            com.gtsn.lib.ui.layout.Sizing.fixed(OVERLAY_RECT.height()));
            overlay.orderSink = () -> orderSink;
            root.add(viewportWidget);
            root.add(overlay);
            host = new WidgetHost(root);
            host.resize(width, height);
        }
    }

    /** 仅含一个填满屏幕的视口的布局（用于 resize 重排断言）。 */
    private static final class RootFixture {

        final WidgetHost host;

        private RootFixture(WidgetHost host) {
            this.host = host;
        }

        static RootFixture withFillingViewport(FakeViewport viewport, int width, int height) {
            PanelWidget root = new PanelWidget().fill();
            ViewportWidget widget = new ViewportWidget(viewport, (context, bounds) -> {
            });
            widget.node().params().fill();
            root.add(widget);
            WidgetHost host = new WidgetHost(root);
            host.resize(width, height);
            return new RootFixture(host);
        }
    }

    /** 记录调用的假视口；相机状态机复用真实 {@link ViewportController}。 */
    private static final class FakeViewport implements SceneViewport {

        final ViewportController controller = new ViewportController();
        Rect bounds = Rect.ZERO;
        int presses;
        int drags;
        int scrolls;
        double lastLocalX;
        double lastLocalY;
        double lastDragX;
        double lastDragY;

        @Override
        public void setBounds(Rect bounds) {
            this.bounds = bounds;
        }

        @Override
        public Rect bounds() {
            return bounds;
        }

        @Override
        public Rect clipRect() {
            return bounds;
        }

        @Override
        public boolean mouseMoved(double localX, double localY) {
            return controller.moved(localX, localY);
        }

        @Override
        public boolean mousePressed(double localX, double localY, int button) {
            lastLocalX = localX;
            lastLocalY = localY;
            boolean consumed = controller.press(localX, localY, button);
            if (consumed) {
                presses++;
            }
            return consumed;
        }

        @Override
        public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) {
            lastDragX = dragX;
            lastDragY = dragY;
            boolean consumed = controller.dragged(localX, localY, dragX, dragY);
            if (consumed) {
                drags++;
            }
            return consumed;
        }

        @Override
        public boolean mouseReleased(double localX, double localY, int button) {
            return controller.released(localX, localY, button);
        }

        @Override
        public boolean mouseScrolled(double localX, double localY, double scrollDelta) {
            scrolls++;
            return controller.scrolled(localX, localY, scrollDelta);
        }

        @Override
        public void partialTick(float partialTick) {
        }

        @Override
        public boolean isDragging() {
            return controller.isDragging();
        }

        @Override
        public double cameraYaw() {
            return controller.rotationYaw();
        }

        @Override
        public double cameraPitch() {
            return controller.rotationPitch();
        }

        @Override
        public double cameraZoom() {
            return controller.zoom();
        }

        @Override
        public void resetCamera() {
            controller.reset();
        }
    }

    /** 记录渲染顺序的覆盖控件（在视口之上，重叠命中优先）。 */
    private static final class RecordingOverlay extends LoggingOverlay {
    }

    private static class LoggingOverlay extends AbstractWidget {

        int clicks;
        boolean pressed;
        java.util.function.Supplier<List<String>> orderSink;

        @Override
        protected void onRender(RenderContext context) {
            List<String> sink = orderSink == null ? null : orderSink.get();
            if (sink != null) {
                sink.add("overlay");
            }
        }

        boolean isPressed() {
            return pressed;
        }

        @Override
        public boolean onInput(InputEvent event) {
            if (event instanceof InputEvent.MousePressed press && bounds().contains(press.x(), press.y())) {
                pressed = true;
                return true;
            }
            if (event instanceof InputEvent.MouseReleased release && pressed) {
                pressed = false;
                if (bounds().contains(release.x(), release.y())) {
                    clicks++;
                }
                return true;
            }
            return false;
        }
    }

    /** 无操作渲染上下文（纯接口实现）。 */
    private static final class NoopRenderContext implements RenderContext {

        @Override
        public int width() {
            return 320;
        }

        @Override
        public int height() {
            return 180;
        }

        @Override
        public int textWidth(String text) {
            return 0;
        }

        @Override
        public int textLineHeight() {
            return 9;
        }

        @Override
        public void fill(int x, int y, int width, int height, int argb) {
        }

        @Override
        public void fillGradient(int x, int y, int width, int height, int argbTop, int argbBottom) {
        }

        @Override
        public void text(String text, int x, int y, int argb, boolean shadow) {
        }

        @Override
        public void blit(TextureRef texture, int x, int y, int width, int height, int u, int v,
                         int textureWidth, int textureHeight) {
        }

        @Override
        public void pushClip(int x, int y, int width, int height) {
        }

        @Override
        public void popClip() {
        }

        @Override
        public void pushTranslate(float deltaX, float deltaY) {
        }

        @Override
        public void popTranslate() {
        }
    }
}
