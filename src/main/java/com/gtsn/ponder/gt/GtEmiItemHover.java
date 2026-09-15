package com.gtsn.ponder.gt;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * EMI「鼠标悬停物品」解析（工单 #16 缺陷 C），属唯一能引用 XEI 类型的适配包 {@code com.gtsn.ponder.gt}。
 *
 * <p>由 {@link GtEmiPlugin}（仅在 EMI 在场时经 {@code @EmiEntrypoint} 加载）注册进中性登记缝
 * {@code com.gtsn.ponder.client.PonderXeiItemHover}，故快捷键入口不引用任何 EMI 类型，EMI 缺席时优雅降级。</p>
 *
 * <p>{@code EmiApi.getHoveredStack(true)}（includeStandard=true）才会把<b>原版容器槽</b>中的物品也算进来
 * （非点击用途需传 true，内部名为 notClick），故背包悬停可用；EMI 物品列表同样覆盖。无悬停时
 * {@link EmiStackInteraction#EMPTY}（绝不返回 null）。</p>
 */
public final class GtEmiItemHover {

    private GtEmiItemHover() {
    }

    /** 当前 EMI 悬停物品的注册 id；无悬停 / 非物品（如流体）时空。 */
    public static Optional<String> hoveredItemId() {
        EmiStackInteraction interaction = EmiApi.getHoveredStack(true);
        if (interaction == null || interaction.isEmpty()) {
            return Optional.empty();
        }
        EmiIngredient ingredient = interaction.getStack();
        if (ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack item = stack.getItemStack();
            if (item != null && !item.isEmpty()) {
                return Optional.of(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            }
        }
        return Optional.empty();
    }
}
