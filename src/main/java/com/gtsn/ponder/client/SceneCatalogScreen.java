package com.gtsn.ponder.client;

import com.gtsn.lib.ui.client.ThemeFontMetrics;
import com.gtsn.lib.ui.layout.CrossAxisAlign;
import com.gtsn.lib.ui.layout.Direction;
import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.MainAxisAlign;
import com.gtsn.lib.ui.layout.Sizing;
import com.gtsn.lib.ui.screen.GtsnScreen;
import com.gtsn.lib.ui.theme.ThemeColorRole;
import com.gtsn.lib.ui.widget.ButtonWidget;
import com.gtsn.lib.ui.widget.ClipWidget;
import com.gtsn.lib.ui.widget.PanelWidget;
import com.gtsn.lib.ui.widget.ScrollPanelWidget;
import com.gtsn.lib.ui.widget.SpacerWidget;
import com.gtsn.lib.ui.widget.Stack;
import com.gtsn.lib.ui.widget.TextMetrics;
import com.gtsn.lib.ui.widget.TextWidget;
import com.gtsn.ponder.catalog.CatalogEntry;
import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.catalog.SceneCatalog;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.engine.model.SceneData;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 思索图鉴 / 目录（GTSN UI，ADR-0004）：按类别浏览 + 搜索 + 已看 / 未看进度，从条目播放场景。
 *
 * <p>纯逻辑在 {@link SceneCatalog} / {@link com.gtsn.ponder.catalog.SceneCategories}；本屏只做渲染与
 * 输入。类别栏列出全部类别（+「全部」），搜索框实时过滤（重建控件树后恢复焦点）；条目行显示
 * 已看 / 未看标记与「播放」按钮，点击即经 {@link PonderEntrypoints#openForTarget} 打开现有播放屏
 * （打开播放屏即标记已看，故进度随即更新）。</p>
 *
 * <p>客户端专用类。</p>
 */
public final class SceneCatalogScreen extends GtsnScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SIDE_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    /** 根内边距（四周）。 */
    private static final int ROOT_PADDING = 8;
    /** 侧栏（类别）与列表之间的列间距。 */
    private static final int MAIN_GAP = 6;
    /** 滚动条槽宽：两个 {@code ScrollPanelWidget} 都显式设置，使行宽推导与真实视口一致。 */
    private static final int SCROLLBAR_WIDTH = 6;
    /** 列表内容内边距（四周）。 */
    private static final int LIST_PADDING = 2;
    /**
     * 条目行的「屏幕扣除量」（工单 #19）：根内边距 2×8 + 侧栏 132 + 主列间距 6 + 列表滚动条 6 +
     * 列表内边距 2×2 = 164。行可用宽 = 屏幕宽 − 此值，与 {@link #buildRoot()} 的实际布局一一对应
     * （各窗口 / GUI 缩放下的行内边界由 catalog 自动测试断言自证）。
     */
    private static final int ROW_CHROME_WIDTH = 2 * ROOT_PADDING + SIDE_WIDTH + MAIN_GAP
            + SCROLLBAR_WIDTH + 2 * LIST_PADDING;
    /** 屏幕宽未知（{@code init()} 前）时的保守回退值。 */
    private static final int FALLBACK_SCREEN_WIDTH = 640;

    private final List<SceneData> scenes;
    private final List<String> registeredTargets;
    private final List<String> usageTargets;
    private final TextMetrics metrics;

    private String query = "";
    private String selectedCategory;
    private CatalogEntry relatedAnchor;

    private SceneCatalog catalog;
    private EditorTextField searchField;
    private ButtonWidget allCategoryButton;
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private final List<ButtonWidget> visiblePlayButtons = new ArrayList<>();
    private final List<ButtonWidget> visibleRelatedButtons = new ArrayList<>();
    private final List<Stack> visibleRows = new ArrayList<>();
    private List<CatalogEntry> visibleEntries = List.of();
    private TextWidget progressLabel;
    private TextWidget emptyLabel;
    private ButtonWidget closeButton;

    private int renderedFrames;

    /**
     * @param scenes            已加载的手作 / 随包场景
     * @param registeredTargets 全部注册多方块目标（无对应场景者合成 {@code source=auto} 条目，
     *                          使目录对每台注册多方块都可播——工单 #13 全量覆盖）
     */
    public SceneCatalogScreen(List<SceneData> scenes, List<String> registeredTargets) {
        this(scenes, registeredTargets, List.of());
    }

    /**
     * @param scenes            已加载的手作 / 随包场景
     * @param registeredTargets 全部注册多方块目标（工单 #13 全量覆盖）
     * @param usageTargets      代表性单方块机器目标（工单 #15）：无对应场景者合成「使用场景」条目
     *                          （键 = {@code SingleBlockUsageGenerator.sceneIdFor}），使目录对代表性
     *                          单方块机器也可播
     */
    public SceneCatalogScreen(List<SceneData> scenes, List<String> registeredTargets,
            List<String> usageTargets) {
        super(Component.translatable(CatalogKeys.TITLE), new PanelWidget().fill());
        this.scenes = List.copyOf(Objects.requireNonNull(scenes, "scenes must not be null"));
        this.registeredTargets = List.copyOf(
                Objects.requireNonNull(registeredTargets, "registeredTargets must not be null"));
        this.usageTargets = List.copyOf(Objects.requireNonNull(usageTargets, "usageTargets must not be null"));
        this.metrics = new ThemeFontMetrics(Minecraft.getInstance().font);
        rebuild();
    }

    /** 由场景库快照构建目录（外部可观察：条目 / 类别 / 进度）。 */
    public SceneCatalog catalog() {
        return catalog;
    }

    /** 当前过滤后的可见条目（搜索 + 类别）。 */
    public List<CatalogEntry> visibleEntries() {
        return visibleEntries;
    }

    public List<ButtonWidget> visiblePlayButtons() {
        return List.copyOf(visiblePlayButtons);
    }

    /** 与 {@link #visibleEntries()} 对齐的「相关机器」按钮（自动测试可点击）。 */
    public List<ButtonWidget> visibleRelatedButtons() {
        return List.copyOf(visibleRelatedButtons);
    }

    /** 与 {@link #visibleEntries()} 对齐的条目行（自动测试断言行内各列 / 按钮的边界）。 */
    public List<Stack> visibleRows() {
        return List.copyOf(visibleRows);
    }

    /** 当前「相关机器」导航锚点；{@code null} = 未进入相关视图。 */
    public CatalogEntry relatedAnchor() {
        return relatedAnchor;
    }

    public EditorTextField searchField() {
        return searchField;
    }

    public ButtonWidget allCategoryButton() {
        return allCategoryButton;
    }

    public String query() {
        return query;
    }

    public String selectedCategory() {
        return selectedCategory;
    }

    public String progressText() {
        return progressLabel == null ? "" : progressLabel.text();
    }

    public ButtonWidget closeButton() {
        return closeButton;
    }

    public int renderedFrames() {
        return renderedFrames;
    }

    /** 全部类别（显示顺序）。 */
    public List<String> categoryOrder() {
        return List.copyOf(categoryOrder);
    }

    public ButtonWidget categoryButton(String category) {
        int index = categoryOrder.indexOf(category);
        return index < 0 ? null : categoryButtons.get(index);
    }

    /** 程序化设置搜索词并重建（自动测试 / 外部驱动用）。 */
    public void setQuery(String value) {
        this.query = value == null ? "" : value;
        this.relatedAnchor = null;
        rebuild();
    }

    /** 程序化选择类别（{@code null} = 全部）并重建。 */
    public void selectCategory(String category) {
        this.selectedCategory = category;
        this.relatedAnchor = null;
        rebuild();
    }

    /**
     * 「相关机器」导航（目录内跳转，冻结 v1 格式无 {@code related} 字段，故由
     * {@link SceneCatalog#relatedTo(CatalogEntry)} 派生）：以条目为锚点，列表切换为其相关条目
     * （同类别 / 同机器族），并清空搜索与类别过滤。点「全部」/ 选类别 / 搜索即退出该视图。
     */
    public void showRelated(CatalogEntry entry) {
        this.relatedAnchor = entry;
        this.query = "";
        this.selectedCategory = null;
        rebuild();
    }

    private void onQueryChanged(String value) {
        this.query = value == null ? "" : value;
        this.relatedAnchor = null;
        rebuild();
        host().router().focus().requestFocus(searchField);
    }

    /** 重建控件树：重算目录 / 过滤并重新布局（组件无可见性属性，故整树重建）。 */
    private void rebuild() {
        this.catalog = SceneCatalog.of(scenes, registeredTargets, usageTargets, PonderProgress.get().snapshot());
        setRoot(buildRoot());
        host().resize(width, height);
    }

    private PanelWidget buildRoot() {
        PanelWidget root = new PanelWidget().fill();
        root.node().params().padding(Insets.all(ROOT_PADDING)).gap(6);
        root.align(MainAxisAlign.START, CrossAxisAlign.STRETCH);

        Stack header = root.add(hstack());
        header.add(new TextWidget(localized(CatalogKeys.TITLE), metrics).colorRole(ThemeColorRole.TEXT_STRONG));
        header.add(new SpacerWidget().weight(1));
        progressLabel = header.add(new TextWidget(progressString(), metrics).colorRole(ThemeColorRole.TEXT_MUTED));

        if (relatedAnchor != null) {
            Stack relatedRow = root.add(hstack());
            relatedRow.add(new TextWidget(
                    Component.translatable(CatalogKeys.RELATED_TO, displayTitle(relatedAnchor)).getString(), metrics)
                    .colorRole(ThemeColorRole.TEXT_STRONG));
            relatedRow.add(new SpacerWidget().weight(1));
        }

        Stack searchRow = root.add(hstack());
        searchRow.add(new TextWidget(localized(CatalogKeys.SEARCH), metrics).colorRole(ThemeColorRole.TEXT_MUTED)
                .size(Sizing.fixed(60), Sizing.fixed(BUTTON_HEIGHT)));
        searchField = searchRow.add(new EditorTextField(query, metrics, 64, this::onQueryChanged).fillWidth());
        searchField.node().params().height(Sizing.fixed(BUTTON_HEIGHT));

        Stack main = root.add(new Stack(Direction.HORIZONTAL).fillWidth().weight(1).gap(MAIN_GAP)
                .crossAxisAlign(CrossAxisAlign.STRETCH));

        ScrollPanelWidget side = main.add(new ScrollPanelWidget()
                .scrollbarWidth(SCROLLBAR_WIDTH).size(Sizing.fixed(SIDE_WIDTH), Sizing.fill()));
        Stack sideContent = side.add(new Stack(Direction.VERTICAL).fillWidth().gap(3).padding(Insets.all(3)));
        allCategoryButton = sideContent.add(new ButtonWidget(localized(CatalogKeys.ALL), metrics,
                () -> selectCategory(null)).fixedSize(SIDE_WIDTH - 12, BUTTON_HEIGHT));
        categoryButtons.clear();
        categoryOrder.clear();
        for (String category : catalog.categories()) {
            categoryOrder.add(category);
            categoryButtons.add(sideContent.add(new ButtonWidget(localized(SceneCategories.langKey(category)), metrics,
                    () -> selectCategory(category)).fixedSize(SIDE_WIDTH - 12, BUTTON_HEIGHT)));
        }

        ScrollPanelWidget list = main.add(new ScrollPanelWidget()
                .scrollbarWidth(SCROLLBAR_WIDTH).size(Sizing.fill(), Sizing.fill()).weight(1));
        Stack listContent = list.add(new Stack(Direction.VERTICAL).fillWidth().gap(2)
                .padding(Insets.all(LIST_PADDING)));
        visibleEntries = filterVisible();
        visiblePlayButtons.clear();
        visibleRelatedButtons.clear();
        visibleRows.clear();
        if (visibleEntries.isEmpty()) {
            emptyLabel = listContent.add(new TextWidget(localized(CatalogKeys.EMPTY), metrics)
                    .colorRole(ThemeColorRole.TEXT_MUTED));
        } else {
            emptyLabel = null;
            for (CatalogEntry entry : visibleEntries) {
                visiblePlayButtons.add(addEntryRow(listContent, entry));
            }
        }

        Stack footer = root.add(hstack());
        footer.add(new SpacerWidget().weight(1));
        closeButton = footer.add(new ButtonWidget(localized(CatalogKeys.CLOSE), metrics, this::onClose)
                .fixedSize(60, BUTTON_HEIGHT));
        return root;
    }

    private ButtonWidget addEntryRow(Stack listContent, CatalogEntry entry) {
        // 响应式列宽（工单 #19）：行可用宽 − 固定列后按 id → 相关 → 播放 → 名称 收缩，保证整行放得下。
        CatalogRowLayout.Columns columns = CatalogRowLayout.columnsFor(availableRowWidth());
        Stack row = listContent.add(new Stack(Direction.HORIZONTAL).fillWidth().gap(CatalogRowLayout.GAP)
                .crossAxisAlign(CrossAxisAlign.CENTER));
        visibleRows.add(row);
        row.add(new TextWidget(localized(entry.watched() ? CatalogKeys.WATCHED : CatalogKeys.UNWATCHED), metrics)
                .colorRole(entry.watched() ? ThemeColorRole.TEXT_STRONG : ThemeColorRole.TEXT_MUTED)
                .size(Sizing.fixed(columns.mark()), Sizing.fixed(ROW_HEIGHT)));
        // 名称（主）：显式列宽（不能再是 Sizing.fill——余量 ≤ 0 时 fill 会把名称撑满整行、把右侧列挤出
        // 视口，工单 #19）；文本按列宽截断 + ClipWidget 裁剪，绝不溢出到 id 列。
        ClipWidget nameCell = row.add(new ClipWidget().fixedSize(columns.name(), ROW_HEIGHT));
        nameCell.add(new TextWidget(CatalogRowLayout.truncate(displayTitle(entry), columns.name() - 4, metrics),
                metrics)
                .colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(ROW_HEIGHT)));
        // 原始 id（次）：显式列宽（窄屏第一个收缩）+ 裁剪 + 省略号（长 id 不再压到名称 / 按钮上）。
        ClipWidget idCell = row.add(new ClipWidget().fixedSize(columns.id(), ROW_HEIGHT));
        idCell.add(new TextWidget(
                CatalogRowLayout.truncate(entry.target() == null ? "" : entry.target(),
                        columns.id() - 4, metrics), metrics)
                .colorRole(ThemeColorRole.TEXT_MUTED)
                .size(Sizing.fill(), Sizing.fixed(ROW_HEIGHT)));
        visibleRelatedButtons.add(row.add(new ButtonWidget(localized(CatalogKeys.RELATED), metrics,
                () -> showRelated(entry)).fixedSize(columns.related(), BUTTON_HEIGHT)));
        return row.add(new ButtonWidget(localized(CatalogKeys.PLAY), metrics, () -> play(entry))
                .fixedSize(columns.play(), BUTTON_HEIGHT));
    }

    /**
     * 条目行的可用像素宽度（工单 #16 缺陷 B / #19）：屏幕宽扣除 {@link #ROW_CHROME_WIDTH}（根内边距、
     * 左侧类别栏、列间距、列表滚动条与列表内边距的具名常量之和），故与真实容器宽度一致；{@code init()}
     * 前（宽高未定）用一个保守默认值，{@code init()} 会用真实宽度重建。
     */
    private int availableRowWidth() {
        int screenWidth = width > 0 ? width : FALLBACK_SCREEN_WIDTH;
        return Math.max(0, screenWidth - ROW_CHROME_WIDTH);
    }

    private List<CatalogEntry> filterVisible() {
        if (relatedAnchor != null) {
            return catalog.relatedTo(relatedAnchor);
        }
        return catalog.search(query).stream()
                .filter(entry -> selectedCategory == null || selectedCategory.equals(entry.category()))
                .toList();
    }

    private String progressString() {
        return Component.translatable(CatalogKeys.PROGRESS, catalog.watchedCount(), catalog.total()).getString();
    }

    private void play(CatalogEntry entry) {
        if (entry == null || entry.target() == null) {
            return;
        }
        LOGGER.info("[GTSNPonder] catalog opens scene for {}", entry.target());
        PonderEntrypoints.openForTarget(entry.target());
    }

    private static Stack hstack() {
        return new Stack(Direction.HORIZONTAL).fillWidth().gap(4).crossAxisAlign(CrossAxisAlign.CENTER);
    }

    private static String displayTitle(CatalogEntry entry) {
        if (entry.titleKey() != null && !entry.titleKey().isBlank()) {
            return Component.translatable(entry.titleKey()).getString();
        }
        return entry.target() == null ? "" : entry.target();
    }

    private static String localized(String key) {
        return key == null ? "" : Component.translatable(key).getString();
    }

    @Override
    protected void init() {
        super.init();
        // 屏幕尺寸已知后再重建一次：条目行按真实宽度计算名称列宽并截断长名 / 长 id（工单 #16 缺陷 B）。
        rebuild();
        LOGGER.info("[GTSNPonder] scene catalog init: screen={}x{} scenes={} categories={} watched={}/{}",
                width, height, catalog.total(), catalog.categories(), catalog.watchedCount(), catalog.total());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderedFrames++;
        if (renderedFrames == 1 || renderedFrames % 120 == 0) {
            LOGGER.info("[GTSNPonder] scene catalog frame={} query='{}' category={} visible={}/{} watched={}/{}",
                    renderedFrames, query, selectedCategory, visibleEntries.size(), catalog.total(),
                    catalog.watchedCount(), catalog.total());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
