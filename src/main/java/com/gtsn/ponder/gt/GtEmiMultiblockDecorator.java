package com.gtsn.ponder.gt;

import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.client.PonderEntrypoints;

import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiRecipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * EMI 多方块页的「思索」按钮（#11）：以 EMI 官方扩展点 {@link EmiRecipeDecorator} 在 GT 的
 * {@code MultiblockInfoEmiCategory} 页面上添加一个可点击控件，<b>不 mixin、不改 EMI/GT 内部</b>。
 *
 * <p><b>机制</b>：EMI 装饰器允许向配方页注入 {@link Widget}；本类注入一个自绘的「思索」按钮控件，
 * 点击动作直接经 {@link PonderEntrypoints#openForTarget(String)} 打开思索屏（EMI 自身处理点击），
 * 目标取自 GT 多方块 EMI 配方的 {@code getId()}（即机器定义 id）。</p>
 *
 * <p>属于适配包 {@code com.gtsn.ponder.gt}（引用 GT 类型）；仅在 EMI 存在时由 {@code GtEmiPlugin} 注册。</p>
 */
public final class GtEmiMultiblockDecorator implements EmiRecipeDecorator {

    static final int BUTTON_WIDTH = 46;
    static final int BUTTON_HEIGHT = 14;
    private static final int MARGIN = 2;
    private static final int FILL_IDLE = 0xE01A2028;
    private static final int FILL_HOVER = 0xFF3A4A5C;
    private static final int ACCENT = 0xFFFFC24A;
    private static final int TEXT = 0xFFFFE6A8;

    @Override
    public void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        if (!(recipe instanceof MultiblockInfoEmiRecipe multiblock) || multiblock.getId() == null) {
            return;
        }
        String target = multiblock.getId().toString();
        int x = Math.max(MARGIN, widgets.getWidth() - BUTTON_WIDTH - MARGIN);
        int y = MARGIN;
        widgets.add(new PonderEmiButton(x, y, target));
    }

    /** 自绘的可点击「思索」控件（EMI 处理命中与点击，无需 Forge 屏幕事件）。 */
    private static final class PonderEmiButton extends Widget {

        private final int x;
        private final int y;
        private final String target;
        private final Component tooltip = Component.translatable(CatalogKeys.XEI_OPEN);

        private PonderEmiButton(int x, int y, String target) {
            this.x = x;
            this.y = y;
            this.target = target;
        }

        @Override
        public Bounds getBounds() {
            return new Bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean hovered = getBounds().contains(mouseX, mouseY);
            graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? FILL_HOVER : FILL_IDLE);
            graphics.fill(x, y, x + BUTTON_WIDTH, y + 1, ACCENT);
            graphics.drawString(Minecraft.getInstance().font, label(), x + 5, y + 3, TEXT, true);
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            if (!getBounds().contains(mouseX, mouseY)) {
                return List.of();
            }
            return List.of(ClientTooltipComponent.create(tooltip.getVisualOrderText()));
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !getBounds().contains(mouseX, mouseY)) {
                return false;
            }
            PonderEntrypoints.openForTarget(target);
            return true;
        }

        private static String label() {
            return Component.translatable(CatalogKeys.XEI_OPEN_SHORT).getString();
        }
    }
}
