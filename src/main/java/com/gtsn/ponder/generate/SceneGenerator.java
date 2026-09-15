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
 * <h2>分步教学模式（工单 #18）</h2>
 * <p>旁白不再是「本机 N 个仓口／总线（枚举列表）」式的机械罗列，而是按<b>固定教学顺序</b>分步，
 * 每步一句完整人话：</p>
 * <ol>
 *   <li><b>用途</b>（{@link #NARRATION_PURPOSE}）：这是什么机器；关键机器命中手写解说
 *       （{@link MachineDescriptions}）；</li>
 *   <li><b>怎么搭 / 成型要点</b>（{@link #NARRATION_SETUP}）：控制器与成型条件；</li>
 *   <li><b>输入什么</b>（{@link #NARRATION_INPUTS}）：原料从哪些接口进入（自然表述，不堆砌计数）；</li>
 *   <li><b>输出什么</b>（{@link #NARRATION_OUTPUTS}）：产物从哪些接口取出；</li>
 *   <li><b>供能与层级</b>（{@link #NARRATION_ENERGY}）：怎么供电；</li>
 *   <li><b>常见坑</b>（{@link #NARRATION_PITFALLS}）：过压、堵塞、维护等注意事项。</li>
 * </ol>
 * <p>仓口 / 总线的<b>角色</b>信息保留，但以自然语句嵌入输入 / 输出 / 供能 / 坑各步（角色名经 datagen
 * 本地化），不再单独用「数量 + 括号枚举」当主内容。</p>
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
 * <p>旁白键携带 {@link SceneStep#narrationArgs() 模板参数}（机器名 / 标识 / 结构尺寸 / 仓口角色），
 * 同一模板产出机器特定文案；场景 {@code title} 亦为本地化键（{@link GeneratedKeys#machineTitleKey}），
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
    public static final String GENERATOR_VERSION = "auto-2";

    /** 自动生成场景 id 前缀（后缀为清洗后的目标 id）；目录 / 进度键与产物 id 共用此推导。 */
    public static final String SCENE_ID_PREFIX = "gtsnponder:auto_";

    /**
     * 目标 id → 自动生成场景的稳定 {@code id}（{@code gtsnponder:auto_<sanitized>}）。
     * 图鉴目录为「无手作场景的注册多方块」合成条目时用同一推导，保证条目键与播放后写入的
     * 观看进度键一致（否则已看标记永远点不亮）。
     */
    public static String sceneIdFor(String targetId) {
        return SCENE_ID_PREFIX + GeneratedKeys.sanitize(targetId);
    }

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

    /**
     * 分步教学模式：用途 / 搭建 / 输入 / 输出 / 供能 / 常见坑（工单 #18）。参数见各步骤 narrationArgs。
     */
    public static final String NARRATION_PURPOSE = "ponder.gtsnponder.generated.narration.purpose";
    public static final String NARRATION_SETUP = "ponder.gtsnponder.generated.narration.setup";
    public static final String NARRATION_SETUP_NONE = "ponder.gtsnponder.generated.narration.setup.none";
    public static final String NARRATION_INPUTS = "ponder.gtsnponder.generated.narration.inputs";
    public static final String NARRATION_INPUTS_NONE = "ponder.gtsnponder.generated.narration.inputs.none";
    public static final String NARRATION_OUTPUTS = "ponder.gtsnponder.generated.narration.outputs";
    public static final String NARRATION_OUTPUTS_NONE = "ponder.gtsnponder.generated.narration.outputs.none";
    public static final String NARRATION_ENERGY = "ponder.gtsnponder.generated.narration.energy";
    public static final String NARRATION_ENERGY_NONE = "ponder.gtsnponder.generated.narration.energy.none";
    /** 发电机（只有能量输出仓）的供能叙述：它把电力送出去，而不是取电。 */
    public static final String NARRATION_ENERGY_OUTPUT = "ponder.gtsnponder.generated.narration.energy.output";
    public static final String NARRATION_PITFALLS = "ponder.gtsnponder.generated.narration.pitfalls";
    public static final String NARRATION_PITFALLS_NONE = "ponder.gtsnponder.generated.narration.pitfalls.none";
    /** 成型旁白（参数：机器标识 / 结构尺寸）：一句完整的「这就是它运转时的样子」。 */
    public static final String NARRATION_FORMED = "ponder.gtsnponder.generated.narration.formed";

    /** 模块系统旁白（模块位计数 / 每槽可接受模块 / 安装效果汇总）。 */
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

    /**
     * 教学顺序里的仓口 / 总线分组（按功能）：输入 / 输出 / 供能 / 辅助。角色名经 datagen 本地化后
     * 自然嵌入对应句子。{@code PASSTHROUGH} 归输入（物料可双向通过），{@code OTHER_HATCH} 与
     * 消声 / 维护归辅助（常见坑里提示「保持就绪」）。
     */
    private static final List<StructureRole> INPUT_ROLES =
            List.of(StructureRole.ITEM_INPUT, StructureRole.FLUID_INPUT, StructureRole.PASSTHROUGH);
    private static final List<StructureRole> OUTPUT_ROLES =
            List.of(StructureRole.ITEM_OUTPUT, StructureRole.FLUID_OUTPUT);
    private static final List<StructureRole> ENERGY_ROLES =
            List.of(StructureRole.ENERGY_INPUT, StructureRole.ENERGY_OUTPUT);
    private static final List<StructureRole> AUX_ROLES =
            List.of(StructureRole.MUFFLER, StructureRole.MAINTENANCE, StructureRole.OTHER_HATCH);

    private static final int PURPOSE_TEXT_DURATION = 50;
    private static final int LAYER_SECTION_DURATION = 12;
    private static final int ROLE_SECTION_DURATION = 18;
    private static final int HIGHLIGHT_DURATION = 25;
    private static final int OUTLINE_DURATION = 20;
    private static final int TEXT_DURATION = 50;
    private static final int FORMED_HIDE_DURATION = 15;
    private static final int FORMED_SHOW_DURATION = 15;
    private static final int FORMED_PULSE_DURATION = 20;
    private static final int FORMED_TEXT_DURATION = 45;

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

    /** 把结构源确定性生成场景数据（默认变体）。 */
    public static SceneData generate(StructureSource source) {
        return generate(source, "default", sceneIdFor(source.id()));
    }

    /**
     * 变体感知生成（工单 #21 反馈 2）：与 {@link #generate(StructureSource)} 产出的步骤 / 元素完全一致，
     * 只把场景 {@code variant} 与 {@code id} 换成给定变体（供 {@link SceneVariants} 为 GT 的多结构页
     * 生成「短 / 长」「n 节」等变体）。默认路径即委托到此，参数为
     * {@code ("default", sceneIdFor(source.id()))}。
     *
     * @param variantId 写入 {@code variant} 的稳定变体 id；空则回退 {@code default}
     * @param sceneId   场景稳定 id；空则回退 {@link #sceneIdFor(String)}
     */
    public static SceneData generate(StructureSource source, String variantId, String sceneId) {
        Objects.requireNonNull(source, "source must not be null");
        String variant = variantId == null || variantId.isBlank() ? "default" : variantId;
        String id = sceneId == null || sceneId.isBlank() ? sceneIdFor(source.id()) : sceneId;

        List<SceneElement> elements = new ArrayList<>();
        List<SceneStep> steps = new ArrayList<>();
        List<String> buildSectionIds = new ArrayList<>();

        // ① 用途（手写优先，否则结构化模板）。
        steps.add(purposeNarration(source));

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

        // ② 怎么搭 / 成型要点：控制器高亮 + 成型条件叙述。
        appendControllerHighlight(source, steps);

        // ③④⑤⑥ 输入 / 输出 / 供能 / 常见坑：仓口按功能分批高亮并自然叙述。
        appendHatchTeaching(source, elements, steps);

        appendModuleDemonstration(source, elements, steps);
        appendFormedDemonstration(source, steps, buildSectionIds);

        return SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .id(id)
                .title(GeneratedKeys.machineTitleKey(source.id()))
                .target(source.id())
                .variant(variant)
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

    /** 控制器高亮 + 成型要点叙述（无控制器时仍讲一句「没有控制器，摆齐即可」）。 */
    private static void appendControllerHighlight(StructureSource source, List<SceneStep> steps) {
        if (source.hasController()) {
            steps.add(SceneStep.builder()
                    .id("highlight.controller")
                    .type(StepType.HIGHLIGHT)
                    .duration(HIGHLIGHT_DURATION)
                    .targets(List.of(ELEMENT_CONTROLLER))
                    .param("visible", Boolean.TRUE)
                    .build());
        }
        steps.add(setupNarration(source));
    }

    /**
     * 仓口 / 总线教学：按功能分组（输入 → 输出 → 供能 → 辅助）分批 {@link StepType#OUTLINE} 高亮，
     * 并在每组后给出一句自然语言的讲解。轮廓总数收敛到 {@link #HATCH_OUTLINE_LIMIT}。
     */
    private static void appendHatchTeaching(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps) {
        int remaining = HATCH_OUTLINE_LIMIT;

        List<StructureRole> inputs = presentRoles(source, INPUT_ROLES);
        remaining = appendRolesOutline(source, elements, steps, inputs, remaining);
        steps.add(inputsNarration(source, inputs));

        List<StructureRole> outputs = presentRoles(source, OUTPUT_ROLES);
        remaining = appendRolesOutline(source, elements, steps, outputs, remaining);
        steps.add(outputsNarration(source, outputs));

        List<StructureRole> energy = presentRoles(source, ENERGY_ROLES);
        remaining = appendRolesOutline(source, elements, steps, energy, remaining);
        steps.add(energyNarration(source, energy));

        List<StructureRole> aux = presentRoles(source, AUX_ROLES);
        appendRolesOutline(source, elements, steps, aux, remaining);
        steps.add(pitfallsNarration(source, aux));
    }

    /**
     * 为一组仓口角色发出 {@code outline} 步骤（每角色一个元素，区域位置在角色内均匀抽样），并从
     * 剩余预算里扣减。返回剩余预算。
     */
    private static int appendRolesOutline(StructureSource source, List<SceneElement> elements,
            List<SceneStep> steps, List<StructureRole> roles, int remaining) {
        int left = remaining;
        for (StructureRole role : roles) {
            if (left <= 0) {
                break;
            }
            int roleBlocks = countBlocksWithRole(source, role);
            int limit = Math.min(roleBlocks, left);
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
            left -= limit;
        }
        return left;
    }

    /** ① 用途：手写优先，否则「这是 <机器名>，一台 <尺寸> 的多方块机器」。 */
    private static SceneStep purposeNarration(StructureSource source) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.purpose",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.PURPOSE),
                    PURPOSE_TEXT_DURATION, List.of());
        }
        return text("text.purpose", NARRATION_PURPOSE, PURPOSE_TEXT_DURATION,
                List.of(source.id(), sizeText(source)));
    }

    /** ② 怎么搭 / 成型要点：手写优先，否则讲控制器与通电成型。 */
    private static SceneStep setupNarration(StructureSource source) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.setup",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.SETUP), TEXT_DURATION,
                    List.of());
        }
        if (source.hasController()) {
            return text("text.setup", NARRATION_SETUP, TEXT_DURATION, List.of());
        }
        return text("text.setup", NARRATION_SETUP_NONE, TEXT_DURATION, List.of());
    }

    /** ③ 输入什么：手写优先，否则用输入类仓口角色名自然叙述。 */
    private static SceneStep inputsNarration(StructureSource source, List<StructureRole> roles) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.inputs",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.INPUTS), TEXT_DURATION,
                    List.of());
        }
        if (roles.isEmpty()) {
            return text("text.inputs", NARRATION_INPUTS_NONE, TEXT_DURATION, List.of());
        }
        return text("text.inputs", NARRATION_INPUTS, TEXT_DURATION, List.of(rolesText(roles)));
    }

    /** ④ 输出什么：手写优先，否则用输出类仓口角色名自然叙述。 */
    private static SceneStep outputsNarration(StructureSource source, List<StructureRole> roles) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.outputs",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.OUTPUTS), TEXT_DURATION,
                    List.of());
        }
        if (roles.isEmpty()) {
            return text("text.outputs", NARRATION_OUTPUTS_NONE, TEXT_DURATION, List.of());
        }
        return text("text.outputs", NARRATION_OUTPUTS, TEXT_DURATION, List.of(rolesText(roles)));
    }

    /** ⑤ 供能与层级：手写优先；取电机器讲接线，发电机讲送电，不接电的机器明确说明。 */
    private static SceneStep energyNarration(StructureSource source, List<StructureRole> roles) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.energy",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.ENERGY), TEXT_DURATION,
                    List.of());
        }
        if (roles.contains(StructureRole.ENERGY_INPUT)) {
            return text("text.energy", NARRATION_ENERGY, TEXT_DURATION, List.of(rolesText(roles)));
        }
        if (roles.contains(StructureRole.ENERGY_OUTPUT)) {
            // 发电机只有能量输出仓：它向外送电，而不是取电。
            return text("text.energy", NARRATION_ENERGY_OUTPUT, TEXT_DURATION, List.of());
        }
        return text("text.energy", NARRATION_ENERGY_NONE, TEXT_DURATION, List.of());
    }

    /** ⑥ 常见坑：手写优先，否则通用注意事项（有消声 / 维护 / 其它仓口时点出「保持就绪」）。 */
    private static SceneStep pitfallsNarration(StructureSource source, List<StructureRole> aux) {
        if (MachineDescriptions.isCurated(source.id())) {
            return text("text.pitfalls",
                    MachineDescriptions.key(source.id(), MachineDescriptions.Field.PITFALLS), TEXT_DURATION,
                    List.of());
        }
        if (aux.isEmpty()) {
            return text("text.pitfalls", NARRATION_PITFALLS_NONE, TEXT_DURATION, List.of());
        }
        return text("text.pitfalls", NARRATION_PITFALLS, TEXT_DURATION, List.of(rolesText(aux)));
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
        // 成型旁白：一句完整的「这就是它通电运转时的样子」，机器特定（标识 / 尺寸），不再是计数堆砌。
        steps.add(text("formed.text", NARRATION_FORMED, FORMED_TEXT_DURATION,
                List.of(source.id(), sizeText(source))));
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

    /** 候选角色里在结构中实际出现的那些，按候选顺序（稳定的教学分组顺序）。 */
    private static List<StructureRole> presentRoles(StructureSource source, List<StructureRole> candidates) {
        List<StructureRole> present = new ArrayList<>();
        for (StructureRole role : candidates) {
            if (countBlocksWithRole(source, role) > 0) {
                present.add(role);
            }
        }
        return List.copyOf(present);
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

    /** 角色列表文本，以 {@code ", "} 连接（渲染层替换为本地化分隔符）。 */
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

}
