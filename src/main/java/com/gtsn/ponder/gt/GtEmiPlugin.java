package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiCategory;

import com.gtsn.ponder.client.PonderXeiItemHover;
import com.gtsn.ponder.client.PonderXeiPageTargets;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

import org.slf4j.Logger;

/**
 * GTSNPonder 的 EMI 插件（#11）：把「思索」按钮装饰器挂到 GT 的多方块 EMI 页上。
 *
 * <p>由 EMI 的 {@code @EmiEntrypoint} 注解在 EMI 存在时发现并加载；EMI 缺席时本类<b>永不加载</b>
 * （零 {@code NoClassDefFoundError}），目录 / 快捷键 / GT 注视入口照常工作（优雅降级）。</p>
 *
 * <p>工单 #16 缺陷 C：注册时把「鼠标悬停物品」来源（{@link GtEmiItemHover}，引用 EMI 类型）登记进中性缝
 * {@code PonderXeiItemHover}，使快捷键在背包槽 / EMI 物品列表上也能打开思索。</p>
 *
 * <p>工单 #17 缺陷 A：把 EMI 普通页面（合成配方页 / 物品页）的「思索」目标来源
 * （{@link GtEmiXeiPageTargets}）登记进中性缝 {@code PonderXeiPageTargets}，使覆盖层能在 EMI 页上绘制按钮。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）。</p>
 */
@EmiEntrypoint
public final class GtEmiPlugin implements EmiPlugin {

    /** 稳定的来源引用（注册 / 注销同一实例）。 */
    private static final GtEmiXeiPageTargets PAGE_TARGETS = new GtEmiXeiPageTargets();

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void register(EmiRegistry registry) {
        // 缺陷 C/A 的中性缝注册先行：即使 GT 多方块页装饰器注册失败（fork / EMI 版本差异），
        // 悬停 / XEI 普通页面入口仍可用（入口与多方块装饰器解耦）。
        PonderXeiItemHover.get().register(GtEmiItemHover::hoveredItemId);
        PonderXeiPageTargets.get().register(PAGE_TARGETS);
        LOGGER.info("[GTSNPonder] EMI plugin registered: item-hover source(s)={}, XEI page target source(s)={}",
                PonderXeiItemHover.get().sourceCount(), PonderXeiPageTargets.get().sourceCount());
        try {
            // EMI 官方配方装饰器：在 GT 多方块页加一个可点击的「思索」控件（EMI 自行处理点击）。
            registry.addRecipeDecorator(MultiblockInfoEmiCategory.CATEGORY, new GtEmiMultiblockDecorator());
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[GTSNPonder] could not register the EMI multiblock decorator", failure);
        }
    }
}
