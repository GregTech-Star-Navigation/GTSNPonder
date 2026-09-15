package com.gtsn.ponder.gt;

import com.gtsn.ponder.client.MachinePonderButton;
import com.gtsn.ponder.client.PonderXeiItemHover;
import com.gtsn.ponder.client.PonderXeiPageTargets;
import com.gtsn.ponder.client.XeiPageEntryLayout;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/**
 * JEI 普通配方页（{@link RecipesGui}）的「思索」目标解析（工单 #17 缺陷 A），属唯一能引用 JEI 类型的
 * 适配包 {@code com.gtsn.ponder.gt}。
 *
 * <p>由 {@link GtJeiPlugin}（仅在 JEI 运行时可用时）注册进中性缝 {@link PonderXeiPageTargets}。当前屏是
 * JEI 配方页时，取中性悬停物品缝 {@link PonderXeiItemHover}（JEI 源的实时悬停物品）解析出的物品 id，
 * 再经 {@link GtMachineItemTarget} 判定其是否为 GT 机器；是则返回机器 id 作为思索目标，否则空。</p>
 *
 * <p><b>入口位置（工单 #20）</b>：{@link #entryAnchor} 按 JEI 的<b>真实布局</b>算出「思索」入口——
 * JEI {@code RecipesGui.getArea()} 的左缘 + 配方区左内缩 6（见 {@code RecipesGui#getRecipeLayoutsArea()}），
 * 纵向落在 <b>JEI 左侧导航按钮列之下</b>（JEI 的类别翻页 / 上一页按钮在 {@code area.y + 4} 与
 * {@code area.y + 19}，见 {@code RecipesGui#init}；{@code headerHeight == 32} 由同一处推导）。
 * 与 EMI 侧同一列 / 同一样式 / 同一点击行为。</p>
 */
public final class GtJeiXeiPageTargets implements PonderXeiPageTargets.Source {

    /**
     * JEI 配方区左内缩：{@code RecipesGui.getRecipeLayoutsArea()} 用 {@code area.getX() + 6}；
     * JEI 左侧导航按钮（上一类别 / 上一页）也在 {@code area.x + 6}，故入口与它们同列。
     */
    static final int JEI_RECIPE_AREA_LEFT_INSET = 6;
    /** JEI 左侧导航按钮列自 {@code area.y + 4} 起（{@code RecipesGui#init} 把上一类别按钮放在 {@code area.y + 4}）。 */
    static final int JEI_NAV_COLUMN_TOP_INSET = 4;

    @Override
    public Optional<String> machineTargetFor(Screen screen) {
        if (!(screen instanceof RecipesGui)) {
            return Optional.empty();
        }
        return PonderXeiItemHover.get().hoveredItemId().flatMap(GtMachineItemTarget::targetForItem);
    }

    /**
     * JEI 配方页「思索」入口的屏幕矩形：JEI 页面区域（{@code RecipesGui.getArea()}）左缘的页面按钮列，
     * 纵向在左侧导航按钮之下（{@link XeiPageEntryLayout#TOP_OFFSET}）。
     *
     * @return 当前屏是 JEI 配方页时给出锚点；否则空（不绘制、不消费输入）
     */
    @Override
    public Optional<MachinePonderButton.Box> entryAnchor(Screen screen) {
        if (!(screen instanceof RecipesGui gui)) {
            return Optional.empty();
        }
        ImmutableRect2i area = gui.getArea();
        if (area == null || area.isEmpty() || area.getWidth() <= 0 || area.getHeight() <= 0) {
            return Optional.empty();
        }
        int columnLeft = area.getX() + (JEI_RECIPE_AREA_LEFT_INSET - XeiPageEntryLayout.LEFT_INSET);
        int columnTop = area.getY() + JEI_NAV_COLUMN_TOP_INSET;
        return Optional.of(XeiPageEntryLayout.leftColumnBox(
                columnLeft, columnTop, screen.width, screen.height));
    }
}
