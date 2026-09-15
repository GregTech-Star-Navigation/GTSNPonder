package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.structure.StructureRole;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 物品悬停入口 + 旁白本地化的客户端自动测试（开发专用、无人值守证据，工单 #16）。
 *
 * <p>环境变量 {@code GTSNPONDER_UI_AUTOTEST=entryitem} 启用。流程：</p>
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>把 {@code gtceu:lp_steam_furnace} 物品放进玩家背包首格；打开背包屏；把光标移到该格上；</li>
 *   <li>调用思索快捷键的统一入口 {@link PonderEntrypoints#onPonderKeyPressed()}（与游戏内按键同路径），
 *       断言解析出该物品 id 且打开了对应播放屏（缺陷 C）；截图为证；</li>
 *   <li>切换到一台<b>带仓口</b>的多方块（{@code gtceu:large_combustion_engine}）的自动生成场景，seek 到
 *       成型旁白，断言：不含原始注册 id、不含任何 {@link StructureRole} 枚举名、含本地化机器名与本地化
 *       仓口角色名、尺寸段为半角括号（{@code (5x5x5)}）且<b>不含</b>全角括号（缺陷 A1/A2/A3）；截图（zh）；</li>
 *   <li>打开图鉴目录并截图（缺陷 B：条目行不叠印、长 id 截断）；</li>
 *   <li>切换语言到 en_us 后重放同一场景，断言英文旁白同样本地化（缺陷 A，en 截图）。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class EntryItemAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "entryitem";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-entryitem-autotest";
    /** 悬停入口目标：单方块机器（物品 id 与机器 id 一致，且有手作随包场景）。 */
    private static final String HOVER_TARGET = "gtceu:lp_steam_furnace";
    /** 旁白本地化目标：带仓口 / 总线的大型多方块（强制自动生成，故旁白含机器名与角色名）。 */
    private static final String NARRATION_TARGET = "gtceu:large_combustion_engine";

    private static final String HOVER_SCREENSHOT = "gtsnponder-entryitem-hover.png";
    private static final String NARRATION_ZH_SCREENSHOT = "gtsnponder-entryitem-narration-zh.png";
    private static final String NARRATION_EN_SCREENSHOT = "gtsnponder-entryitem-narration-en.png";
    private static final String CATALOG_SCREENSHOT = "gtsnponder-entryitem-catalog.png";
    private static final String FAILED_SCREENSHOT = "gtsnponder-entryitem-failed.png";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    /**
     * 打开背包屏的有界等待窗口（tick）。世界加载收尾可能覆盖我们的 {@code setScreen}，故按
     * {@link #UI_RETRY_INTERVAL_TICKS} 间隔重投，直到容器屏真正打开；超时仍会 FAIL（失败可检出）。
     */
    private static final int OPEN_TIMEOUT_TICKS = 300;
    /** 界面打开请求的重试间隔（tick）。 */
    private static final int UI_RETRY_INTERVAL_TICKS = 20;
    /**
     * 光标落到槽位上的有界等待窗口（tick）：{@link AbstractContainerScreen#getSlotUnderMouse()} 返回的
     * {@code hoveredSlot} 只在 {@code render()} 中依<b>真实鼠标坐标</b>计算，故移动光标后必须等下一帧。
     */
    private static final int HOVER_TIMEOUT_TICKS = 200;

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Stage {
        TITLE, WORLD, HOVER, PLAYER, NARRATION_ZH, CAPTURE_ZH, CATALOG, RELOAD_EN, NARRATION_EN,
        CAPTURE_EN, GRAB, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static String hoveredId;
    /** 打开背包屏的尝试次数（诊断用）。 */
    private static int openAttempts;
    /** 下一次重投打开请求的 tick。 */
    private static int nextOpenRequestTick;
    /** 当前尝试的候选槽位下标（EMI/JEI 面板可能覆盖某个槽位时回退到下一个）。 */
    private static int candidateIndex;

    private EntryItemAutotest() {
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
            case HOVER -> tickHover(minecraft);
            case PLAYER -> tickPlayer(minecraft);
            case NARRATION_ZH -> tickNarrationZh(minecraft);
            case CAPTURE_ZH -> tickCaptureZh(minecraft);
            case CATALOG -> tickCatalog(minecraft);
            case RELOAD_EN -> tickReloadEn(minecraft);
            case NARRATION_EN -> tickNarrationEn(minecraft);
            case CAPTURE_EN -> tickCaptureEn(minecraft);
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
        LOGGER.info("[GTSNPonder] entryitem autotest: {}={} -> loading world '{}'",
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
        // 就绪信号：关卡加载收尾完成——无加载覆盖层且无残留界面。否则此时 setScreen(new InventoryScreen)
        // 会被收尾的 setScreen 覆盖（历史失败：背包屏被加载界面 / CreativeModeInventoryScreen 替换）。
        if (minecraft.getOverlay() != null || minecraft.screen != null) {
            if (ticks > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "world never became ready: screen=" + describeScreen(minecraft.screen)
                        + ", overlay=" + (minecraft.getOverlay() == null
                                ? "null" : minecraft.getOverlay().getClass().getSimpleName()));
            }
            return;
        }
        ItemStack stack = machineItem(HOVER_TARGET);
        if (stack.isEmpty()) {
            fail(minecraft, "unknown item " + HOVER_TARGET + " - cannot test the hover entry");
            return;
        }
        // 在背包两处放置目标物品：光标优先落在热键栏（EMI/JEI 面板通常不覆盖），必要时回退到主背包槽。
        minecraft.player.getInventory().setItem(0, stack.copy());
        minecraft.player.getInventory().setItem(9, stack.copy());
        LOGGER.info("[GTSNPonder] entryitem autotest: world ready; placed {} in inventory slots 0/9", HOVER_TARGET);
        stage = Stage.HOVER;
        ticks = 0;
        openAttempts = 0;
        nextOpenRequestTick = 0;
        candidateIndex = 0;
    }

    /**
     * 打开背包屏（有界重投）。创造模式下 {@code InventoryScreen.init()} 会自行替换为
     * {@code CreativeModeInventoryScreen}（同为 {@link AbstractContainerScreen}），故按泛化类型处理。
     */
    private static void tickHover(Minecraft minecraft) {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            if (ticks >= nextOpenRequestTick) {
                openInventory(minecraft);
                nextOpenRequestTick = ticks + UI_RETRY_INTERVAL_TICKS;
            }
            if (ticks > OPEN_TIMEOUT_TICKS) {
                fail(minecraft, "inventory screen did not open within " + OPEN_TIMEOUT_TICKS
                        + " ticks (openAttempts=" + openAttempts + "); screen="
                        + describeScreen(minecraft.screen));
            }
            return;
        }
        if (ticks < 4) {
            return;
        }
        List<Slot> candidates = playerSlotsShowing(minecraft, screen, HOVER_TARGET);
        if (candidates.isEmpty()) {
            if (ticks > OPEN_TIMEOUT_TICKS) {
                fail(minecraft, "no player inventory slot shows " + HOVER_TARGET
                        + " (screen=" + screen.getClass().getSimpleName() + ")");
            }
            return;
        }
        if (candidateIndex >= candidates.size()) {
            fail(minecraft, "no player slot accepted the cursor hover for " + HOVER_TARGET
                    + ": candidates=" + candidates.size() + ", lastHoveredItem=" + hoveredId);
            return;
        }
        Slot slot = candidates.get(candidateIndex);
        // AbstractContainerScreen.hoveredSlot 由 render() 依真实鼠标坐标计算；screen.mouseMoved() 不会更新
        // 它（历史失败的根因）。故移动真实光标，并在有界窗口内等待其落到该槽位。
        moveCursorToSlot(minecraft, screen, slot);
        Slot under = screen.getSlotUnderMouse();
        if (under != slot) {
            if (ticks > OPEN_TIMEOUT_TICKS + HOVER_TIMEOUT_TICKS) {
                fail(minecraft, "cursor never hovered the " + HOVER_TARGET + " slot: hovered="
                        + describeSlot(under) + ", expected=" + describeSlot(slot) + ", cursor=("
                        + (int) minecraft.mouseHandler.xpos() + "," + (int) minecraft.mouseHandler.ypos()
                        + "), slotGui=(" + (screen.getGuiLeft() + slot.x + 8) + ","
                        + (screen.getGuiTop() + slot.y + 8) + ")");
            }
            return;
        }
        Optional<String> hovered = PonderEntrypoints.hoveredItemId();
        hoveredId = hovered.orElse(null);
        if (!HOVER_TARGET.equals(hoveredId)) {
            // XEI（JEI/EMI）面板可能覆盖该槽位并先返回自己的物品；换下一个候选槽位重试（仍失败可检出）。
            candidateIndex++;
            if (candidateIndex >= candidates.size()) {
                fail(minecraft, "hovered item id was " + hoveredId + ", expected " + HOVER_TARGET
                        + " (screen=" + screen.getClass().getSimpleName() + ", xei="
                        + PonderXeiItemHover.get().hoveredItemId().orElse(null) + ")");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] entryitem autotest: item under cursor = {} (slot={}, XEI source(s)={})",
                hoveredId, describeSlot(slot), PonderXeiItemHover.get().sourceCount());
        // 与游戏内快捷键同路径：有界面时按「悬停物品」入口。
        if (!PonderEntrypoints.onPonderKeyPressed()) {
            fail(minecraft, "pressing the ponder key on a hovered item did not open a scene");
            return;
        }
        stage = Stage.PLAYER;
        ticks = 0;
    }

    /** 打开背包屏（有界重投，见 {@link #tickHover}）。 */
    private static void openInventory(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        openAttempts++;
        minecraft.setScreen(new InventoryScreen(minecraft.player));
    }

    /**
     * 把<b>真实光标</b>移到槽位中心（GUI 坐标 × GUI 缩放 = 窗口像素坐标）。{@code hoveredSlot} 由
     * {@code render()} 依真实鼠标坐标计算，故这是让槽位真正「被悬停」的唯一途径。
     */
    private static void moveCursorToSlot(Minecraft minecraft, AbstractContainerScreen<?> screen, Slot slot) {
        Window window = minecraft.getWindow();
        double scale = window.getGuiScale();
        double guiX = screen.getGuiLeft() + slot.x + 8.0d;
        double guiY = screen.getGuiTop() + slot.y + 8.0d;
        GLFW.glfwSetCursorPos(window.getWindow(), guiX * scale, guiY * scale);
    }

    private static void tickPlayer(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "hover entry did not open the scene player; screen=" + minecraft.screen);
            }
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        if (!HOVER_TARGET.equals(player.scene().target())) {
            fail(minecraft, "hover entry opened the wrong target: expected " + HOVER_TARGET
                    + " got " + player.scene().target());
            return;
        }
        LOGGER.info("[GTSNPonder] entryitem autotest: hover entry opened '{}' (source={}, steps={})",
                player.scene().target(), player.scene().source(), player.scene().steps().size());
        grabScreenshot(minecraft, HOVER_SCREENSHOT);
        if (!PonderEntrypoints.openGenerated(NARRATION_TARGET)) {
            fail(minecraft, "could not open the generated scene for " + NARRATION_TARGET);
            return;
        }
        stage = Stage.NARRATION_ZH;
        ticks = 0;
    }

    private static void tickNarrationZh(Minecraft minecraft) {
        ScenePlayerScreen player = awaitGeneratedPlayer(minecraft, NARRATION_TARGET);
        if (player == null) {
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        seekToFormedNarration(player);
        if (!assertLocalizedNarration(minecraft, player, "zh")) {
            return;
        }
        stage = Stage.CAPTURE_ZH;
        ticks = 0;
    }

    /**
     * 等成型帧实际渲染后再截图（{@code Screenshot.grab} 读的是上一帧的后台缓冲；在 tick 内 seek 后立刻
     * 截图会拍到 seek 之前的帧——历史 zh 旁白截图因此显示的是 intro 而非成型句）。{@link #seekToFormedNarration}
     * 已暂停播放，故成型旁白在等待期间保持不变。
     */
    private static void tickCaptureZh(Minecraft minecraft) {
        if (ticks < 4) {
            return;
        }
        grabScreenshot(minecraft, NARRATION_ZH_SCREENSHOT);
        PonderEntrypoints.openCatalog();
        stage = Stage.CATALOG;
        ticks = 0;
    }

    private static void tickCatalog(Minecraft minecraft) {
        if (!(minecraft.screen instanceof SceneCatalogScreen catalog)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "catalog did not open; screen=" + minecraft.screen);
            }
            return;
        }
        if (catalog.renderedFrames() < 4) {
            return;
        }
        // 缺陷 B：行内名称与 id 均已截断到有界列宽（不再相互叠印）。
        LOGGER.info("[GTSNPonder] entryitem autotest: catalog rows={} categories={} progress='{}'",
                catalog.visibleEntries().size(), catalog.categoryOrder(), catalog.progressText());
        grabScreenshot(minecraft, CATALOG_SCREENSHOT);
        switchToEnglish(minecraft);
        stage = Stage.RELOAD_EN;
        ticks = 0;
    }

    private static void tickReloadEn(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || ticks < 5) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "resource reload after switching to en_us never finished");
            }
            return;
        }
        LOGGER.info("[GTSNPonder] entryitem autotest: switched language to en_us");
        if (!PonderEntrypoints.openGenerated(NARRATION_TARGET)) {
            fail(minecraft, "could not reopen the generated scene after switching language");
            return;
        }
        stage = Stage.NARRATION_EN;
        ticks = 0;
    }

    private static void tickNarrationEn(Minecraft minecraft) {
        ScenePlayerScreen player = awaitGeneratedPlayer(minecraft, NARRATION_TARGET);
        if (player == null) {
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        seekToFormedNarration(player);
        if (!assertLocalizedNarration(minecraft, player, "en")) {
            return;
        }
        stage = Stage.CAPTURE_EN;
        ticks = 0;
    }

    /** 等 en 成型帧实际渲染后再截图（同 {@link #tickCaptureZh}）。 */
    private static void tickCaptureEn(Minecraft minecraft) {
        if (ticks < 4) {
            return;
        }
        grabScreenshot(minecraft, NARRATION_EN_SCREENSHOT);
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        LOGGER.info("[GTSNPonder] entryitem autotest PASS: item-hover entry opened '{}'; narration localized "
                + "zh+en (no raw id / raw role names, halfwidth size parens); catalog row screenshot taken; "
                + "screenshots {} / {} / {} / {}", HOVER_TARGET, HOVER_SCREENSHOT, NARRATION_ZH_SCREENSHOT,
                CATALOG_SCREENSHOT, NARRATION_EN_SCREENSHOT);
        stage = Stage.DONE;
        ticks = 0;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] entryitem autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    // --- 断言辅助 -----------------------------------------------------------

    /** 等待 {@code openGenerated} 打开的播放屏；超时 / 目标不符即失败并返回 {@code null}。 */
    private static ScenePlayerScreen awaitGeneratedPlayer(Minecraft minecraft, String target) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "generated player for " + target + " did not open; screen=" + minecraft.screen);
            }
            return null;
        }
        if (!target.equals(player.scene().target())) {
            fail(minecraft, "player opened the wrong target: expected " + target
                    + " got " + player.scene().target());
            return null;
        }
        return player;
    }

    /** seek 到成型旁白帧（最后一步），暂停并同步一次控件文本。 */
    private static void seekToFormedNarration(ScenePlayerScreen player) {
        player.playback().pause();
        player.playback().seekFraction(1.0d);
        player.advance();
    }

    /**
     * 断言成型旁白已本地化（缺陷 A1/A2/A3）：不含原始注册 id、不含任何角色枚举名、含本地化机器名
     * （有仓口时还需含至少一个本地化角色名）、尺寸段为<b>半角</b>括号且不含全角括号。
     */
    private static boolean assertLocalizedNarration(Minecraft minecraft, ScenePlayerScreen player, String locale) {
        String text = player.narrationText().text();
        String target = player.scene().target();
        if (text == null || text.isBlank()) {
            fail(minecraft, locale + " narration is blank");
            return false;
        }
        if (text.contains(target)) {
            fail(minecraft, locale + " narration still shows the raw registry id: " + text);
            return false;
        }
        for (StructureRole role : StructureRole.values()) {
            if (text.contains(role.name())) {
                fail(minecraft, locale + " narration still shows the raw role enum name "
                        + role.name() + ": " + text);
                return false;
            }
        }
        String title = Component.translatable(GeneratedKeys.machineTitleKey(target)).getString();
        if (!text.contains(title)) {
            fail(minecraft, locale + " narration does not show the localized machine name '"
                    + title + "': " + text);
            return false;
        }
        if (!player.structure().hatches().isEmpty()) {
            boolean anyRole = Arrays.stream(StructureRole.values())
                    .filter(StructureRole::isHatch)
                    .map(role -> Component.translatable(GeneratedKeys.roleKey(role.name())).getString())
                    .anyMatch(text::contains);
            if (!anyRole) {
                fail(minecraft, locale + " narration does not show any localized hatch role name: " + text);
                return false;
            }
        }
        String size = player.structure().sizeX() + "x" + player.structure().sizeY()
                + "x" + player.structure().sizeZ();
        if (!text.contains("(" + size + ")")) {
            fail(minecraft, locale + " narration size segment is not halfwidth parenthesis "
                    + "(" + size + "): " + text);
            return false;
        }
        if (text.indexOf('\uFF08') >= 0 || text.indexOf('\uFF09') >= 0) {
            fail(minecraft, locale + " narration still contains a fullwidth parenthesis (font-broken): " + text);
            return false;
        }
        LOGGER.info("[GTSNPonder] entryitem autotest narration [{}]: {}", locale, text);
        return true;
    }

    /** 玩家自己背包里显示 {@code itemId} 的槽（排除创造模式物品网格等“假”槽），按菜单顺序。 */
    private static List<Slot> playerSlotsShowing(Minecraft minecraft, AbstractContainerScreen<?> screen,
            String itemId) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory() || !slot.hasItem()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();
            if (itemId.equals(id)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** 屏类名（诊断用；{@code null} → {@code "null"}）。 */
    private static String describeScreen(Screen screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }

    /** 槽位描述（诊断用）。 */
    private static String describeSlot(Slot slot) {
        if (slot == null) {
            return "null";
        }
        String item = slot.hasItem()
                ? BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString() : "<empty>";
        return "index=" + slot.index + ",item=" + item;
    }

    private static ItemStack machineItem(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(location));
    }

    /** 切到 en_us 并重载资源（旁白 en 截图用）。 */
    private static void switchToEnglish(Minecraft minecraft) {
        minecraft.getLanguageManager().setSelected("en_us");
        minecraft.options.languageCode = "en_us";
        minecraft.reloadResourcePacks();
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] entryitem autotest FAIL: {}", reason);
        grabScreenshot(minecraft, FAILED_SCREENSHOT);
        stage = Stage.FAILED;
        ticks = 0;
    }

    private static void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[GTSNPonder] entryitem autotest screenshot: {}", message.getString()));
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
        // 置于前台：移动光标触发路径依赖窗口接收鼠标事件（自动测试无人值守仍能驱动真实悬停）。
        GLFW.glfwFocusWindow(window.getWindow());
        window.setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        LOGGER.info("[GTSNPonder] entryitem autotest window prepared: {}x{} guiScale=2",
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
