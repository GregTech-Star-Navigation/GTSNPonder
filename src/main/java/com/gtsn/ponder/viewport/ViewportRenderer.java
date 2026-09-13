package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.render.RenderContext;

/**
 * 视口渲染钩子：把「在控件树中的某处绘制 3D 场景」这一动作从纯逻辑的 {@link ViewportWidget}
 * 中拆出。纯接口（只依赖 GTSNLib 的 {@link RenderContext}），使控件本身保持无 MC 依赖、可
 * headless 测试；客户端实现（{@code LdlibSceneViewport}）在 {@code renderViewport} 中把
 * {@link RenderContext} 下转为 {@code GuiGraphicsRenderContext} 并调用 LDLib 渲染。
 */
@FunctionalInterface
public interface ViewportRenderer {

    /**
     * 在给定 GTSN 渲染上下文中绘制视口内容，且只能绘制在 {@code bounds} 内。
     *
     * @param context 当前 GTSN 渲染上下文（客户端为 {@code GuiGraphicsRenderContext} 后端的实现）
     * @param bounds  视口矩形（GUI 像素），即 {@link SceneViewport#clipRect()} 的宿主侧来源
     */
    void renderViewport(RenderContext context, Rect bounds);
}
