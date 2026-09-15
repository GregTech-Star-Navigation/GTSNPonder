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
    /**
     * XEI（JEI/EMI）普通页面覆盖层的独立实例（工单 #17 缺陷 A）：GT 机器屏覆盖层与 XEI 页覆盖层可能
     * 在同一帧被不同渲染处理器分别 present / clear，共用单例会互相清空，故各自持有状态。
     */
    private static final MachinePonderOverlay XEI_INSTANCE = new MachinePonderOverlay();

    private String target;
    private MachinePonderButton.Box box;
    /**
     * XEI 页面入口的「页面身份」（工单 #20）：入口属于页面而非悬停——一旦在本页解析出机器，入口在本页
     * 存续（可被点击），即便指针为移向入口而离开悬停物品。换页 / 非 XEI 页即清除。
     */
    private Object pageToken;
    private int clicks;
    private int opens;

    private MachinePonderOverlay() {
    }

    public static MachinePonderOverlay get() {
        return INSTANCE;
    }

    /** XEI（JEI/EMI）普通页面覆盖层的独立实例（工单 #17 缺陷 A）；语义与 {@link #get()} 相同。 */
    public static MachinePonderOverlay xei() {
        return XEI_INSTANCE;
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
        this.pageToken = null;
    }

    /**
     * XEI（JEI/EMI）页面入口的渲染期登记（工单 #20）——与 GT 机器屏覆盖层共用同一套点击交接，但位置由
     * XEI 适配器按真实页面布局算出的 {@code anchor} 决定（页面左侧页面按钮列），而非屏幕右上角。
     *
     * <p><b>为何要有 pageToken</b>：EMI/JEI 的悬停解析只在指针压在物品上时给出机器——而用户必须把指针
     * <b>移开</b>物品才能点到入口，若入口随悬停消失则永远点不到（#20 的「EMI 页内思索不能用」根因）。
     * 故入口一旦在本页出现就<b>锁存</b>到该页（页面身份 = 屏幕实例），换页 / 切屏 / 非 XEI 页才清除。</p>
     *
     * <p><b>已知边界</b>：在同一屏内切换标签（如 EMI 配方页从机器页切到别的类别）不会改变屏幕实例，故锁存
     * 的机器会延续到用户下一次悬停另一台机器或关屏为止；这是为「点击必定可达」付出的取舍（悬停驱动的
     * 「非目标不绘制」在<b>重新打开</b>非机器页时仍然成立，由客户端自动测试覆盖）。</p>
     *
     * @param target    当前页悬停解析出的机器目标；{@code null} / 空白表示本帧未悬停机器（保留锁存）
     * @param anchor    本页入口矩形（屏幕坐标）；{@code null} / 空矩形表示当前屏不是该 XEI 的页面（清除）
     * @param pageToken 页面身份（屏幕实例）；与已锁存身份不同即视为换页并清除
     */
    public synchronized void presentPage(String target, MachinePonderButton.Box anchor, Object pageToken) {
        if (anchor == null || anchor.width() <= 0 || anchor.height() <= 0) {
            // 当前屏不是该 XEI 的页面：入口不存在（不绘制、不消费输入）。
            clear();
            return;
        }
        // 布局每帧刷新（窗口 resize / GUI 缩放变化时锚点随之更新），命中测试与绘制共用同一矩形。
        this.box = anchor;
        if (target != null && !target.isBlank()) {
            this.target = target;
            this.pageToken = pageToken;
            return;
        }
        if (pageToken == null || pageToken != this.pageToken) {
            // 换页：不继承上一页的机器。
            this.target = null;
        }
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
