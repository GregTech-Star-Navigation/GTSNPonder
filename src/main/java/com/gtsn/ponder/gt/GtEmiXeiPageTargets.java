package com.gtsn.ponder.gt;

import com.gtsn.ponder.client.MachinePonderButton;
import com.gtsn.ponder.client.PonderXeiItemHover;
import com.gtsn.ponder.client.PonderXeiPageTargets;
import com.gtsn.ponder.client.XeiPageEntryLayout;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/**
 * EMI 普通页面（合成配方页 {@link RecipeScreen} / 配方树 {@link BoMScreen}）的「思索」目标解析
 * （工单 #17 缺陷 A），属唯一能引用 EMI 类型的适配包 {@code com.gtsn.ponder.gt}。
 *
 * <p>由 {@link GtEmiPlugin}（仅在 EMI 在场时加载）注册进中性缝 {@link PonderXeiPageTargets}。当前屏是
 * EMI 页面时，取中性悬停物品缝 {@link PonderXeiItemHover}（EMI 源的实时悬停物品）解析出的物品 id，
 * 再经 {@link GtMachineItemTarget} 判定其是否为 GT 机器；是则返回机器 id 作为思索目标，否则空
 * （覆盖层据此<b>不绘制</b>按钮）。</p>
 *
 * <p><b>入口位置（工单 #20）</b>：{@link #entryAnchor} 按 EMI 的<b>真实屏幕布局</b>算出「思索」入口的
 * 屏幕矩形——放在配方面板<b>左侧的页面按钮列</b>（{@code RecipeScreen} 左侧翻页箭头之下），而不是屏幕
 * 右上角悬浮。锚点与悬停无关，只取决于布局；覆盖层据锚点绘制并在指针移开物品后仍保留入口（可点击）。</p>
 */
public final class GtEmiXeiPageTargets implements PonderXeiPageTargets.Source {

    /**
     * EMI {@code RecipeScreen.getBounds()} 的 y 比配方面板顶高 26（上方页签条）；
     * 见 {@code RecipeScreen#getBounds()}（{@code y - 26}）。
     */
    static final int EMI_TAB_STRIP_HEIGHT = 26;
    /**
     * {@code workstationLocation == LEFT} 时 {@code RecipeScreen.getBounds()} 的 x 左移 22（左侧工作台列）；
     * 见 {@code RecipeScreen#getBounds()} / {@code #getWorkstationBounds(int)}。入口必须锚在<b>面板</b>左缘，
     * 故需把这段宽度还原，避免压住左侧工作台槽。
     */
    static final int EMI_WORKSTATION_COLUMN_WIDTH = 22;

    @Override
    public Optional<String> machineTargetFor(Screen screen) {
        if (!(screen instanceof RecipeScreen) && !(screen instanceof BoMScreen)) {
            return Optional.empty();
        }
        return PonderXeiItemHover.get().hoveredItemId().flatMap(GtMachineItemTarget::targetForItem);
    }

    /**
     * EMI 页面「思索」入口的屏幕矩形：锚在配方面板左侧的页面按钮列（左侧翻页箭头之下）。
     *
     * <p>{@link BoMScreen}（配方树）没有配方面板 / 页签布局，故退化为<b>屏幕左缘</b>的同一列位置——
     * 保住既有入口（不因位置整改而丢功能），且不再回到被用户否定的右上角悬浮。</p>
     *
     * @return 当前屏是 EMI 页面时给出锚点；否则空（不绘制、不消费输入）
     */
    @Override
    public Optional<MachinePonderButton.Box> entryAnchor(Screen screen) {
        if (!(screen instanceof RecipeScreen recipe)) {
            if (screen instanceof BoMScreen) {
                return Optional.of(XeiPageEntryLayout.leftColumnBox(
                        0, 0, screen.width, screen.height));
            }
            return Optional.empty();
        }
        Bounds page = recipe.getBounds();
        if (page == null || page.width() <= 0 || page.height() <= 0) {
            return Optional.empty();
        }
        int panelLeft = page.x() + (EmiConfig.workstationLocation == SidebarSide.LEFT
                ? EMI_WORKSTATION_COLUMN_WIDTH : 0);
        int panelTop = page.y() + EMI_TAB_STRIP_HEIGHT;
        return Optional.of(XeiPageEntryLayout.leftColumnBox(
                panelLeft, panelTop, screen.width, screen.height));
    }
}
