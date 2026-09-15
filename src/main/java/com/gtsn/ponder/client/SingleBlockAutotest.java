package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.catalog.SingleBlockScenes;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
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

import java.util.List;
import java.util.Optional;

/**
 * 单方块机器使用场景 + 入口的客户端自动测试（工单 #15，开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=singleblock} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后断言覆盖契约 {@link SingleBlockScenes#analyze}：代表性单方块机器零死链 + 精选手作齐全，
 *       并断言目录为代表性机器合成了使用场景条目（键 = 使用场景 id）；</li>
 *   <li>放置一台真实单方块机器（{@code gtceu:lp_steam_furnace}）并以 GT 标准路径打开其 GUI；断言
 *       {@link GtMachineScreenAdapter#resolveTarget} 解析出该机器 id（<b>失败可检出</b>：修复前单方块
 *       返回空 → 无按钮）；断言覆盖按钮已被真实渲染循环登记；截图；</li>
 *   <li>经 Forge 事件总线投递真实 {@code ScreenEvent.MouseButtonPressed.Pre}，断言事件被取消、
 *       覆盖层记录一次点击 / 一次打开，且打开的播放屏 target 一致（手作场景 source=hand）；</li>
 *   <li>断言场景播放到期望的手作旁白键、旁白框非空；截图；</li>
 *   <li>再断言一台<b>生成</b>的代表性机器（{@code gtceu:lv_centrifuge}）经入口解析为 source=auto 并播放；
 *       截图；</li>
 *   <li>退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class SingleBlockAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "singleblock";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    /** 手作场景的真实单方块机器（有随包场景 + 覆盖按钮入口）。 */
    public static final String MACHINE_ID = "gtceu:lp_steam_furnace";
    /** 生成使用场景的代表性单方块机器。 */
    public static final String GENERATED_TARGET = "gtceu:lv_centrifuge";

    private static final String LEVEL_NAME = "gtsnponder-singleblock-autotest";
    private static final String OVERLAY_SCREENSHOT = "gtsnponder-singleblock.png";
    private static final String SCENE_SCREENSHOT = "gtsnponder-singleblock-scene.png";
    private static final String GENERATED_SCREENSHOT = "gtsnponder-singleblock-generated.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, PLACE, OPEN, VERIFY, CLICK, PLAY, GENERATED, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static BlockPos machinePos;
    private static volatile boolean placed;
    private static volatile boolean uiRequested;
    private static String target;
    private static boolean narrationSeen;

    private SingleBlockAutotest() {
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
            case PLAY -> tickPlay(minecraft);
            case GENERATED -> tickGenerated(minecraft);
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
        LOGGER.info("[GTSNPonder] singleblock autotest: {}={} -> loading world '{}'",
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
        MachinePonderOverlay.get().reset();

        // 覆盖契约：代表性单方块机器零死链 + 精选手作齐全（与 GameTest / headless 单测同一套数字）。
        SingleBlockScenes.Report report = SingleBlockScenes.analyze(PonderEntrypoints::resolveSceneForTarget);
        LOGGER.info("[GTSNPonder] singleblock autotest: {}", report.summary());
        if (!report.isComplete()) {
            fail(minecraft, "single-block usage coverage incomplete: " + report.summary());
            return;
        }
        if (!verifyCatalogWiring(minecraft, report)) {
            return;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerPlayer player = serverPlayer(minecraft);
        machinePos = player.blockPosition().offset(3, 0, 3);
        LOGGER.info("[GTSNPonder] singleblock autotest: placing {} at {} and opening its GT GUI",
                MACHINE_ID, machinePos);
        stage = Stage.PLACE;
        ticks = 0;
    }

    /** 断言目录为代表性机器合成了使用场景条目（键 = 使用场景 id，无死链）。 */
    private static boolean verifyCatalogWiring(Minecraft minecraft, SingleBlockScenes.Report report) {
        List<String> usageTargets = PonderEntrypoints.representativeSingleBlockTargets();
        if (usageTargets.size() < SingleBlockScenes.MIN_REPRESENTATIVE) {
            fail(minecraft, "too few representative single-block targets resolve: " + usageTargets);
            return false;
        }
        SceneCatalog catalog = SceneCatalog.of(SceneLibrary.get().scenes(),
                PonderEntrypoints.registeredMultiblockTargets(), usageTargets,
                PonderProgress.get().snapshot());
        for (String usageTarget : usageTargets) {
            CatalogEntry entry = catalog.entries().stream()
                    .filter(candidate -> usageTarget.equals(candidate.target()))
                    .findFirst().orElse(null);
            if (entry == null) {
                fail(minecraft, "catalog is missing a representative single-block entry: " + usageTarget);
                return false;
            }
            if (!SingleBlockUsageGenerator.sceneIdFor(usageTarget).equals(entry.key())) {
                fail(minecraft, "catalog entry key does not match the usage scene id for " + usageTarget);
                return false;
            }
        }
        LOGGER.info("[GTSNPonder] singleblock autotest: catalog covers {} representative single-block "
                + "machine(s) with usage entries", usageTargets.size());
        return true;
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
                fail(minecraft, "the real single-block machine GUI never resolved a ponder target "
                        + "(uiRequested=" + uiRequested + ", screen=" + minecraft.screen + ")");
            }
            return;
        }
        if (!MACHINE_ID.equals(resolved.get())) {
            fail(minecraft, "single-block machine screen resolved the wrong target: expected " + MACHINE_ID
                    + " got " + resolved.get());
            return;
        }
        target = resolved.get();
        LOGGER.info("[GTSNPonder] singleblock autotest: real single-block machine screen detected, "
                + "ponder target '{}' (screen={})", target, minecraft.screen.getClass().getName());
        stage = Stage.VERIFY;
        ticks = 0;
    }

    private static void tickVerify(Minecraft minecraft) {
        MachinePonderOverlay overlay = MachinePonderOverlay.get();
        if (!overlay.isActive()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the overlay button was never registered on the single-block machine screen");
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
            fail(minecraft, "the overlay button is off-screen: " + box);
            return;
        }
        LOGGER.info("[GTSNPonder] singleblock autotest: overlay button registered at {} for target {}",
                box, target);
        grabScreenshot(minecraft, OVERLAY_SCREENSHOT);

        double clickX = box.x() + box.width() / 2.0d;
        double clickY = box.y() + box.height() / 2.0d;
        boolean canceled = MinecraftForge.EVENT_BUS.post(
                new ScreenEvent.MouseButtonPressed.Pre(minecraft.screen, clickX, clickY, 0));
        LOGGER.info("[GTSNPonder] singleblock autotest: posted MouseButtonPressed.Pre at ({}, {}), "
                + "canceled={}, clicks={}, opens={}", clickX, clickY, canceled, overlay.clicks(), overlay.opens());
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
            if (player.scene().source() != Source.HAND) {
                fail(minecraft, "the curated single-block machine must open its hand-authored scene, got "
                        + player.scene().source());
                return;
            }
            if (MachinePonderOverlay.get().isActive()) {
                fail(minecraft, "the overlay stayed active on the non-GT scene player screen");
                return;
            }
            LOGGER.info("[GTSNPonder] singleblock autotest: overlay click opened the usage scene for '{}' "
                    + "({} steps, source={})", target, player.scene().steps().size(), player.scene().source());
            stage = Stage.PLAY;
            ticks = 0;
            return;
        }
        if (ticks > STAGE_TIMEOUT_TICKS) {
            fail(minecraft, "clicking the overlay did not open the scene player; screen=" + minecraft.screen);
        }
    }

    private static void tickPlay(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "usage scene player closed unexpectedly; screen=" + minecraft.screen);
            }
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        if (!narrationSeen) {
            if (SingleBlockScenes.HAND_STEAM_FURNACE_INTRO.equals(player.playback().narrationKey())) {
                if (player.narrationText().text().isEmpty()) {
                    fail(minecraft, "narration box is empty while the hand-authored narration key is active");
                    return;
                }
                narrationSeen = true;
                LOGGER.info("[GTSNPonder] singleblock autotest: hand-authored narration shown: '{}'",
                        player.narrationText().text());
                grabScreenshot(minecraft, SCENE_SCREENSHOT);
                stage = Stage.GENERATED;
                ticks = 0;
                return;
            }
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "usage scene never reached the hand-authored narration (got "
                        + player.playback().narrationKey() + ")");
            }
            return;
        }
    }

    private static void tickGenerated(Minecraft minecraft) {
        Optional<SceneData> generated = PonderEntrypoints.resolveSceneForTarget(GENERATED_TARGET);
        if (generated.isEmpty()) {
            fail(minecraft, GENERATED_TARGET + " did not resolve to a usage scene");
            return;
        }
        if (generated.get().source() != Source.AUTO) {
            fail(minecraft, GENERATED_TARGET + " must resolve to a generated (source=auto) usage scene, got "
                    + generated.get().source());
            return;
        }
        if (!SingleBlockUsageGenerator.sceneIdFor(GENERATED_TARGET).equals(generated.get().id())) {
            fail(minecraft, GENERATED_TARGET + " usage scene id mismatch: " + generated.get().id());
            return;
        }
        if (!PonderEntrypoints.openForTarget(GENERATED_TARGET)) {
            fail(minecraft, "could not open the generated usage scene for " + GENERATED_TARGET);
            return;
        }
        LOGGER.info("[GTSNPonder] singleblock autotest: on-demand usage scene '{}' resolves ({} steps, source={})",
                GENERATED_TARGET, generated.get().steps().size(), generated.get().source());
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player) || player.renderedFrames() < 4) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "generated usage scene player did not open; screen=" + minecraft.screen);
            }
            return;
        }
        if (!GENERATED_TARGET.equals(player.scene().target())) {
            fail(minecraft, "generated player opened the wrong target: " + player.scene().target());
            return;
        }
        grabScreenshot(minecraft, GENERATED_SCREENSHOT);
        LOGGER.info("[GTSNPonder] singleblock autotest PASS: single-block entry resolves, overlay button "
                + "appears, click opens '{}' hand scene, generated '{}' plays; screenshots {} / {} / {}",
                MACHINE_ID, GENERATED_TARGET, OVERLAY_SCREENSHOT, SCENE_SCREENSHOT, GENERATED_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] singleblock autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] singleblock autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-singleblock-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] singleblock autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] singleblock autotest window prepared: {}x{} guiScale=2",
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
