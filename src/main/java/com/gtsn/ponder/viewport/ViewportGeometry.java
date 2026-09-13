package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.Rect;

/**
 * 视口矩形几何：GUI 坐标 ⇄ 视口局部坐标的映射、命中测试与裁剪矩形推导。
 *
 * <p>纯静态工具，无状态、无 MC 依赖；供 {@link ViewportWidget}（宿主）与 headless 单测共用，
 * 保证「矩形内转发 / 矩形外不转发」与裁剪矩形只有一处定义。</p>
 */
public final class ViewportGeometry {

    private ViewportGeometry() {
    }

    /** 视口矩形是否包含给定 GUI 点（左上闭、右下开，与 {@link Rect#contains} 一致）。 */
    public static boolean contains(Rect bounds, double guiX, double guiY) {
        return bounds.contains(guiX, guiY);
    }

    /** GUI X → 视口局部 X（矩形左上角为 0）。 */
    public static double localX(Rect bounds, double guiX) {
        return guiX - bounds.x();
    }

    /** GUI Y → 视口局部 Y（矩形左上角为 0）。 */
    public static double localY(Rect bounds, double guiY) {
        return guiY - bounds.y();
    }

    /**
     * 场景允许绘制的裁剪矩形：默认即视口矩形本身（无内边距）。若实现需要留出边框 / 内边距，
     * 传入 {@link Insets}（向内收缩）。
     */
    public static Rect clipRect(Rect bounds, Insets insets) {
        return insets == null ? bounds : bounds.inset(insets);
    }

    /** 裁剪矩形（无内边距）。 */
    public static Rect clipRect(Rect bounds) {
        return bounds;
    }
}
