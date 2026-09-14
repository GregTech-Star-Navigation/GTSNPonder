package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.gt.GtStructureAdapter;
import com.gtsn.ponder.structure.ModuleEffectInfo;
import com.gtsn.ponder.structure.ModuleOption;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureRole;
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

import java.util.List;

/**
 * 模块系统演示的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=modules} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后，记录真实数据路径证据：{@link GtStructureAdapter#countMachinesDeclaringModuleSlots()}
 *       （现行 fork 无生产机器声明模块位 → 模块演示走「夹具结构源 + 注入缝」）；</li>
 *   <li>以<b>夹具结构源</b>（声明两个模块位：一个接受两个具名带效果模块、一个接受任意模块）经
 *       {@link SceneGenerator} 生成场景并打开播放；</li>
 *   <li>推进到效果汇总帧，断言<b>失败可检出</b>的可观察量：模块位区域已解析为多个单元并高亮（绿）、
 *       模块已安装（槽位被占用）、效果汇总旁白已显示；抓一张截图；</li>
 *   <li>重播（rewind）后断言安装效果被清除，再推进到同一帧断言安装效果确定性重现；</li>
 *   <li>全部通过后自动退出。</li>
 * </ol>
 *
 * <p>任一断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class ModuleAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "modules";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-modules-autotest";
    private static final String SCREENSHOT_NAME = "gtsnponder-modules.png";

    /** 效果汇总步骤 id（夹具生成器确定性产出）；其起始时刻即抓图 / 断言帧。 */
    private static final String EFFECT_STEP_ID = "text.installed.0";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;
    private static final int CAPTURE_SETTLE_TICKS = 10;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, PLAY, INSTALL, REWIND, REPLAY, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static StructureSource source;
    private static SceneData scene;
    private static double effectFrame;

    private ModuleAutotest() {
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
            case INSTALL -> tickInstall(minecraft);
            case REWIND -> tickRewind(minecraft);
            case REPLAY -> tickReplay(minecraft);
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
        LOGGER.info("[GTSNPonder] modules autotest: {}={} -> loading world '{}'",
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
        int declaring = GtStructureAdapter.countMachinesDeclaringModuleSlots();
        LOGGER.info("[GTSNPonder] modules autotest: production multiblocks declaring module slots = {} "
                + "(0 means the module demo must use the fixture StructureSource injection seam)", declaring);

        source = fixture();
        scene = SceneGenerator.generate(source);
        if (!verifyDeterminism(minecraft)) {
            return;
        }
        effectFrame = stepStartTime(scene, EFFECT_STEP_ID);
        LOGGER.info("[GTSNPonder] modules autotest: fixture '{}' -> {} slot(s), {} block(s); "
                        + "effect frame t={} (scene '{}' steps={})",
                source.id(), source.moduleSlotCount(), source.blockCount(), effectFrame, scene.id(),
                scene.steps().size());
        minecraft.execute(() -> minecraft.setScreen(new ScenePlayerScreen(scene, source, minecraft.level)));
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
        if (screen.playback().time() < effectFrame) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "playback never reached the effect frame (t=" + screen.playback().time()
                        + " / target=" + effectFrame + ")");
            }
            return;
        }
        stage = Stage.INSTALL;
        ticks = 0;
    }

    /** 效果汇总帧：断言模块位区域高亮、安装已生效、效果汇总已显示。 */
    private static void tickInstall(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        if (screen.scene().element("moduleslot.0").isEmpty()) {
            fail(minecraft, "generated scene has no module-slot region element");
            return;
        }
        int regionCells = screen.bridge().elementPositions("moduleslot.0").size();
        if (regionCells < 2) {
            fail(minecraft, "module slot region must highlight multiple cells, got " + regionCells);
            return;
        }
        if (!Boolean.TRUE.equals(screen.bridge().outlines().get("moduleslot.0"))) {
            fail(minecraft, "module slot region was not outlined: " + screen.bridge().outlines());
            return;
        }
        if (screen.bridge().moduleSlotOutlineCount() < regionCells) {
            fail(minecraft, "module-slot outline coverage too small: "
                    + screen.bridge().moduleSlotOutlineCount() + " < " + regionCells);
            return;
        }
        if (!"gtceu:parallel_module".equals(screen.bridge().installedModules().get("moduleslot.0"))) {
            fail(minecraft, "module was not installed into the empty slot: "
                    + screen.bridge().installedModules());
            return;
        }
        if (!SceneGenerator.NARRATION_MODULE_INSTALLED.equals(screen.playback().narrationKey())) {
            fail(minecraft, "expected the module effect-summary narration, got "
                    + screen.playback().narrationKey());
            return;
        }
        if (screen.narrationText().text().isEmpty() || !screen.narrationText().text().contains("4")) {
            fail(minecraft, "effect-summary narration box is missing the effect numbers: '"
                    + screen.narrationText().text() + "'");
            return;
        }
        LOGGER.info("[GTSNPonder] modules autotest: slot region cells={} installed={} narration='{}'",
                regionCells, screen.bridge().installedModules(), screen.narrationText().text());
        capture(minecraft, screen);
        clickWidget(minecraft, screen.replayButton());
        stage = Stage.REWIND;
        ticks = 0;
    }

    /** 重播后：安装效果必须被 rewind 清除（快照恢复）。 */
    private static void tickRewind(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (screen.playback().time() > 5.0d) {
            fail(minecraft, "replay did not rewind (t=" + screen.playback().time() + ")");
            return;
        }
        if (!screen.bridge().installedModules().isEmpty()) {
            fail(minecraft, "rewind did not clear the installed module: "
                    + screen.bridge().installedModules());
            return;
        }
        LOGGER.info("[GTSNPonder] modules autotest: rewind cleared the installed module (t={})",
                screen.playback().time());
        stage = Stage.REPLAY;
        ticks = 0;
    }

    /** 再推进到同一帧：安装效果必须确定性重现。 */
    private static void tickReplay(Minecraft minecraft) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        if (screen.playback().time() < effectFrame) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "replay never reached the effect frame (t=" + screen.playback().time() + ")");
            }
            return;
        }
        if (!"gtceu:parallel_module".equals(screen.bridge().installedModules().get("moduleslot.0"))) {
            fail(minecraft, "replaying did not deterministically reinstall the module: "
                    + screen.bridge().installedModules());
            return;
        }
        LOGGER.info("[GTSNPonder] modules autotest PASS: slot region highlighted, install applied, "
                + "effect summary shown, rewind/replay consistent; screenshot {}", SCREENSHOT_NAME);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] modules autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void capture(Minecraft minecraft, ScenePlayerScreen screen) {
        var viewport = screen.viewportBounds();
        LOGGER.info("[GTSNPonder] modules autotest: capture t={} step={} visibleBlocks={} viewport={}x{}",
                screen.playback().time(), screen.playback().stepIndex(),
                screen.bridge().visibleBlockCount(), viewport.width(), viewport.height());
        grabScreenshot(minecraft, SCREENSHOT_NAME);
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] modules autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-modules-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] modules autotest screenshot: {}", message.getString()));
    }

    private static void clickWidget(Minecraft minecraft, com.gtsn.lib.ui.widget.Widget widget) {
        var bounds = widget.bounds();
        double x = bounds.x() + bounds.width() / 2.0;
        double y = bounds.y() + bounds.height() / 2.0;
        minecraft.screen.mouseClicked(x, y, 0);
        minecraft.screen.mouseReleased(x, y, 0);
    }

    private static double stepStartTime(SceneData scene, String stepId) {
        List<SceneStep> steps = scene.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(stepId)) {
                return com.gtsn.ponder.engine.director.SceneRunner.startTime(scene, i);
            }
        }
        return 0.0d;
    }

    /** 断言同一夹具两次生成产生字节相等 JSON（确定性）。 */
    private static boolean verifyDeterminism(Minecraft minecraft) {
        String first = SceneDataWriter.toJson(scene);
        String second = SceneDataWriter.toJson(SceneGenerator.generate(source));
        if (!first.equals(second)) {
            fail(minecraft, "module-demo generation is not deterministic");
            return false;
        }
        return true;
    }

    /**
     * 夹具结构源：声明两个模块位（槽 0 接受两个具名带效果模块、槽 1 接受任意模块），
     * 区域坐标为<b>空穴</b>（模块安装后才会出现几何）——这是「真实 GT 数据路径不可用」时
     * 经注入缝驱动模块演示的中立数据（见 AGENTS.md / ADR-0006）。
     */
    private static StructureSource fixture() {
        return StructureSource.builder("gtsnponder:module_demo_machine")
                .displayName("Module Demo Machine")
                .size(4, 2, 4)
                .controller(3, 0, 3)
                .addBlock(3, 0, 3, "minecraft:iron_block", StructureRole.CONTROLLER)
                .addBlock(0, 1, 0, "minecraft:iron_block")
                .addBlock(3, 0, 0, "minecraft:iron_block")
                .addModuleSlot(new ModuleSlot(0, 0, 0, 2, 1, 2, false,
                        List.of("gtceu:speed_module", "gtceu:parallel_module"),
                        List.of(
                                new ModuleOption("gtceu:parallel_module",
                                        new ModuleEffectInfo(4, 1.0d, 1.0d, 1.0d, 1.0d, 0)),
                                new ModuleOption("gtceu:speed_module",
                                        new ModuleEffectInfo(0, 0.5d, 1.0d, 1.0d, 1.0d, 0)))))
                .addModuleSlot(new ModuleSlot(2, 0, 0, 1, 1, 1, true, List.of()))
                .build();
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
        LOGGER.info("[GTSNPonder] modules autotest window prepared: {}x{} guiScale=2",
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
