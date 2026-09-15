package com.gtsn.ponder.gt;

import com.gtsn.ponder.client.PonderXeiItemHover;
import com.gtsn.ponder.client.PonderXeiPageTargets;

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
 */
public final class GtJeiXeiPageTargets implements PonderXeiPageTargets.Source {

    @Override
    public Optional<String> machineTargetFor(Screen screen) {
        if (!(screen instanceof RecipesGui)) {
            return Optional.empty();
        }
        return PonderXeiItemHover.get().hoveredItemId().flatMap(GtMachineItemTarget::targetForItem);
    }
}
