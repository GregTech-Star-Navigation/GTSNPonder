package com.gtsn.ponder.generate;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.structure.StructureSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 多变体枚举（工单 #21 反馈 2）：把同一台机器的<b>多个结构页</b>（GT {@code getMatchingShapes()}，
 * 例如可重复结构段的机器会给出「短 / 长」或多节长度）翻译成确定的变体清单与场景。
 *
 * <p><b>为什么需要它</b>：GT 自己允许可重复结构段的机器有多种合法搭法（装配线 5–16 节等），
 * 其 {@code getMatchingShapes()} 会返回多个结构页；旧生成器只取第一页（默认最短），玩家看不到
 * 长的变体。本类为每一页产出一个 {@link Spec}（稳定 id + 本地化标签键 + 标签参数）与对应的
 * {@link SceneData}，使播放屏可以在变体之间切换。</p>
 *
 * <h2>命名规则（确定性）</h2>
 * <ul>
 *   <li>单页：id {@code default}，标签「默认」；</li>
 *   <li>两页：第一页 id {@code default}（标签「短」），第二页 id {@code long}（标签「长」）；</li>
 *   <li>多页：按<b>变化轴</b>的长度命名——id {@code slices_<n>}，标签「n 节」（如 4 节 / 12 节）。
 *       变化轴取「跨变体尺寸取值最丰富」的轴；若三个轴都不变则退化为按非空方块数命名。</li>
 * </ul>
 * <p>第一页始终保留 id {@code default} 与默认场景 id（{@link SceneGenerator#sceneIdFor}），
 * 从而与既有目录 / 观看进度键保持兼容。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（可在 headless 单测中以 DTO 夹具断言）。</p>
 */
public final class SceneVariants {

    /** 默认变体 id（与既有产物 / 目录 / 进度键兼容；始终对应第一页）。 */
    public static final String VARIANT_DEFAULT = "default";
    /** 两页机器的第二页 id。 */
    public static final String VARIANT_LONG = "long";
    /** 多页机器 id 前缀（后缀为变化轴上的长度，如 {@code slices_12}）。 */
    public static final String SLICES_PREFIX = "slices_";

    /** 变体标签本地化键（datagen 产出中英）。 */
    public static final String LABEL_DEFAULT = "ponder.gtsnponder.variant.default";
    public static final String LABEL_SHORT = "ponder.gtsnponder.variant.short";
    public static final String LABEL_LONG = "ponder.gtsnponder.variant.long";
    /** 「%s 节」/「%s sections」：参数为变化轴长度。 */
    public static final String LABEL_SLICES = "ponder.gtsnponder.variant.slices";
    /** 「变体 %s」/「Variant %s」：结构尺寸无规律变化时（如线圈 / 机壳等部件变体）的通用编号。 */
    public static final String LABEL_NUMBERED = "ponder.gtsnponder.variant.numbered";

    private SceneVariants() {
    }

    /**
     * 一个变体的稳定描述：{@code id}（写入场景 {@code variant} 与 id 后缀）、本地化标签键与参数。
     */
    public record Spec(String id, String labelKey, List<String> labelArgs) {

        public Spec {
            Objects.requireNonNull(id, "variant id must not be null");
            Objects.requireNonNull(labelKey, "variant label key must not be null");
            labelArgs = List.copyOf(labelArgs == null ? List.of() : labelArgs);
        }

        /** 该变体的场景 id：默认变体沿用 {@link SceneGenerator#sceneIdFor}，其余追加 {@code _<id>}。 */
        public String sceneIdFor(String targetId) {
            String base = SceneGenerator.sceneIdFor(targetId);
            return VARIANT_DEFAULT.equals(id) ? base : base + "_" + id;
        }
    }

    /**
     * 为给定结构页清单产出确定的变体清单：单页 → 默认；两页 → 短 / 长；多页 → 按变化轴长度（节数）。
     * 空清单 / 单页均返回单元素清单，保证调用方永远至少有一个变体。
     */
    public static List<Spec> specs(List<StructureSource> shapes) {
        if (shapes == null || shapes.size() <= 1) {
            return List.of(new Spec(VARIANT_DEFAULT, LABEL_DEFAULT, List.of()));
        }
        int count = shapes.size();
        if (count == 2) {
            return List.of(
                    new Spec(VARIANT_DEFAULT, LABEL_SHORT, List.of()),
                    new Spec(VARIANT_LONG, LABEL_LONG, List.of()));
        }
        int axis = varyingAxis(shapes);
        List<Spec> specs = new ArrayList<>(count);
        Set<String> usedIds = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            if (axis < 0) {
                // 结构尺寸无规律变化（如线圈 / 机壳等部件变体）：用通用编号，避免误导性的「n 节」。
                String id = index == 0 ? VARIANT_DEFAULT : "v" + (index + 1);
                usedIds.add(id);
                specs.add(new Spec(id, LABEL_NUMBERED, List.of(String.valueOf(index + 1))));
                continue;
            }
            int extent = extentOf(shapes.get(index), axis);
            String id = index == 0 ? VARIANT_DEFAULT : SLICES_PREFIX + extent;
            if (!usedIds.add(id)) {
                // 变化轴上长度相同的两页（例如同时沿两轴变长）：追加下标保持 id 唯一。
                id = SLICES_PREFIX + extent + "_" + index;
                usedIds.add(id);
            }
            specs.add(new Spec(id, LABEL_SLICES, List.of(String.valueOf(extent))));
        }
        return List.copyOf(specs);
    }

    /**
     * 按变体描述生成该结构页的场景（与默认路径同一生成器，仅 id / variant 不同）。
     */
    public static SceneData sceneFor(StructureSource shape, Spec spec) {
        Objects.requireNonNull(shape, "shape must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        return SceneGenerator.generate(shape, spec.id(), spec.sceneIdFor(shape.id()));
    }

    /** 跨变体尺寸取值最丰富的轴（0 = X、1 = Y、2 = Z）；无变化返回 {@code -1}。 */
    private static int varyingAxis(List<StructureSource> shapes) {
        int bestAxis = -1;
        int bestDistinct = 0;
        for (int axis = 0; axis < 3; axis++) {
            Set<Integer> sizes = new LinkedHashSet<>();
            for (StructureSource shape : shapes) {
                sizes.add(sizeOn(shape, axis));
            }
            if (sizes.size() > bestDistinct) {
                bestDistinct = sizes.size();
                bestAxis = axis;
            }
        }
        return bestDistinct > 1 ? bestAxis : -1;
    }

    /** 变化轴上的长度（节数）；轴为 {@code -1} 时退化为非空方块数。 */
    private static int extentOf(StructureSource shape, int axis) {
        return axis < 0 ? shape.blockCount() : sizeOn(shape, axis);
    }

    private static int sizeOn(StructureSource shape, int axis) {
        return switch (axis) {
            case 0 -> shape.sizeX();
            case 1 -> shape.sizeY();
            case 2 -> shape.sizeZ();
            default -> shape.volume();
        };
    }
}
