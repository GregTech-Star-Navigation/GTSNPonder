package com.gtsn.ponder.bridge;

import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneParams;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li>{@code layer}：{@code params.y} 指定的结构局部 Y 层（缺参数时为空）。</li>
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
}
