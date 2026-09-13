package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * 思索快捷键的注册（客户端、mod 事件总线）。未绑定时 {@code KeyMapping} 不产生点击，
 * 因此注册本身是「未绑定安全」的。
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.MOD, value = Dist.CLIENT)
public final class PonderKeyMappings {

    private PonderKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PonderEntrypoints.PONDER_KEY);
    }
}
