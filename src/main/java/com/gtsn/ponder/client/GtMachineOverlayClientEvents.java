package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.gt.GtMachineScreenAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.Optional;

/**
 * GT 机器界面「思索」覆盖按钮的客户端接线（#12，入口 ④，用户批准的覆盖层方案）。
 *
 * <p><b>不改 GT fork、不用 mixin、不动 GT 的 UI 树</b>：只经 Forge 官方的 {@link ScreenEvent} 在 GT 自己的
 * 机器屏（LDLib {@code ModularUIGuiContainer}）之上自绘一个小按钮，并处理其点击 / 按键。</p>
 *
 * <ul>
 *   <li>{@link ScreenEvent.Render.Post}：每帧经 {@link GtMachineScreenAdapter} 解析当前屏的 GT 多方块目标；
 *       有目标则在屏幕右上角绘制「思索」按钮（悬浮高亮）；非 GT 屏 / 无目标 → 清除状态、不绘制（no-op）。</li>
 *   <li>{@link ScreenEvent.MouseButtonPressed.Pre}：左键命中按钮矩形则经 {@link PonderEntrypoints#openForTarget}
 *       打开该机器的思索播放屏，并 {@code setCanceled(true)}（GT 屏自己不接收这次点击）。</li>
 *   <li>{@link ScreenEvent.KeyPressed.Pre}：在 GT 机器屏内按下思索快捷键（默认 P）等效于点击按钮。</li>
 *   <li>{@link ScreenEvent.Opening}：切屏即清除，避免上一屏的矩形残留到新屏。</li>
 * </ul>
 *
 * <p>本类不 import 任何 {@code com.gregtechceu} 类型（GT 识别全在 {@link GtMachineScreenAdapter}），
 * 客户端专用（{@code Dist.CLIENT}）。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class GtMachineOverlayClientEvents {

    private static final int BACKGROUND = 0xE0202630;
    private static final int BACKGROUND_HOVER = 0xE0345C8C;
    private static final int BORDER = 0xFF8AB4E8;
    private static final int TEXT = 0xFFFFFFFF;

    private GtMachineOverlayClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        MachinePonderOverlay.get().clear();
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        Screen screen = event.getScreen();
        Optional<String> target = GtMachineScreenAdapter.resolveTarget(screen);
        overlay.present(target.orElse(null), screen.width, screen.height);
        if (target.isEmpty()) {
            return;
        }
        MachinePonderButton.Box box = overlay.button().orElse(null);
        if (box == null) {
            return;
        }
        draw(event.getGuiGraphics(), box, overlay.hovered(event.getMouseX(), event.getMouseY()));
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        Optional<String> target = overlay.click(event.getMouseX(), event.getMouseY());
        if (target.isPresent() && PonderEntrypoints.openForTarget(target.get())) {
            overlay.recordOpen();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!PonderEntrypoints.PONDER_KEY.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        Optional<String> target = overlay.target();
        if (target.isPresent() && PonderEntrypoints.openForTarget(target.get())) {
            overlay.recordOpen();
            event.setCanceled(true);
        }
    }

    private static void draw(GuiGraphics graphics, MachinePonderButton.Box box, boolean hovered) {
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), hovered ? BACKGROUND_HOVER : BACKGROUND);
        graphics.fill(box.x(), box.y(), box.right(), box.y() + 1, BORDER);
        graphics.fill(box.x(), box.bottom() - 1, box.right(), box.bottom(), BORDER);
        graphics.fill(box.x(), box.y(), box.x() + 1, box.bottom(), BORDER);
        graphics.fill(box.right() - 1, box.y(), box.right(), box.bottom(), BORDER);
        Component label = Component.translatable(CatalogKeys.GT_MACHINE_OPEN_SHORT);
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
                box.x() + box.width() / 2, box.y() + (box.height() - 8) / 2, TEXT);
    }
}
