package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * GTSNPonder 的 JEI 插件（#11）：把「思索」按钮装饰器挂到 GT 的多方块信息页上。
 *
 * <p>由 JEI 的 {@code @JeiPlugin} 注解在 JEI 存在时发现并加载；JEI 缺席时本类<b>永不加载</b>
 * （零 {@code NoClassDefFoundError}），目录 / 快捷键 / GT 注视入口照常工作（优雅降级）。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）。</p>
 */
@JeiPlugin
public final class GtJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation("gtsnponder", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // 用 JEI 官方装饰器扩展点给 GT 多方块页加按钮；不新增配方类别，也不碰 JEI/GT 内部。
        registration.addRecipeCategoryDecorator(MultiblockInfoCategory.RECIPE_TYPE,
                new GtJeiMultiblockDecorator());
    }
}
