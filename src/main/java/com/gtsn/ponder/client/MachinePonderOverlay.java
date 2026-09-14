package com.gtsn.ponder.client;

import java.util.Optional;

/**
 * GT 机器界面「思索」覆盖层的<b>点击交接状态</b>（纯 Java，零 MC）。
 *
 * <p>分工：Forge {@code ScreenEvent.Render.Post} 每帧经 GT 适配包解析「当前 GT 机器屏的目标」并登记于此
 * （{@link #present}）；点击处理器（{@code GtMachineOverlayClientEvents}）据此命中按钮矩形并打开思索屏。
 * 非 GT 屏 / 无可思索目标时 {@link #present} 以 {@code null} 清除，故覆盖层<b>不绘制、不消费输入</b>
 * （优雅 no-op）。点击解析与状态转移因此可在无客户端环境 headless 单测（{@code MachinePonderOverlayTest}）。</p>
 *
 * <p>单例；每次渲染覆盖旧值。客户端专用。</p>
 */
public final class MachinePonderOverlay {

    private static final MachinePonderOverlay INSTANCE = new MachinePonderOverlay();

    private String target;
    private MachinePonderButton.Box box;
    private int clicks;
    private int opens;

    private MachinePonderOverlay() {
    }

    public static MachinePonderOverlay get() {
        return INSTANCE;
    }

    /**
     * 渲染期登记当前 GT 机器屏的目标与按钮矩形；空白目标（非 GT 屏 / 无目标）即清除。
     *
     * @param target      当前屏解析出的思索目标 id；{@code null} / 空白表示不显示按钮
     * @param screenWidth 当前 GUI 缩放后的屏宽
     * @param screenHeight 当前 GUI 缩放后的屏高
     */
    public synchronized void present(String target, int screenWidth, int screenHeight) {
        if (target == null || target.isBlank()) {
            clear();
            return;
        }
        this.target = target;
        this.box = MachinePonderButton.bounds(screenWidth, screenHeight);
    }

    public synchronized void clear() {
        this.target = null;
        this.box = null;
    }

    /** 是否应绘制 / 响应覆盖按钮（存在目标且已算出矩形）。 */
    public synchronized boolean isActive() {
        return target != null && box != null;
    }

    /** 当前登记的思索目标；无则空。 */
    public synchronized Optional<String> target() {
        return Optional.ofNullable(target);
    }

    public synchronized Optional<MachinePonderButton.Box> button() {
        return Optional.ofNullable(box);
    }

    /** 鼠标是否悬浮在按钮矩形内。 */
    public synchronized boolean hovered(double mouseX, double mouseY) {
        return box != null && box.contains(mouseX, mouseY);
    }

    /**
     * 命中测试：点在登记矩形内则计入一次点击并返回目标，否则空。调用方负责真正打开思索屏
     * （{@code PonderEntrypoints.openForTarget}）并随后 {@link #recordOpen()}。
     */
    public synchronized Optional<String> click(double mouseX, double mouseY) {
        if (target == null || box == null || !box.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        clicks++;
        return Optional.of(target);
    }

    /** 记录一次成功的「打开思索屏」（自动测试断言用）。 */
    public synchronized void recordOpen() {
        opens++;
    }

    public synchronized int clicks() {
        return clicks;
    }

    public synchronized int opens() {
        return opens;
    }

    /** 自动测试 / 切屏清理：清除目标与计数。 */
    public synchronized void reset() {
        clear();
        clicks = 0;
        opens = 0;
    }
}
