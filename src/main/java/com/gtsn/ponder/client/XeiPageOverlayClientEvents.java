package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogKeys;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * JEI/EMI（XEI）<b>普通页面</b>（合成配方页 / 物品页）的「思索」入口客户端接线（工单 #17 缺陷 A、
 * 工单 #20 位置与可用性整改）。
 *
 * <p>复用 #12 已验证的覆盖层方案（<b>不改 EMI/JEI、不用 mixin、不动其 UI 树</b>）：</p>
 * <ul>
 *   <li>{@link ScreenEvent.Render.Post}：每帧经中性登记缝 {@link PonderXeiPageTargets} 一次取出
 *       <b>页面锚点</b>（{@link PonderXeiPageTargets.Source#entryAnchor}，与悬停无关）与
 *       <b>悬停机器目标</b>（{@link PonderXeiPageTargets.Source#machineTargetFor}），登记进
 *       {@link MachinePonderOverlay#presentPage}。非 XEI 屏 / 无锚点 → 清除、不绘制（no-op）。</li>
 *   <li>{@link ScreenEvent.MouseButtonPressed.Pre}：左键命中入口矩形则经
 *       {@link PonderEntrypoints#openForTarget} 打开该机器的思索播放屏，并 {@code setCanceled(true)}。
 *       Forge 在屏幕自身 {@code mouseClicked}（EMI/JEI 的处理）<b>之前</b>派发本事件，故命中即先于
 *       EMI/JEI 消费，点击不会被它们吞掉。</li>
 *   <li>{@link ScreenEvent.Opening}：切屏即清除，避免旧矩形残留。</li>
 * </ul>
 *
 * <p><b>#20 根因与修法</b>：入口原先画在屏幕右上角，且其存在与否取决于「鼠标下的物品」——用户必须把
 * 指针<b>移开</b>那件物品才能点到右上角按钮，指针一离开物品悬停解析即返回空，入口当帧就被清除
 * （矩形置空），点击自然落空（表现为「EMI 页内思索不能用」）。现在入口锚在页面左侧按钮列
 * （{@link XeiPageEntryLayout}），且 {@link MachinePonderOverlay#presentPage} 把它<b>锁存到本页</b>：
 * 指针移向入口期间目标为空也不清除，点击必达。</p>
 *
 * <p>本类不 import 任何 JEI/EMI/GT 类型（识别、目标解析与锚点全在 {@code com.gtsn.ponder.gt} 的
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
        Optional<PonderXeiPageTargets.Entry> entry = PonderXeiPageTargets.get().entryFor(screen);
        overlay.presentPage(
                entry.map(PonderXeiPageTargets.Entry::target).orElse(null),
                entry.map(PonderXeiPageTargets.Entry::box).orElse(null),
                screen);
        if (!overlay.isActive()) {
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

    /**
     * 页面按钮风格的入口：填充 + 亮边 + 居中短标签；标签按 {@link XeiPageEntryLayout#labelScale}
     * 缩放到入口宽度内（英文 "Ponder" 比中文「思索」宽）。
     */
    private static void draw(GuiGraphics graphics, MachinePonderButton.Box box, boolean hovered) {
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), hovered ? BACKGROUND_HOVER : BACKGROUND);
        graphics.fill(box.x(), box.y(), box.right(), box.y() + 1, BORDER);
        graphics.fill(box.x(), box.bottom() - 1, box.right(), box.bottom(), BORDER);
        graphics.fill(box.x(), box.y(), box.x() + 1, box.bottom(), BORDER);
        graphics.fill(box.right() - 1, box.y(), box.right(), box.bottom(), BORDER);
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable(CatalogKeys.XEI_OPEN_SHORT);
        float scale = XeiPageEntryLayout.labelScale(
                font.width(label), font.lineHeight, box.width(), box.height());
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(box.x() + box.width() / 2.0f, box.y() + box.height() / 2.0f, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, label, 0, -font.lineHeight / 2, TEXT);
        pose.popPose();
    }
}
