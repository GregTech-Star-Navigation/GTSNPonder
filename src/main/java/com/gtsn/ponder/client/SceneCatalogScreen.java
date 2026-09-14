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
    private static final int MARK_WIDTH = 40;
    private static final int PLAY_WIDTH = 52;
    private static final int RELATED_WIDTH = 56;

    private final List<SceneData> scenes;
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
    private List<CatalogEntry> visibleEntries = List.of();
    private TextWidget progressLabel;
    private TextWidget emptyLabel;
    private ButtonWidget closeButton;

    private int renderedFrames;

    public SceneCatalogScreen(List<SceneData> scenes) {
        super(Component.translatable(CatalogKeys.TITLE), new PanelWidget().fill());
        this.scenes = List.copyOf(Objects.requireNonNull(scenes, "scenes must not be null"));
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
        this.catalog = SceneCatalog.of(scenes, PonderProgress.get().snapshot());
        setRoot(buildRoot());
        host().resize(width, height);
    }

    private PanelWidget buildRoot() {
        PanelWidget root = new PanelWidget().fill();
        root.node().params().padding(Insets.all(8)).gap(6);
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

        Stack main = root.add(new Stack(Direction.HORIZONTAL).fillWidth().weight(1).gap(6)
                .crossAxisAlign(CrossAxisAlign.STRETCH));

        ScrollPanelWidget side = main.add(new ScrollPanelWidget().size(Sizing.fixed(SIDE_WIDTH), Sizing.fill()));
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

        ScrollPanelWidget list = main.add(new ScrollPanelWidget().size(Sizing.fill(), Sizing.fill()).weight(1));
        Stack listContent = list.add(new Stack(Direction.VERTICAL).fillWidth().gap(2).padding(Insets.all(2)));
        visibleEntries = filterVisible();
        visiblePlayButtons.clear();
        visibleRelatedButtons.clear();
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
        Stack row = listContent.add(new Stack(Direction.HORIZONTAL).fillWidth().gap(6)
                .crossAxisAlign(CrossAxisAlign.CENTER));
        row.add(new TextWidget(localized(entry.watched() ? CatalogKeys.WATCHED : CatalogKeys.UNWATCHED), metrics)
                .colorRole(entry.watched() ? ThemeColorRole.TEXT_STRONG : ThemeColorRole.TEXT_MUTED)
                .size(Sizing.fixed(MARK_WIDTH), Sizing.fixed(ROW_HEIGHT)));
        row.add(new TextWidget(displayTitle(entry), metrics).colorRole(ThemeColorRole.TEXT)
                .size(Sizing.fill(), Sizing.fixed(ROW_HEIGHT)));
        row.add(new TextWidget(entry.target() == null ? "" : entry.target(), metrics)
                .colorRole(ThemeColorRole.TEXT_MUTED));
        visibleRelatedButtons.add(row.add(new ButtonWidget(localized(CatalogKeys.RELATED), metrics,
                () -> showRelated(entry)).fixedSize(RELATED_WIDTH, BUTTON_HEIGHT)));
        return row.add(new ButtonWidget(localized(CatalogKeys.PLAY), metrics, () -> play(entry))
                .fixedSize(PLAY_WIDTH, BUTTON_HEIGHT));
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
        host().resize(width, height);
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
