package com.gtsn.ponder.bridge;

import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneParams;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 场景元素 → 结构坐标的纯逻辑解析器（世界桥的绑定层）。
 *
 * <p>场景数据以<b>稳定字符串 ID</b> 的 {@code elements[]} 引用结构片段；本解析器把元素上的
 * 字面量选择器（{@code params.selector}）翻译成 {@link StructureSource} 中具体的方块单元。
 * 选择器是<b>封闭词汇</b>（反 DSL：仅字面量，无表达式），未知取值返回<b>空集</b>而非抛异常，
 * 以保持前向兼容：</p>
 *
 * <ul>
 *   <li>{@code all}（缺省）：结构全部方块；</li>
 *   <li>{@code controller}：多方块控制器单元（{@link StructureSource#hasController()} 为假时为空）；</li>
 *   <li>{@code block}：{@code params.block} 指定的方块注册名（缺参数时为空）；</li>
 *   <li>{@code layer}：{@code params.y} 指定的结构局部 Y 层（缺参数时为空）；</li>
 *   <li>{@code role}：{@code params.role} 指定的 {@link StructureRole} 名（大小写不敏感；缺参数 / 未知角色时为空）。
 *       可选 {@code params.limit}（正整数）把结果截断为确定性前 N 个单元，用于收敛高亮轮廓数量；
 *       可选 {@code params.spread}（布尔）在 {@code limit} 生效时改为在该角色全部单元里<b>均匀抽样</b>
 *       （而非取前 N 个），避免代表位置彼此相邻导致轮廓重叠。两者均为确定性选择。</li>
 *   <li>{@code moduleslot}：{@code params.index} 指定的 0 基模块位下标，展开为该区域覆盖的全部单元
 *       （空穴也计入，以 {@link #MODULE_SLOT_CELL_BLOCK_ID} 占位），供模块位区域高亮 / 安装定位；
 *       缺参数 / 非整数 / 越界时为空。</li>
 * </ul>
 *
 * <p>纯 Java：不依赖 Minecraft 或格雷科技，可 headless 单测（导演核心纪律的延伸）。</p>
 */
public final class SceneElementResolver {

    /** 选择器字面量：全部方块。 */
    public static final String SELECTOR_ALL = "all";
    /** 选择器字面量：控制器单元。 */
    public static final String SELECTOR_CONTROLLER = "controller";
    /** 选择器字面量：按方块注册名过滤（参数 {@code block}）。 */
    public static final String SELECTOR_BLOCK = "block";
    /** 选择器字面量：按结构局部 Y 层过滤（参数 {@code y}）。 */
    public static final String SELECTOR_LAYER = "layer";
    /** 选择器字面量：按方块角色过滤（参数 {@code role}，取 {@link StructureRole} 名，大小写不敏感）。 */
    public static final String SELECTOR_ROLE = "role";
    /** 选择器字面量：按模块位区域展开为区域单元（参数 {@code index}，0 基模块位下标）。 */
    public static final String SELECTOR_MODULE_SLOT = ModuleSlot.SELECTOR;

    /**
     * 模块位区域单元使用的<b>合成方块 id</b>：区域本身可能不含结构方块（空穴模块位），
     * 故以该占位 id 表达「模块位单元」（仅供高亮 / 轮廓定位，不用于替换真实方块）。
     */
    public static final String MODULE_SLOT_CELL_BLOCK_ID = "gtsnponder:module_slot";

    private final StructureSource structure;

    public SceneElementResolver(StructureSource structure) {
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
    }

    public StructureSource structure() {
        return structure;
    }

    /**
     * 解析单个元素为其引用的方块单元（保持结构源中的产出顺序）。无匹配 / 未知选择器 → 空列表。
     */
    public List<StructureBlock> resolve(SceneElement element) {
        Objects.requireNonNull(element, "element must not be null");
        String selector = SceneParams.string(element.params(), "selector", SELECTOR_ALL);
        return switch (selector) {
            case SELECTOR_ALL -> List.copyOf(structure.blocks());
            case SELECTOR_CONTROLLER -> resolveController();
            case SELECTOR_BLOCK -> resolveBlock(SceneParams.string(element.params(), "block", null));
            case SELECTOR_LAYER -> resolveLayer(SceneParams.number(element.params(), "y", Double.NaN));
            case SELECTOR_ROLE -> resolveRole(
                    SceneParams.string(element.params(), "role", null),
                    SceneParams.number(element.params(), "limit", 0.0d),
                    SceneParams.bool(element.params(), "spread", false));
            case SELECTOR_MODULE_SLOT -> resolveModuleSlot(SceneParams.number(element.params(), "index", Double.NaN));
            default -> List.of();
        };
    }

    /** 解析全部元素，保持输入顺序（重复 id 时后者覆盖前者）。 */
    public Map<String, List<StructureBlock>> resolveAll(List<SceneElement> elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        Map<String, List<StructureBlock>> resolved = new LinkedHashMap<>();
        for (SceneElement element : elements) {
            resolved.put(element.id(), resolve(element));
        }
        return resolved;
    }

    private List<StructureBlock> resolveController() {
        if (!structure.hasController()) {
            return List.of();
        }
        StructureSource.ControllerCell cell = structure.controller();
        List<StructureBlock> matches = new ArrayList<>(1);
        for (StructureBlock block : structure.blocks()) {
            if (block.x() == cell.x() && block.y() == cell.y() && block.z() == cell.z()) {
                matches.add(block);
            }
        }
        return List.copyOf(matches);
    }

    private List<StructureBlock> resolveBlock(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return List.of();
        }
        List<StructureBlock> matches = new ArrayList<>();
        for (StructureBlock block : structure.blocks()) {
            if (block.blockId().equals(blockId)) {
                matches.add(block);
            }
        }
        return List.copyOf(matches);
    }

    private List<StructureBlock> resolveLayer(double rawY) {
        if (!Double.isFinite(rawY) || rawY != Math.rint(rawY)) {
            return List.of();
        }
        int layer = (int) rawY;
        List<StructureBlock> matches = new ArrayList<>();
        for (StructureBlock block : structure.blocks()) {
            if (block.y() == layer) {
                matches.add(block);
            }
        }
        return List.copyOf(matches);
    }

    private List<StructureBlock> resolveRole(String roleName, double rawLimit, boolean spread) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        StructureRole role;
        try {
            role = StructureRole.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownRole) {
            return List.of();
        }
        List<StructureBlock> matches = new ArrayList<>();
        for (StructureBlock block : structure.blocks()) {
            if (block.role() == role) {
                matches.add(block);
            }
        }
        if (matches.isEmpty()) {
            return List.of();
        }
        int limit = rawLimit > 0.0d && rawLimit == Math.rint(rawLimit) ? (int) rawLimit : 0;
        if (limit <= 0 || limit >= matches.size()) {
            return List.copyOf(matches);
        }
        if (!spread || limit == 1) {
            return List.copyOf(matches.subList(0, limit));
        }
        // 均匀抽样：在 [0, size-1] 上取 limit 个等距下标（含首尾），确定性且覆盖整个角色分布。
        List<StructureBlock> selected = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            int index = (int) Math.round((double) i * (matches.size() - 1) / (limit - 1));
            selected.add(matches.get(index));
        }
        return List.copyOf(selected);
    }

    /**
     * 把模块位区域展开为其覆盖的全部结构单元（含区域内的空穴单元，以
     * {@link #MODULE_SLOT_CELL_BLOCK_ID} 占位）。参数 {@code index} 为 0 基模块位下标；
     * 缺失 / 非整数 / 越界 → 空集（前向兼容，不抛异常）。单元顺序为确定的
     * X → Y → Z 嵌套升序。
     */
    private List<StructureBlock> resolveModuleSlot(double rawIndex) {
        if (!Double.isFinite(rawIndex) || rawIndex != Math.rint(rawIndex) || rawIndex < 0.0d) {
            return List.of();
        }
        int index = (int) rawIndex;
        List<ModuleSlot> slots = structure.moduleSlots();
        if (index >= slots.size()) {
            return List.of();
        }
        ModuleSlot slot = slots.get(index);
        List<StructureBlock> cells = new ArrayList<>(slot.volume());
        for (int x = slot.offsetX(); x < slot.offsetX() + slot.sizeX(); x++) {
            for (int y = slot.offsetY(); y < slot.offsetY() + slot.sizeY(); y++) {
                for (int z = slot.offsetZ(); z < slot.offsetZ() + slot.sizeZ(); z++) {
                    cells.add(new StructureBlock(x, y, z, MODULE_SLOT_CELL_BLOCK_ID));
                }
            }
        }
        return List.copyOf(cells);
    }
}
