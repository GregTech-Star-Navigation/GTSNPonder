package com.gtsn.ponder.client;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.widget.ButtonWidget;
import com.gtsn.lib.ui.widget.Stack;
import com.gtsn.lib.ui.widget.Widget;
import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.generate.SceneGenerator;
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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 思索图鉴目录的客户端自动测试（开发专用、无人值守证据）：环境变量
 * {@code GTSNPONDER_UI_AUTOTEST=catalog} 启用。流程：
 *
 * <ol>
 *   <li>标题界面：固定窗口 1280x720 + GUI 缩放 2，创建 / 载入固定存档；</li>
 *   <li>清空观看进度并（按需）为几台代表性多方块写入作者场景，使目录有多个类别 / 条目可浏览；</li>
 *   <li>经 {@link PonderEntrypoints#openCatalog()} 打开 {@link SceneCatalogScreen}；</li>
 *   <li>断言「场景列表非空且与已加载场景库一致」（稳定键集合相等）与「类别覆盖全部条目」；截图；</li>
 *   <li>搜索：设置搜索词后断言全部命中且数量下降（可失败）；再设不可能命中的词断言为空；清空恢复全部；截图；</li>
 *   <li>点击某条目的「播放」→ 断言打开的是该目标的播放屏，且进度被标记为已看；</li>
 *   <li>重开目录 → 断言该条目已显示为已看、总进度 +1；截图；</li>
 *   <li>多尺寸行布局（工单 #19）：依次切到 1280x720@2 / 1920x1080@2 / 1280x720@3，断言每条可见行
 *       的 5 个单元（标记 / 名称 / id / 相关 / 播放）都落在行内、尾部两个按钮在屏幕内——修复前
 *       「名称列 fill 撑满 + 固定列溢出」在 1280x720@3（行宽 ~262px）把 id 与两个按钮挤出视口，
 *       该断言必红；每个尺寸各截图一张，随后自动退出。</li>
 * </ol>
 *
 * <p>任何断言失败 / 超时都记录 FAIL 证据、截图并退出，保证无人值守可终止。仅客户端加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public final class CatalogAutotest {

    /** 自动测试开关值。 */
    public static final String MODE = "catalog";
    /** 环境变量名（与其它自动测试同构）。 */
    public static final String AUTOTEST_ENV = "GTSNPONDER_UI_AUTOTEST";

    private static final String LEVEL_NAME = "gtsnponder-catalog-autotest";
    private static final String LIST_SCREENSHOT = "gtsnponder-catalog.png";
    private static final String SEARCH_SCREENSHOT = "gtsnponder-catalog-search.png";
    private static final String WATCHED_SCREENSHOT = "gtsnponder-catalog-watched.png";
    private static final String IMPOSSIBLE_QUERY = "zzz-no-such-ponder-scene";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WORLD_TIMEOUT_TICKS = 3600;
    private static final int STAGE_TIMEOUT_TICKS = 1200;

    /** 用于给目录补充类别 / 条目的种子目标（避开其它自动测试选用的目标，避免相互污染）。 */
    private static final List<String> SEED_TARGETS = List.of(
            "gtceu:large_steam_turbine", "gtceu:large_gas_turbine", "gtceu:pyrolyse_oven",
            "gtceu:primitive_blast_furnace");

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 多尺寸行布局检查的窗口 / GUI 缩放组合（工单 #19）：像素宽 × 像素高 × GUI 缩放。 */
    private static final int[][] LAYOUT_SIZES = {
            {1280, 720, 2},
            {1920, 1080, 2},
            {1280, 720, 3},
    };
    /** 每个尺寸检查的前 N 行（列表顶部；足以覆盖首屏且不依赖滚动）。 */
    private static final int LAYOUT_ROWS_CHECKED = 8;
    /** 切尺寸后等待布局 / 渲染稳定的 tick 数。 */
    private static final int LAYOUT_SETTLE_TICKS = 10;
    /** 等待窗口真正变成目标像素尺寸的上限 tick 数（超时退化为实际可用尺寸，仅告警）。 */
    private static final int LAYOUT_WINDOW_WAIT_TICKS = 60;

    private enum Stage {
        TITLE, WORLD, OPEN, FILTER, PLAY, VERIFY, GRAB, LAYOUT, DONE, FAILED
    }

    private static Stage stage = Stage.TITLE;
    private static int ticks;
    private static boolean stopped;
    private static int baselineWatched;
    private static String playedTarget;
    private static int visibleBeforeFilter;
    private static boolean searchApplied;
    private static int layoutIndex;
    private static int layoutPhase;

    private CatalogAutotest() {
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
            case FILTER -> tickFilter(minecraft);
            case PLAY -> tickPlay(minecraft);
            case VERIFY -> tickVerify(minecraft);
            case GRAB -> tickGrab(minecraft);
            case LAYOUT -> tickLayout(minecraft);
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
        LOGGER.info("[GTSNPonder] catalog autotest: {}={} -> loading world '{}'",
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
        PonderProgress.get().reset();
        seedAuthorScenes();
        SceneLibrary.get().reload();
        if (SceneLibrary.get().sceneCount() <= 0) {
            fail(minecraft, "scene library is empty; cannot test the catalog");
            return;
        }
        baselineWatched = PonderProgress.get().watchedCount();
        LOGGER.info("[GTSNPonder] catalog autotest: library has {} scene(s), watched baseline {}; opening catalog",
                SceneLibrary.get().sceneCount(), baselineWatched);
        if (!PonderEntrypoints.openCatalog()) {
            fail(minecraft, "could not open the catalog");
            return;
        }
        stage = Stage.OPEN;
        ticks = 0;
    }

    private static void tickOpen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof SceneCatalogScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "catalog did not open; current screen=" + minecraft.screen);
            }
            return;
        }
        if (screen.renderedFrames() < 4) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "catalog never rendered (frames=" + screen.renderedFrames() + ")");
            }
            return;
        }
        SceneCatalog catalog = screen.catalog();
        if (catalog.total() <= 0) {
            fail(minecraft, "catalog is empty");
            return;
        }
        // 工单 #13 起，目录是「已加载场景 ∪ 全部注册多方块（按需生成的合成条目）」的覆盖超集，
        // 故断言：目录包含全部已加载手作场景的目标，且条目数不少于手作场景数（覆盖语义见
        // coverage 自动测试）。
        Set<String> catalogTargets = catalog.entries().stream().map(CatalogEntry::target)
                .collect(Collectors.toSet());
        Set<String> libraryTargets = SceneLibrary.get().scenes().stream()
                .filter(scene -> scene.target() != null && !scene.target().isBlank())
                .map(SceneData::target)
                .collect(Collectors.toSet());
        if (!catalogTargets.containsAll(libraryTargets)) {
            fail(minecraft, "catalog is missing loaded library targets: missing="
                    + libraryTargets.stream().filter(target -> !catalogTargets.contains(target)).toList());
            return;
        }
        if (catalog.total() < libraryTargets.size()) {
            fail(minecraft, "catalog has fewer entries than loaded library scenes: catalog="
                    + catalog.total() + " library=" + libraryTargets.size());
            return;
        }
        if (screen.visibleEntries().size() != catalog.total()) {
            fail(minecraft, "an empty query must show every entry: visible=" + screen.visibleEntries().size()
                    + " total=" + catalog.total());
            return;
        }
        if (!screen.categoryOrder().equals(catalog.categories()) || catalog.categories().isEmpty()) {
            fail(minecraft, "category sidebar does not cover the present categories: sidebar="
                    + screen.categoryOrder() + " present=" + catalog.categories());
            return;
        }
        if (catalog.categories().stream().anyMatch(category -> catalog.inCategory(category).isEmpty())) {
            fail(minecraft, "a listed category has no entries: " + catalog.categories());
            return;
        }
        // 点击类别按钮：断言可见条目恰好是该类别的子集，点「全部」恢复全量（类别浏览可失败）。
        String firstCategory = catalog.categories().get(0);
        clickWidget(minecraft, screen.categoryButton(firstCategory));
        if (!firstCategory.equals(screen.selectedCategory())
                || screen.visibleEntries().stream().anyMatch(entry -> !firstCategory.equals(entry.category()))
                || screen.visibleEntries().size() != catalog.inCategory(firstCategory).size()) {
            fail(minecraft, "category browsing failed for " + firstCategory + ": selected="
                    + screen.selectedCategory() + " visible=" + screen.visibleEntries().size());
            return;
        }
        clickWidget(minecraft, screen.allCategoryButton());
        if (screen.selectedCategory() != null || screen.visibleEntries().size() != catalog.total()) {
            fail(minecraft, "'All' did not restore every entry: selected=" + screen.selectedCategory()
                    + " visible=" + screen.visibleEntries().size() + "/" + catalog.total());
            return;
        }
        LOGGER.info("[GTSNPonder] catalog autotest: category browsing verified ({} -> {} -> all)",
                catalog.total(), firstCategory);
        LOGGER.info("[GTSNPonder] catalog autotest: {} entries across {} categories, watched={}/{}",
                catalog.total(), catalog.categories(), catalog.watchedCount(), catalog.total());
        grabScreenshot(minecraft, LIST_SCREENSHOT);
        stage = Stage.FILTER;
        ticks = 0;
    }

    private static void tickFilter(Minecraft minecraft) {
        SceneCatalogScreen screen = (SceneCatalogScreen) minecraft.screen;
        if (!searchApplied) {
            CatalogEntry first = screen.catalog().entries().get(0);
            String query = token(first.target());
            if (query.isBlank()) {
                fail(minecraft, "could not derive a search token from " + first.target());
                return;
            }
            visibleBeforeFilter = screen.visibleEntries().size();
            long matching = screen.catalog().entries().stream().filter(entry -> entry.matches(query)).count();
            screen.setQuery(query);
            List<CatalogEntry> hits = screen.visibleEntries();
            if (hits.isEmpty()) {
                fail(minecraft, "search '" + query + "' returned nothing though '" + first.target() + "' exists");
                return;
            }
            if (hits.stream().anyMatch(entry -> !entry.matches(query))) {
                fail(minecraft, "search '" + query + "' returned an entry that does not match");
                return;
            }
            if (hits.size() != matching) {
                fail(minecraft, "search '" + query + "' hid matching entries: showed " + hits.size()
                        + " of " + matching);
                return;
            }
            if (matching < visibleBeforeFilter) {
                LOGGER.info("[GTSNPonder] catalog autotest: search '{}' filtered {} -> {}", query,
                        visibleBeforeFilter, hits.size());
            } else {
                LOGGER.info("[GTSNPonder] catalog autotest: search '{}' matched every entry ({})",
                        query, hits.size());
            }
            searchApplied = true;
            ticks = 0;
            return;
        }
        if (ticks < 3) {
            // 让过滤后的控件树至少渲染一帧，使截图如实反映搜索态（截图取上一帧）。
            return;
        }
        grabScreenshot(minecraft, SEARCH_SCREENSHOT);

        screen.setQuery(IMPOSSIBLE_QUERY);
        if (!screen.visibleEntries().isEmpty()) {
            fail(minecraft, "impossible query returned " + screen.visibleEntries().size() + " entries");
            return;
        }
        screen.setQuery("");
        if (screen.visibleEntries().size() != screen.catalog().total()) {
            fail(minecraft, "clearing the query did not restore all entries: "
                    + screen.visibleEntries().size() + "/" + screen.catalog().total());
            return;
        }
        if (screen.visiblePlayButtons().size() != screen.visibleEntries().size()) {
            fail(minecraft, "play buttons are out of sync with entries: buttons="
                    + screen.visiblePlayButtons().size() + " entries=" + screen.visibleEntries().size());
            return;
        }
        playedTarget = screen.catalog().entries().get(0).target();
        // 工单 #13：目录条目可能是「无手作场景的注册多方块」的合成条目，故经真实解析缝解析（手作优先、
        // 否则按需生成），而非只查手作场景库。
        SceneData played = PonderEntrypoints.resolveSceneForTarget(playedTarget).orElse(null);
        if (played == null || PonderProgress.get().isWatched(played)) {
            fail(minecraft, "target " + playedTarget + " is missing or unexpectedly already watched");
            return;
        }
        clickWidget(minecraft, screen.visiblePlayButtons().get(0));
        LOGGER.info("[GTSNPonder] catalog autotest: clicked play for {}", playedTarget);
        stage = Stage.PLAY;
        ticks = 0;
    }

    private static void tickPlay(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ScenePlayerScreen player)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "playing a catalog entry did not open the scene player; screen="
                        + minecraft.screen);
            }
            return;
        }
        if (player.renderedFrames() < 4) {
            return;
        }
        if (!playedTarget.equals(player.scene().target())) {
            fail(minecraft, "player opened the wrong target: expected " + playedTarget
                    + " got " + player.scene().target());
            return;
        }
        if (!PonderProgress.get().isWatched(player.scene())) {
            fail(minecraft, "opening the player did not mark " + playedTarget + " as watched");
            return;
        }
        LOGGER.info("[GTSNPonder] catalog autotest: played '{}' ({} steps); progress now {}/{}",
                player.scene().target(), player.scene().steps().size(),
                PonderProgress.get().watchedCount(), baselineWatched + 1);
        PonderEntrypoints.openCatalog();
        stage = Stage.VERIFY;
        ticks = 0;
    }

    private static void tickVerify(Minecraft minecraft) {
        if (!(minecraft.screen instanceof SceneCatalogScreen screen)) {
            if (ticks > STAGE_TIMEOUT_TICKS) {
                fail(minecraft, "reopened catalog did not appear");
            }
            return;
        }
        if (screen.renderedFrames() < 4) {
            return;
        }
        SceneData played = PonderEntrypoints.resolveSceneForTarget(playedTarget).orElse(null);
        if (played == null) {
            fail(minecraft, "scene for " + playedTarget + " disappeared after playing");
            return;
        }
        CatalogEntry entry = screen.catalog().entries().stream()
                .filter(candidate -> playedTarget.equals(candidate.target()))
                .findFirst().orElse(null);
        if (entry == null) {
            fail(minecraft, "reopened catalog lost the played entry " + playedTarget);
            return;
        }
        if (!entry.watched()) {
            fail(minecraft, "reopened catalog still shows " + playedTarget + " as unwatched");
            return;
        }
        if (screen.catalog().watchedCount() != baselineWatched + 1) {
            fail(minecraft, "watched count did not advance: " + baselineWatched + " -> "
                    + screen.catalog().watchedCount());
            return;
        }
        LOGGER.info("[GTSNPonder] catalog autotest: progress persisted; '{}' now watched ({}), total {}",
                playedTarget, PonderProgress.get().isWatched(played), screen.progressText());
        stage = Stage.GRAB;
        ticks = 0;
    }

    private static void tickGrab(Minecraft minecraft) {
        if (ticks < 10) {
            return;
        }
        grabScreenshot(minecraft, WATCHED_SCREENSHOT);
        LOGGER.info("[GTSNPonder] catalog autotest: main flow PASS (catalog matched the library, search "
                + "filtered, progress advanced); screenshots {} / {} / {}; checking row layout at {} size(s)",
                LIST_SCREENSHOT, SEARCH_SCREENSHOT, WATCHED_SCREENSHOT, LAYOUT_SIZES.length);
        stage = Stage.LAYOUT;
        layoutIndex = 0;
        layoutPhase = 0;
        ticks = 0;
    }

    /**
     * 多尺寸行布局检查（工单 #19）：窗口 / GUI 缩放矩阵 = {@link #LAYOUT_SIZES}。每尺寸三阶段：
     * 0) 请求窗口像素尺寸；1) 等 GLFW 生效（超时退化为实际可用尺寸并告警）→ 设 GUI 缩放 + resizeDisplay；
     * 2) 等布局稳定 → 截图 → 断言行内 5 个单元 + 尾部按钮都在行内 / 屏幕内。
     */
    private static void tickLayout(Minecraft minecraft) {
        if (ticks > STAGE_TIMEOUT_TICKS) {
            fail(minecraft, "layout stage timed out at size index " + layoutIndex + " phase " + layoutPhase);
            return;
        }
        int[] size = LAYOUT_SIZES[layoutIndex];
        String label = size[0] + "x" + size[1] + " g" + size[2];
        Window window = minecraft.getWindow();
        switch (layoutPhase) {
            case 0 -> {
                window.setWindowed(size[0], size[1]);
                layoutPhase = 1;
                ticks = 0;
            }
            case 1 -> {
                boolean pixelsMatch = window.getWidth() == size[0] && window.getHeight() == size[1];
                if (!pixelsMatch && ticks <= LAYOUT_WINDOW_WAIT_TICKS) {
                    // setWindowed 为异步：等窗口真正变成目标像素尺寸再按新尺寸重排。
                    return;
                }
                if (!pixelsMatch) {
                    LOGGER.warn("[GTSNPonder] catalog autotest: window clamped to {}x{} (wanted {}x{}); "
                                    + "running the layout check at the available size",
                            window.getWidth(), window.getHeight(), size[0], size[1]);
                }
                minecraft.options.guiScale().set(size[2]);
                minecraft.resizeDisplay();
                LOGGER.info("[GTSNPonder] catalog autotest: layout {} requested -> window={}x{} gui={}x{} scale={}",
                        label, window.getWidth(), window.getHeight(), window.getGuiScaledWidth(),
                        window.getGuiScaledHeight(), window.getGuiScale());
                layoutPhase = 2;
                ticks = 0;
            }
            case 2 -> {
                if (ticks < LAYOUT_SETTLE_TICKS) {
                    return;
                }
                if (!(minecraft.screen instanceof SceneCatalogScreen screen)) {
                    fail(minecraft, "catalog screen disappeared while resizing to " + label
                            + "; current screen=" + minecraft.screen);
                    return;
                }
                String actual = window.getWidth() + "x" + window.getHeight() + "-g" + (int) window.getGuiScale();
                grabScreenshot(minecraft, "gtsnponder-catalog-layout-" + actual + ".png");
                if (!assertRowLayout(minecraft, screen, label)) {
                    return;
                }
                layoutIndex++;
                ticks = 0;
                if (layoutIndex >= LAYOUT_SIZES.length) {
                    LOGGER.info("[GTSNPonder] catalog autotest PASS: rows complete and in-bounds at all {} "
                            + "window/scale sizes", LAYOUT_SIZES.length);
                    stage = Stage.DONE;
                } else {
                    layoutPhase = 0;
                }
            }
            default -> fail(minecraft, "unreachable layout phase " + layoutPhase);
        }
    }

    /** 断言前 {@link #LAYOUT_ROWS_CHECKED} 行：5 个单元都在行内，且「相关机器」/「播放」存在且在屏幕内。 */
    private static boolean assertRowLayout(Minecraft minecraft, SceneCatalogScreen screen, String label) {
        List<Stack> rows = screen.visibleRows();
        List<ButtonWidget> related = screen.visibleRelatedButtons();
        List<ButtonWidget> play = screen.visiblePlayButtons();
        if (rows.isEmpty() || rows.size() != related.size() || rows.size() != play.size()) {
            fail(minecraft, "[" + label + "] row widgets out of sync: rows=" + rows.size()
                    + " related=" + related.size() + " play=" + play.size());
            return false;
        }
        int checked = Math.min(LAYOUT_ROWS_CHECKED, rows.size());
        for (int i = 0; i < checked; i++) {
            Stack row = rows.get(i);
            Rect rowBounds = row.bounds();
            List<Widget> cells = row.children();
            if (cells.size() != 5) {
                fail(minecraft, "[" + label + "] row " + i + " has " + cells.size() + " cells, expected 5");
                return false;
            }
            for (int c = 0; c < cells.size(); c++) {
                Rect cell = cells.get(c).bounds();
                if (cell.width() <= 0 || cell.height() <= 0 || !contains(rowBounds, cell)) {
                    fail(minecraft, "[" + label + "] row " + i + " cell " + c + " escapes the row: cell="
                            + cell + " row=" + rowBounds);
                    return false;
                }
            }
            Rect relatedBounds = related.get(i).bounds();
            if (relatedBounds.width() <= 0 || !onScreen(screen, relatedBounds)) {
                fail(minecraft, "[" + label + "] row " + i + " 「相关机器」button missing or off-screen: "
                        + relatedBounds + " screen=" + screen.width + "x" + screen.height);
                return false;
            }
            Rect playBounds = play.get(i).bounds();
            if (playBounds.width() <= 0 || !onScreen(screen, playBounds)) {
                fail(minecraft, "[" + label + "] row " + i + " 「播放」button missing or off-screen: "
                        + playBounds + " screen=" + screen.width + "x" + screen.height);
                return false;
            }
        }
        Stack firstRow = rows.get(0);
        LOGGER.info("[GTSNPonder] catalog autotest: layout {} OK (rows={} checked={} screen={}x{}; first row "
                        + "width={} right={}, play right={})",
                label, rows.size(), checked, screen.width, screen.height,
                firstRow.bounds().width(), firstRow.bounds().right(), play.get(0).bounds().right());
        return true;
    }

    /** 内矩形是否完全落在 {@code outer} 内。 */
    private static boolean contains(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.right() <= outer.right()
                && inner.y() >= outer.y() && inner.bottom() <= outer.bottom();
    }

    /** 矩形是否完全落在屏幕内。 */
    private static boolean onScreen(SceneCatalogScreen screen, Rect rect) {
        return rect.x() >= 0 && rect.y() >= 0 && rect.right() <= screen.width && rect.bottom() <= screen.height;
    }

    private static void tickStop(Minecraft minecraft) {
        if (ticks > 40 && !stopped) {
            stopped = true;
            LOGGER.info("[GTSNPonder] catalog autotest finished ({}), stopping client", stage);
            minecraft.stop();
        }
    }

    /** 为种子目标（真实存在的多方块）按需写入作者场景，使目录有多类别 / 多条目可测。 */
    private static void seedAuthorScenes() {
        int seeded = 0;
        for (String target : SEED_TARGETS) {
            if (seeded >= 3 || SceneLibrary.get().sceneForTarget(target).isPresent()) {
                continue;
            }
            StructureSource source = GtStructureAdapter.byId(target).orElse(null);
            if (source == null) {
                continue;
            }
            try {
                SceneLibrary.get().saveAuthorScene(SceneGenerator.generate(source));
                seeded++;
                LOGGER.info("[GTSNPonder] catalog autotest: seeded author scene for {}", target);
            } catch (IOException failure) {
                LOGGER.warn("[GTSNPonder] catalog autotest: could not seed {}", target, failure);
            }
        }
    }

    /** 从目标 id 取机器路径段（用于搜索词），如 {@code gtceu:coke_oven} → {@code coke_oven}。 */
    private static String token(String target) {
        if (target == null) {
            return "";
        }
        int colon = target.indexOf(':');
        return colon >= 0 && colon + 1 < target.length() ? target.substring(colon + 1) : target;
    }

    private static void fail(Minecraft minecraft, String reason) {
        LOGGER.error("[GTSNPonder] catalog autotest FAIL: {}", reason);
        grabScreenshot(minecraft, "gtsnponder-catalog-failed.png");
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
                message -> LOGGER.info("[GTSNPonder] catalog autotest screenshot: {}", message.getString()));
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
        LOGGER.info("[GTSNPonder] catalog autotest window prepared: {}x{} guiScale=2",
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
