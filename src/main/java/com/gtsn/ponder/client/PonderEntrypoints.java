package com.gtsn.ponder.client;

import com.gtsn.ponder.catalog.SingleBlockScenes;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.editor.EditorSession;
import com.gtsn.ponder.editor.SceneDraft;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
import com.gtsn.ponder.gt.GtMultiblockCatalog;
import com.gtsn.ponder.gt.GtSingleBlockAdapter;
import com.gtsn.ponder.gt.GtStructureAdapter;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import com.gtsn.ponder.structure.StructureSource;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /** 思索图鉴目录快捷键（默认 O；冲突时玩家可自行改绑）。 */
    public static final KeyMapping CATALOG_KEY = new KeyMapping(
            "key.gtsnponder.catalog", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O,
            "key.categories.gtsnponder");

    /** 注视判定距离（格）。 */
    public static final double REACH = 5.0d;

    /** 生成场景导出目录（相对 gameDir）：fork 升级后可重生成并 diff 的稳定输出目录。 */
    public static final String GENERATED_DIRECTORY = "gtsnponder-generated";

    private static final Logger LOGGER = LogUtils.getLogger();

    private PonderEntrypoints() {
    }

    /**
     * 全部注册的多方块目标 id（稳定排序）——全量覆盖的枚举来源，全部经唯一适配包
     * {@link GtMultiblockCatalog}（GT import 仍只在该包）。
     */
    public static List<String> registeredMultiblockTargets() {
        List<String> targets = new ArrayList<>();
        for (GtMultiblockCatalog.Multiblock machine : GtMultiblockCatalog.all()) {
            targets.add(machine.id());
        }
        return List.copyOf(targets);
    }

    /**
     * 代表性单方块机器目标 id（工单 #15）：来自 {@link SingleBlockScenes#REPRESENTATIVE}，并过滤为
     * 经适配器 {@link GtSingleBlockAdapter} 真正可解析者（保证目录条目零死链）。
     */
    public static List<String> representativeSingleBlockTargets() {
        List<String> targets = new ArrayList<>();
        for (String target : SingleBlockScenes.REPRESENTATIVE) {
            if (GtSingleBlockAdapter.byId(target).isPresent()) {
                targets.add(target);
            }
        }
        return List.copyOf(targets);
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
     * 思索快捷键的统一入口（工单 #16 缺陷 C）：无界面时按「注视方块」进入，有界面时按「鼠标悬停的
     * 物品」进入（背包 / JEI / EMI）。由 {@code PonderClientEvents} 每 tick 消费快捷键点击后调用。
     */
    public static boolean onPonderKeyPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return openForLookedAtTarget();
        }
        if (!isHoverEntryScreen(minecraft.screen)) {
            return false;
        }
        return openForHoveredItem();
    }

    /** 当前屏是否为「物品悬停入口」屏（原版容器屏，或 JEI/EMI 正悬停某物品的任意屏）。 */
    public static boolean isHoverEntryScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof AbstractContainerScreen<?>) {
            return true;
        }
        return PonderXeiItemHover.get().hoveredItemId().isPresent();
    }

    /**
     * 鼠标悬停物品入口：解析当前光标下的物品注册 id（优先 JEI/EMI 悬停物品，其次原版容器槽），
     * 再按目标 id 打开思索屏。无可解析目标时显示本地化空态提示。
     */
    public static boolean openForHoveredItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            message("ponder.gtsnponder.message.no_world");
            return false;
        }
        Optional<String> itemId = hoveredItemId();
        if (itemId.isEmpty()) {
            message("ponder.gtsnponder.message.no_item");
            return false;
        }
        return openForTarget(itemId.get());
    }

    /**
     * 当前光标下物品的注册 id：<b>先</b>查 XEI（JEI/EMI）登记缝——命中物品列表 / 配方槽；<b>再</b>查
     * 原版容器槽（{@link AbstractContainerScreen#getSlotUnderMouse()}，背包 / 箱子 / 创造栏）。
     * 无悬停物品时空。
     */
    public static Optional<String> hoveredItemId() {
        Optional<String> xei = PonderXeiItemHover.get().hoveredItemId();
        if (xei.isPresent()) {
            return xei;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        return Optional.empty();
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
        Optional<Resolved> resolved = resolve(target, forceGenerate);
        if (resolved.isEmpty()) {
            message("ponder.gtsnponder.message.no_scene", target);
            return false;
        }
        Resolved ready = resolved.get();
        minecraft.execute(() -> minecraft.setScreen(
                new ScenePlayerScreen(ready.scene(), ready.structure(), minecraft.level)));
        return true;
    }

    /**
     * 覆盖验证的解析缝（工单 #13）：解析目标的<b>可播场景</b>——手作场景优先（{@link SceneLibrary}），
     * 否则由真实 GT 多方块结构<b>按需自动生成</b>（{@link SceneGenerator}）。返回空 = 死链（目标不是
     * 可解析的多方块）：{@link com.gtsn.ponder.catalog.SceneCoverage} 据此算出「零死链」。
     *
     * <p>与 {@link #openForTarget} 走同一解析路径，故「目录里能解析 = 真能播」。</p>
     */
    public static Optional<SceneData> resolveSceneForTarget(String target) {
        return resolve(target, false).map(Resolved::scene);
    }

    /** 已解析的场景 + 其结构源（播放屏两者都需要）。 */
    private record Resolved(SceneData scene, StructureSource structure) {
    }

    /**
     * 手作优先 / 按需生成的统一解析（不打开界面）。目标须是可解析的 GT 多方块<b>或单方块机器</b>
     * （手作场景亦需结构源才能渲染）；否则为空（死链）。
     *
     * <ul>
     *   <li><b>多方块</b>：结构源经 {@link GtStructureAdapter}，无手作者用 {@link SceneGenerator}
     *       （搭建演示）；</li>
     *   <li><b>单方块机器</b>（工单 #15）：机器元数据经 {@link GtSingleBlockAdapter}，结构为 1×1×1 的
     *       机器本体（{@link SingleBlockUsageGenerator#structureOf}），无手作者用
     *       {@link SingleBlockUsageGenerator}（使用场景）。</li>
     * </ul>
     */
    private static Optional<Resolved> resolve(String target, boolean forceGenerate) {
        Optional<StructureSource> multiblock = GtStructureAdapter.byId(target);
        if (multiblock.isPresent()) {
            StructureSource source = multiblock.get();
            if (!forceGenerate) {
                SceneData authored = SceneLibrary.get().sceneForTarget(target).orElse(null);
                if (authored != null) {
                    return Optional.of(new Resolved(authored, source));
                }
            }
            return Optional.of(new Resolved(SceneGenerator.generate(source), source));
        }

        Optional<SingleBlockMachineSource> singleBlock = GtSingleBlockAdapter.byId(target);
        if (singleBlock.isPresent()) {
            SingleBlockMachineSource machine = singleBlock.get();
            StructureSource structure = SingleBlockUsageGenerator.structureOf(machine);
            if (!forceGenerate) {
                SceneData authored = SceneLibrary.get().sceneForTarget(target).orElse(null);
                if (authored != null) {
                    return Optional.of(new Resolved(authored, structure));
                }
            }
            return Optional.of(new Resolved(SingleBlockUsageGenerator.generate(machine), structure));
        }
        return Optional.empty();
    }

    /**
     * 图鉴目录入口（③ 目录入口 / 快捷键 / 客户端命令 / 自动测试）：打开 {@link SceneCatalogScreen}，
     * 列出场景库全部可播放场景，支持分类浏览、搜索与已看 / 未看进度。
     */
    public static boolean openCatalog() {
        Minecraft minecraft = Minecraft.getInstance();
        List<SceneData> scenes = SceneLibrary.get().scenes();
        List<String> registeredTargets = registeredMultiblockTargets();
        List<String> usageTargets = representativeSingleBlockTargets();
        minecraft.execute(() -> minecraft.setScreen(
                new SceneCatalogScreen(scenes, registeredTargets, usageTargets)));
        return true;
    }

    /**
     * 编辑器入口（快捷键 / 客户端命令，仅门控通过时）：为注视目标打开游戏内可视化编辑器。
     */
    public static boolean openEditorForLookedAtTarget() {
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
        return openEditor(blockId);
    }

    /**
     * 确定性编辑器入口（客户端命令 {@code /gtsnponder editor [target]} / 自动测试）：为目标打开编辑器。
     * 门控未通过（正式玩家默认）时拒绝并提示。草稿种子：已有手作 / 作者场景优先，否则自动生成基线
     * （「导出自动场景为草稿」后即可编辑）。
     */
    public static boolean openEditor(String target) {
        if (!PonderEditorAccess.isEditorEnabled()) {
            message(EditorKeys.GATED);
            return false;
        }
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
        SceneData seed = SceneLibrary.get().sceneForTarget(target)
                .orElseGet(() -> SceneGenerator.generate(source));
        EditorSession session = EditorSession.startingFrom(seed, SceneLibrary.get().authorDirectory());
        minecraft.execute(() -> minecraft.setScreen(new SceneEditorScreen(source, minecraft.level, session)));
        return true;
    }

    /**
     * 开发 / 作者入口：把目标的自动生成场景导出为<b>手作草稿</b>并写入可写作者目录（场景库随即热重载），
     * 使作者从生成基线开始编辑。返回写出文件路径；门控未通过 / 目标无效时为空。
     */
    public static Optional<Path> exportGeneratedDraft(String target) {
        if (!PonderEditorAccess.isEditorEnabled()) {
            message(EditorKeys.GATED);
            return Optional.empty();
        }
        Optional<StructureSource> structure = GtStructureAdapter.byId(target);
        if (structure.isEmpty()) {
            message("ponder.gtsnponder.message.no_scene", target);
            return Optional.empty();
        }
        SceneData draft = SceneDraft.from(SceneGenerator.generate(structure.get())).toSceneData();
        try {
            Path file = SceneLibrary.get().saveAuthorScene(draft);
            message("ponder.gtsnponder.message.dump", file.toString());
            return Optional.of(file);
        } catch (IOException failure) {
            message("ponder.gtsnponder.message.dump_failed", String.valueOf(failure.getMessage()));
            return Optional.empty();
        }
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
        SceneData scene = SceneGenerator.generate(structure.get());
        try {
            Path directory = generatedDirectory();
            Files.createDirectories(directory);
            Path file = writeGeneratedScene(directory, target, scene);
            message("ponder.gtsnponder.message.dump", file.toString());
            return Optional.of(file);
        } catch (IOException failure) {
            message("ponder.gtsnponder.message.dump_failed", String.valueOf(failure.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * 开发 / 维护入口（工单 #13 的重生成缝）：把<b>全部注册多方块</b>的自动生成场景 JSON 批量导出到
     * {@code <gameDir>/gtsnponder-generated/}（稳定文件名），供 GT fork 升级后重生成并 diff。
     * 返回输出目录；写出失败为空。
     */
    public static Optional<Path> dumpAllGenerated() {
        List<String> targets = registeredMultiblockTargets();
        try {
            Path directory = generatedDirectory();
            Files.createDirectories(directory);
            int written = 0;
            int skipped = 0;
            for (String target : targets) {
                Optional<StructureSource> structure = GtStructureAdapter.byId(target);
                if (structure.isEmpty()) {
                    skipped++;
                    continue;
                }
                writeGeneratedScene(directory, target, SceneGenerator.generate(structure.get()));
                written++;
            }
            LOGGER.info("[GTSNPonder] dumped {} generated scene(s) to {} ({} ungeneratable target(s), "
                    + "{} registered)", written, directory, skipped, targets.size());
            message("ponder.gtsnponder.message.dump", directory.toString());
            return Optional.of(directory);
        } catch (IOException failure) {
            message("ponder.gtsnponder.message.dump_failed", String.valueOf(failure.getMessage()));
            return Optional.empty();
        }
    }

    /** 生成场景稳定输出目录（{@code <gameDir>/gtsnponder-generated}）。 */
    public static Path generatedDirectory() {
        return new File(Minecraft.getInstance().gameDirectory, GENERATED_DIRECTORY).toPath();
    }

    /** 写出单个生成场景（稳定文件名 = 清洗后的目标 id + {@code .json}）。 */
    private static Path writeGeneratedScene(Path directory, String target, SceneData scene) throws IOException {
        Path file = directory.resolve(GeneratedKeys.sanitize(target) + ".json");
        Files.writeString(file, SceneDataWriter.toJson(scene), StandardCharsets.UTF_8);
        return file;
    }

    private static void message(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), false);
        }
    }
}
