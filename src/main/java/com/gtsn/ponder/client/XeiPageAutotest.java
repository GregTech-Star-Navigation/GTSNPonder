package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.gt.GtXeiPageProbe;
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
 * EMI/JEI 普通页面「思索」入口的客户端自动测试（工单 #17 缺陷 A，开发专用、无人值守证据）：
 * 环境变量 {@code GTSNPONDER_UI_AUTOTEST=xeipage} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>把中性悬停物品缝替换为测试来源（返回「鼠标下的物品」），并置为 GT 机器物品
 *       {@code gtceu:lv_macerator}；</li>
 *   <li>经 EMI 官方 API 打开该物品的真实配方页（{@link GtXeiPageProbe#openEmiRecipePage}），断言当前屏
 *       确为 EMI 页面；</li>
 *   <li>断言覆盖层（{@link MachinePonderOverlay#xei()}）识别机器物品并在屏上登记「思索」入口（真实渲染
 *       循环驱动），并断言入口落在 <b>EMI 配方面板左侧的页面按钮列</b>（与 {@code RecipeScreen.getBounds()}
 *       比较，工单 #20），截图为证；</li>
 *   <li><b>工单 #20 根因复现</b>：把悬停物品置空（真实 EMI 悬停解析在指针离开物品后即返回空）并推进若干帧，
 *       断言入口<b>仍在且矩形不变</b>——这是修复前失败的那一步（覆盖层当帧清空矩形）；</li>
 *   <li>经 Forge 事件总线投递真实的 {@code ScreenEvent.MouseButtonPressed.Pre} 命中入口矩形，断言事件被
 *       取消、覆盖层记到一次点击 / 打开，且打开的是该物品对应机器的 {@link ScenePlayerScreen}（目标一致）；</li>
 *   <li>把悬停物品换成<b>非机器</b>物品（{@code minecraft:stone}）并重开 EMI 配方页，断言覆盖层
 *       <b>不绘制</b>按钮（非目标不绘制）；</li>
 *   <li>退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class XeiPageAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "xeipage";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-xeipage-autotest";
    /** 悬停的 GT 机器物品（id 与机器 id 一致；单方块机器有使用场景）。 */
    private static final String MACHINE_ITEM = "gtceu:lv_macerator";
    /** 非机器物品（不得绘制按钮）。 */
    private static final String NON_MACHINE_ITEM = "minecraft:stone";

    private static final String OVERLAY_SCREENSHOT = "gtsnponder-xeipage.png";
    private static final String PLAYER_SCREENSHOT = "gtsnponder-xeipage-player.png";
    private static final String NON_TARGET_SCREENSHOT = "gtsnponder-xeipage-nontarget.png";
    private static final String FAILED_SCREENSHOT = "gtsnponder-xeipage-failed.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;
    private static final int OVERLAY_SETTLE_TICKS = 10;
    /** EMI {@code RecipeScreen} 页签条高度（{@code getBounds()} 的 y 比配方面板顶高 26）——位置断言用。 */
    private static final int TAB_STRIP_HEIGHT = 26;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static String hoveredItem = MACHINE_ITEM;

    /** 测试悬停来源：返回当前设定的「鼠标下物品」id（模拟 EMI/JEI 悬停解析结果）。 */
    private static final PonderXeiItemHover.Source TEST_HOVER =
            () -> Optional.ofNullable(hoveredItem);

    private enum Stage {
        TITLE, WORLD, OPEN, VERIFY, HOVER_OFF, CLICK, NON_TARGET, NON_TARGET_VERIFY, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static boolean worldPrepared;
    private static String target;
    /** 首帧登记到的入口矩形（#20）：指针离开悬停物品后必须仍在且不变。 */
    private static MachinePonderButton.Box expectedBox;

    private XeiPageAutotest() {
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
            case VERIFY -> tickVerify(minecraft);
            case HOVER_OFF -> tickHoverOff(minecraft);
            case CLICK -> tickClick(minecraft);
            case NON_TARGET -> tickNonTarget(minecraft);
            case NON_TARGET_VERIFY -> tickNonTargetVerify(minecraft);
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
        LOGGER.info("[GTSNPonder] xeipage autotest: {}={} -> loading world '{}'", AUTOTEST_ENV, MODE, LEVEL_NAME);
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
        if (minecraft.getOverlay() != null || minecraft.screen != null) {
            if (ticks > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "world never became ready: screen=" + describeScreen(minecraft.screen));
            }
            return;
        }
        if (!worldPrepared) {
            // 用测试来源替换中性悬停物品缝（EMI/JEI 源的实时悬停难以无人值守定位；本测试聚焦覆盖层
            // 的屏幕识别 + 机器解析 + 绘制 / 点击，悬停提取本身已由 `entryitem` 真机测试覆盖）。
            PonderXeiItemHover.get().reset();
            PonderXeiItemHover.get().register(TEST_HOVER);
            hoveredItem = MACHINE_ITEM;
            MachinePonderOverlay.xei().reset();
            worldPrepared = true;
            LOGGER.info("[GTSNPonder] xeipage autotest: world ready; waiting for the EMI/JEI plugin to register "
                    + "its XEI page target source...");
        }
        // EMI 在资源重载线程上分阶段调用插件的 initialize() 与 register()；世界「就绪」可能早于
        // EMI 的 register 阶段（实测：本 mod 的 register 排在 emi/ldlib/gtceu 之后），故有界等待来源接线。
        if (PonderXeiPageTargets.get().sourceCount() == 0) {
            if (ticks > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "no XEI page target source became available (EMI/JEI plugin not loaded?)");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] xeipage autotest: XEI page target source(s)={}, hover source(s)={}, "
                + "hovered item='{}'", PonderXeiPageTargets.get().sourceCount(),
                PonderXeiItemHover.get().sourceCount(), hoveredItem);
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        // EMI 的插件 register / 配方烘焙在资源重载线程上晚于世界就绪（实测 ~10s）；故每 20 tick 重试
        // 打开，直到真实 EMI 页面出现（displayRecipes 在 recipe manager 未就绪时静默 no-op）。
        if (ticks >= 5 && ticks % 20 == 0 && !GtXeiPageProbe.isEmiScreen(minecraft.screen)) {
            if (!GtXeiPageProbe.openEmiRecipePage(MACHINE_ITEM)) {
                LOGGER.info("[GTSNPonder] xeipage autotest: EMI recipe page not ready yet, retrying");
            }
        }
        if (!GtXeiPageProbe.isEmiScreen(minecraft.screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the EMI recipe page never appeared (screen="
                        + describeScreen(minecraft.screen) + ")");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] xeipage autotest: EMI page detected ({}), resolving overlay target",
                minecraft.screen.getClass().getName());
        stage = Stage.VERIFY;
        ticks = 0;
    }

    private static void tickVerify(Minecraft minecraft) {
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        if (!overlay.isActive()) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the overlay button was never registered on the EMI page (target source(s)="
                        + PonderXeiPageTargets.get().sourceCount() + ")");
            }
            return;
        }
        if (!MACHINE_ITEM.equals(overlay.target().orElse(null))) {
            fail(minecraft, "the overlay shows the wrong target: expected " + MACHINE_ITEM
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
        // 工单 #20：入口必须落在 EMI 配方面板左侧的页面按钮列（不再是屏幕右上角悬浮）。
        MachinePonderButton.Box page = GtXeiPageProbe.recipePageBounds(minecraft.screen).orElse(null);
        if (page == null) {
            fail(minecraft, "could not read the EMI recipe page layout for the position assertion (screen="
                    + describeScreen(minecraft.screen) + ")");
            return;
        }
        LOGGER.info("[GTSNPonder] xeipage autotest: entry box={} vs EMI page bounds={} ({}x{} screen)",
                box, page, minecraft.screen.width, minecraft.screen.height);
        if (box.x() >= minecraft.screen.width / 2
                || box.right() > page.x() + page.width() / 4) {
            fail(minecraft, "the entry is not in the page's left page-button column (box=" + box
                    + " page=" + page + ") - it looks like the old top-right floating button");
            return;
        }
        if (box.x() < page.x() || box.right() > page.x() + page.width() / 2) {
            fail(minecraft, "the entry is not on the EMI page's left half: box=" + box + " page=" + page);
            return;
        }
        if (box.y() < page.y() + TAB_STRIP_HEIGHT || box.bottom() > page.y() + page.height() / 2) {
            fail(minecraft, "the entry is not in the page's top-left button block: box=" + box
                    + " page=" + page);
            return;
        }
        target = MACHINE_ITEM;
        expectedBox = box;
        LOGGER.info("[GTSNPonder] xeipage autotest: entry registered in the page-list column at {} for {}",
                box, target);
        grabScreenshot(minecraft, OVERLAY_SCREENSHOT);

        // 工单 #20 根因复现：真实 EMI 悬停解析只在指针压在物品上时给出机器；用户必须把指针移开物品
        // 才能点到入口。此处把悬停来源置空以模拟「指针正移向入口」的那些帧——入口必须仍在。
        // 只保留测试来源：EMI 的实时来源可能晚于本测试注册，会把「无悬停」兜底成真实悬停而掩盖复现
        // （只影响中性悬停缝；页面锚点缝不受影响）。
        PonderXeiItemHover.get().reset();
        PonderXeiItemHover.get().register(TEST_HOVER);
        hoveredItem = null;
        stage = Stage.HOVER_OFF;
        ticks = 0;
    }

    /**
     * 指针离开悬停物品后的帧：断言入口<b>仍在</b>（锁存到本页）且矩形不变，然后点击它必须打开场景。
     * 这是 #20 的 fail-able 复现——修复前 {@code present(null,...)} 会把矩形清空，点击落空。
     */
    private static void tickHoverOff(Minecraft minecraft) {
        if (ticks < OVERLAY_SETTLE_TICKS) {
            return;
        }
        MachinePonderOverlay overlay = MachinePonderOverlay.xei();
        if (!overlay.isActive()) {
            fail(minecraft, "the entry vanished once the pointer left the hovered item - it cannot be clicked"
                    + " (root cause of #20; target=" + overlay.target().orElse(null) + ")");
            return;
        }
        if (!expectedBox.equals(overlay.button().orElse(null))) {
            fail(minecraft, "the entry moved after the pointer left the hovered item: expected "
                    + expectedBox + " got " + overlay.button().orElse(null));
            return;
        }
        if (!MACHINE_ITEM.equals(overlay.target().orElse(null))) {
            fail(minecraft, "the latched target changed while moving onto the entry: expected " + MACHINE_ITEM
                    + " got " + overlay.target().orElse(null));
            return;
        }
        LOGGER.info("[GTSNPonder] xeipage autotest: entry survived the hover loss at {} (clicks={}, opens={})",
                expectedBox, overlay.clicks(), overlay.opens());

        double clickX = expectedBox.x() + expectedBox.width() / 2.0d;
        double clickY = expectedBox.y() + expectedBox.height() / 2.0d;
        boolean canceled = MinecraftForge.EVENT_BUS.post(
                new ScreenEvent.MouseButtonPressed.Pre(minecraft.screen, clickX, clickY, 0));
        LOGGER.info("[GTSNPonder] xeipage autotest: posted MouseButtonPressed.Pre at ({}, {}), canceled={}, "
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
            if (MachinePonderOverlay.xei().isActive()) {
                fail(minecraft, "the XEI overlay stayed active on the non-XEI scene player screen");
                return;
            }
            LOGGER.info("[GTSNPonder] xeipage autotest: overlay click opened the scene player for '{}'", target);
            grabScreenshot(minecraft, PLAYER_SCREENSHOT);
            // 换成非机器物品，重开一个 EMI 页，验证「非目标不绘制」。
            hoveredItem = NON_MACHINE_ITEM;
            stage = Stage.NON_TARGET;
            ticks = 0;
            return;
        }
        if (ticks > STAGE_TIMEOUT_TICKS) {
            fail(minecraft, "clicking the overlay did not open the scene player; screen="
                    + describeScreen(minecraft.screen));
        }
    }

    private static void tickNonTarget(Minecraft minecraft) {
        if (ticks >= 5 && ticks % 20 == 0 && !GtXeiPageProbe.isEmiScreen(minecraft.screen)) {
            GtXeiPageProbe.openEmiRecipePage(NON_MACHINE_ITEM);
        }
        if (!GtXeiPageProbe.isEmiScreen(minecraft.screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "the EMI page for the non-machine item never appeared (screen="
                        + describeScreen(minecraft.screen) + ")");
            }
            return;
        }
        stage = Stage.NON_TARGET_VERIFY;
        ticks = 0;
    }

    private static void tickNonTargetVerify(Minecraft minecraft) {
        if (ticks < OVERLAY_SETTLE_TICKS) {
            return;
        }
        if (MachinePonderOverlay.xei().isActive()) {
            fail(minecraft, "the overlay drew a button for a non-machine item ("
                    + NON_MACHINE_ITEM + "): target=" + MachinePonderOverlay.xei().target().orElse(null));
            return;
        }
        grabScreenshot(minecraft, NON_TARGET_SCREENSHOT);
        LOGGER.info("[GTSNPonder] xeipage autotest PASS: EMI page overlay button drawn and click opened '{}'; "
                + "no button for the non-machine item '{}'; screenshots {} / {} / {}", target, NON_MACHINE_ITEM,
                OVERLAY_SCREENSHOT, PLAYER_SCREENSHOT, NON_TARGET_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] xeipage autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] xeipage autotest FAIL: {}", reason);
        grabScreenshot(minecraft, FAILED_SCREENSHOT);
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] xeipage autotest screenshot: {}", message.getString()));
    }

    private static String describeScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen == null ? "null" : screen.getClass().getName();
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
        GLFW.glfwFocusWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] xeipage autotest window prepared: {}x{} guiScale=2",
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
