package com.gtsn.ponder.client;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.generate.SceneGenerator;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * 确定性入口（客户端命令 / 自动测试）：为目标 id 打开思索屏。若该目标没有手作场景
     * （{@link SceneLibrary} 未注册），则<b>按需自动生成</b>一个（{@link SceneGenerator}）再打开。
     */
    public static boolean openForTarget(String target) {
        return openForTarget(target, false);
    }

    /**
     * 强制自动生成的确定性入口：忽略手作场景，始终由结构源生成并打开（开发者 / 自动测试用，
     * 以便对「自动产物」本身做教学质量评审）。
     */
    public static boolean openGenerated(String target) {
        return openForTarget(target, true);
    }

    private static boolean openForTarget(String target, boolean forceGenerate) {
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
        StructureSource source = structure.get();
        SceneData scene = forceGenerate ? null : SceneLibrary.get().sceneForTarget(target).orElse(null);
        if (scene == null) {
            scene = SceneGenerator.generate(source);
        }
        final SceneData sceneToOpen = scene;
        final StructureSource sourceToOpen = source;
        minecraft.execute(() -> minecraft.setScreen(
                new ScenePlayerScreen(sceneToOpen, sourceToOpen, minecraft.level)));
        return true;
    }

    /**
     * 开发入口：把目标自动生成的场景 JSON 导出到 {@code run/gtsnponder-generated/}（可重生成产物，
     * 供作者以草稿为起点手作，或对生成器 diff）。返回导出文件路径；失败为空。
     */
    public static Optional<Path> dumpGenerated(String target) {
        Optional<StructureSource> structure = GtStructureAdapter.byId(target);
        if (structure.isEmpty()) {
            message("ponder.gtsnponder.message.no_scene", target);
            return Optional.empty();
        }
        String json = SceneDataWriter.toJson(SceneGenerator.generate(structure.get()));
        File directory = new File(Minecraft.getInstance().gameDirectory, "gtsnponder-generated");
        try {
            Files.createDirectories(directory.toPath());
            Path file = new File(directory, target.replace(':', '_') + ".json").toPath();
            Files.writeString(file, json, StandardCharsets.UTF_8);
            message("ponder.gtsnponder.message.dump", file.toString());
            return Optional.of(file);
        } catch (IOException failure) {
            message("ponder.gtsnponder.message.dump_failed", String.valueOf(failure.getMessage()));
            return Optional.empty();
        }
    }

    private static void message(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), false);
        }
    }
}
