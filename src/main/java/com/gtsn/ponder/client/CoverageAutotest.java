package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.catalog.SceneCoverage;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 全量覆盖（工单 #13）的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=coverage} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后清空进度、热重载场景库，用共享纯分析器 {@link SceneCoverage#analyze} 对<b>全部注册
 *       多方块</b>核算覆盖数字（与 GameTest / headless 单测同一套断言），经 {@code PonderEntrypoints}
 *       的真实解析缝（手作优先、否则按需生成）：</li>
 *   <li>打开图鉴目录，断言 <b>每一台注册多方块都有条目</b>，且 <b>每个条目都能解析到可播场景</b>
 *       （零死链）；截图；</li>
 *   <li>挑一台「无手作场景」的注册多方块，按需生成并播放，断言其 {@code source=auto}、可渲染；
 *       截图；自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class CoverageAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "coverage";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-coverage-autotest";
    private static final String CATALOG_SCREENSHOT = "gtsnponder-coverage-catalog.png";
    private static final String GENERATED_SCREENSHOT = "gtsnponder-coverage-generated.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1800;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, CATALOG, PLAY, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static String playTarget;

    private CoverageAutotest() {
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
            case CATALOG -> tickCatalog(minecraft);
            case PLAY -> tickPlay(minecraft);
            case GRAB -> tickGrab(minecraft);
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
        LOGGER.info("[GTSNPonder] coverage autotest: {}={} -> loading world '{}'",
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
        PonderProgress.get().reset();
        SceneLibrary.get().reload();

        List<String> registered = PonderEntrypoints.registeredMultiblockTargets();
        if (registered.isEmpty()) {
            fail(minecraft, "no registered GT multiblocks were enumerated");
            return;
        }
        SceneCoverage.Report report = SceneCoverage.analyze(registered,
                PonderEntrypoints::resolveSceneForTarget);
        LOGGER.info("[GTSNPonder] coverage autotest: {}", report.summary());
        if (report.registered() != registered.size()) {
            fail(minecraft, "registered count mismatch: report=" + report.registered()
                    + " enumerated=" + registered.size());
            return;
        }
        if (!report.coversEveryMultiblock()) {
            fail(minecraft, "dead links (registered multiblocks without a scene): " + report.deadLinks());
            return;
        }
        if (!report.curatedNarrationComplete()) {
            fail(minecraft, "curated key machines without hand narration: "
                    + report.curatedWithoutHandNarration());
            return;
        }
        if (!report.isComplete()) {
            fail(minecraft, "coverage incomplete: " + report.summary());
            return;
        }
        if (report.curatedHandAuthored() != SceneCoverage.CURATED.size()) {
            fail(minecraft, "expected " + SceneCoverage.CURATED.size() + " curated hand-authored scenes, got "
                    + report.curatedHandAuthored());
            return;
        }
        LOGGER.info("[GTSNPonder] coverage autotest PASS(numbers): {} registered multiblocks resolve; "
                + "{} hand-authored, {} generated; {} curated key machines hand-narrated; {} dead links",
                report.resolved(), report.handAuthored(), report.generated(),
                report.curatedHandAuthored(), report.deadLinkCount());

        // 重生成缝（fork 升级后重生成并 diff）：/gtsnponder dumpall 的底层入口。
        if (!verifyRegenerationDump(minecraft, registered)) {
            return;
        }
        if (!PonderEntrypoints.openCatalog()) {
            fail(minecraft, "could not open the catalog");
            return;
        }
        stage = Stage.CATALOG;
        ticks = 0;
    }

    /**
     * 重生成缝：把全部注册多方块的自动生成场景批量导出到稳定目录，断言写出文件数 = 注册数
     * （供 GT fork 升级后重生成并 diff）。
     */
    private static boolean verifyRegenerationDump(Minecraft minecraft, List<String> registered) {
        Optional<Path> directory = PonderEntrypoints.dumpAllGenerated();
        if (directory.isEmpty()) {
            fail(minecraft, "dumpAllGenerated() produced no output directory");
            return false;
        }
        Path dir = directory.get();
        long written = 0;
        for (String target : registered) {
            Path file = dir.resolve(com.gtsn.ponder.generate.GeneratedKeys.sanitize(target) + ".json");
            if (!Files.isRegularFile(file)) {
                fail(minecraft, "regeneration dump is missing a scene file for " + target + " (" + file + ")");
                return false;
            }
            written++;
        }
        long total;
        try (Stream<Path> files = Files.list(dir)) {
            total = files.filter(path -> path.toString().endsWith(".json")).count();
        } catch (IOException failure) {
            fail(minecraft, "could not list the regeneration dump directory " + dir + ": " + failure);
            return false;
        }
        LOGGER.info("[GTSNPonder] coverage autotest: regeneration dump wrote a scene for every one of {} "
                + "registered multiblock(s) to {} ({} json file(s) total)", written, dir, total);
        return true;
    }

    private static void tickCatalog(Minecraft minecraft) {
        if (!(minecraft.screen instanceof SceneCatalogScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "catalog did not open; current screen=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 4) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "catalog never rendered (frames=" + screen.renderedFrames() + ")");
            }
            return;
        }
        SceneCatalog catalog = screen.catalog();
        Set<String> catalogTargets = new HashSet<>();
        for (CatalogEntry entry : catalog.entries()) {
            catalogTargets.add(entry.target());
        }
        List<String> registered = PonderEntrypoints.registeredMultiblockTargets();
        for (String target : registered) {
            if (!catalogTargets.contains(target)) {
                fail(minecraft, "catalog is missing a registered multiblock entry: " + target);
                return;
            }
        }
        // 无死链：目录每个条目都能经真实解析缝解析到可播场景。
        for (CatalogEntry entry : catalog.entries()) {
            Optional<SceneData> resolved = PonderEntrypoints.resolveSceneForTarget(entry.target());
            if (resolved.isEmpty()) {
                fail(minecraft, "catalog entry is a dead link (does not resolve): " + entry.target());
                return;
            }
        }
        LOGGER.info("[GTSNPonder] coverage autotest: catalog covers all {} registered multiblocks "
                + "({} entries total); every entry resolves", registered.size(), catalog.total());
        grabScreenshot(minecraft, CATALOG_SCREENSHOT);

        Set<String> curated = new HashSet<>(SceneCoverage.curatedTargets());
        playTarget = registered.stream().filter(target -> !curated.contains(target)).findFirst().orElse(null);
        if (playTarget == null) {
            fail(minecraft, "no generated-only registered multiblock to play");
            return;
        }
        if (!PonderEntrypoints.openForTarget(playTarget)) {
            fail(minecraft, "could not open the generated scene for " + playTarget);
            return;
        }
        stage = Stage.PLAY;
        ticks = 0;
    }

    private static void tickPlay(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "generated scene player did not open; screen=" + minecraft.screen);
            }
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        if (!playTarget.equals(player.scene().target())) {
            fail(minecraft, "player opened the wrong target: expected " + playTarget
                    + " got " + player.scene().target());
            return;
        }
        if (player.scene().source() != Source.AUTO) {
            fail(minecraft, playTarget + " scene must be source=auto (on-demand generation), got "
                    + player.scene().source());
            return;
        }
        if (player.scene().steps().isEmpty()) {
            fail(minecraft, playTarget + " generated scene has no steps");
            return;
        }
        LOGGER.info("[GTSNPonder] coverage autotest: on-demand generated scene '{}' plays ({} steps, source={})",
                playTarget, player.scene().steps().size(), player.scene().source());
        grabScreenshot(minecraft, GENERATED_SCREENSHOT);
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        LOGGER.info("[GTSNPonder] coverage autotest PASS: every registered multiblock has a playable scene, "
                + "curated key machines are hand-narrated, zero dead links; screenshots {} / {}",
                CATALOG_SCREENSHOT, GENERATED_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] coverage autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] coverage autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-coverage-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] coverage autotest screenshot: {}", message.getString()));
    }

    private static ServerPlayer serverPlayer(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    private static void prepareWindow(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        GLFW.glfwShowWindow(window.getWindow());
        GLFW.glfwRestoreWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] coverage autotest window prepared: {}x{} guiScale=2",
                window.getWidth(), window.getHeight());
    }

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
