package com.gtsn.ponder.client;

import java.util.Optional;

/**
 * JEI/EMI（XEI）「思索」入口的点击交接状态（纯 Java，零 MC）。
 *
 * <p>分工：XEI 的<b>装饰器</b>（{@code com.gtsn.ponder.gt.GtJeiMultiblockDecorator} /
 * {@code GtEmiMultiblockDecorator}）在渲染 GT 多方块信息页时，把「当前目标」与按钮的<b>屏幕矩形</b>
 * 登记在此；Forge {@code ScreenEvent} 的点击处理器（{@code PonderXeiClientEvents}）据此解析目标并
 * 打开思索屏。故点击解析逻辑可在无 JEI/EMI 环境下 headless 单测（XEI 集成本体无法 headless 启动）。</p>
 *
 * <p>单例；每次渲染覆盖旧值；空白目标视为无效（{@link #clear()}）。</p>
 */
public final class PonderXeiEntry {

    /** 屏幕坐标系下的按钮矩形（左上闭、右下开，与 {@code Rect} 语义一致）。 */
    public record Box(int x, int y, int width, int height) {

        public static Box of(int x, int y, int width, int height) {
            return new Box(x, y, width, height);
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private static final PonderXeiEntry INSTANCE = new PonderXeiEntry();

    private String target;
    private Box box;

    private PonderXeiEntry() {
    }

    public static PonderXeiEntry get() {
        return INSTANCE;
    }

    /** 渲染期登记当前 XEI 机器页目标与按钮矩形；空白目标或空矩形即清除。 */
    public synchronized void present(String target, Box box) {
        if (target == null || target.isBlank() || box == null || box.width() <= 0 || box.height() <= 0) {
            clear();
            return;
        }
        this.target = target;
        this.box = box;
    }

    public synchronized void clear() {
        this.target = null;
        this.box = null;
    }

    /** 当前登记的目标；无则 {@code null}。 */
    public synchronized String target() {
        return target;
    }

    public synchronized Optional<Box> box() {
        return Optional.ofNullable(box);
    }

    /** 命中测试：点在登记矩形内则返回目标，否则空。 */
    public synchronized Optional<String> targetAt(double x, double y) {
        if (target == null || box == null) {
            return Optional.empty();
        }
        return box.contains(x, y) ? Optional.of(target) : Optional.empty();
    }
}
