package com.gtsn.ponder.gt;

import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.client.PonderXeiEntry;

import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.mojang.blaze3d.vertex.PoseStack;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * JEI 多方块信息页的「思索」按钮（#11）：以 JEI 官方扩展点
 * {@link IRecipeCategoryDecorator} 叠加在 GT 的 {@code MultiblockInfoCategory} 页面上，
 * <b>不 mixin、不改 JEI/GT 内部</b>。
 *
 * <p><b>机制</b>：JEI 在绘制配方布局时先把 pose 平移到配方原点再调用本装饰器
 * （见 JEI {@code RecipeLayout.drawRecipe}），故本类用 {@link GuiGraphics#pose()} 的平移分量把
 * 配方局部坐标换算成<b>屏幕坐标</b>，绘制按钮，并把「当前目标 + 屏幕矩形」登记到
 * {@link PonderXeiEntry}。点击由 Forge {@code ScreenEvent}（{@code PonderXeiClientEvents}）命中并打开
 * 思索屏——JEI 的装饰器接口没有输入钩子，故点击走 Forge 屏幕事件（仍非 mixin）。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）；仅在 JEI 存在时由 {@code GtJeiPlugin} 注册。</p>
 */
public final class GtJeiMultiblockDecorator implements IRecipeCategoryDecorator<MultiblockInfoWrapper> {

    static final int BUTTON_WIDTH = 46;
    static final int BUTTON_HEIGHT = 14;
    private static final int MARGIN = 3;
    private static final int FILL_IDLE = 0xE01A2028;
    private static final int FILL_HOVER = 0xFF3A4A5C;
    private static final int ACCENT = 0xFFFFC24A;
    private static final int TEXT = 0xFFFFE6A8;

    @Override
    public void draw(MultiblockInfoWrapper recipe, IRecipeCategory<MultiblockInfoWrapper> category,
            IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        String target = targetOf(recipe);
        if (target == null) {
            return;
        }
        int localX = Math.max(MARGIN, category.getWidth() - BUTTON_WIDTH - MARGIN);
        int localY = MARGIN;
        PoseStack.Pose pose = graphics.pose().last();
        Matrix4f matrix = pose.pose();
        int screenX = Math.round(matrix.m30()) + localX;
        int screenY = Math.round(matrix.m31()) + localY;

        PonderXeiEntry.get().present(target,
                PonderXeiEntry.Box.of(screenX, screenY, BUTTON_WIDTH, BUTTON_HEIGHT));

        boolean hovered = mouseX >= localX && mouseX < localX + BUTTON_WIDTH
                && mouseY >= localY && mouseY < localY + BUTTON_HEIGHT;
        graphics.fill(screenX, screenY, screenX + BUTTON_WIDTH, screenY + BUTTON_HEIGHT,
                hovered ? FILL_HOVER : FILL_IDLE);
        graphics.fill(screenX, screenY, screenX + BUTTON_WIDTH, screenY + 1, ACCENT);
        graphics.drawString(Minecraft.getInstance().font, label(), screenX + 5, screenY + 3, TEXT, true);
    }

    @Override
    public void decorateTooltips(ITooltipBuilder tooltip, MultiblockInfoWrapper recipe,
            IRecipeCategory<MultiblockInfoWrapper> category, IRecipeSlotsView slotsView,
            double mouseX, double mouseY) {
        if (targetOf(recipe) == null) {
            return;
        }
        int localX = Math.max(MARGIN, category.getWidth() - BUTTON_WIDTH - MARGIN);
        int localY = MARGIN;
        if (mouseX >= localX && mouseX < localX + BUTTON_WIDTH
                && mouseY >= localY && mouseY < localY + BUTTON_HEIGHT) {
            tooltip.add(Component.translatable(CatalogKeys.XEI_OPEN));
        }
    }

    /** GT 多方块信息页的目标 id（= 机器定义 id，如 {@code gtceu:coke_oven}）。 */
    private static String targetOf(MultiblockInfoWrapper recipe) {
        if (recipe == null || recipe.definition == null || recipe.definition.getId() == null) {
            return null;
        }
        return recipe.definition.getId().toString();
    }

    private static String label() {
        return Component.translatable(CatalogKeys.XEI_OPEN_SHORT).getString();
    }
}
