package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.editor.EditorSession;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneFormat;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 游戏内编辑器的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=editor} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后解析一台代表性多方块（避开其它自动测试的目标），经门控入口
 *       {@link PonderEntrypoints#openEditor(String)} 打开 {@link SceneEditorScreen}；</li>
 *   <li>打开「录制」，点击动作按钮<b>录 ≥2 步</b>（显示分段 + 旁白），再用属性表单把末步时长
 *       +5（编辑属性），断言草稿步骤数 = 基线 + 2 且末步时长被改；</li>
 *   <li>点击保存：断言写出文件存在、解析为合法 v1、步骤数 = 基线 + 2、含录制的旁白键；</li>
 *   <li>点击热重载重放：断言场景库 reload 后打开的是 <b>{@code source=hand}</b> 的手作场景，
 *       步骤数与录制一致，且 seek 到录制旁白步骤时 {@code narrationKey} 即录制的键（可重放）；</li>
 *   <li>抓取截图 {@code run/screenshots/gtsnponder-editor.png} 并自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class SceneEditorAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "editor";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-editor-autotest";
    /** 编辑器界面截图（录制 / 属性表单 / 保存已生效时抓取）。 */
    private static final String SCREENSHOT_NAME = "gtsnponder-editor.png";
    /** 热重载后重放场景的截图（编辑后的手作场景已可重放）。 */
    private static final String REPLAY_SCREENSHOT_NAME = "gtsnponder-editor-replay.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    /** 候选目标：规避 {@code scene}（焦炉）与 {@code autogen}（强制生成，忽略作者场景）的目标，避免相互污染。 */
    private static final List<String> CANDIDATES = List.of(
            "gtceu:steam_grinder", "gtceu:multi_smelter", "gtceu:implosion_compressor",
            "gtceu:vacuum_freezer", "gtceu:steel_blast_furnace");

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, OPEN, EDIT, SAVED, RELOAD, PLAY, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static String resolvedTarget;
    private static int baselineSteps;
    private static int savedDuration;
    private static Path savedFile;
    private static int narrationStepIndex = -1;

    private SceneEditorAutotest() {
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
            case EDIT -> tickEdit(minecraft);
            case SAVED -> tickSaved(minecraft);
            case RELOAD -> tickReload(minecraft);
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
        LOGGER.info("[GTSNPonder] editor autotest: {}={} -> loading world '{}'",
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
        if (!PonderEditorAccess.isEditorEnabled()) {
            fail(minecraft, "editor gate is closed in a development run");
            return;
        }
        Optional<StructureSource> source = resolveCandidate();
        if (source.isEmpty()) {
            fail(minecraft, "no representative multiblock resolved from " + CANDIDATES);
            return;
        }
        if (!PonderEntrypoints.openEditor(resolvedTarget)) {
            fail(minecraft, "could not open the editor for " + resolvedTarget);
            return;
        }
        LOGGER.info("[GTSNPonder] editor autotest: opening editor for {} ({} blocks)",
                resolvedTarget, source.get().blockCount());
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof SceneEditorScreen editor)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "editor did not open; current screen=" + minecraft.screen);
            }
            return;
        }
        if (editor.renderedFrames() < 6) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "editor never rendered (frames=" + editor.renderedFrames() + ")");
            }
            return;
        }
        baselineSteps = editor.draft().steps().size();
        LOGGER.info("[GTSNPonder] editor autotest: editor ready ({} baseline steps, {} elements)",
                baselineSteps, editor.draft().elements().size());
        stage = Stage.EDIT;
        ticks = 0;
    }

    /** 一次性脚本化编辑：录制显示分段 + 旁白（≥2 步），再微调末步时长（属性表单）。 */
    private static void tickEdit(Minecraft minecraft) {
        SceneEditorScreen editor = (SceneEditorScreen) minecraft.screen;
        if (!editor.recordToggle().checked()) {
            clickWidget(minecraft, editor.recordToggle());
        }
        if (!editor.recordToggle().checked()) {
            fail(minecraft, "record toggle did not turn on");
            return;
        }
        clickWidget(minecraft, editor.showButton());
        clickWidget(minecraft, editor.narrateButton());
        if (editor.draft().steps().size() != baselineSteps + 2) {
            fail(minecraft, "recording did not append 2 steps: baseline=" + baselineSteps
                    + " now=" + editor.draft().steps().size());
            return;
        }
        int before = editor.draft().steps().get(editor.draft().steps().size() - 1).duration();
        clickWidget(minecraft, editor.durationUpButton());
        savedDuration = editor.draft().steps().get(editor.draft().steps().size() - 1).duration();
        if (savedDuration != before + 5) {
            fail(minecraft, "property form did not nudge the duration: " + before + " -> " + savedDuration);
            return;
        }
        LOGGER.info("[GTSNPonder] editor autotest: recorded 2 steps (now {}), duration {} -> {}, selected={}",
                editor.draft().steps().size(), before, savedDuration, editor.selectedStepIndex());

        clickWidget(minecraft, editor.saveButton());
        savedFile = SceneLibrary.get().authorDirectory()
                .resolve(EditorSession.fileStemFor(editor.scene()) + ".json");
        stage = Stage.SAVED;
        ticks = 0;
    }

    private static void tickSaved(Minecraft minecraft) {
        SceneData saved;
        try {
            if (!Files.isRegularFile(savedFile)) {
                fail(minecraft, "saved draft file is missing: " + savedFile);
                return;
            }
            saved = SceneDataParser.parseOrThrow(Files.readString(savedFile, StandardCharsets.UTF_8));
        } catch (Exception error) {
            fail(minecraft, "saved draft is not valid v1 JSON: " + error);
            return;
        }
        narrationStepIndex = indexOfNarration(saved.steps(), EditorKeys.RECORD_NARRATION);
        if (saved.formatVersion() != SceneFormat.CURRENT_VERSION) {
            fail(minecraft, "saved formatVersion=" + saved.formatVersion()
                    + " (expected " + SceneFormat.CURRENT_VERSION + ")");
            return;
        }
        if (saved.steps().size() != baselineSteps + 2) {
            fail(minecraft, "saved step count=" + saved.steps().size()
                    + " (expected baseline+2=" + (baselineSteps + 2) + ")");
            return;
        }
        if (narrationStepIndex < 0) {
            fail(minecraft, "saved draft is missing the recorded narration key");
            return;
        }
        LOGGER.info("[GTSNPonder] editor autotest: saved {} (formatVersion={}, {} steps, narration step #{})",
                savedFile, saved.formatVersion(), saved.steps().size(), narrationStepIndex);

        SceneEditorScreen editor = (SceneEditorScreen) minecraft.screen;
        // 先对仍打开的编辑器界面截图（录制动作 + 属性表单 + 步骤列表的视觉证据）。
        grabScreenshot(minecraft, SCREENSHOT_NAME);
        clickWidget(minecraft, editor.reloadButton());
        stage = Stage.RELOAD;
        ticks = 0;
    }

    private static void tickReload(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "hot reload did not reopen a player; current screen=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 4) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "reloaded player never rendered (frames=" + screen.renderedFrames() + ")");
            }
            return;
        }
        SceneData reloaded = screen.scene();
        if (reloaded.source() != Source.HAND) {
            fail(minecraft, "reloaded scene source=" + reloaded.source() + " (expected hand)");
            return;
        }
        if (reloaded.steps().size() != baselineSteps + 2) {
            fail(minecraft, "reloaded step count=" + reloaded.steps().size()
                    + " (expected " + (baselineSteps + 2) + ")");
            return;
        }
        if (screen.playback().stepCount() <= narrationStepIndex) {
            fail(minecraft, "reloaded scene lost the recorded narration step");
            return;
        }
        // 重放到录制的旁白步骤：断言旁白键即编辑时录制的键（编辑 → 保存 → 热重载 → 重放闭环）。
        screen.playback().runner().seekTo(screen.playback().stepStartTime(narrationStepIndex) + 1.0d);
        String narration = screen.playback().narrationKey();
        if (!EditorKeys.RECORD_NARRATION.equals(narration)) {
            fail(minecraft, "reloaded scene did not replay the edited narration: got " + narration
                    + " (expected " + EditorKeys.RECORD_NARRATION + ")");
            return;
        }
        LOGGER.info("[GTSNPonder] editor autotest: hot reload rebuilt {} (source={}, {} steps, "
                        + "step #{} narration='{}', visibleBlocks={})",
                reloaded.target(), reloaded.source(), reloaded.steps().size(), narrationStepIndex,
                narration, screen.bridge().visibleBlockCount());
        stage = Stage.PLAY;
        ticks = 0;
    }

    private static void tickPlay(Minecraft minecraft) {
        if (ticks > 40) {
            stage = Stage.GRAB;
            ticks = 0;
        }
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        LOGGER.info("[GTSNPonder] editor autotest PASS: recorded 2 steps + edited property, saved valid v1, "
                + "hot-reloaded as hand scene and replayed the edited narration; screenshots {} / {}",
                SCREENSHOT_NAME, REPLAY_SCREENSHOT_NAME);
        grabScreenshot(minecraft, REPLAY_SCREENSHOT_NAME);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] editor autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static Optional<StructureSource> resolveCandidate() {
        for (String candidate : CANDIDATES) {
            Optional<StructureSource> source = GtStructureAdapter.byId(candidate);
            if (source.isPresent()) {
                resolvedTarget = candidate;
                return source;
            }
        }
        return Optional.empty();
    }

    private static int indexOfNarration(List<SceneStep> steps, String narrationKey) {
        for (int i = 0; i < steps.size(); i++) {
            if (narrationKey.equals(steps.get(i).narration())) {
                return i;
            }
        }
        return -1;
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] editor autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-editor-failed.png");
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
                message -> LOGGER.info("[GTSNPonder] editor autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] editor autotest window prepared: {}x{} guiScale=2",
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
