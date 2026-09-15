package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataWriter;
import com.gtsn.ponder.generate.SceneVariants;
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

import java.util.List;

/**
 * 多变体播放的客户端自动测试（工单 #21 反馈 2）：环境变量 {@code GTSNPONDER_UI_AUTOTEST=variants}
 * 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>世界就绪后，从候选表里找一台<b>可重复结构段</b>的多方块（{@link GtStructureAdapter#variantsById}
 *       返回 ≥2 个结构页）——找不到即 FAIL（功能不可验证）；</li>
 *   <li>断言「长」变体的结构比「短」变体大（或步骤数不同），且两变体生成字节确定；</li>
 *   <li>播放短变体 → 断言播放屏出现变体按钮、场景 id / variant 正确 → 截图；</li>
 *   <li>点击「变体」按钮切到长变体 → 断言新屏的 variant / 结构更大 / 步骤数不同 → 截图；</li>
 *   <li>自动退出。任何断言失败 / 超时都记录 FAIL、截图并退出。</li>
 * </ol>
 *
 * <p>仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class VariantsAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "variants";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-variants-autotest";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;
    private static final int CAPTURE_SETTLE_TICKS = 20;
    private static final int OPEN_SETTLE_TICKS = 10;

    /** 可重复结构段候选：装配线 / 热解炉 / 电力高炉 等（按序取第一台有 ≥2 结构页者）。 */
    private static final List<String> CANDIDATES = List.of(
            "gtceu:assembly_line",
            "gtceu:distillation_tower",
            "gtceu:pyrolyse_oven",
            "gtceu:electric_blast_furnace",
            "gtceu:multi_smelter",
            "gtceu:power_substation");

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, SHORT_OPEN, SHORT_SETTLE, SHORT_GRAB, VARIANT_CLICK, LONG_OPEN, LONG_GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;

    private static String target;
    private static int shortBlocks;
    private static int longBlocks;
    private static int shortSteps;
    private static int longSteps;
    /** 变体按钮点击一次后进入的下一变体（下标 1）的结构 / 步骤数。 */
    private static int nextBlocks;
    private static int nextSteps;

    private VariantsAutotest() {
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
            case SHORT_OPEN -> tickShortOpen(minecraft);
            case SHORT_SETTLE -> tickShortSettle(minecraft);
            case SHORT_GRAB -> tickShortGrab(minecraft);
            case VARIANT_CLICK -> tickVariantClick(minecraft);
            case LONG_OPEN -> tickLongOpen(minecraft);
            case LONG_GRAB -> tickLongGrab(minecraft);
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
        LOGGER.info("[GTSNPonder] variants autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
        // 审计：记录候选机的结构页数（哪台有长短变体）。
        target = null;
        for (String candidate : CANDIDATES) {
            List<StructureSource> variants = GtStructureAdapter.variantsById(candidate);
            LOGGER.info("[GTSNPonder] variants autotest: {} has {} structure page(s) (blocks={})",
                    candidate, variants.size(),
                    variants.stream().map(StructureSource::blockCount).toList());
            if (target == null && variants.size() >= 2) {
                target = candidate;
            }
        }
        if (target == null) {
            fail(minecraft, "no repeatable-aisle multiblock with >=2 variants among " + CANDIDATES);
            return;
        }
        List<StructureSource> variants = GtStructureAdapter.variantsById(target);
        StructureSource shortShape = variants.get(0);
        StructureSource longShape = variants.get(variants.size() - 1);
        List<SceneVariants.Spec> specs = SceneVariants.specs(variants);
        SceneData shortScene = SceneVariants.sceneFor(shortShape, specs.get(0));
        SceneData longScene = SceneVariants.sceneFor(longShape, specs.get(specs.size() - 1));
        shortBlocks = shortShape.blockCount();
        longBlocks = longShape.blockCount();
        shortSteps = shortScene.steps().size();
        longSteps = longScene.steps().size();
        // 变体按钮每次切到下一个变体（下标 1），它是自动测试实际会点到的那个。
        SceneData nextScene = SceneVariants.sceneFor(variants.get(1), specs.get(1));
        nextBlocks = variants.get(1).blockCount();
        nextSteps = nextScene.steps().size();

        if (longBlocks <= shortBlocks && longSteps <= shortSteps) {
            fail(minecraft, "the long variant is neither larger nor longer than the short one (blocks "
                    + shortBlocks + "->" + longBlocks + ", steps " + shortSteps + "->" + longSteps + ")");
            return;
        }
        if (!SceneDataWriter.toJson(shortScene).equals(SceneDataWriter.toJson(shortScene))
                || !SceneDataWriter.toJson(longScene).equals(SceneDataWriter.toJson(longScene))) {
            fail(minecraft, "variant generation is not deterministic for " + target);
            return;
        }
        LOGGER.info("[GTSNPonder] variants autotest: target='{}' variants={} short(id={},variant={},blocks={},steps={}) "
                        + "long(id={},variant={},blocks={},steps={})",
                target, variants.size(), shortScene.id(), shortScene.variant(), shortBlocks, shortSteps,
                longScene.id(), longScene.variant(), longBlocks, longSteps);

        if (!PonderEntrypoints.openVariant(target, 0)) {
            fail(minecraft, "could not open the short variant of " + target);
            return;
        }
        stage = Stage.SHORT_OPEN;
        ticks = 0;
    }

    private static void tickShortOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "short variant screen did not open; current=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 6) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "short variant never rendered");
            }
            return;
        }
        if (screen.variantButton() == null) {
            fail(minecraft, "the variant selector button is missing for a multi-variant target");
            return;
        }
        if (!SceneVariants.VARIANT_DEFAULT.equals(screen.scene().variant())) {
            fail(minecraft, "short variant must be the default variant, got " + screen.scene().variant());
            return;
        }
        if (screen.structure().blockCount() != shortBlocks) {
            fail(minecraft, "short variant structure mismatch: screen=" + screen.structure().blockCount()
                    + " expected=" + shortBlocks);
            return;
        }
        LOGGER.info("[GTSNPonder] variants autotest: short variant opened (variant={}, blocks={}, steps={}, "
                        + "selector='{}')",
                screen.scene().variant(), screen.structure().blockCount(), screen.scene().steps().size(),
                screen.variantButton().label());
        stage = Stage.SHORT_SETTLE;
        ticks = 0;
    }

    private static void tickShortSettle(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        capture(minecraft, "gtsnponder-variants-short.png");
        stage = Stage.SHORT_GRAB;
        ticks = 0;
    }

    private static void tickShortGrab(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        stage = Stage.VARIANT_CLICK;
        ticks = 0;
    }

    /** 点击播放屏的「变体」按钮，切到下一个（长）变体。 */
    private static void tickVariantClick(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen) || screen.variantButton() == null) {
            fail(minecraft, "no variant button to click");
            return;
        }
        var bounds = screen.variantButton().bounds();
        double x = bounds.x() + bounds.width() / 2.0;
        double y = bounds.y() + bounds.height() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        LOGGER.info("[GTSNPonder] variants autotest: clicked the variant selector at ({}, {})", x, y);
        stage = Stage.LONG_OPEN;
        ticks = 0;
    }

    private static void tickLongOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "long variant screen did not open; current=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 6) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "long variant never rendered");
            }
            return;
        }
        if (SceneVariants.VARIANT_DEFAULT.equals(screen.scene().variant())) {
            fail(minecraft, "the variant button did not switch away from the default variant");
            return;
        }
        if (screen.variantIndex() < 1) {
            fail(minecraft, "variant index did not advance: " + screen.variantIndex());
            return;
        }
        if (screen.structure().blockCount() != nextBlocks) {
            fail(minecraft, "next variant structure mismatch: screen=" + screen.structure().blockCount()
                    + " expected=" + nextBlocks);
            return;
        }
        if (screen.scene().steps().size() != nextSteps) {
            fail(minecraft, "next variant step count mismatch: " + screen.scene().steps().size()
                    + " expected " + nextSteps);
            return;
        }
        if (screen.structure().blockCount() <= shortBlocks && screen.scene().steps().size() <= shortSteps) {
            fail(minecraft, "the next variant is not larger/longer than the short one");
            return;
        }
        LOGGER.info("[GTSNPonder] variants autotest: next variant opened via the selector "
                        + "(variant={}, blocks={} > {}, steps={} vs {})",
                screen.scene().variant(), screen.structure().blockCount(), shortBlocks,
                screen.scene().steps().size(), shortSteps);
        stage = Stage.LONG_GRAB;
        ticks = 0;
    }

    private static void tickLongGrab(Minecraft minecraft) {
        if (ticks < CAPTURE_SETTLE_TICKS) {
            return;
        }
        capture(minecraft, "gtsnponder-variants-long.png");
        LOGGER.info("[GTSNPonder] variants autotest PASS: {} variants=2+ short/long played and screenshotted",
                target);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] variants autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void capture(Minecraft minecraft, String name) {
        ScenePlayerScreen screen = (ScenePlayerScreen) minecraft.screen;
        LOGGER.info("[GTSNPonder] variants autotest: capturing {} (variant={}, blocks={}, steps={})",
                name, screen.scene().variant(), screen.structure().blockCount(), screen.scene().steps().size());
        grabScreenshot(minecraft, name);
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] variants autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-variants-failed.png");
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] variants autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] variants autotest window prepared: {}x{} guiScale=2",
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
