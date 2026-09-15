package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;

import com.gtsn.ponder.client.PonderXeiItemHover;
import com.mojang.blaze3d.platform.Window;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * GTSNPonder 的 JEI 插件（#11）：把「思索」按钮装饰器挂到 GT 的多方块信息页上。
 *
 * <p>由 JEI 的 {@code @JeiPlugin} 注解在 JEI 存在时发现并加载；JEI 缺席时本类<b>永不加载</b>
 * （零 {@code NoClassDefFoundError}），目录 / 快捷键 / GT 注视入口照常工作（优雅降级）。</p>
 *
 * <p>工单 #16 缺陷 C：JEI 运行时可用后，把「鼠标悬停物品」来源注册进中性登记缝
 * {@link PonderXeiItemHover}（本类只在 JEI 在场时加载，故 lambda 引用 JEI 类型安全），使快捷键在
 * 背包槽 / JEI 物品列表上也能打开思索。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）。</p>
 */
@JeiPlugin
public final class GtJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation("gtsnponder", "jei_plugin");

    /** 稳定的来源引用（注册 / 注销同一实例）。 */
    private static final PonderXeiItemHover.Source HOVER_SOURCE = GtJeiPlugin::hoveredItemId;

    private static IJeiRuntime runtime;

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

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        PonderXeiItemHover.get().register(HOVER_SOURCE);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        PonderXeiItemHover.get().unregister(HOVER_SOURCE);
    }

    /**
     * 当前光标下物品的注册 id（JEI 路径）：优先容器槽 / 配方槽（{@code getClickableIngredientUnderMouse}
     * 覆盖原版背包槽），其次 JEI 物品列表（{@code getIngredientUnderMouse}）。无悬停 / 非物品时空。
     */
    private static Optional<String> hoveredItemId() {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            return Optional.empty();
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return Optional.empty();
        }
        double[] mouse = guiMouse();
        Optional<ItemStack> fromScreen = jei.getScreenHelper()
                .getClickableIngredientUnderMouse(screen, mouse[0], mouse[1])
                .map(IClickableIngredient::getTypedIngredient)
                .map(ITypedIngredient::getItemStack)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(stack -> !stack.isEmpty())
                .findFirst();
        if (fromScreen.isPresent()) {
            return itemId(fromScreen.get());
        }
        return jei.getIngredientListOverlay().getIngredientUnderMouse()
                .flatMap(ITypedIngredient::getItemStack)
                .flatMap(GtJeiPlugin::itemId);
    }

    private static Optional<String> itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** 当前鼠标的 GUI 坐标（窗口坐标 × GUI 缩放）。 */
    private static double[] guiMouse() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        double scaleX = window.getScreenWidth() <= 0
                ? 1.0d : (double) window.getGuiScaledWidth() / window.getScreenWidth();
        double scaleY = window.getScreenHeight() <= 0
                ? 1.0d : (double) window.getGuiScaledHeight() / window.getScreenHeight();
        return new double[] { minecraft.mouseHandler.xpos() * scaleX, minecraft.mouseHandler.ypos() * scaleY };
    }
}
