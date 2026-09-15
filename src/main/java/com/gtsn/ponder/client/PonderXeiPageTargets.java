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

    /** 当前屏的可思索机器目标：按注册顺序取第一个非空结果；非 XEI 页 / 非机器物品时空。 */
    public Optional<String> resolve(Screen screen) {
        for (Source source : sources) {
            Optional<String> target;
            try {
                target = source.machineTargetFor(screen);
            } catch (RuntimeException | LinkageError failure) {
                // 来源异常 / 版本不匹配（XEI 未就绪、类链接错误）不得影响覆盖层——视为无命中。
                continue;
            }
            if (target != null && target.isPresent()) {
                String id = target.get();
                if (id != null && !id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }
}
