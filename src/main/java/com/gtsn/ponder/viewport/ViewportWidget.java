package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.input.InputEvent;
import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.render.RenderContext;
import com.gtsn.lib.ui.widget.AbstractWidget;

import java.util.Objects;

/**
 * GTSN UI 视口控件：把 {@link SceneViewport} 接进 GTSNLib 控件树。
 *
 * <p>本控件是「GTSN UI × LDLib 视口」契约的宿主侧实现（纯 Java，可 headless 测试）：</p>
 * <ul>
 *   <li><b>预留矩形 / resize</b>：布局完成后 {@link #onLayout()} 把布局引擎算出的
 *       {@link #bounds()}（GUI 像素）交给视口；窗口尺寸 / GUI 缩放变化触发重排后自动再次下发。</li>
 *   <li><b>输入转发</b>：命中测试由 GTSNLib {@code InputRouter} 完成——只有指针落在本控件
 *       包围盒内时才会把事件冒泡到这里。本控件把 GUI 坐标换算为<b>视口局部坐标</b>后转发；
 *       矩形外的移动 / 按下 / 滚轮不转发（事件继续冒泡给其它控件）。矩形内按下被消费后，
 *       拖拽与释放即使在矩形外也继续转发（鼠标捕获语义）。</li>
 *   <li><b>z 序</b>：本控件不绘制超出矩形的内容；作为较早添加的子控件，它在同层中先于
 *       后添加的覆盖控件渲染与命中，因此重叠的 GTSN UI 覆盖层总是在场景之上。</li>
 *   <li><b>渲染</b>：{@link #onRender(RenderContext)} 经 {@link ViewportRenderer} 触发 3D 场景绘制
 *       （裁剪由视口实现把 GPU 视口钳到矩形达成）。</li>
 *   <li><b>partial-tick</b>：宿主屏幕每帧经 {@link SceneViewport#partialTick(float)} 传入；
 *       本控件本身无逐帧职责。</li>
 * </ul>
 */
public final class ViewportWidget extends AbstractWidget {

    private final SceneViewport viewport;
    private final ViewportRenderer renderer;

    public ViewportWidget(SceneViewport viewport, ViewportRenderer renderer) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public SceneViewport viewport() {
        return viewport;
    }

    @Override
    public void onLayout() {
        viewport.setBounds(bounds());
    }

    @Override
    protected void onRender(RenderContext context) {
        renderer.renderViewport(context, bounds());
    }

    @Override
    public boolean onInput(InputEvent event) {
        if (event instanceof InputEvent.MouseMoved moved) {
            if (!inside(moved.x(), moved.y())) {
                return false;
            }
            return viewport.mouseMoved(localX(moved.x()), localY(moved.y()));
        }
        if (event instanceof InputEvent.MousePressed pressed) {
            // 只有矩形内、主键按下才接管；否则冒泡（覆盖控件 / 其它控件优先）。
            if (!inside(pressed.x(), pressed.y())) {
                return false;
            }
            return viewport.mousePressed(localX(pressed.x()), localY(pressed.y()), pressed.button());
        }
        if (event instanceof InputEvent.MouseDragged dragged) {
            // 拖拽期即使指针越出矩形也继续转发（捕获态）。
            if (!viewport.isDragging()) {
                return false;
            }
            return viewport.mouseDragged(localX(dragged.x()), localY(dragged.y()), dragged.button(),
                    dragged.deltaX(), dragged.deltaY());
        }
        if (event instanceof InputEvent.MouseReleased released) {
            if (!viewport.isDragging()) {
                return false;
            }
            return viewport.mouseReleased(localX(released.x()), localY(released.y()), released.button());
        }
        if (event instanceof InputEvent.MouseScrolled scrolled) {
            if (!inside(scrolled.x(), scrolled.y())) {
                return false;
            }
            return viewport.mouseScrolled(localX(scrolled.x()), localY(scrolled.y()), scrolled.scrollY());
        }
        return false;
    }

    private boolean inside(double guiX, double guiY) {
        return ViewportGeometry.contains(bounds(), guiX, guiY);
    }

    private double localX(double guiX) {
        return ViewportGeometry.localX(bounds(), guiX);
    }

    private double localY(double guiY) {
        return ViewportGeometry.localY(bounds(), guiY);
    }
}
