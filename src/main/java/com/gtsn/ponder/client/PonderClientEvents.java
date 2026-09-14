package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * 客户端接线（Forge 事件总线）：
 *
 * <ul>
 *   <li>客户端命令（本地执行、不发往服务器）：
 *       <ul>
 *         <li>{@code /gtsnponder scene [target]}——无快捷键场景的确定性入口（有手作用手作，无则自动生成）；</li>
 *         <li>{@code /gtsnponder generate <target>}——强制自动生成并播放（开发者 / 自动测试）；</li>
 *         <li>{@code /gtsnponder dump <target>}——把自动生成的场景 JSON 导出到
 *             {@code run/gtsnponder-generated/}（作者草稿起点 / 生成器 diff）。</li>
 *         <li>{@code /gtsnponder editor [target]}——打开游戏内可视化编辑器（<b>门控</b>：仅作者 / 开发，
 *             正式玩家不可见）；</li>
 *         <li>{@code /gtsnponder export <target>}——把自动生成场景导出为手作草稿到可写作者目录（门控）。</li>
 *       </ul>
 *   </li>
 *   <li>每客户端 tick：推进当前 {@link ScenePlayerScreen}（若存在），并消费快捷键点击
 *       （仅在无界面时打开，避免与已打开界面冲突）。</li>
 * </ul>
 *
 * <p>客户端专用类（由 {@code Dist.CLIENT} 订阅保证专职服务端不加载）。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class PonderClientEvents {

    private PonderClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gtsnponder")
                .then(Commands.literal("scene")
                        .executes(context -> PonderEntrypoints.openForLookedAtTarget() ? 1 : 0)
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> PonderEntrypoints.openForTarget(
                                        StringArgumentType.getString(context, "target")) ? 1 : 0)))
                .then(Commands.literal("generate")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> PonderEntrypoints.openGenerated(
                                        StringArgumentType.getString(context, "target")) ? 1 : 0)))
                .then(Commands.literal("dump")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> PonderEntrypoints.dumpGenerated(
                                        StringArgumentType.getString(context, "target")).isPresent() ? 1 : 0)))
                .then(Commands.literal("editor")
                        .executes(context -> PonderEntrypoints.openEditorForLookedAtTarget() ? 1 : 0)
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> PonderEntrypoints.openEditor(
                                        StringArgumentType.getString(context, "target")) ? 1 : 0)))
                .then(Commands.literal("export")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> PonderEntrypoints.exportGeneratedDraft(
                                        StringArgumentType.getString(context, "target")).isPresent() ? 1 : 0))));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ScenePlayerScreen screen) {
            screen.advance();
        } else if (minecraft.screen instanceof SceneEditorScreen editor) {
            editor.advance();
        }
        int presses = 0;
        while (PonderEntrypoints.PONDER_KEY.consumeClick()) {
            presses++;
        }
        if (presses > 0 && minecraft.screen == null) {
            PonderEntrypoints.openForLookedAtTarget();
        }
    }
}
