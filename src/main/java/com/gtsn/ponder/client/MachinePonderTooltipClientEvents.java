package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.gt.GtMachineItemTarget;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * GT 机器物品 tooltip 的「思索」入口提示（工单 #17 缺陷 C 的可选增强）：把光标移到任意 GT 机器物品
 * （背包 / JEI / EMI 物品页）上时，tooltip 追加一行「[G] 思索」，说明可按键打开教程，提升入口可发现性。
 *
 * <p>物品 → 机器判定收敛在唯一适配包 {@code com.gtsn.ponder.gt}（{@link GtMachineItemTarget}），本类
 * 不 import 任何 {@code com.gregtechceu} 类型。键名来自当前绑定（{@code PONDER_KEY}），改绑后 tooltip
 * 同步显示新键。客户端专用（{@code Dist.CLIENT}）。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class MachinePonderTooltipClientEvents {

    private MachinePonderTooltipClientEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack() == null || event.getItemStack().isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (GtMachineItemTarget.targetForItem(itemId).isEmpty()) {
            return;
        }
        event.getToolTip().add(Component.translatable(CatalogKeys.MACHINE_ITEM_TOOLTIP,
                PonderEntrypoints.PONDER_KEY.getTranslatedKeyMessage()));
    }
}
