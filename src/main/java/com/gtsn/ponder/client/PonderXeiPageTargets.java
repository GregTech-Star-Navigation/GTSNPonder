package com.gtsn.ponder.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JEI/EMI（XEI）<b>普通页面</b>（合成配方页 / 物品页）的「思索」目标登记缝（工单 #17 缺陷 A）。
 *
 * <p>与 {@link PonderXeiEntry}（多方块页装饰器登记矩形）并列：XEI 的集成类
 * （{@code com.gtsn.ponder.gt}，唯一能 import JEI/EMI 类型的包）在插件加载时<b>注册</b>一个
 * {@link Source}——它判断「当前屏是否为该 XEI 的普通页面」，若是则返回「鼠标下物品」解析出的机器目标。
 * 覆盖层（{@link XeiPageOverlayClientEvents}）只查询本登记缝，<b>不 import 任何 JEI/EMI 类型</b>，
 * 故二者缺席时优雅降级（零 {@code NoClassDefFoundError}、零按钮绘制）。</p>
 *
 * <p>多个来源（JEI + EMI 同时在场）按注册顺序查询，返回第一个非空结果；来源异常 / 链接错误视为无命中。
 * 单例；来源可注销（资源重载 / 断言重置）。</p>
 */
public final class PonderXeiPageTargets {

    /** 一个 XEI 普通页面目标来源；当前屏不是该 XEI 的页面 / 无机器物品时返回空。 */
    @FunctionalInterface
    public interface Source {
        Optional<String> machineTargetFor(Screen screen);

        /**
         * 本页「思索」入口的屏幕矩形（工单 #20）：由 XEI 适配器按<b>真实页面布局</b>算出（EMI 为配方
         * 面板左侧页面按钮列），<b>与悬停无关</b>——只要当前屏是该 XEI 的普通页面即非空。
         *
         * <p>覆盖层用它决定「画在哪」；目标（{@link #machineTargetFor}）决定「画不画」。默认空实现
         * 使既有来源无需改动即可编译（无非页面锚点的来源不提供入口）。</p>
         */
        default Optional<MachinePonderButton.Box> entryAnchor(Screen screen) {
            return Optional.empty();
        }
    }

    private static final PonderXeiPageTargets INSTANCE = new PonderXeiPageTargets();

    private final List<Source> sources = new CopyOnWriteArrayList<>();

    private PonderXeiPageTargets() {
    }

    public static PonderXeiPageTargets get() {
        return INSTANCE;
    }

    /** 注册一个来源（幂等：同一实例重复注册只保留一次）。 */
    public synchronized void register(Source source) {
        if (source != null && !sources.contains(source)) {
            sources.add(source);
        }
    }

    /** 注销来源（插件卸载 / 资源重载）。 */
    public synchronized void unregister(Source source) {
        sources.remove(source);
    }

    /** 重置（自动测试 / 资源重载）：清空全部来源。 */
    public synchronized void reset() {
        sources.clear();
    }

    /** 已注册来源数量（自动测试断言「XEI 页面入口已接线」）。 */
    public synchronized int sourceCount() {
        return sources.size();
    }

    /**
     * XEI 页面「思索」入口（工单 #20）：{@code box} = 入口的屏幕矩形（非空表示当前屏是该 XEI 的页面），
     * {@code target} = 该页鼠标下的机器目标（{@code null} 表示本帧未悬停机器）。
     */
    public record Entry(String target, MachinePonderButton.Box box) {
    }

    /** 当前屏的可思索机器目标：按注册顺序取第一个非空结果；非 XEI 页 / 非机器物品时空。 */
    public Optional<String> resolve(Screen screen) {
        for (Source source : sources) {
            Optional<String> target = targetOf(source, screen);
            if (target.isPresent()) {
                return target;
            }
        }
        return Optional.empty();
    }

    /**
     * 当前屏的页面入口：按注册顺序取第一个提供锚点的来源；非 XEI 页时空。
     *
     * <p>锚点与目标一次取出，保证「画在哪」（锚点）与「画不画 / 点什么」（目标）来自同一来源：锚点
     * <b>只取决于页面布局</b>（悬停无关），目标取决于悬停。覆盖层据此在「指针已移开物品、正走向入口」
     * 的帧仍保留入口矩形（点击可达，见 {@link MachinePonderOverlay#presentPage}）。</p>
     */
    public Optional<Entry> entryFor(Screen screen) {
        for (Source source : sources) {
            MachinePonderButton.Box anchor = anchorOf(source, screen);
            if (anchor == null) {
                continue;
            }
            return Optional.of(new Entry(targetOf(source, screen).orElse(null), anchor));
        }
        return Optional.empty();
    }

    /** 页面锚点的薄封装（自动测试 / 调用方只需矩形时）。 */
    public Optional<MachinePonderButton.Box> anchorFor(Screen screen) {
        return entryFor(screen).map(Entry::box);
    }

    /** 来源对该屏的页面锚点；不负责该屏 / 异常 / 链接错误 / 空矩形时返回 {@code null}。 */
    private static MachinePonderButton.Box anchorOf(Source source, Screen screen) {
        Optional<MachinePonderButton.Box> anchor;
        try {
            anchor = source.entryAnchor(screen);
        } catch (RuntimeException | LinkageError failure) {
            // 来源异常 / 版本不匹配（XEI 未就绪、类链接错误）不得影响覆盖层——视为不负责该屏。
            return null;
        }
        if (anchor == null || anchor.isEmpty() || anchor.get() == null
                || anchor.get().width() <= 0 || anchor.get().height() <= 0) {
            return null;
        }
        return anchor.get();
    }

    /** 来源对该屏的机器目标；空白 / 异常 / 链接错误视为无命中。 */
    private static Optional<String> targetOf(Source source, Screen screen) {
        Optional<String> target;
        try {
            target = source.machineTargetFor(screen);
        } catch (RuntimeException | LinkageError failure) {
            // 来源异常 / 版本不匹配（XEI 未就绪、类链接错误）不得影响覆盖层——视为无命中。
            return Optional.empty();
        }
        if (target != null && target.isPresent()) {
            String id = target.get();
            if (id != null && !id.isBlank()) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}
