package com.gtsn.ponder.generate;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 自动生成器（v1）：把中立结构源 {@link StructureSource} 翻译成可播放的场景数据 {@link SceneData}。
 *
 * <p><b>边界（ADR-0002 / 规格）</b>：只生成「搭建顺序、控制器与仓口 / 总线高亮、成型演示」——
 * 使用流程与原理必须手作。产物携带 {@code source=auto} 与 {@link #GENERATOR_VERSION}，是可重生成
 * 的构建产物；手作以场景粒度覆盖。</p>
 *
 * <h2>构建顺序与单元格预算</h2>
 * <p>默认<b>逐层</b>（Y 升序）揭示；非空方块数超过 {@link #CELL_BUDGET} 的超大机器切换为
 * <b>按角色分组 + LOD / 淡入</b>（每角色一个 {@code showSection}，{@code lod=grouped} /
 * {@code fadeIn=true}），避免逐方块动画。预算以 {@link StructureSource#blockCount()} 计量，
 * {@code > CELL_BUDGET} 即切换（恰好等于预算仍走逐层）。</p>
 *
 * <h2>自适应取景</h2>
 * <p>相机距离由结构包围盒<b>对角线</b>推导（{@code FIT_FACTOR × diagonal}，下限
 * {@link #MIN_CAMERA_DISTANCE}），使结构占据视口主要区域；相机步骤排在搭建之前，故整个揭示过程
 * 都在合适取景下呈现。视口投影的取景<b>比例</b>与视口像素尺寸无关，故同一因子在不同视口尺寸下
 * 保持一致的填充度。</p>
 *
 * <h2>高亮可读性</h2>
 * <p>控制器用 {@code highlight}（视口金色边框），仓口 / 总线用 {@code outline}（视口蓝色边框），
 * 颜色区分 + 旁白图例（{@link #NARRATION_LEGEND}）。仓口按角色<b>分批</b>高亮，且总数收敛到
 * {@link #HATCH_OUTLINE_LIMIT} 个代表位置，避免大量重叠轮廓。</p>
 *
 * <h2>机器特定旁白</h2>
 * <p>旁白键携带 {@link SceneStep#narrationArgs() 模板参数}（机器名 / 标识 / 结构尺寸 / 仓口数量与
 * 角色 / 模块位数量），同一模板产出机器特定文案；中英双语键见 {@code lang/*.json}。</p>
 *
 * <h2>成型演示</h2>
 * <p>隐藏全部搭建分段（未成型）→ 重新显示（成型）→ 对控制器发一次成型脉冲 → 成型旁白。</p>
 *
 * <h2>确定性</h2>
 * <p>无随机：层 / 角色 / 仓口角色均用固定比较器排序；元素 / 步骤 ID 由结构确定性推导。对同一
 * {@link StructureSource} 两次生成产生字节相等 JSON（见 {@code SceneDataWriter}）。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（自动生成缝可在 headless 单测中以 DTO 夹具断言）。</p>
 */
public final class SceneGenerator {

    /**
     * 单元格预算：非空方块数 {@code > CELL_BUDGET} 的多方块切换为「按角色分组 + LOD / 淡入」，
     * 避免超大机器逐方块动画（规格 §自动生成「大机器设单元格预算」）。
     */
    public static final int CELL_BUDGET = 64;

    /** 生成器版本：产物可重生成 / 可 diff 的标识（写入 {@code generatorVersion}）。 */
    public static final String GENERATOR_VERSION = "auto-1";

    /** 逐层搭建分段元素 ID 前缀（后缀为结构局部 Y）。 */
    public static final String SECTION_LAYER_PREFIX = "section.layer.";
    /** 角色分组搭建分段元素 ID 前缀（后缀为 {@link StructureRole} 名）。 */
    public static final String SECTION_ROLE_PREFIX = "section.role.";
    /** 仓口 / 总线高亮锚点 ID 前缀（后缀为 {@link StructureRole} 名）。 */
    public static final String HATCH_PREFIX = "hatch.";
    /** 控制器锚点元素 ID。 */
    public static final String ELEMENT_CONTROLLER = "controller";

    /** 旁白键（自动文案经 datagen 批量产出，见规格 §本地化）。参数见各步骤 narrationArgs。 */
    public static final String NARRATION_INTRO = "ponder.gtsnponder.generated.narration.intro";
    public static final String NARRATION_CONTROLLER = "ponder.gtsnponder.generated.narration.controller";
    public static final String NARRATION_CONTROLLER_NONE =
            "ponder.gtsnponder.generated.narration.controller.none";
    public static final String NARRATION_HATCHES = "ponder.gtsnponder.generated.narration.hatches";
    public static final String NARRATION_HATCHES_NONE = "ponder.gtsnponder.generated.narration.hatches.none";
    public static final String NARRATION_MODULES = "ponder.gtsnponder.generated.narration.modules";
    public static final String NARRATION_MODULES_NONE = "ponder.gtsnponder.generated.narration.modules.none";
    public static final String NARRATION_LEGEND = "ponder.gtsnponder.generated.narration.legend";
    public static final String NARRATION_FORMED = "ponder.gtsnponder.generated.narration.formed";

    /** 旁白图例用到的颜色词：控制器金色、仓口 / 总线蓝色（与视口桥的两种边框颜色一致）。 */
    public static final String HIGHLIGHT_COLOR_CONTROLLER = "gold";
    public static final String HIGHLIGHT_COLOR_HATCH = "blue";

    private static final int INTRO_TEXT_DURATION = 45;
    private static final int LAYER_SECTION_DURATION = 12;
    private static final int ROLE_SECTION_DURATION = 18;
    private static final int HIGHLIGHT_DURATION = 25;
    private static final int OUTLINE_DURATION = 20;
    private static final int TEXT_DURATION = 45;
    private static final int FORMED_HIDE_DURATION = 15;
    private static final int FORMED_SHOW_DURATION = 15;
    private static final int FORMED_PULSE_DURATION = 20;
    private static final int FORMED_TEXT_DURATION = 40;

    /** 相机取景：距离 = FIT_FACTOR × 包围盒对角线（下限 {@link #MIN_CAMERA_DISTANCE}）。 */
    private static final double FIT_FACTOR = 0.82d;
    private static final double MIN_CAMERA_DISTANCE = 2.0d;
    private static final double CAMERA_YAW = 25.0d;
    private static final double CAMERA_PITCH = -135.0d;

    /** 仓口 / 总线高亮轮廓总数上限（收敛，避免大量重叠轮廓）。 */
    public static final int HATCH_OUTLINE_LIMIT = 4;

    private SceneGenerator() {
    }

    /** 是否走「按角色分组 + LOD」路径（非空方块数超过 {@link #CELL_BUDGET}）。 */
    public static boolean isRoleGrouped(StructureSource source) {
        return source.blockCount() > CELL_BUDGET;
    }

    /** 把结构源确定性生成场景数据。 */
    public static SceneData generate(StructureSource source) {
        Objects.requireNonNull(source, "source must not be null");

        List<SceneElement> elements = new ArrayList<>();
        List<SceneStep> steps = new ArrayList<>();
        List<String> buildSectionIds = new ArrayList<>();

        steps.add(text("intro", NARRATION_INTRO, INTRO_TEXT_DURATION,
                List.of(source.id(), sizeText(source))));

        if (source.hasController()) {
            elements.add(SceneElement.of(ELEMENT_CONTROLLER, "anchor", Map.of("selector", "controller")));
        }
        steps.add(camera(source));

        if (!source.isEmpty()) {
            if (isRoleGrouped(source)) {
                appendRoleBuild(source, elements, steps, buildSectionIds);
            } else {
                appendLayerBuild(source, elements, steps, buildSectionIds);
            }
        }

        appendControllerHighlight(source, steps);
        appendHatchHighlight(source, elements, steps);
        appendModuleNarration(source, steps);
        steps.add(text("text.legend", NARRATION_LEGEND, TEXT_DURATION, List.of()));
        appendFormedDemonstration(source, steps, buildSectionIds);

        return SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .id("gtsnponder:auto_" + sanitize(source.id()))
                .title(displayName(source))
                .target(source.id())
                .variant("default")
                .source(Source.AUTO)
                .generatorVersion(GENERATOR_VERSION)
                .elements(elements)
                .steps(steps)
                .build();
    }

    private static SceneStep camera(StructureSource source) {
        SceneStep.Builder builder = SceneStep.builder()
                .id("focus.camera")
                .type(StepType.CAMERA)
                .duration(0)
                .param("yaw", CAMERA_YAW)
                .param("pitch", CAMERA_PITCH)
                .param("distance", cameraDistance(source));
        if (source.hasController()) {
            builder.addTarget(ELEMENT_CONTROLLER);
        }
        return builder.build();
    }

    private static void appendLayerBuild(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps, List<String> buildSectionIds) {
        for (int y : occupiedLayers(source)) {
            String id = SECTION_LAYER_PREFIX + y;
            elements.add(SceneElement.of(id, "section", Map.of("selector", "layer", "y", (double) y)));
            buildSectionIds.add(id);
            steps.add(SceneStep.builder()
                    .id("build.layer." + y)
                    .type(StepType.SHOW_SECTION)
                    .duration(LAYER_SECTION_DURATION)
                    .targets(List.of(id))
                    .build());
        }
    }

    private static void appendRoleBuild(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps, List<String> buildSectionIds) {
        for (StructureRole role : orderedRoles(source)) {
            String id = SECTION_ROLE_PREFIX + role.name();
            elements.add(SceneElement.of(id, "section", roleSelector(role)));
            buildSectionIds.add(id);
            steps.add(SceneStep.builder()
                    .id("build.role." + role.name())
                    .type(StepType.SHOW_SECTION)
                    .duration(ROLE_SECTION_DURATION)
                    .targets(List.of(id))
                    .param("lod", "grouped")
                    .param("fadeIn", Boolean.TRUE)
                    .build());
        }
    }

    private static void appendControllerHighlight(StructureSource source, List<SceneStep> steps) {
        if (source.hasController()) {
            steps.add(SceneStep.builder()
                    .id("highlight.controller")
                    .type(StepType.HIGHLIGHT)
                    .duration(HIGHLIGHT_DURATION)
                    .targets(List.of(ELEMENT_CONTROLLER))
                    .param("visible", Boolean.TRUE)
                    .build());
            steps.add(text("text.controller", NARRATION_CONTROLLER, TEXT_DURATION,
                    List.of(positionText(source))));
        } else {
            steps.add(text("text.controller", NARRATION_CONTROLLER_NONE, TEXT_DURATION, List.of()));
        }
    }

    /**
     * 仓口 / 总线高亮：按角色分批用 {@link StepType#OUTLINE}（蓝色）勾勒，且总轮廓数收敛到
     * {@link #HATCH_OUTLINE_LIMIT} 个代表位置（每角色取确定性前若干个），避免大量重叠轮廓。
     */
    private static void appendHatchHighlight(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps) {
        List<StructureRole> hatchRoles = hatchRolesPresent(source);
        if (hatchRoles.isEmpty()) {
            steps.add(text("text.hatches", NARRATION_HATCHES_NONE, TEXT_DURATION, List.of()));
            return;
        }
        int remaining = HATCH_OUTLINE_LIMIT;
        for (StructureRole role : hatchRoles) {
            if (remaining <= 0) {
                break;
            }
            int roleBlocks = countBlocksWithRole(source, role);
            int limit = Math.min(roleBlocks, remaining);
            if (limit <= 0) {
                continue;
            }
            String id = HATCH_PREFIX + role.name();
            elements.add(SceneElement.of(id, "anchor", Map.of(
                    "selector", "role", "role", role.name(), "limit", (double) limit)));
            steps.add(SceneStep.builder()
                    .id("outline.hatch." + role.name())
                    .type(StepType.OUTLINE)
                    .duration(OUTLINE_DURATION)
                    .targets(List.of(id))
                    .param("visible", Boolean.TRUE)
                    .build());
            remaining -= limit;
        }
        steps.add(text("text.hatches", NARRATION_HATCHES, TEXT_DURATION,
                List.of(String.valueOf(source.hatches().size()), rolesText(hatchRoles))));
    }

    private static void appendModuleNarration(StructureSource source, List<SceneStep> steps) {
        if (source.hasModuleSlots()) {
            steps.add(text("text.modules", NARRATION_MODULES, TEXT_DURATION,
                    List.of(String.valueOf(source.moduleSlotCount()))));
        } else {
            steps.add(text("text.modules", NARRATION_MODULES_NONE, TEXT_DURATION, List.of()));
        }
    }

    private static void appendFormedDemonstration(StructureSource source, List<SceneStep> steps,
            List<String> buildSectionIds) {
        if (buildSectionIds.isEmpty()) {
            return;
        }
        steps.add(SceneStep.builder()
                .id("formed.hide")
                .type(StepType.HIDE_SECTION)
                .duration(FORMED_HIDE_DURATION)
                .targets(buildSectionIds)
                .build());
        steps.add(SceneStep.builder()
                .id("formed.show")
                .type(StepType.SHOW_SECTION)
                .duration(FORMED_SHOW_DURATION)
                .targets(buildSectionIds)
                .param("formed", Boolean.TRUE)
                .build());
        String pulseTarget = source.hasController() ? ELEMENT_CONTROLLER : buildSectionIds.get(0);
        steps.add(SceneStep.builder()
                .id("formed.pulse")
                .type(StepType.FORMED_PULSE)
                .duration(FORMED_PULSE_DURATION)
                .targets(List.of(pulseTarget))
                .build());
        steps.add(text("formed.text", NARRATION_FORMED, FORMED_TEXT_DURATION, List.of()));
    }

    private static SceneStep text(String id, String narrationKey, int duration, List<String> args) {
        return SceneStep.builder()
                .id(id)
                .type(StepType.TEXT)
                .duration(duration)
                .narration(narrationKey)
                .narrationArgs(args)
                .build();
    }

    private static Map<String, Object> roleSelector(StructureRole role) {
        return Map.of("selector", "role", "role", role.name());
    }

    private static List<Integer> occupiedLayers(StructureSource source) {
        Set<Integer> layers = new TreeSet<>();
        for (StructureBlock block : source.blocks()) {
            layers.add(block.y());
        }
        return List.copyOf(layers);
    }

    /**
     * 出现的角色，按构建顺序排列：先外壳（{@link StructureRole#PLAIN}），再仓口 / 总线（按枚举序），
     * 最后控制器——控制器作为最后安装的部件。
     */
    private static List<StructureRole> orderedRoles(StructureSource source) {
        Set<StructureRole> present = EnumSet.noneOf(StructureRole.class);
        for (StructureBlock block : source.blocks()) {
            present.add(block.role());
        }
        List<StructureRole> roles = new ArrayList<>(present);
        roles.sort(Comparator.comparingInt(SceneGenerator::buildOrderWeight)
                .thenComparingInt(StructureRole::ordinal));
        return roles;
    }

    private static int buildOrderWeight(StructureRole role) {
        if (role == StructureRole.PLAIN) {
            return 0;
        }
        if (role == StructureRole.CONTROLLER) {
            return 2;
        }
        return 1;
    }

    private static List<StructureRole> hatchRolesPresent(StructureSource source) {
        Set<StructureRole> roles = EnumSet.noneOf(StructureRole.class);
        for (StructureBlock block : source.blocks()) {
            if (block.role().isHatch()) {
                roles.add(block.role());
            }
        }
        List<StructureRole> sorted = new ArrayList<>(roles);
        sorted.sort(Comparator.comparingInt(StructureRole::ordinal));
        return sorted;
    }

    private static int countBlocksWithRole(StructureSource source, StructureRole role) {
        int count = 0;
        for (StructureBlock block : source.blocks()) {
            if (block.role() == role) {
                count++;
            }
        }
        return count;
    }

    /** 相机距离：包围盒对角线 × {@link #FIT_FACTOR}，下限 {@link #MIN_CAMERA_DISTANCE}。 */
    private static double cameraDistance(StructureSource source) {
        double diagonal = Math.sqrt((double) source.sizeX() * source.sizeX()
                + (double) source.sizeY() * source.sizeY()
                + (double) source.sizeZ() * source.sizeZ());
        return Math.max(MIN_CAMERA_DISTANCE, FIT_FACTOR * diagonal);
    }

    private static String sizeText(StructureSource source) {
        return source.sizeX() + "x" + source.sizeY() + "x" + source.sizeZ();
    }

    private static String positionText(StructureSource source) {
        StructureSource.ControllerCell cell = source.controller();
        return cell.x() + "," + cell.y() + "," + cell.z();
    }

    private static String rolesText(List<StructureRole> roles) {
        StringBuilder builder = new StringBuilder();
        for (StructureRole role : roles) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(role.name());
        }
        return builder.toString();
    }

    private static String displayName(StructureSource source) {
        return source.displayName() == null ? source.id() : source.displayName();
    }

    /** 结构 id → 稳定场景 id 片段：小写，非 {@code [a-z0-9_]} 的字符替换为 {@code _}。 */
    private static String sanitize(String id) {
        StringBuilder builder = new StringBuilder(id.length());
        for (char character : id.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean alphanumeric = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9');
            builder.append(alphanumeric || character == '_' ? character : '_');
        }
        return builder.toString();
    }
}
