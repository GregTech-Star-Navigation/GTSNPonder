package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.gt.GtStructureAdapter;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureSource;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 自动生成场景的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=autogen} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后，从真实 GT 注册表解析 <b>三台代表性多方块</b>（小 / 中 / 大各一，见候选表），
 *       记录其方块数与生成模式（逐层 / 角色分组 LOD）；</li>
 *   <li>对每台机器<b>强制自动生成</b>（{@link SceneGenerator}）并打开播放
 *       （{@link PonderEntrypoints#openGenerated(String)}）；</li>
 *   <li>断言生成产物头部为 {@code source=auto} + {@link SceneGenerator#GENERATOR_VERSION}、
 *       有步骤、渲染出方块；并断言同一结构两次生成字节相等 JSON（确定性）；</li>
 *   <li>每台机器推进到 {@value #CAPTURE_TIME} tick（搭建 + 高亮已呈现）后抓一张截图到
 *       {@code run/screenshots/}；</li>
 *   <li>全部完成后自动退出。</li>
 * </ol>
 *
 * <p>抓到的截图是<b>人工教学质量评审</b>的输入（人门，非自动判定）。任何断言失败 / 超时都记录
 * FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class AutogenAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "autogen";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-autogen-autotest";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;
    private static final int CAPTURE_SETTLE_TICKS = 20;

    /**
     * 「揭示」抓图时刻：等所有 {@code build.*} 分段都已显示（结构完全呈现、尚未进入成型演示）时抓图，
     * 使相机取景（按完整包围盒自适应）与成型图一致地填满视口；此时旁白仍是机器特定文案。
     */
    private static final int REVEAL_SETTLE_TICKS = 4;

    /** 小 / 中候选：按顺序取第一个真实存在者。 */
    private static final List<String> SMALL_CANDIDATES = List.of(
            "gtceu:coke_oven", "gtceu:steam_oven", "gtceu:steam_grinder", "gtceu:multi_smelter");
    private static final List<String> MEDIUM_CANDIDATES = List.of(
            "gtceu:assembly_line", "gtceu:electric_blast_furnace", "gtceu:pyrolyse_oven",
            "gtceu:primitive_blast_furnace", "gtceu:vacuum_freezer");
    /** 大候选：取其中方块数最多者（确保真·大机器，通常超过 {@link SceneGenerator#CELL_BUDGET} → LOD）。 */
    private static final List<String> LARGE_CANDIDATES = List.of(
            "gtceu:cleanroom", "gtceu:assembly_line", "gtceu:electric_blast_furnace");

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, OPEN, REVEAL, REVEAL_GRAB, FORMED, FORMED_GRAB, DONE, FAILED
    }

    private record TargetScene(String target, StructureSource source, boolean roleGrouped) {
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static final List<TargetScene> targets = new ArrayList<>();
    private static int index;

    private AutogenAutotest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!MODE.equals(System.getenv(AUTOTEST_ENV))) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        switch (stage) {
            case TITLE -> tickTitle(minecraft);
            case WORLD -> tickWorld(minecraft);
            case OPEN -> tickOpen(minecraft);
            case REVEAL -> tickReveal(minecraft);
            case REVEAL_GRAB -> tickRevealGrab(minecraft);
            case FORMED -> tickFormed(minecraft);
            case FORMED_GRAB -> tickFormedGrab(minecraft);
            case DONE, FAILED -> tickStop(minecraft);
        }
    }

    private static void tickTitle(Minecraft minecraft) {
        if (!minecraft.isRunning() || minecraft.getOverlay() != null
                || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        prepareWindow(minecraft);
        loadOrCreateWorld(minecraft);
        LOGGER.info("[GTSNPonder] autogen autotest: {}={} -> loading world '{}'",
                AUTOTEST_ENV, MODE, LEVEL_NAME);
        stage = Stage.WORLD;
        ticks = 0;
    }

    private static void tickWorld(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                || serverPlayer(minecraft) == null || minecraft.level == null) {
            if (ticks > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "timed out waiting for the integrated world (ticks=" + ticks + ")");
            }
            return;
        }
        targets.clear();
        resolveFirst("small", SMALL_CANDIDATES);
        resolveFirst("medium", MEDIUM_CANDIDATES);
        resolveLargest("large", LARGE_CANDIDATES);
        if (targets.size() < 3) {
            fail(minecraft, "resolved only " + targets.size() + " of 3 representative multiblocks");
            return;
        }
        if (!verifyDeterminism(minecraft)) {
            return;
        }
        index = 0;
        LOGGER.info("[GTSNPonder] autogen autotest: {} target(s) resolved -> {}", targets.size(), targetNames());
        if (!openCurrent(minecraft)) {
            return;
        }
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "scene player did not open; current screen=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 6) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "scene player never rendered (frames=" + screen.renderedFrames() + ")");
            }
            return;
        }
        SceneData scene = screen.scene();
        if (scene.source() != Source.AUTO) {
            fail(minecraft, "generated scene source must be auto, got " + scene.source());
            return;
        }
        if (!SceneGenerator.GENERATOR_VERSION.equals(scene.generatorVersion())) {
            fail(minecraft, "unexpected generatorVersion: " + scene.generatorVersion());
            return;
        }
        if (scene.steps().isEmpty()) {
            fail(minecraft, "generated scene has no steps for " + scene.target());
            return;
        }
        LOGGER.info("[GTSNPonder] autogen autotest: playing generated '{}' ({} blocks, mode={}, steps={})",
                scene.target(), targets.get(index).source().blockCount(),
                targets.get(index).roleGrouped() ? "role-grouped/LOD" : "layer-by-layer",
                scene.steps().size());
        stage = Stage.REVEAL;
        ticks = 0;
    }

    /** 结构完全揭示（所有 build 分段可见）后抓一张（展示搭建完成的未成型形态）。 */
    private static void tickReveal(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        double target = buildCompleteTime(screen);
        if (screen.playback().time() < target || screen.bridge().visibleBlockCount() <= 0) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "reveal never became capturable (t=" + screen.playback().time()
                        + " / target=" + target + ", visibleBlocks=" + screen.bridge().visibleBlockCount() + ")");
            }
            return;
        }
        if (ticks < REVEAL_SETTLE_TICKS) {
            return;
        }
        capture(minecraft, "reveal");
        stage = Stage.REVEAL_GRAB;
        ticks = 0;
    }

    /**
     * 结构完全揭示的时刻 = 最后一个 {@code build.*} 分段的起始时刻（该分段随即生效，结构完整可见，
     * 且尚未进入控制器 / 仓口高亮步骤——与「成型」截图（高亮 + 成型脉冲后）明显区分）。
     */
    private static double buildCompleteTime(ScenePlayerScreen screen) {
        List<SceneStep> steps = screen.scene().steps();
        int lastBuild = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().startsWith("build.")) {
                lastBuild = i;
            }
        }
        return lastBuild < 0 ? 0.0d : screen.playback().stepStartTime(lastBuild);
    }

    private static void tickRevealGrab(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        stage = Stage.FORMED;
        ticks = 0;
    }

    /** 装配完成 / 成型关键帧抓一张（展示最终形态）。 */
    private static void tickFormed(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (!screen.playback().isComplete()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "playback never completed (t=" + screen.playback().time()
                        + " / " + screen.playback().totalTime() + ")");
            }
            return;
        }
        if (screen.bridge().visibleBlockCount() <= 0) {
            fail(minecraft, "generated scene rendered no visible blocks when formed");
            return;
        }
        capture(minecraft, "formed");
        stage = Stage.FORMED_GRAB;
        ticks = 0;
    }

    private static void tickFormedGrab(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        index++;
        if (index >= targets.size()) {
            LOGGER.info("[GTSNPonder] autogen autotest PASS: generated + played {} machine(s), "
                    + "2 screenshots each in run/screenshots/ ({})", targets.size(), targetNames());
            stage = Stage.DONE;
            ticks = 0;
            return;
        }
        if (!openCurrent(minecraft)) {
            return;
        }
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void capture(Minecraft minecraft, String phase) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        TargetScene target = targets.get(index);
        String name = screenshotName(index, target.target(), phase);
        var viewport = screen.viewportBounds();
        LOGGER.info("[GTSNPonder] autogen autotest: {} t={} step={} visibleBlocks={} narration='{}'",
                phase, screen.playback().time(), screen.playback().stepIndex(),
                screen.bridge().visibleBlockCount(), screen.narrationText().text());
        LOGGER.info("[GTSNPonder] autogen autotest: {} framing margin={} zoom={} viewport={}x{} "
                        + "(structure {}x{}x{}) -> {}",
                phase, screen.viewport().framingMargin(), screen.viewport().cameraZoom(),
                viewport.width(), viewport.height(),
                target.source().sizeX(), target.source().sizeY(), target.source().sizeZ(), name);
        grabScreenshot(minecraft, name);
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] autogen autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    /** 打开当前下标机器的<b>强制自动生成</b>场景。 */
    private static boolean openCurrent(Minecraft minecraft) {
        TargetScene target = targets.get(index);
        if (!PonderEntrypoints.openGenerated(target.target())) {
            fail(minecraft, "could not open the generated scene for " + target.target());
            return false;
        }
        return true;
    }

    /** 解析候选表中第一个真实存在的多方块；全部缺席时记录并跳过。 */
    private static void resolveFirst(String tier, List<String> candidates) {
        for (String candidate : candidates) {
            var source = GtStructureAdapter.byId(candidate);
            if (source.isPresent()) {
                boolean roleGrouped = SceneGenerator.isRoleGrouped(source.get());
                targets.add(new TargetScene(candidate, source.get(), roleGrouped));
                LOGGER.info("[GTSNPonder] autogen autotest: {} tier -> {} ({} blocks, mode={})",
                        tier, candidate, source.get().blockCount(),
                        roleGrouped ? "role-grouped/LOD" : "layer-by-layer");
                logMaterials(tier, source.get());
                return;
            }
        }
        LOGGER.warn("[GTSNPonder] autogen autotest: no {} multiblock resolved from {}", tier, candidates);
    }

    /** 解析候选表中方块数最多的真实多方块（大机器代表）。 */
    private static void resolveLargest(String tier, List<String> candidates) {
        TargetScene best = null;
        for (String candidate : candidates) {
            var source = GtStructureAdapter.byId(candidate);
            if (source.isPresent() && (best == null || source.get().blockCount() > best.source().blockCount())) {
                best = new TargetScene(candidate, source.get(), SceneGenerator.isRoleGrouped(source.get()));
            }
        }
        if (best == null) {
            LOGGER.warn("[GTSNPonder] autogen autotest: no {} multiblock resolved from {}", tier, candidates);
            return;
        }
        targets.add(best);
        LOGGER.info("[GTSNPonder] autogen autotest: {} tier -> {} ({} blocks, mode={})",
                tier, best.target(), best.source().blockCount(),
                best.roleGrouped() ? "role-grouped/LOD" : "layer-by-layer");
        logMaterials(tier, best.source());
    }

    /**
     * 记录结构的方块材质直方图：人工评审可据此核对预览是否使用了各机器<b>真实的机壳材质</b>
     * （如焦炉砖 / 装配线机壳 / 洁净室材质），而非退化为通用灰石。
     */
    private static void logMaterials(String tier, StructureSource source) {
        Map<String, Integer> histogram = new TreeMap<>();
        for (StructureBlock block : source.blocks()) {
            histogram.merge(block.blockId(), 1, Integer::sum);
        }
        LOGGER.info("[GTSNPonder] autogen autotest: {} materials of {} -> {}",
                tier, source.id(), histogram);
    }

    /** 断言同一结构两次生成产生字节相等 JSON（确定性）。 */
    private static boolean verifyDeterminism(Minecraft minecraft) {
        for (TargetScene target : targets) {
            String first = SceneDataWriter.toJson(SceneGenerator.generate(target.source()));
            String second = SceneDataWriter.toJson(SceneGenerator.generate(target.source()));
            if (!first.equals(second)) {
                fail(minecraft, "generation is not deterministic for " + target.target());
                return false;
            }
        }
        LOGGER.info("[GTSNPonder] autogen autotest: determinism verified (byte-equal JSON for all targets)");
        return true;
    }

    private static String screenshotName(int position, String target, String phase) {
        return "gtsnponder-autogen-" + (position + 1) + "-" + target.replace(':', '_') + "-" + phase + ".png";
    }

    private static List<String> targetNames() {
        return targets.stream().map(TargetScene::target).toList();
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] autogen autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-autogen-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] autogen autotest screenshot: {}", message.getString()));
    }

    private static ServerPlayer serverPlayer(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    /** 固定窗口 1280x720 + GUI 缩放 2（与其它自动测试一致）。 */
    private static void prepareWindow(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        GLFW.glfwShowWindow(window.getWindow());
        GLFW.glfwRestoreWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] autogen autotest window prepared: {}x{} guiScale=2",
                window.getWidth(), window.getHeight());
    }

    /** 创建 / 载入固定存档（首次创建，之后复用，自动测试可重复）。 */
    private static void loadOrCreateWorld(Minecraft minecraft) {
        LevelStorageSource levelSource = minecraft.getLevelSource();
        if (levelSource.levelExists(LEVEL_NAME)) {
            minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), LEVEL_NAME);
            return;
        }
        LevelSettings settings = new LevelSettings(LEVEL_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(LEVEL_NAME, settings,
                WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions);
    }
}
