package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.Optional;

/**
 * XEI（JEI/EMI）「思索」入口的客户端接线（#11）：把 JEI 装饰器登记的按钮矩形接到点击上。
 *
 * <p>JEI 的配方装饰器接口没有输入钩子，故点击经 Forge 官方 {@link ScreenEvent} 处理（非 mixin）：
 * 装饰器每帧把「目标 + 屏幕矩形」登记到 {@link PonderXeiEntry}，本类在鼠标按下时命中该矩形并打开
 * 思索屏。{@link ScreenEvent.Render.Pre} 每帧先清空登记，故只有「当前帧确实绘制了按钮」才有效
 * （切到别的 JEI 页 / 别的屏幕后旧矩形立即失效）。EMI 侧由 EMI 控件自行处理点击，不经此处。</p>
 *
 * <p>本类不引用任何 JEI/EMI 类型，故在两者缺席时也能安全注册并空转（优雅降级）。客户端专用。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class PonderXeiClientEvents {

    private PonderXeiClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        // 清空上一帧的登记；当前帧若绘制 GT 多方块页，装饰器会重新登记。
        PonderXeiEntry.get().clear();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        PonderXeiEntry.get().clear();
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        Optional<String> target = PonderXeiEntry.get().targetAt(event.getMouseX(), event.getMouseY());
        if (target.isPresent() && PonderEntrypoints.openForTarget(target.get())) {
            event.setCanceled(true);
        }
    }
}
