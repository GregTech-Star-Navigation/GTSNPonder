package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.engine.director.SceneRunner;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.gt.GtStructureAdapter;
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

import java.util.List;

/**
 * 内容工单 #10（发电·能量网 / 物流管网）的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=systems} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后清空进度、热重载场景库，断言两类内容的<b>真实 GT 目标</b>都能经适配器解析、
 *       且场景库中存在对应的随包 {@code source=mixed} 场景；</li>
 *   <li>打开目录：断言「发电与能量」与「物流与管网」两个类别都存在且各有期望目标；截图；</li>
 *   <li>「相关机器」导航：点击某条目的「相关机器」→ 断言可见条目切为其相关条目（含同类别兄弟）、
 *       点「全部」恢复；截图；</li>
 *   <li>播放发电主场景：断言目标 / 来源 / 步骤数与推进到「线缆熔断」旁白键；截图；</li>
 *   <li>播放物流主场景：断言步骤数与推进到「覆盖板」旁白键；截图；自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class SystemsAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "systems";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-systems-autotest";
    private static final String CATALOG_SCREENSHOT = "gtsnponder-systems-catalog.png";
    private static final String RELATED_SCREENSHOT = "gtsnponder-systems-related.png";
    private static final String POWER_SCREENSHOT = "gtsnponder-systems-power.png";
    private static final String LOGISTICS_SCREENSHOT = "gtsnponder-systems-logistics.png";

    private static final List<String> POWER_TARGETS = List.of(
            "gtceu:large_combustion_engine", "gtceu:active_transformer");
    private static final List<String> LOGISTICS_TARGETS = List.of(
            "gtceu:steel_multiblock_tank", "gtceu:primitive_pump");

    private static final String POWER_PLAY_TARGET = "gtceu:large_combustion_engine";
    private static final String POWER_RELATED_TARGET = "gtceu:active_transformer";
    private static final String LOGISTICS_PLAY_TARGET = "gtceu:steel_multiblock_tank";

    private static final String POWER_LAST_NARRATION = "ponder.gtsnponder.power.narration.burning";
    private static final String LOGISTICS_LAST_NARRATION = "ponder.gtsnponder.logistics.narration.covers";
    private static final int POWER_STEPS = 9;
    private static final int LOGISTICS_STEPS = 10;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, CATALOG, PLAY_POWER, PLAY_LOGISTICS, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static int catalogPhase;

    private SystemsAutotest() {
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
            case PLAY_POWER -> tickPlayPower(minecraft);
            case PLAY_LOGISTICS -> tickPlayLogistics(minecraft);
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
        LOGGER.info("[GTSNPonder] systems autotest: {}={} -> loading world '{}'",
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

        for (String target : POWER_TARGETS) {
            if (!verifyTargetAndScene(minecraft, target, SceneCategories.POWER)) {
                return;
            }
        }
        for (String target : LOGISTICS_TARGETS) {
            if (!verifyTargetAndScene(minecraft, target, SceneCategories.LOGISTICS)) {
                return;
            }
        }
        LOGGER.info("[GTSNPonder] systems autotest: 2 categories x 2 real targets resolve and are bundled; "
                + "opening catalog");
        if (!PonderEntrypoints.openCatalog()) {
            fail(minecraft, "could not open the catalog");
            return;
        }
        catalogPhase = 0;
        stage = Stage.CATALOG;
        ticks = 0;
    }

    /** 断言目标经适配器解析、场景库有其随包 mixed 场景、且该场景落入期望类别。 */
    private static boolean verifyTargetAndScene(Minecraft minecraft, String target, String category) {
        if (GtStructureAdapter.byId(target).isEmpty()) {
            fail(minecraft, target + " did not resolve through GtStructureAdapter (entry points would 404)");
            return false;
        }
        SceneData scene = SceneLibrary.get().sceneForTarget(target).orElse(null);
        if (scene == null) {
            fail(minecraft, "no bundled ponder scene for " + target);
            return false;
        }
        if (scene.source() != Source.MIXED) {
            fail(minecraft, target + " scene must be source=mixed (auto structure + hand narration), got "
                    + scene.source());
            return false;
        }
        if (!category.equals(SceneCategories.categoryOf(scene))) {
            fail(minecraft, target + " classified as " + SceneCategories.categoryOf(scene)
                    + ", expected " + category);
            return false;
        }
        LOGGER.info("[GTSNPonder] systems autotest: {} -> {} steps, source={}, category={}",
                target, scene.steps().size(), scene.source(), category);
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
        if (!catalog.categories().contains(SceneCategories.POWER)
                || !catalog.categories().contains(SceneCategories.LOGISTICS)) {
            fail(minecraft, "both content categories must appear in the catalog: " + catalog.categories());
            return;
        }
        for (String target : POWER_TARGETS) {
            if (catalog.inCategory(SceneCategories.POWER).stream()
                    .noneMatch(entry -> target.equals(entry.target()))) {
                fail(minecraft, target + " is not listed under the power category");
                return;
            }
        }
        for (String target : LOGISTICS_TARGETS) {
            if (catalog.inCategory(SceneCategories.LOGISTICS).stream()
                    .noneMatch(entry -> target.equals(entry.target()))) {
                fail(minecraft, target + " is not listed under the logistics category");
                return;
            }
        }
        LOGGER.info("[GTSNPonder] systems autotest: catalog has {} entries across {} categories",
                catalog.total(), catalog.categories());
        if (catalogPhase == 0) {
            grabScreenshot(minecraft, CATALOG_SCREENSHOT);

            CatalogEntry anchor = screen.visibleEntries().stream()
                    .filter(entry -> POWER_PLAY_TARGET.equals(entry.target()))
                    .findFirst().orElse(null);
            if (anchor == null) {
                fail(minecraft, "catalog is missing the primary power entry " + POWER_PLAY_TARGET);
                return;
            }
            int index = screen.visibleEntries().indexOf(anchor);
            List<CatalogEntry> expectedRelated = catalog.relatedTo(anchor);
            if (expectedRelated.isEmpty()) {
                fail(minecraft, "related-machine navigation resolved no sibling for " + POWER_PLAY_TARGET);
                return;
            }
            clickWidget(minecraft, screen.visibleRelatedButtons().get(index));
            if (screen.relatedAnchor() == null || !POWER_PLAY_TARGET.equals(screen.relatedAnchor().target())) {
                fail(minecraft, "clicking 'related' did not anchor on " + POWER_PLAY_TARGET
                        + " (anchor=" + (screen.relatedAnchor() == null ? "null"
                                : screen.relatedAnchor().target()) + ")");
                return;
            }
            if (!screen.visibleEntries().equals(expectedRelated)) {
                fail(minecraft, "related navigation did not show the derived related entries: shown="
                        + screen.visibleEntries().stream().map(CatalogEntry::target).toList()
                        + " expected=" + expectedRelated.stream().map(CatalogEntry::target).toList());
                return;
            }
            if (screen.visibleEntries().stream().noneMatch(entry -> POWER_RELATED_TARGET.equals(entry.target()))) {
                fail(minecraft, "related navigation is missing the same-category sibling "
                        + POWER_RELATED_TARGET);
                return;
            }
            LOGGER.info("[GTSNPonder] systems autotest: related navigation -> {}",
                    screen.visibleEntries().stream().map(CatalogEntry::target).toList());
            // 让「相关机器」视图至少渲染一帧再截图（截图取上一帧），使证据如实反映导航结果。
            catalogPhase = 1;
            ticks = 0;
            return;
        }
        if (ticks < 3) {
            return;
        }
        grabScreenshot(minecraft, RELATED_SCREENSHOT);

        clickWidget(minecraft, screen.allCategoryButton());
        if (screen.relatedAnchor() != null || screen.visibleEntries().size() != catalog.total()) {
            fail(minecraft, "'All' did not leave the related view: anchor="
                    + (screen.relatedAnchor() == null ? "null" : screen.relatedAnchor().target())
                    + " visible=" + screen.visibleEntries().size() + "/" + catalog.total());
            return;
        }
        if (!PonderEntrypoints.openForTarget(POWER_PLAY_TARGET)) {
            fail(minecraft, "could not open the power scene for " + POWER_PLAY_TARGET);
            return;
        }
        stage = Stage.PLAY_POWER;
        ticks = 0;
    }

    private static void tickPlayPower(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "power scene player did not open; screen=" + minecraft.screen);
            }
            return;
        }
        if (!waitForNarration(minecraft, player, POWER_PLAY_TARGET, POWER_STEPS, POWER_LAST_NARRATION)) {
            return;
        }
        grabScreenshot(minecraft, POWER_SCREENSHOT);
        if (!PonderEntrypoints.openForTarget(LOGISTICS_PLAY_TARGET)) {
            fail(minecraft, "could not open the logistics scene for " + LOGISTICS_PLAY_TARGET);
            return;
        }
        stage = Stage.PLAY_LOGISTICS;
        ticks = 0;
    }

    private static void tickPlayLogistics(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "logistics scene player did not open; screen=" + minecraft.screen);
            }
            return;
        }
        if (!waitForNarration(minecraft, player, LOGISTICS_PLAY_TARGET, LOGISTICS_STEPS,
                LOGISTICS_LAST_NARRATION)) {
            return;
        }
        grabScreenshot(minecraft, LOGISTICS_SCREENSHOT);
        LOGGER.info("[GTSNPonder] systems autotest PASS: both categories listed, related navigation works, "
                + "both scenes play the expected step counts and concept narration; screenshots {} / {} / {} / {}",
                CATALOG_SCREENSHOT, RELATED_SCREENSHOT, POWER_SCREENSHOT, LOGISTICS_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    /**
     * 断言播放器打开的是期望目标 / 来源 / 步骤数，并推进到该场景最后一个概念旁白步骤时其旁白键正确。
     * 未达时间点返回 {@code false}（继续等待），失败即 {@link #fail}。
     */
    private static boolean waitForNarration(Minecraft minecraft, ScenePlayerScreen player, String target,
            int expectedSteps, String expectedNarration) {
        if (player.renderedFrames() < 4) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, target + " player never rendered");
            }
            return false;
        }
        if (!target.equals(player.scene().target())) {
            fail(minecraft, "player opened the wrong target: expected " + target
                    + " got " + player.scene().target());
            return false;
        }
        if (player.scene().source() != Source.MIXED) {
            fail(minecraft, target + " played scene is not source=mixed, got " + player.scene().source());
            return false;
        }
        if (player.scene().steps().size() != expectedSteps) {
            fail(minecraft, target + " scene has " + player.scene().steps().size()
                    + " steps, expected " + expectedSteps);
            return false;
        }
        double narrationStart = narrationStartTime(player.scene(), expectedNarration);
        if (narrationStart < 0.0d) {
            fail(minecraft, target + " scene does not contain narration key " + expectedNarration);
            return false;
        }
        if (player.playback().time() < narrationStart) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, target + " playback never reached the concept narration (t="
                        + player.playback().time() + " < " + narrationStart + ")");
            }
            return false;
        }
        if (!expectedNarration.equals(player.playback().narrationKey())) {
            fail(minecraft, target + " narration at t=" + player.playback().time() + " is '"
                    + player.playback().narrationKey() + "', expected '" + expectedNarration + "'");
            return false;
        }
        LOGGER.info("[GTSNPonder] systems autotest: {} played {} steps; concept narration '{}' at t={}",
                target, player.scene().steps().size(), expectedNarration, player.playback().time());
        return true;
    }

    /** 某旁白键对应步骤的起始时间（tick）；不存在返回 {@code -1}。 */
    private static double narrationStartTime(SceneData scene, String narrationKey) {
        for (int index = 0; index < scene.steps().size(); index++) {
            if (narrationKey.equals(scene.steps().get(index).narration())) {
                return SceneRunner.startTime(scene, index);
            }
        }
        return -1.0d;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] systems autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] systems autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-systems-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void clickWidget(Minecraft minecraft, com.gtsn.lib.ui.widget.Widget widget) {
        var bounds = widget.bounds();
        double x = bounds.x() + bounds.width() / 2.0;
        double y = bounds.y() + bounds.height() / 2.0;
        minecraft.screen.mouseClicked(x, y, 0);
        minecraft.screen.mouseReleased(x, y, 0);
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] systems autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] systems autotest window prepared: {}x{} guiScale=2",
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
