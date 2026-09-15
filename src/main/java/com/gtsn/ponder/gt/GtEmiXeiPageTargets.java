package com.gtsn.ponder.gt;

import com.gtsn.ponder.client.PonderXeiItemHover;
import com.gtsn.ponder.client.PonderXeiPageTargets;

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
 */
public final class GtEmiXeiPageTargets implements PonderXeiPageTargets.Source {

    @Override
    public Optional<String> machineTargetFor(Screen screen) {
        if (!(screen instanceof RecipeScreen) && !(screen instanceof BoMScreen)) {
            return Optional.empty();
        }
        return PonderXeiItemHover.get().hoveredItemId().flatMap(GtMachineItemTarget::targetForItem);
    }
}
