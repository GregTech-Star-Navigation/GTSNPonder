package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiCategory;

import com.gtsn.ponder.client.PonderXeiItemHover;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * GTSNPonder 的 EMI 插件（#11）：把「思索」按钮装饰器挂到 GT 的多方块 EMI 页上。
 *
 * <p>由 EMI 的 {@code @EmiEntrypoint} 注解在 EMI 存在时发现并加载；EMI 缺席时本类<b>永不加载</b>
 * （零 {@code NoClassDefFoundError}），目录 / 快捷键 / GT 注视入口照常工作（优雅降级）。</p>
 *
 * <p>工单 #16 缺陷 C：注册时把「鼠标悬停物品」来源（{@link GtEmiItemHover}，引用 EMI 类型）登记进中性缝
 * {@code PonderXeiItemHover}，使快捷键在背包槽 / EMI 物品列表上也能打开思索。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）。</p>
 */
@EmiEntrypoint
public final class GtEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // EMI 官方配方装饰器：在 GT 多方块页加一个可点击的「思索」控件（EMI 自行处理点击）。
        registry.addRecipeDecorator(MultiblockInfoEmiCategory.CATEGORY, new GtEmiMultiblockDecorator());
        // 缺陷 C：把 EMI 悬停物品接进中性登记缝（此类只在 EMI 在场时加载，引用 EMI 类型安全）。
        PonderXeiItemHover.get().register(GtEmiItemHover::hoveredItemId);
    }
}
