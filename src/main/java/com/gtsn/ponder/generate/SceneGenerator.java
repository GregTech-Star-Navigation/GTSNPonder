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
import com.gtsn.ponder.structure.ModuleEffectInfo;
import com.gtsn.ponder.structure.ModuleOption;
import com.gtsn.ponder.structure.ModuleSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
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
 * <p>相机步骤声明 {@code fit=true} + {@code margin}（{@link #FIT_MARGIN}），由视口层按结构包围盒
 * <b>与视口纵横比</b>反算恰好容纳结构的相机距离（见 {@code com.gtsn.ponder.viewport.CameraFraming}），
 * 使结构占满视口（窄轴填充 ≈ {@code margin}）。相机步骤排在搭建之前，故整个揭示过程都在合适
 * 取景下呈现；同时仍写出确定性的 {@code distance}（包围盒对角线 × {@link #FIT_FACTOR}）作为不含
 * 视口尺寸时的回退值，保证 headless 单测 / 产物可 diff。</p>
 *
 * <h2>高亮可读性</h2>
 * <p>控制器用 {@code highlight}（视口金色线框盒），仓口 / 总线用 {@code outline}（视口蓝色细线框
 * 盒，6 面绘制，从任意角度可见）。颜色图例移入播放屏的<b>常驻图例区</b>（不再占用旁白句子）。
 * 仓口按角色<b>分批</b>高亮，总数收敛到 {@link #HATCH_OUTLINE_LIMIT} 个代表位置，并在角色内
 * <b>均匀抽样</b>（{@code spread}）避免代表位置彼此相邻导致轮廓重叠。</p>
 *
 * <h2>机器特定旁白</h2>
 * <p>旁白键携带 {@link SceneStep#narrationArgs() 模板参数}（机器名 / 标识 / 结构尺寸 / 仓口数量与
 * 角色 / 模块位数量），同一模板产出机器特定文案；<b>成型旁白同样机器特定</b>（标识 / 尺寸 /
 * 仓口数量与角色 / 模块位数量）。场景 {@code title} 亦为本地化键（{@link GeneratedKeys#machineTitleKey}），
 * 与自动文案一起经 datagen 批量产出中英键（见 {@code com.gtsn.ponder.datagen}）。</p>
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
    /** 模块位区域元素 ID 前缀（后缀为 0 基模块位下标）。 */
    public static final String ELEMENT_MODULE_SLOT_PREFIX = "moduleslot.";

    /**
     * 「任意模块」模块位无具名可接受模块时，安装演示所用的<b>代表模块 id</b>（有据可查的占位：
     * 仅用于场景叙述与安装效果，不代表一个真实注册的模块定义）。
     */
    public static final String GENERIC_MODULE_ID = "gtsnponder:generic_module";

    /** 旁白键（自动文案经 datagen 批量产出，见规格 §本地化）。参数见各步骤 narrationArgs。 */
    public static final String NARRATION_INTRO = "ponder.gtsnponder.generated.narration.intro";
    public static final String NARRATION_CONTROLLER = "ponder.gtsnponder.generated.narration.controller";
    public static final String NARRATION_CONTROLLER_NONE =
            "ponder.gtsnponder.generated.narration.controller.none";
    public static final String NARRATION_HATCHES = "ponder.gtsnponder.generated.narration.hatches";
    public static final String NARRATION_HATCHES_NONE = "ponder.gtsnponder.generated.narration.hatches.none";
    public static final String NARRATION_MODULES = "ponder.gtsnponder.generated.narration.modules";
    public static final String NARRATION_MODULES_NONE = "ponder.gtsnponder.generated.narration.modules.none";
    /** 单个模块位叙述（参数：槽号 / 可接受模块列表）。 */
    public static final String NARRATION_MODULE_SLOT = "ponder.gtsnponder.generated.narration.module.slot";
    /** 「接受任意模块」的模块位叙述（参数：槽号）。 */
    public static final String NARRATION_MODULE_SLOT_ANY =
            "ponder.gtsnponder.generated.narration.module.slot.any";
    /** 「不接受任何模块」的模块位叙述（参数：槽号）。 */
    public static final String NARRATION_MODULE_SLOT_NONE =
            "ponder.gtsnponder.generated.narration.module.slot.none";
    /**
     * 安装效果汇总叙述（参数：模块 id / 槽号 / 并行 / 速度 / 能耗 / 输入 / 输出 / 等级加成）。
     * 数值来自 fork 的模块效果汇总（{@link ModuleEffectInfo}）。
     */
    public static final String NARRATION_MODULE_INSTALLED =
            "ponder.gtsnponder.generated.narration.module.installed";
    /** 无配方效果的安装叙述（参数：模块 id / 槽号）。 */
    public static final String NARRATION_MODULE_INSTALLED_NO_EFFECT =
            "ponder.gtsnponder.generated.narration.module.installed.none";
    public static final String NARRATION_FORMED = "ponder.gtsnponder.generated.narration.formed";

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

    /**
     * 取景自适应目标填充比例（视口窄轴的占比）：相机步骤写出 {@code fit=true, margin=FIT_MARGIN}，
     * 由视口层按包围盒 + 纵横比反算。0.90 表示窄轴填充 90%（相机方向为斜 3/4，AABB 投影保守，
     * 实际方块轮廓约 70–85%）。
     */
    public static final double FIT_MARGIN = 0.90d;

    /** 相机取景回退：距离 = FIT_FACTOR × 包围盒对角线（下限 {@link #MIN_CAMERA_DISTANCE}）。 */
    private static final double FIT_FACTOR = 0.82d;
    private static final double MIN_CAMERA_DISTANCE = 2.0d;
    private static final double CAMERA_YAW = 25.0d;
    private static final double CAMERA_PITCH = -135.0d;

    /** 仓口 / 总线高亮轮廓总数上限（收敛，避免大量重叠轮廓）。 */
    public static final int HATCH_OUTLINE_LIMIT = 4;

    /** 模块安装演示步骤时长（tick）：安装动作占用的时间轴。 */
    private static final int INSTALL_DURATION = 25;

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
        appendModuleDemonstration(source, elements, steps);
        appendFormedDemonstration(source, steps, buildSectionIds);

        return SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .id("gtsnponder:auto_" + GeneratedKeys.sanitize(source.id()))
                .title(GeneratedKeys.machineTitleKey(source.id()))
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
                .param("distance", cameraDistance(source))
                .param("fit", Boolean.TRUE)
                .param("margin", FIT_MARGIN);
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
            // spread=true：在该角色的全部单元里均匀抽样 limit 个代表位置，避免取前 N 个彼此相邻
            // 导致的轮廓重叠（见 SceneElementResolver 的 role + spread）。
            elements.add(SceneElement.of(id, "anchor", Map.of(
                    "selector", "role", "role", role.name(),
                    "limit", (double) limit, "spread", Boolean.TRUE)));
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

    /**
     * 模块系统演示：对每个模块位——
     * <ol>
     *   <li>把模块位<b>区域</b>声明为元素（选择器 {@link ModuleSlot#SELECTOR}），并以 {@code outline}
     *       高亮整个区域（非单块）；</li>
     *   <li>叙述该模块位的<b>可接受模块</b>（具名列表 / 任意模块 / 不接受任何模块）；</li>
     *   <li>对可安装的模块位发出 {@code installModule}（空模块位 → 安装一个模块），随后叙述
     *       <b>效果汇总</b>（来自 fork 的模块效果，见 {@link ModuleEffectInfo}）。</li>
     * </ol>
     * 顺序确定：模块位按声明下标、可接受模块按 id 升序、安装取第一个（字典序最小）可接受模块；
     * 「任意模块」模块位安装一个有据可查的代表模块（{@link #GENERIC_MODULE_ID}）。
     */
    private static void appendModuleDemonstration(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps) {
        if (!source.hasModuleSlots()) {
            steps.add(text("text.modules", NARRATION_MODULES_NONE, TEXT_DURATION, List.of()));
            return;
        }
        steps.add(text("text.modules", NARRATION_MODULES, TEXT_DURATION,
                List.of(String.valueOf(source.moduleSlotCount()))));

        List<ModuleSlot> slots = source.moduleSlots();
        for (int index = 0; index < slots.size(); index++) {
            ModuleSlot slot = slots.get(index);
            String elementId = ELEMENT_MODULE_SLOT_PREFIX + index;
            elements.add(SceneElement.of(elementId, "region", Map.of(
                    "selector", ModuleSlot.SELECTOR, "index", (double) index)));
            steps.add(SceneStep.builder()
                    .id("outline.moduleslot." + index)
                    .type(StepType.OUTLINE)
                    .duration(OUTLINE_DURATION)
                    .targets(List.of(elementId))
                    .param("visible", Boolean.TRUE)
                    .build());

            String slotNumber = String.valueOf(index + 1);
            if (!slot.hasAcceptableModules()) {
                steps.add(text("text.moduleslot." + index, NARRATION_MODULE_SLOT_NONE, TEXT_DURATION,
                        List.of(slotNumber)));
                continue;
            }
            if (slot.moduleOptions().isEmpty()) {
                steps.add(text("text.moduleslot." + index, NARRATION_MODULE_SLOT_ANY, TEXT_DURATION,
                        List.of(slotNumber)));
            } else {
                steps.add(text("text.moduleslot." + index, NARRATION_MODULE_SLOT, TEXT_DURATION,
                        List.of(slotNumber, acceptedModulesText(slot))));
            }

            String moduleId = installModuleFor(slot);
            steps.add(SceneStep.builder()
                    .id("install.moduleslot." + index)
                    .type(StepType.INSTALL_MODULE)
                    .duration(INSTALL_DURATION)
                    .targets(List.of(elementId))
                    .param("module", moduleId)
                    .build());
            steps.add(installedModuleText(index, slot, moduleId, slotNumber));
        }
    }

    /** 可接受模块的确定性文本：取模块效果选项的 id（否则取下声明 id），按字典序升序、逗号分隔。 */
    private static String acceptedModulesText(ModuleSlot slot) {
        List<String> ids = new ArrayList<>();
        if (slot.moduleOptions().isEmpty()) {
            ids.addAll(slot.acceptableModuleIds());
        } else {
            for (ModuleOption option : slot.moduleOptions()) {
                ids.add(option.moduleId());
            }
        }
        ids.sort(Comparator.naturalOrder());
        return String.join(", ", ids);
    }

    /** 安装演示所用的模块：可接受模块中字典序最小者；无具名模块时用 {@link #GENERIC_MODULE_ID}。 */
    private static String installModuleFor(ModuleSlot slot) {
        String smallest = null;
        for (ModuleOption option : slot.moduleOptions()) {
            if (smallest == null || option.moduleId().compareTo(smallest) < 0) {
                smallest = option.moduleId();
            }
        }
        if (smallest != null) {
            return smallest;
        }
        return GENERIC_MODULE_ID;
    }

    /** 安装后的效果汇总叙述（数值来自 {@link ModuleEffectInfo}；中性效果走「无效果」键）。 */
    private static SceneStep installedModuleText(int index, ModuleSlot slot, String moduleId, String slotNumber) {
        ModuleEffectInfo effect = slot.optionFor(moduleId).map(ModuleOption::effect)
                .orElse(ModuleEffectInfo.EMPTY);
        if (effect.isEmpty()) {
            return text("text.installed." + index, NARRATION_MODULE_INSTALLED_NO_EFFECT, TEXT_DURATION,
                    List.of(moduleId, slotNumber));
        }
        return text("text.installed." + index, NARRATION_MODULE_INSTALLED, TEXT_DURATION, List.of(
                moduleId,
                slotNumber,
                String.valueOf(effect.parallelCapacity()),
                String.valueOf(effect.speedMultiplier()),
                String.valueOf(effect.energyMultiplier()),
                String.valueOf(effect.inputMultiplier()),
                String.valueOf(effect.outputMultiplier()),
                String.valueOf(effect.tierBonus())));
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
        // 成型旁白机器特定：机器标识 / 结构尺寸 / 仓口·总线数量与角色 / 模块位数量。
        steps.add(text("formed.text", NARRATION_FORMED, FORMED_TEXT_DURATION, List.of(
                source.id(),
                sizeText(source),
                String.valueOf(source.hatches().size()),
                rolesTextOrDash(hatchRolesPresent(source)),
                String.valueOf(source.moduleSlotCount()))));
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

    /** 角色列表文本；空列表返回占位符 {@code —}，使成型旁白在无仓口时也可读。 */
    private static String rolesTextOrDash(List<StructureRole> roles) {
        return roles.isEmpty() ? "—" : rolesText(roles);
    }

}
