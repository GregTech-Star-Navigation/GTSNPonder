package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogKeys;

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
 * JEI/EMI（XEI）<b>普通页面</b>（合成配方页 / 物品页）的「思索」覆盖按钮客户端接线（工单 #17 缺陷 A）。
 *
 * <p>复用 #12 已验证的覆盖层方案（<b>不改 EMI/JEI、不用 mixin、不动其 UI 树</b>）：</p>
 * <ul>
 *   <li>{@link ScreenEvent.Render.Post}：每帧经中性登记缝 {@link PonderXeiPageTargets} 解析当前屏——
 *       仅当屏是 EMI/JEI 页面<b>且</b>鼠标下物品解析出带场景的机器时才登记目标并绘制「思索」按钮；
 *       非目标 / 非 XEI 屏 → 清除状态、<b>不绘制</b>（no-op）。</li>
 *   <li>{@link ScreenEvent.MouseButtonPressed.Pre}：左键命中按钮矩形则经
 *       {@link PonderEntrypoints#openForTarget} 打开该机器的思索播放屏，并 {@code setCanceled(true)}。</li>
 *   <li>{@link ScreenEvent.Opening}：切屏即清除，避免旧矩形残留。</li>
 * </ul>
 *
 * <p>本类不 import 任何 JEI/EMI/GT 类型（识别与目标解析全在 {@code com.gtsn.ponder.gt} 的
 * {@link PonderXeiPageTargets} 来源），故 XEI 缺席时零加载、空转。客户端专用（{@code Dist.CLIENT}）。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class XeiPageOverlayClientEvents {

    private static final int BACKGROUND = 0xE0202630;
    private static final int BACKGROUND_HOVER = 0xE0345C8C;
    private static final int BORDER = 0xFF8AB4E8;
    private static final int TEXT = 0xFFFFFFFF;

    private XeiPageOverlayClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        MachinePonderOverlay.xei().clear();
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        Screen screen = event.getScreen();
        Optional<String> target = PonderXeiPageTargets.get().resolve(screen);
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
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        Optional<String> target = overlay.click(event.getMouseX(), event.getMouseY());
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
        Component label = Component.translatable(CatalogKeys.XEI_OPEN_SHORT);
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
                box.x() + box.width() / 2, box.y() + (box.height() - 8) / 2, TEXT);
    }
}
