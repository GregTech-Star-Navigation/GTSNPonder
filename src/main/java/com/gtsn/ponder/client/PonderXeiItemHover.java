package com.gtsn.ponder.client;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JEI/EMI「悬停物品」的<b>中性登记缝</b>（纯 Java，零 MC / 零 JEI/EMI 类型，工单 #16 缺陷 C）。
 *
 * <p>与 {@link PonderXeiEntry}（机器页按钮矩形）同构：XEI 的集成类（{@code com.gtsn.ponder.gt}，
 * 唯一能 import JEI/EMI 的包）在插件加载时<b>注册</b>一个 {@link Source}（返回当前鼠标悬停物品的
 * 注册 id）；快捷键处理只查询本登记缝，<b>不 import 任何 JEI/EMI 类型</b>，故二者缺席时优雅降级
 * （零 {@code NoClassDefFoundError}）。</p>
 *
 * <p>多个来源（JEI + EMI 同时在场）按注册顺序查询，返回第一个非空结果。单例；来源可注销
 * （资源重载 / 断言重置）。</p>
 */
public final class PonderXeiItemHover {

    /** 一个 XEI 悬停物品来源；无悬停时返回空。 */
    @FunctionalInterface
    public interface Source {
        Optional<String> hoveredItemId();
    }

    private static final PonderXeiItemHover INSTANCE = new PonderXeiItemHover();

    private final List<Source> sources = new CopyOnWriteArrayList<>();

    private PonderXeiItemHover() {
    }

    public static PonderXeiItemHover get() {
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

    /** 已注册来源数量（自动测试断言「JEI/EMI 集成已接线」）。 */
    public synchronized int sourceCount() {
        return sources.size();
    }

    /** 当前鼠标悬停物品的注册 id：按注册顺序取第一个非空结果。 */
    public Optional<String> hoveredItemId() {
        for (Source source : sources) {
            Optional<String> hovered;
            try {
                hovered = source.hoveredItemId();
            } catch (RuntimeException | LinkageError failure) {
                // 来源异常 / 版本不匹配（XEI 未就绪、类链接错误）不得影响快捷键入口——视为无悬停。
                continue;
            }
            if (hovered != null && hovered.isPresent()) {
                String id = hovered.get();
                if (id != null && !id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    /** 是否有任一来源（用于决定是否尝试 XEI 悬停入口）。 */
    public synchronized boolean hasSources() {
        return !sources.isEmpty();
    }
}
