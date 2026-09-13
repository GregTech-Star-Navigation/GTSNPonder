package com.gtsn.ponder.client;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.gt.GtStructureAdapter;
import com.gtsn.ponder.structure.StructureSource;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * 思索的客户端入口：快捷键与客户端命令共用的「打开注视目标 / 指定目标的思索屏」逻辑。
 *
 * <p>注视 → 目标 id：对玩家视线做方块拾取，取方块注册名（如 {@code gtceu:coke_oven}），再经
 * 唯一 GT 适配包 {@link GtStructureAdapter} 判定其是否为多方块并取得结构源。GT 类型引用仍全部
 * 收敛于 {@code com.gtsn.ponder.gt}；本类只调用其门面。</p>
 *
 * <p>快捷键 {@link #PONDER_KEY} 由 {@code PonderKeyMappings} 注册（未绑定时不触发），
 * {@link #openForTarget(String)} 同时是客户端命令与自动测试的确定性入口。</p>
 *
 * <p>客户端专用类。</p>
 */
public final class PonderEntrypoints {

    /** 思索快捷键（默认 P；冲突时玩家可自行改绑）。 */
    public static final KeyMapping PONDER_KEY = new KeyMapping(
            "key.gtsnponder.ponder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            "key.categories.gtsnponder");

    /** 注视判定距离（格）。 */
    public static final double REACH = 5.0d;

    private PonderEntrypoints() {
    }

    /** 快捷键入口：打开玩家注视目标（GT 多方块）的思索屏。 */
    public static boolean openForLookedAtTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            message("ponder.gtsnponder.message.no_world");
            return false;
        }
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            message("ponder.gtsnponder.message.no_target");
            return false;
        }
        BlockPos pos = blockHit.getBlockPos();
        String blockId = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock()).toString();
        return openForTarget(blockId);
    }

    /** 确定性入口（客户端命令 / 自动测试）：为目标 id 打开思索屏。 */
    public static boolean openForTarget(String target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            message("ponder.gtsnponder.message.no_world");
            return false;
        }
        Optional<StructureSource> structure = GtStructureAdapter.byId(target);
        if (structure.isEmpty()) {
            message("ponder.gtsnponder.message.no_scene", target);
            return false;
        }
        SceneData scene = SceneLibrary.get().sceneForTarget(target).orElse(null);
        if (scene == null) {
            message("ponder.gtsnponder.message.no_scene", target);
            return false;
        }
        StructureSource source = structure.get();
        minecraft.execute(() -> minecraft.setScreen(new ScenePlayerScreen(scene, source, minecraft.level)));
        return true;
    }

    private static void message(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), false);
        }
    }
}
