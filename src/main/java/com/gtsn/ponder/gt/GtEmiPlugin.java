package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiCategory;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * GTSNPonder 的 EMI 插件（#11）：把「思索」按钮装饰器挂到 GT 的多方块 EMI 页上。
 *
 * <p>由 EMI 的 {@code @EmiEntrypoint} 注解在 EMI 存在时发现并加载；EMI 缺席时本类<b>永不加载</b>
 * （零 {@code NoClassDefFoundError}），目录 / 快捷键 / GT 注视入口照常工作（优雅降级）。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）。</p>
 */
@EmiEntrypoint
public final class GtEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // EMI 官方配方装饰器：在 GT 多方块页加一个可点击的「思索」控件（EMI 自行处理点击）。
        registry.addRecipeDecorator(MultiblockInfoEmiCategory.CATEGORY, new GtEmiMultiblockDecorator());
    }
}
