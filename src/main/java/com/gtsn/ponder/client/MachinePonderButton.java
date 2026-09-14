package com.gtsn.ponder.client;

/**
 * GT 机器界面覆盖层「思索」按钮的<b>纯几何</b>（零 MC 依赖）：尺寸、屏幕内位置与命中测试。
 *
 * <p>覆盖层（{@link MachinePonderOverlay}）与 Forge 接线（{@code GtMachineOverlayClientEvents}）分开：
 * 本类只回答「按钮画在哪、点到了没」，故可在无客户端环境 headless 单测（{@code MachinePonderButtonTest}）。
 * 按钮定位在<b>屏幕右上角</b>——GT 的机器容器界面居中绘制，右上角为空白区，不遮挡 GT 自身的
 * 控件树（ADR-0005 的「入口例外」：不改 GT UI 树，只在其上自绘）。</p>
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class MachinePonderButton {

    /** 按钮宽度（含内边距）。 */
    public static final int WIDTH = 56;
    /** 按钮高度。 */
    public static final int HEIGHT = 16;
    /** 距离屏幕边缘的边距。 */
    public static final int MARGIN = 4;

    /** 屏幕坐标系下的按钮矩形（左上闭、右下开，与 {@code Rect} 语义一致）。 */
    public record Box(int x, int y, int width, int height) {

        public static Box of(int x, int y, int width, int height) {
            return new Box(x, y, width, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        /** 命中测试：右下边界为开区间。 */
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    private MachinePonderButton() {
    }

    /**
     * 按钮矩形：屏幕右上角，四侧留 {@link #MARGIN} 边距。屏幕过窄时夹到左边距，保证矩形始终在屏内。
     */
    public static Box bounds(int screenWidth, int screenHeight) {
        int x = Math.max(MARGIN, screenWidth - WIDTH - MARGIN);
        int y = MARGIN;
        return new Box(x, y, WIDTH, HEIGHT);
    }
}
