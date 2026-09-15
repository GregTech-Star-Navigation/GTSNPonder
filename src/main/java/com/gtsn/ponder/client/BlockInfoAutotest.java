package com.gtsn.ponder.client;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.ponder.GTSNPonder;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
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

import java.util.Optional;

/**
 * 「点击方块看名称」的客户端自动测试（工单 #21 反馈 3）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=blockinfo} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>打开单方块机器 {@code gtceu:lv_macerator} 的使用场景（本体恒在视口中心）；</li>
 *   <li>把指针移到视口中心并等一帧渲染，点击 → 断言覆盖层显示该方块的<b>本地化名称</b>
 *       （{@code block.gtceu.lv_macerator}）且选中单元是机器本体；</li>
 *   <li>再次点击同一方块 → 断言覆盖层被关闭（点击即切换）；</li>
 *   <li>再次点击显示覆盖层并截图 {@code gtsnponder-blockinfo.png}；</li>
 *   <li>自动退出。任何断言失败 / 超时都记录 FAIL、截图并退出。</li>
 * </ol>
 *
 * <p>仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class BlockInfoAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "blockinfo";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-blockinfo-autotest";
    private static final String TARGET = "gtceu:lv_macerator";
    private static final String BLOCK_NAME_KEY = "block.gtceu.lv_macerator";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;
    private static final int RENDER_SETTLE_TICKS = 4;
    private static final int CAPTURE_SETTLE_TICKS = 10;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, OPEN, AIM, CLICK, DISMISS, SHOW, CAPTURE, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static double aimX;
    private static double aimY;

    private BlockInfoAutotest() {
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
            case AIM -> tickAim(minecraft);
            case CLICK -> tickClick(minecraft);
            case DISMISS -> tickDismiss(minecraft);
            case SHOW -> tickShow(minecraft);
            case CAPTURE -> tickCapture(minecraft);
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
        LOGGER.info("[GTSNPonder] blockinfo autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
            fail(minecraft, "could not open the usage scene for " + TARGET);
            return;
        }
        LOGGER.info("[GTSNPonder] blockinfo autotest: opening usage scene for {}", TARGET);
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "scene player did not open; current=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 6 || screen.viewportBounds().width() <= 0) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "scene player never rendered a viewport");
            }
            return;
        }
        Rect bounds = screen.viewportBounds();
        aimX = bounds.x() + bounds.width() / 2.0;
        aimY = bounds.y() + bounds.height() / 2.0;
        // 指针决定视口的拾取坐标：移到本体中心，等一帧渲染让 LDLib 算出 hoverPosFace。
        screen.mouseMoved(aimX, aimY);
        LOGGER.info("[GTSNPonder] blockinfo autotest: aiming at viewport centre ({}, {}) viewport={}",
                aimX, aimY, bounds);
        stage = Stage.AIM;
        ticks = 0;
    }

    private static void tickAim(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        screen.mouseMoved(aimX, aimY);
        if (ticks < RENDER_SETTLE_TICKS) {
            return;
        }
        screen.mouseClicked(aimX, aimY, 0);
        screen.mouseReleased(aimX, aimY, 0);
        stage = Stage.CLICK;
        ticks = 0;
    }

    private static void tickClick(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        Optional<String> label = screen.blockOverlayText();
        if (label.isEmpty()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "clicking the machine block did not show the block-name overlay "
                        + "(picked=" + screen.selectedBlock() + ", pointer=" + aimX + "," + aimY + ")");
            }
            return;
        }
        String expectedName = Component.translatable(BLOCK_NAME_KEY).getString();
        if (!label.get().contains(expectedName)) {
            fail(minecraft, "overlay '" + label.get() + "' does not contain the localized block name '"
                    + expectedName + "'");
            return;
        }
        if (screen.selectedBlock().isEmpty()
                || !TARGET.equals(screen.selectedBlock().get().blockId())) {
            fail(minecraft, "selected block is not the machine body: " + screen.selectedBlock());
            return;
        }
        LOGGER.info("[GTSNPonder] blockinfo autotest: overlay shows '{}' for {}",
                label.get(), screen.selectedBlock().get());
        // 再点一次同一方块 → 覆盖层应关闭。
        screen.mouseClicked(aimX, aimY, 0);
        screen.mouseReleased(aimX, aimY, 0);
        stage = Stage.DISMISS;
        ticks = 0;
    }

    private static void tickDismiss(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (ticks < RENDER_SETTLE_TICKS) {
            return;
        }
        if (screen.blockOverlayText().isPresent()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "clicking the same block again did not dismiss the overlay");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] blockinfo autotest: second click dismissed the overlay");
        // 再点一次以显示覆盖层用于截图。
        screen.mouseClicked(aimX, aimY, 0);
        screen.mouseReleased(aimX, aimY, 0);
        stage = Stage.SHOW;
        ticks = 0;
    }

    private static void tickShow(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (ticks < RENDER_SETTLE_TICKS || screen.blockOverlayText().isEmpty()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "overlay did not reappear for the screenshot");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] blockinfo autotest: overlay visible again -> capturing screenshot");
        grabScreenshot(minecraft, "gtsnponder-blockinfo.png");
        stage = Stage.CAPTURE;
        ticks = 0;
    }

    private static void tickCapture(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        LOGGER.info("[GTSNPonder] blockinfo autotest PASS: block-name overlay shown, dismissed and captured");
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] blockinfo autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] blockinfo autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-blockinfo-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] blockinfo autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] blockinfo autotest window prepared: {}x{} guiScale=2",
                window.getWidth(), window.getHeight());
    }

    /** 创建 / 载入固定存档（首次创建，之后复用）。 */
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
