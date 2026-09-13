package com.gtsn.ponder.client;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.gt.GtStructureAdapter;
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

import java.util.Optional;

/**
 * 视口嵌入 spike 的客户端自动测试（开发专用，无人值守证据）：
 * 环境变量 {@code GTSNPONDER_UI_AUTOTEST=viewport} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：把窗口固定为 1280x720 + GUI 缩放 2，创建 / 载入固定存档（需真实客户端世界，
 *       LDLib 虚世界以之为 biome / tint 代理，且视口必须在世界内打开）；</li>
 *   <li>世界就绪后经 GT 适配器取一台小型多方块结构，打开 {@link PonderViewportScreen}；</li>
 *   <li>等待场景渲染出方块后，向视口矩形内注入 <b>拖拽</b>（断言相机朝向改变）与
 *       <b>滚轮</b>（断言缩放改变），并核对 LDLib 控件侧的实际值（证明状态确实写入渲染器）；</li>
 *   <li>点击与视口重叠的覆盖按钮，断言覆盖层消费点击且相机不变（z 序 + 输入路由）；</li>
 *   <li>改变窗口尺寸触发重排，断言视口矩形随之改变且新矩形内输入仍被转发（resize）；</li>
 *   <li>抓取截图 {@code run/screenshots/gtsnponder-viewport.png} 并自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class ViewportAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "viewport";
    /** 环境变量名（与 GTSNLib 的 {@code GTSNLIB_UI_AUTOTEST} 同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-viewport-autotest";
    private static final String SCREENSHOT_NAME = "gtsnponder-viewport.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int MIN_USABLE_WIDTH = 320;
    private static final int MIN_USABLE_HEIGHT = 240;

    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 400;
    private static final int SCENE_READY_TICKS = 60;

    /** 视口内、覆盖面板之外的拖拽起点（相对视口矩形的比例；覆盖面板在右下角）。 */
    private static final double DRAG_FRACTION_X = 0.15;
    private static final double DRAG_FRACTION_Y = 0.80;
    private static final double DRAG_DX = 30;
    private static final double DRAG_DY = 20;
    private static final int RESIZE_WIDTH = 960;
    private static final int RESIZE_HEIGHT = 540;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, SCREEN, DRAG, SCROLL, OVERLAY, RESIZE, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static double yaw0;
    private static double pitch0;
    private static double zoom0;
    private static float ldcYaw0;
    private static float ldcZoom0;
    private static double dragX;
    private static double dragY;
    private static int resizeWidthBefore;
    private static int resizeHeightBefore;

    private ViewportAutotest() {
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
            case SCREEN -> tickScreen(minecraft);
            case DRAG -> tickDrag(minecraft);
            case SCROLL -> tickScroll(minecraft);
            case OVERLAY -> tickOverlay(minecraft);
            case RESIZE -> tickResize(minecraft);
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
        LOGGER.info("[GTSNPonder] viewport autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
        Optional<StructureSource> structure = GtStructureAdapter.smallestMultiblock();
        if (structure.isEmpty()) {
            fail(minecraft, "GT structure adapter returned no multiblock (is GT loaded?)");
            return;
        }
        StructureSource source = structure.get();
        LOGGER.info("[GTSNPonder] viewport autotest: opening viewport for {} ({} blocks, {}x{}x{})",
                source.id(), source.blockCount(), source.sizeX(), source.sizeY(), source.sizeZ());
        minecraft.setScreen(new PonderViewportScreen(source, minecraft.level));
        stage = Stage.SCREEN;
        ticks = 0;
    }

    private static void tickScreen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof PonderViewportScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "viewport screen did not open; current screen=" + minecraft.screen);
            }
            return;
        }
        boolean sceneReady = screen.viewport().renderedBlockCount() > 0 && screen.renderedFrames() > 5;
        if (!sceneReady) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "scene never rendered blocks (rendered=" + screen.viewport().renderedBlockCount()
                        + ", frames=" + screen.renderedFrames() + ")");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] viewport autotest: scene ready (blocks={}, frames={}, ldcZoom={})",
                screen.viewport().renderedBlockCount(), screen.renderedFrames(), screen.viewport().ldlibZoom());

        yaw0 = screen.viewport().cameraYaw();
        pitch0 = screen.viewport().cameraPitch();
        zoom0 = screen.viewport().cameraZoom();
        ldcYaw0 = screen.viewport().ldlibRotationYaw();
        ldcZoom0 = screen.viewport().ldlibZoom();

        // 视口内、覆盖层之外（左下区）的按下 → 视口接管拖拽。
        Rect viewport = screen.viewportBounds();
        dragX = viewport.x() + viewport.width() * DRAG_FRACTION_X;
        dragY = viewport.y() + viewport.height() * DRAG_FRACTION_Y;
        screen.mouseClicked(dragX, dragY, 0);
        if (!screen.viewport().isDragging()) {
            fail(minecraft, "viewport did not capture press inside its rect at (" + dragX + "," + dragY + ")");
            return;
        }
        screen.mouseDragged(dragX + DRAG_DX, dragY + DRAG_DY, 0, DRAG_DX, DRAG_DY);
        screen.mouseDragged(dragX + 2 * DRAG_DX, dragY + 2 * DRAG_DY, 0, DRAG_DX, DRAG_DY);
        screen.mouseReleased(dragX + 2 * DRAG_DX, dragY + 2 * DRAG_DY, 0);
        if (screen.viewport().isDragging()) {
            fail(minecraft, "viewport still captured after release");
            return;
        }
        stage = Stage.DRAG;
        ticks = 0;
    }

    private static void tickDrag(Minecraft minecraft) {
        PonderViewportScreen screen = (PonderViewportScreen) minecraft.screen;
        double yaw = screen.viewport().cameraYaw();
        double pitch = screen.viewport().cameraPitch();
        boolean viewportRotated = yaw != yaw0 || pitch != pitch0;
        boolean ldlibRotated = screen.viewport().ldlibRotationPitch() != 0.0f
                && (float) pitch == screen.viewport().ldlibRotationPitch();
        if (!viewportRotated) {
            fail(minecraft, "drag did not rotate the camera (yaw=" + yaw + "/" + yaw0 + ", pitch=" + pitch + "/"
                    + pitch0 + ")");
            return;
        }
        LOGGER.info("[GTSNPonder] viewport autotest: drag rotated camera yaw {}->{}, pitch {}->{} "
                        + "(LDLib rotationPitch={}, reachedLdlib={})",
                yaw0, yaw, pitch0, pitch, screen.viewport().ldlibRotationPitch(), ldlibRotated);
        screen.mouseScrolled(dragX, dragY, 1.0);
        stage = Stage.SCROLL;
        ticks = 0;
    }

    private static void tickScroll(Minecraft minecraft) {
        PonderViewportScreen screen = (PonderViewportScreen) minecraft.screen;
        double zoom = screen.viewport().cameraZoom();
        float ldcZoom = screen.viewport().ldlibZoom();
        if (zoom == zoom0) {
            fail(minecraft, "scroll did not change zoom (zoom=" + zoom + "/" + zoom0 + ")");
            return;
        }
        LOGGER.info("[GTSNPonder] viewport autotest: scroll changed zoom {}->{} (LDLib zoom {}->{}, delta={})",
                zoom0, zoom, ldcZoom0, ldcZoom, ldcZoom - ldcZoom0);
        stage = Stage.OVERLAY;
        ticks = 0;
    }

    private static void tickOverlay(Minecraft minecraft) {
        PonderViewportScreen screen = (PonderViewportScreen) minecraft.screen;
        double yawBefore = screen.viewport().cameraYaw();
        double pitchBefore = screen.viewport().cameraPitch();
        double zoomBefore = screen.viewport().cameraZoom();

        double x = screen.overlayButtonBounds().x() + screen.overlayButtonBounds().width() / 2.0;
        double y = screen.overlayButtonBounds().y() + screen.overlayButtonBounds().height() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);

        if (screen.overlayClicks() != 1) {
            fail(minecraft, "overlapping overlay button did not receive the click (clicks="
                    + screen.overlayClicks() + ")");
            return;
        }
        if (screen.viewport().cameraYaw() != yawBefore || screen.viewport().cameraPitch() != pitchBefore
                || screen.viewport().cameraZoom() != zoomBefore) {
            fail(minecraft, "overlay click leaked into the viewport camera");
            return;
        }
        LOGGER.info("[GTSNPonder] viewport autotest: overlay consumed click (clicks={}), camera untouched",
                screen.overlayClicks());
        stage = Stage.RESIZE;
        ticks = 0;
    }

    /**
     * resize（窗口尺寸变化）：改变窗口尺寸并触发重排，断言视口矩形随之变化，且新矩形内输入
     * 仍被正确转发。随后恢复窗口尺寸以便抓取更大的截图。
     */
    private static void tickResize(Minecraft minecraft) {
        PonderViewportScreen screen = (PonderViewportScreen) minecraft.screen;
        if (ticks == 1) {
            Rect before = screen.viewportBounds();
            resizeWidthBefore = before.width();
            resizeHeightBefore = before.height();
            resizeWindow(minecraft, RESIZE_WIDTH, RESIZE_HEIGHT);
            LOGGER.info("[GTSNPonder] viewport autotest: window resized to {}x{}; viewport before={}x{}",
                    RESIZE_WIDTH, RESIZE_HEIGHT, resizeWidthBefore, resizeHeightBefore);
            return;
        }
        if (ticks < 12) {
            return;
        }
        Rect after = screen.viewportBounds();
        if (after.width() == resizeWidthBefore && after.height() == resizeHeightBefore) {
            fail(minecraft, "viewport rect did not relayout after resize (before=" + resizeWidthBefore + "x"
                    + resizeHeightBefore + ", after=" + after.width() + "x" + after.height() + ")");
            return;
        }
        // 新矩形内输入仍正确转发。
        double x = after.x() + after.width() * DRAG_FRACTION_X;
        double y = after.y() + after.height() * DRAG_FRACTION_Y;
        double yawBefore = screen.viewport().cameraYaw();
        double pitchBefore = screen.viewport().cameraPitch();
        screen.mouseClicked(x, y, 0);
        screen.mouseDragged(x + 25, y + 15, 0, 25, 15);
        screen.mouseReleased(x + 25, y + 15, 0);
        if (screen.viewport().cameraYaw() == yawBefore && screen.viewport().cameraPitch() == pitchBefore) {
            fail(minecraft, "input was not forwarded in the resized viewport rect");
            return;
        }
        LOGGER.info("[GTSNPonder] viewport autotest: after resize viewport={}x{}, input still forwarded",
                after.width(), after.height());
        resizeWindow(minecraft, WIDTH, HEIGHT);
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        ensureSized(minecraft);
        LOGGER.info("[GTSNPonder] viewport autotest PASS: structure rendered, drag/scroll/overlay/resize verified; "
                + "grabbing screenshot {}", SCREENSHOT_NAME);
        grabScreenshot(minecraft);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] viewport autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] viewport autotest FAIL: {}", reason);
        grabScreenshot(minecraft);
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft) {
        Screenshot.grab(minecraft.gameDirectory, SCREENSHOT_NAME, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] viewport autotest screenshot: {}", message.getString()));
    }

    private static ServerPlayer serverPlayer(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    /** 固定窗口 1280x720 + GUI 缩放 2（与 GTSNLib 自动测试一致）。 */
    private static void prepareWindow(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        GLFW.glfwShowWindow(window.getWindow());
        GLFW.glfwRestoreWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] viewport autotest window prepared: {}x{} guiScale=2",
                window.getWidth(), window.getHeight());
    }

    /** 改变窗口尺寸并触发重排（resize 证据）。 */
    private static void resizeWindow(Minecraft minecraft, int width, int height) {
        Window window = minecraft.getWindow();
        GLFW.glfwShowWindow(window.getWindow());
        GLFW.glfwRestoreWindow(window.getWindow());
        window.setWindowed(width, height);
        minecraft.resizeDisplay();
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
