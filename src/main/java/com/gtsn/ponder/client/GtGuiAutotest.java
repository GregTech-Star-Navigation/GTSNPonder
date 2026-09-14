package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.gt.GtMachineScreenAdapter;
import com.gtsn.ponder.gt.GtMachineUiProbe;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * GT 机器界面「思索」覆盖按钮的客户端自动测试（#12，入口 ④，开发专用、无人值守证据）：
 * 环境变量 {@code GTSNPONDER_UI_AUTOTEST=gtgui} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>在玩家附近<b>放置一台真实 GT 多方块机器</b>（{@code gtceu:coke_oven}），并以 GT 标准路径
 *       （{@link GtMachineUiProbe#openMachineUi} → 服务端 {@code MachineUIFactory.openUI}）打开其 GUI；</li>
 *   <li>断言当前屏是真实的 GT 机器屏，且 {@link GtMachineScreenAdapter#resolveTarget} 解析出正确目标 id；
 *       断言覆盖按钮已被真实渲染循环（{@code ScreenEvent.Render.Post}）登记（{@link MachinePonderOverlay}），
 *       截图 {@code run/screenshots/gtsnponder-gtgui.png}；</li>
 *   <li>经 Forge 事件总线投递一次真实的 {@code ScreenEvent.MouseButtonPressed.Pre}（生产环境由
 *       {@code MouseHandler} 投递），断言事件被取消、{@link MachinePonderOverlay} 记到一次点击 / 一次打开，
 *       且打开的是该机器的 {@link ScenePlayerScreen}（目标一致）；</li>
 *   <li>断言非 GT 屏（播放屏）上覆盖按钮<b>不再出现</b>，再打开一个原版非 GT 屏（标题界面）复核，
 *       截图 {@code run/screenshots/gtsnponder-gtgui-player.png} / {@code gtsnponder-gtgui-nongt.png}；</li>
 *   <li>退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class GtGuiAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "gtgui";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    /** 自动测试用的真实 GT 多方块（既有 UI 又有结构，可被思索）。 */
    public static final String MACHINE_ID = "gtceu:coke_oven";

    private static final String LEVEL_NAME = "gtsnponder-gtgui-autotest";
    private static final String OVERLAY_SCREENSHOT = "gtsnponder-gtgui.png";
    private static final String PLAYER_SCREENSHOT = "gtsnponder-gtgui-player.png";
    private static final String NON_GT_SCREENSHOT = "gtsnponder-gtgui-nongt.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, PLACE, OPEN, VERIFY, CLICK, NONGT, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static BlockPos machinePos;
    private static volatile boolean placed;
    private static volatile boolean uiRequested;
    private static String target;

    private GtGuiAutotest() {
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
            case PLACE -> tickPlace(minecraft);
            case OPEN -> tickOpen(minecraft);
            case VERIFY -> tickVerify(minecraft);
            case CLICK -> tickClick(minecraft);
            case NONGT -> tickNonGt(minecraft);
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
        LOGGER.info("[GTSNPonder] gtgui autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
        MachinePonderOverlay.get().reset();
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerPlayer player = serverPlayer(minecraft);
        ServerLevel level = player.serverLevel();
        machinePos = player.blockPosition().offset(3, 0, 3);
        LOGGER.info("[GTSNPonder] gtgui autotest: placing {} at {} and opening its GT GUI", MACHINE_ID, machinePos);
        stage = Stage.PLACE;
        ticks = 0;
    }

    private static void tickPlace(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerPlayer player = serverPlayer(minecraft);
        BlockPos pos = machinePos;
        if (server == null || player == null || pos == null) {
            fail(minecraft, "lost the integrated server / player while placing the machine");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (ticks == 5) {
            server.execute(() -> placed = GtMachineUiProbe.placeMachine(level, pos, MACHINE_ID).isPresent());
            return;
        }
        if (ticks < 25) {
            return;
        }
        if (!placed) {
            fail(minecraft, "could not place " + MACHINE_ID + " at " + pos);
            return;
        }
        server.execute(() -> {
            if (GtMachineUiProbe.machinePresent(level, pos)) {
                uiRequested = GtMachineUiProbe.openMachineUi(level, pos, player);
            }
        });
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        Optional<String> resolved = GtMachineScreenAdapter.resolveTarget(minecraft.screen);
        if (resolved.isEmpty()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the real GT machine GUI never appeared (uiRequested=" + uiRequested
                        + ", screen=" + minecraft.screen + ")");
            }
            return;
        }
        if (!MACHINE_ID.equals(resolved.get())) {
            fail(minecraft, "GT machine screen resolved the wrong target: expected " + MACHINE_ID
                    + " got " + resolved.get());
            return;
        }
        target = resolved.get();
        LOGGER.info("[GTSNPonder] gtgui autotest: real GT machine screen detected, ponder target '{}' (screen={})",
                target, minecraft.screen.getClass().getName());
        stage = Stage.VERIFY;
        ticks = 0;
    }

    private static void tickVerify(Minecraft minecraft) {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        if (!overlay.isActive()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the overlay button was never registered on the GT machine screen");
            }
            return;
        }
        if (!target.equals(overlay.target().orElse(null))) {
            fail(minecraft, "overlay shows the wrong target: expected " + target
                    + " got " + overlay.target().orElse(null));
            return;
        }
        MachinePonderButton.Box box = overlay.button().orElse(null);
        if (box == null) {
            fail(minecraft, "the overlay has a target but no button rectangle");
            return;
        }
        if (box.right() > minecraft.screen.width || box.bottom() > minecraft.screen.height) {
            fail(minecraft, "the overlay button is off-screen: " + box + " on "
                    + minecraft.screen.width + "x" + minecraft.screen.height);
            return;
        }
        LOGGER.info("[GTSNPonder] gtgui autotest: overlay button registered at {} ({}x{} screen), target {}",
                box, minecraft.screen.width, minecraft.screen.height, target);
        grabScreenshot(minecraft, OVERLAY_SCREENSHOT);

        // 真实点击路径：生产环境由 MouseHandler 投递该事件；此处经官方事件总线投递同型事件，
        // 从而真正经过订阅的处理器（含取消语义），而非直接调用内部方法。
        double clickX = box.x() + box.width() / 2.0d;
        double clickY = box.y() + box.height() / 2.0d;
        boolean canceled = MinecraftForge.EVENT_BUS.post(
                new ScreenEvent.MouseButtonPressed.Pre(minecraft.screen, clickX, clickY, 0));
        LOGGER.info("[GTSNPonder] gtgui autotest: posted MouseButtonPressed.Pre at ({}, {}), canceled={}, "
                + "clicks={}, opens={}", clickX, clickY, canceled, overlay.clicks(), overlay.opens());
        if (!canceled) {
            fail(minecraft, "the overlay did not consume the click (event not canceled)");
            return;
        }
        if (overlay.clicks() != 1 || overlay.opens() != 1) {
            fail(minecraft, "the overlay click did not open exactly one scene player: clicks="
                    + overlay.clicks() + " opens=" + overlay.opens());
            return;
        }
        stage = Stage.CLICK;
        ticks = 0;
    }

    private static void tickClick(Minecraft minecraft) {
        if (minecraft.screen instanceof ScenePlayerScreen player && player.renderedFrames() >= 2) {
            if (!target.equals(player.scene().target())) {
                fail(minecraft, "the overlay opened the wrong scene: expected " + target
                        + " got " + player.scene().target());
                return;
            }
            if (MachinePonderOverlay.get().isActive()) {
                fail(minecraft, "the overlay stayed active on the non-GT scene player screen");
                return;
            }
            LOGGER.info("[GTSNPonder] gtgui autotest: overlay click opened the scene player for '{}'; "
                    + "overlay hidden on the non-GT screen", target);
            grabScreenshot(minecraft, PLAYER_SCREENSHOT);
            stage = Stage.NONGT;
            ticks = 0;
            return;
        }
        if (ticks > STAGE_TIMEOUT_TICKS) {
            fail(minecraft, "clicking the overlay did not open the scene player; screen=" + minecraft.screen);
        }
    }

    private static void tickNonGt(Minecraft minecraft) {
        if (ticks == 1) {
            minecraft.setScreen(new TitleScreen());
            LOGGER.info("[GTSNPonder] gtgui autotest: opened a vanilla screen to check the overlay stays hidden");
            return;
        }
        if (ticks < 6) {
            return;
        }
        if (MachinePonderOverlay.get().isActive()) {
            fail(minecraft, "the overlay button leaked onto a non-GT (vanilla) screen");
            return;
        }
        grabScreenshot(minecraft, NON_GT_SCREENSHOT);
        LOGGER.info("[GTSNPonder] gtgui autotest PASS: real GT machine screen detected, overlay button drawn, "
                + "click opened '{}', overlay no-op on non-GT screens; screenshots {} / {} / {}",
                target, OVERLAY_SCREENSHOT, PLAYER_SCREENSHOT, NON_GT_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] gtgui autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] gtgui autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-gtgui-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] gtgui autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] gtgui autotest window prepared: {}x{} guiScale=2",
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
