package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
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

/**
 * 场景播放的客户端自动测试（开发专用、无人值守证据）：环境变量 {@code GTSNPONDER_UI_AUTOTEST=scene}
 * 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后经确定性入口 {@link PonderEntrypoints#openForTarget(String)}
 *       打开 {@code gtceu:coke_oven} 的 {@link ScenePlayerScreen}；</li>
 *   <li>断言播放可观察量：步骤下标推进、旁白键先在 t≥70 为 intro、后在 t≥140 变为 controller；</li>
 *   <li>点击暂停按钮，断言播放暂停且进度冻结；</li>
 *   <li>点击进度条（80%）seek，断言时间落在期望值附近；</li>
 *   <li>点击重播，断言回到 0、恢复播放、后段高亮 / 分段被清除、首段重新可见；</li>
 *   <li>抓取截图 {@code run/screenshots/gtsnponder-scene.png} 并自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class SceneAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "scene";
    /** 环境变量名（与 T3 视口自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-scene-autotest";
    private static final String SCREENSHOT_NAME = "gtsnponder-scene.png";
    private static final String TARGET = "gtceu:coke_oven";

    private static final String INTRO_KEY = "ponder.gtsnponder.coke_oven.narration.intro";
    private static final String CONTROLLER_KEY = "ponder.gtsnponder.coke_oven.narration.controller";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int MIN_USABLE_WIDTH = 320;
    private static final int MIN_USABLE_HEIGHT = 240;
    private static final double TOTAL_TIME = 250.0d;

    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 600;
    private static final int NARRATION_A_TIME = 70;
    private static final int NARRATION_B_TIME = 140;
    private static final double SEEK_FRACTION = 0.8d;
    private static final double SEEK_TOLERANCE = 3.0d;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, PLAY, PAUSE, SEEK, REWIND, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static String narrationA;
    private static String narrationB;
    private static double pauseTime;
    private static double seekTime;

    private SceneAutotest() {
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
            case PLAY -> tickPlay(minecraft);
            case PAUSE -> tickPause(minecraft);
            case SEEK -> tickSeek(minecraft);
            case REWIND -> tickRewind(minecraft);
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
        LOGGER.info("[GTSNPonder] scene autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
        if (!PonderEntrypoints.openForTarget(TARGET)) {
            fail(minecraft, "could not open the ponder scene for " + TARGET
                    + " (GT multiblock or bundled scene missing?)");
            return;
        }
        LOGGER.info("[GTSNPonder] scene autotest: opening scene player for {}", TARGET);
        stage = Stage.PLAY;
        ticks = 0;
    }

    private static void tickPlay(Minecraft minecraft) {
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

        double time = screen.playback().time();
        if (narrationA == null && time >= NARRATION_A_TIME) {
            narrationA = screen.playback().narrationKey();
            if (!INTRO_KEY.equals(narrationA)) {
                fail(minecraft, "expected narration '" + INTRO_KEY + "' at t>=" + NARRATION_A_TIME
                        + ", got " + narrationA);
                return;
            }
            LOGGER.info("[GTSNPonder] scene autotest: t={} step={} narration={} visibleBlocks={}",
                    time, screen.playback().stepIndex(), narrationA, screen.bridge().visibleBlockCount());
        }
        if (narrationB == null && time >= NARRATION_B_TIME) {
            narrationB = screen.playback().narrationKey();
            if (narrationA == null) {
                fail(minecraft, "narration never appeared before t>=" + NARRATION_B_TIME);
                return;
            }
            if (!CONTROLLER_KEY.equals(narrationB) || narrationA.equals(narrationB)) {
                fail(minecraft, "narration did not change: A=" + narrationA + " B=" + narrationB);
                return;
            }
            if (screen.playback().stepIndex() < 5) {
                fail(minecraft, "step index did not advance (index=" + screen.playback().stepIndex() + ")");
                return;
            }
            if (screen.bridge().visibleBlockCount() <= 0) {
                fail(minecraft, "no blocks visible after section reveal");
                return;
            }
            if (!Boolean.TRUE.equals(screen.bridge().highlights().get("controller"))) {
                fail(minecraft, "controller highlight was not applied");
                return;
            }
            if (screen.narrationText().text().isEmpty()) {
                fail(minecraft, "narration box is empty while a narration key is active");
                return;
            }
            LOGGER.info("[GTSNPonder] scene autotest: narration box shows '{}'", screen.narrationText().text());
            grabScreenshot(minecraft);
            clickWidget(minecraft, screen.playPauseButton());
            if (screen.playback().isPlaying()) {
                fail(minecraft, "pause button did not pause playback");
                return;
            }
            pauseTime = screen.playback().time();
            LOGGER.info("[GTSNPonder] scene autotest: paused at t={} (steps advanced, narration {}=>{})",
                    pauseTime, narrationA, narrationB);
            stage = Stage.PAUSE;
            ticks = 0;
            return;
        }
        if (ticks > STAGE_TIMEOUT_TICKS) {
            fail(minecraft, "playback did not reach t>=" + NARRATION_B_TIME + " (t=" + time + ")");
        }
    }

    private static void tickPause(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (ticks < 20) {
            return;
        }
        if (screen.playback().isPlaying()) {
            fail(minecraft, "playback resumed without input while paused");
            return;
        }
        if (Math.abs(screen.playback().time() - pauseTime) > 0.001d) {
            fail(minecraft, "paused progress advanced: " + pauseTime + " -> " + screen.playback().time());
            return;
        }
        LOGGER.info("[GTSNPonder] scene autotest: pause froze progress at t={}", screen.playback().time());

        seekOnProgressBar(minecraft, screen, SEEK_FRACTION);
        seekTime = screen.playback().time();
        if (Math.abs(seekTime - SEEK_FRACTION * TOTAL_TIME) > SEEK_TOLERANCE) {
            fail(minecraft, "seek landed at t=" + seekTime + ", expected ~" + (SEEK_FRACTION * TOTAL_TIME));
            return;
        }
        LOGGER.info("[GTSNPonder] scene autotest: seek {}{} -> t={} step={}", (int) (SEEK_FRACTION * 100), "%",
                seekTime, screen.playback().stepIndex());
        stage = Stage.SEEK;
        ticks = 0;
    }

    private static void tickSeek(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (screen.playback().stepIndex() < 6) {
            fail(minecraft, "seek did not move to a later step (index=" + screen.playback().stepIndex() + ")");
            return;
        }
        clickWidget(minecraft, screen.replayButton());
        if (screen.playback().time() > 5.0d) {
            fail(minecraft, "replay did not rewind (t=" + screen.playback().time() + ")");
            return;
        }
        if (!screen.playback().isPlaying()) {
            fail(minecraft, "replay did not resume playback");
            return;
        }
        if (screen.playback().stepIndex() != 0) {
            fail(minecraft, "replay did not return to the first step (index="
                    + screen.playback().stepIndex() + ")");
            return;
        }
        if (Boolean.TRUE.equals(screen.bridge().highlights().get("controller"))) {
            fail(minecraft, "rewind did not clear the later-step highlight");
            return;
        }
        if (!screen.bridge().visibleSections().contains("base")
                || screen.bridge().visibleSections().contains("shell")) {
            fail(minecraft, "rewind did not restore the opening section state: "
                    + screen.bridge().visibleSections());
            return;
        }
        LOGGER.info("[GTSNPonder] scene autotest: replay rewound to t={} step={} sections={}",
                screen.playback().time(), screen.playback().stepIndex(), screen.bridge().visibleSections());
        stage = Stage.REWIND;
        ticks = 0;
    }

    private static void tickRewind(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (screen.bridge().visibleBlockCount() <= 0) {
            fail(minecraft, "no blocks visible after replay");
            return;
        }
        LOGGER.info("[GTSNPonder] scene autotest: replay restored the opening frame (visibleBlocks={})",
                screen.bridge().visibleBlockCount());
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        ensureSized(minecraft);
        LOGGER.info("[GTSNPonder] scene autotest PASS: step/narration/pause/seek/rewind verified; "
                + "screenshot already captured as {}", SCREENSHOT_NAME);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] scene autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] scene autotest FAIL: {}", reason);
        grabScreenshot(minecraft);
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

    private static void seekOnProgressBar(Minecraft minecraft, ScenePlayerScreen screen, double fraction) {
        var bounds = screen.progressBar().bounds();
        double x = bounds.x() + bounds.width() * fraction;
        double y = bounds.y() + bounds.height() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void grabScreenshot(Minecraft minecraft) {
        Screenshot.grab(minecraft.gameDirectory, SCREENSHOT_NAME, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] scene autotest screenshot: {}", message.getString()));
    }

    private static ServerPlayer serverPlayer(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    /** 固定窗口 1280x720 + GUI 缩放 2（与 GTSNLib / T3 自动测试一致）。 */
    private static void prepareWindow(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        GLFW.glfwShowWindow(window.getWindow());
        GLFW.glfwRestoreWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] scene autotest window prepared: {}x{} guiScale=2",
                window.getWidth(), window.getHeight());
    }

    private static void ensureSized(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        if (window.getWidth() < MIN_USABLE_WIDTH || window.getHeight() < MIN_USABLE_HEIGHT) {
            GLFW.glfwShowWindow(window.getWindow());
            GLFW.glfwRestoreWindow(window.getWindow());
            window.setWindowed(WIDTH, HEIGHT);
            minecraft.resizeDisplay();
        }
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
