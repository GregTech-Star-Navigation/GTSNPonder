package com.gtsn.ponder.client;

/**
 * XEI（JEI/EMI）页面内「思索」入口的<b>纯几何</b>（纯 Java，零 MC 依赖，工单 #20）。
 *
 * <p>入口不再是屏幕右上角的孤立悬浮按钮，而是<b>页面左侧页面按钮列</b>里的一枚小按钮（外观接近 EMI
 * 页面按钮，像「多出来的一页」）。位置策略集中在此：</p>
 * <ul>
 *   <li>横向：从配方面板左缘内缩 {@link #LEFT_INSET}（与 EMI {@code RecipeScreen} 左侧翻页箭头同列），
 *       且整体不越过 {@link #CONTENT_LEFT_INSET}（配方内容左内缩），故不遮配方。</li>
 *   <li>纵向：面板顶之下 {@link #TOP_OFFSET}（EMI 左侧翻页箭头占前 ~28px，入口紧随其下）。</li>
 *   <li>尺寸：{@link #WIDTH}x{@link #HEIGHT}（页面按钮量级）。</li>
 * </ul>
 *
 * <p>EMI/JEI 适配器（{@code com.gtsn.ponder.gt}）只负责把各自屏幕的布局换算成「配方面板左上角」
 * （{@code pageLeft}/{@code pageTop}）再调用本类，故「画在哪」可在无客户端环境 headless 单测
 * （{@code XeiPageEntryLayoutTest}）。所有 EMI/JEI 类型访问都不在本类。</p>
 */
public final class XeiPageEntryLayout {

    /** 入口宽度（页面按钮量级）。 */
    public static final int WIDTH = 20;
    /** 入口高度。 */
    public static final int HEIGHT = 18;
    /** 相对配方面板左缘的内缩（与 EMI 左侧翻页箭头同列）。 */
    public static final int LEFT_INSET = 5;
    /** 相对配方面板顶的下移（避开 EMI 面板左上角堆叠的翻页箭头）。 */
    public static final int TOP_OFFSET = 30;
    /** EMI 配方内容相对面板左缘的内缩：入口不得越过（不遮配方）。 */
    public static final int CONTENT_LEFT_INSET = 25;
    /** 文案最小缩放，保证任何长度都缩进按钮（可读性下限）。 */
    public static final float MIN_LABEL_SCALE = 0.1f;

    private XeiPageEntryLayout() {
    }

    /**
     * 页面入口矩形（屏幕坐标系，左上闭 / 右下开）。
     *
     * @param pageLeft     配方面板左缘的屏幕 x（不含左侧工作台列 / 页签条）
     * @param pageTop      配方面板顶部的屏幕 y（不含上方页签条）
     * @param screenWidth  当前 GUI 缩放后的屏宽（用于屏内夹取）
     * @param screenHeight 当前 GUI 缩放后的屏高（用于屏内夹取）
     * @return 入口矩形的整数屏幕坐标
     */
    public static MachinePonderButton.Box leftColumnBox(int pageLeft, int pageTop,
            int screenWidth, int screenHeight) {
        int x = pageLeft + LEFT_INSET;
        int y = pageTop + TOP_OFFSET;
        // 保证矩形始终在屏内（窄屏 / 极小窗口下退化为左上角），命中测试与绘制共用同一矩形。
        int maxX = Math.max(0, screenWidth - WIDTH);
        int maxY = Math.max(0, screenHeight - HEIGHT);
        return MachinePonderButton.Box.of(
                Math.max(0, Math.min(x, maxX)),
                Math.max(0, Math.min(y, maxY)),
                WIDTH,
                HEIGHT);
    }

    /**
     * 入口文案的缩放系数（纯几何）：把文案缩进 {@link #WIDTH}x{@link #HEIGHT} 的按钮（两侧各留 1px）。
     * 中文「思索」在默认字号下已合适（缩放 1）；英文 "Ponder" 更宽，会被缩到可容纳。
     *
     * @param textWidth  文案像素宽（{@code Font#width}）
     * @param textHeight 文案像素高（{@code Font#lineHeight}）
     * @param boxWidth   按钮宽
     * @param boxHeight  按钮高
     * @return 不超过 1、不小于 {@link #MIN_LABEL_SCALE} 的缩放系数
     */
    public static float labelScale(int textWidth, int textHeight, int boxWidth, int boxHeight) {
        if (textWidth <= 0 || textHeight <= 0 || boxWidth <= 2 || boxHeight <= 2) {
            return 1.0f;
        }
        float sx = (boxWidth - 2.0f) / textWidth;
        float sy = (boxHeight - 2.0f) / textHeight;
        return Math.min(1.0f, Math.max(MIN_LABEL_SCALE, Math.min(sx, sy)));
    }
}
