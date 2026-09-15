package com.gtsn.ponder.generate;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.Source;
import com.gtsn.ponder.engine.model.StepType;
import com.gtsn.ponder.structure.SingleBlockMachineSource;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「使用场景」生成器（单方块机器）：把中立的 {@link SingleBlockMachineSource} 翻译成可播放的
 * {@link SceneData}。
 *
 * <p><b>与自动生成器（{@link SceneGenerator}）的区别</b>：多方块讲「怎么搭」（分层 / 角色揭示 →
 * 成型脉冲）；单方块没有结构可搭，只讲「怎么用」——机器本体 + 输入 / 输出槽与流体罐 + 能量 /
 * 进度 + 覆盖板 + 常见坑（概念旁白）。因此本生成器<b>不产生</b> {@code build.layer/role}、
 * {@code installModule} 或 {@code formedPulse} 步骤，而是揭示机器本体后逐条讲解使用方式。</p>
 *
 * <h2>分步教学模式（工单 #18）</h2>
 * <p>旁白按<b>固定教学顺序</b>分步，每步一句完整人话：<b>用途</b> → <b>搭建 / 本体</b> →
 * <b>输入什么</b> → <b>输出什么</b> → <b>供能与层级</b> → <b>常见坑</b> + 进阶（覆盖板）。关键机器
 * （{@link MachineDescriptions}）命中手写解说，其余走结构化模板（仍是无原始 {@code gtceu:} id、
 * 无枚举名堆砌的完整句子）。</p>
 *
 * <h2>可播放性</h2>
 * <p>机器本体以 1×1×1 的 {@link StructureSource} 表达（{@link #structureOf}）：唯一方块即机器方块，
 * 同时是控制器单元。场景只声明一个元素 {@code machine}（选择器 {@code all}，在 1×1×1 结构上恒等于
 * 机器本体），故世界桥 / 元素解析器无需任何新选择器即可渲染与高亮它（封闭词汇不变）。</p>
 *
 * <h2>文案</h2>
 * <p>全部旁白走本地化键（{@code NARRATION_*}），并携带 {@code narrationArgs} 模板参数（机器名 /
 * 等级 / 配方类型 / 槽位与罐数），使同一模板产出机器特定文案；键随 datagen 批量产出中英
 * （见 {@code com.gtsn.ponder.datagen}）。场景 {@code title} 复用 GT 自身的方块名键
 * （{@link #titleKeyFor}，{@code block.<ns>.<path>}），故任意单方块机器都有本地化标题，无需额外枚举。</p>
 *
 * <h2>确定性</h2>
 * <p>无随机：元素 / 步骤 ID 与顺序固定，参数由源确定推导。对同一 {@link SingleBlockMachineSource}
 * 两次生成产生字节相等 JSON（由 {@code SceneDataWriter} 序列化）。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（自动生成缝可在 headless 单测中以 DTO 夹具断言）。</p>
 */
public final class SingleBlockUsageGenerator {

    /** 生成器版本：产物可重生成 / 可 diff 的标识（写入 {@code generatorVersion}）。 */
    public static final String GENERATOR_VERSION = "usage-3";

    /** 单方块使用场景 id 前缀（后缀为清洗后的目标 id）；目录 / 进度键与产物 id 共用此推导。 */
    public static final String SCENE_ID_PREFIX = "gtsnponder:usage_";

    /** 机器本体元素 ID（单方块结构上选择器 {@code all} 恒等于机器方块）。 */
    public static final String ELEMENT_MACHINE = "machine";

    /** 教学模式旁白键（固定顺序：用途 → 本体 → 输入 → 输出 → 供能 → 常见坑 + 进阶）。 */
    public static final String NARRATION_PURPOSE = "ponder.gtsnponder.generated.usage.purpose";
    public static final String NARRATION_SETUP = "ponder.gtsnponder.generated.usage.setup";
    public static final String NARRATION_INPUTS = "ponder.gtsnponder.generated.usage.inputs";
    public static final String NARRATION_INPUTS_NONE = "ponder.gtsnponder.generated.usage.inputs.none";
    public static final String NARRATION_OUTPUTS = "ponder.gtsnponder.generated.usage.outputs";
    public static final String NARRATION_OUTPUTS_NONE = "ponder.gtsnponder.generated.usage.outputs.none";
    public static final String NARRATION_ENERGY = "ponder.gtsnponder.generated.usage.energy";
    public static final String NARRATION_ENERGY_NONE = "ponder.gtsnponder.generated.usage.energy.none";
    public static final String NARRATION_PITFALLS = "ponder.gtsnponder.generated.usage.pitfalls";
    public static final String NARRATION_PITFALLS_NONE = "ponder.gtsnponder.generated.usage.pitfalls.none";
    /** 进阶：覆盖板 / 自动化（教学顺序之外的补充一步）。 */
    public static final String NARRATION_COVERS = "ponder.gtsnponder.generated.usage.covers";

    private static final int PURPOSE_TEXT_DURATION = 55;
    private static final int TEXT_DURATION = 50;
    private static final int SHOW_DURATION = 12;
    private static final int HIGHLIGHT_DURATION = 30;
    private static final int OUTLINE_DURATION = 24;

    /** 取景自适应目标填充比例（视口窄轴的占比）；1×1×1 结构的回退距离下限见 {@link #MIN_CAMERA_DISTANCE}。 */
    public static final double FIT_MARGIN = 0.90d;
    private static final double MIN_CAMERA_DISTANCE = 2.0d;
    private static final double CAMERA_YAW = 25.0d;
    private static final double CAMERA_PITCH = -135.0d;

    private SingleBlockUsageGenerator() {
    }

    /** 目标 id → 使用场景的稳定 {@code id}（{@code gtsnponder:usage_<sanitized>}）。 */
    public static String sceneIdFor(String targetId) {
        return SCENE_ID_PREFIX + GeneratedKeys.sanitize(targetId);
    }

    /**
     * 目标 id → 标题本地化键：复用 GT 自身的方块名键 {@code block.<namespace>.<path>}
     * （GT 随包 lang 已产出中英名），故任意单方块机器都有可用标题，无需本 mod 再枚举标题键。
     */
    public static String titleKeyFor(String targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return GeneratedKeys.blockNameKey(targetId);
    }

    /**
     * 把单方块机器源表达为 1×1×1 的多方块结构源：唯一方块是机器方块，同时是控制器单元。
     * 这样世界桥 / 元素解析器无需任何新概念即可渲染它。
     */
    public static StructureSource structureOf(SingleBlockMachineSource source) {
        Objects.requireNonNull(source, "source must not be null");
        return StructureSource.builder(source.id())
                .displayName(source.displayName())
                .size(1, 1, 1)
                .controller(0, 0, 0)
                .addBlock(0, 0, 0, source.blockId(), StructureRole.CONTROLLER)
                .build();
    }

    /** 把单方块机器源确定性生成「使用场景」数据。 */
    public static SceneData generate(SingleBlockMachineSource source) {
        Objects.requireNonNull(source, "source must not be null");

        List<SceneElement> elements = new ArrayList<>();
        List<SceneStep> steps = new ArrayList<>();

        elements.add(SceneElement.of(ELEMENT_MACHINE, "anchor", Map.of("selector", "all")));

        // 使用场景先取景并揭示机器本体，随后才讲解——「怎么用」应立刻看见机器（区别于多方块搭建的
        // 「先讲再搭」：搭建过程本身就是叙事，使用场景不是）。
        steps.add(camera());
        steps.add(SceneStep.builder()
                .id("build.machine")
                .type(StepType.SHOW_SECTION)
                .duration(SHOW_DURATION)
                .targets(List.of(ELEMENT_MACHINE))
                .build());

        // ① 用途（手写优先，否则「这是 <机器名>，一台 <等级> 的机器，处理 <配方> 配方」）。
        steps.add(purposeNarration(source));
        steps.add(SceneStep.builder()
                .id("highlight.machine")
                .type(StepType.HIGHLIGHT)
                .duration(HIGHLIGHT_DURATION)
                .targets(List.of(ELEMENT_MACHINE))
                .param("visible", Boolean.TRUE)
                .build());

        // ② 搭建 / 本体 → ③ 输入 → ④ 输出 → ⑤ 供能 → ⑥ 常见坑 → 进阶（覆盖板）。
        steps.add(setupNarration(source));
        // 反馈 1（工单 #21）：单方块没有结构可搭，但仍以「金色本体高亮 → 蓝色部件轮廓」两段式给出
        // 与多方块一致的视觉节奏（轮廓步代表槽 / 罐 / 仓口等内部部件），而不是一帧静止。
        steps.add(SceneStep.builder()
                .id("outline.machine")
                .type(StepType.OUTLINE)
                .duration(OUTLINE_DURATION)
                .targets(List.of(ELEMENT_MACHINE))
                .param("visible", Boolean.TRUE)
                .build());
        steps.add(inputsNarration(source));
        steps.add(outputsNarration(source));
        steps.add(energyNarration(source));
        steps.add(pitfallsNarration(source));
        steps.add(text("text.covers", NARRATION_COVERS, TEXT_DURATION, List.of()));

        return SceneData.builder()
                .formatVersion(SceneDataParser.CURRENT_FORMAT_VERSION)
                .id(sceneIdFor(source.id()))
                .title(titleKeyFor(source.id()))
                .target(source.id())
                .variant("default")
                .source(Source.AUTO)
                .generatorVersion(GENERATOR_VERSION)
                .elements(elements)
                .steps(steps)
                .build();
    }

    private static SceneStep camera() {
        return SceneStep.builder()
                .id("focus.camera")
                .type(StepType.CAMERA)
                .duration(0)
                .targets(List.of(ELEMENT_MACHINE))
                .param("yaw", CAMERA_YAW)
                .param("pitch", CAMERA_PITCH)
                .param("distance", MIN_CAMERA_DISTANCE)
                .param("fit", Boolean.TRUE)
                .param("margin", FIT_MARGIN)
                .build();
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

    private static boolean curated(SingleBlockMachineSource source) {
        return MachineDescriptions.isCurated(source.id());
    }

    private static String curatedKey(SingleBlockMachineSource source, MachineDescriptions.Field field) {
        return MachineDescriptions.key(source.id(), field);
    }

    /** ① 用途：手写优先，否则「这是 <机器名>，一台 <等级> 的机器，处理 <配方> 配方」。 */
    private static SceneStep purposeNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.purpose", curatedKey(source, MachineDescriptions.Field.PURPOSE),
                    PURPOSE_TEXT_DURATION, List.of());
        }
        return text("text.purpose", NARRATION_PURPOSE, PURPOSE_TEXT_DURATION,
                List.of(source.id(), tierText(source), recipeTypesText(source)));
    }

    /** ② 搭建 / 本体：手写优先，否则「整台机器就是这一个方块」。 */
    private static SceneStep setupNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.setup", curatedKey(source, MachineDescriptions.Field.SETUP), TEXT_DURATION,
                    List.of());
        }
        return text("text.setup", NARRATION_SETUP, TEXT_DURATION, List.of(source.id()));
    }

    /** ③ 输入：手写优先，否则按物品 / 流体输入计数自然叙述（无输入时明确说明）。 */
    private static SceneStep inputsNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.inputs", curatedKey(source, MachineDescriptions.Field.INPUTS), TEXT_DURATION,
                    List.of());
        }
        String key = source.itemInputs() > 0 || source.fluidInputs() > 0
                ? NARRATION_INPUTS : NARRATION_INPUTS_NONE;
        return text("text.inputs", key, TEXT_DURATION,
                List.of(String.valueOf(source.itemInputs()), String.valueOf(source.fluidInputs())));
    }

    /** ④ 输出：手写优先，否则按物品 / 流体输出计数自然叙述（无输出时明确说明）。 */
    private static SceneStep outputsNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.outputs", curatedKey(source, MachineDescriptions.Field.OUTPUTS), TEXT_DURATION,
                    List.of());
        }
        String key = source.itemOutputs() > 0 || source.fluidOutputs() > 0
                ? NARRATION_OUTPUTS : NARRATION_OUTPUTS_NONE;
        return text("text.outputs", key, TEXT_DURATION,
                List.of(String.valueOf(source.itemOutputs()), String.valueOf(source.fluidOutputs())));
    }

    /** ⑤ 供能与层级：手写优先，否则说清它用哪个电压 / 不接电。 */
    private static SceneStep energyNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.energy", curatedKey(source, MachineDescriptions.Field.ENERGY), TEXT_DURATION,
                    List.of());
        }
        if (source.hasEnergy()) {
            return text("text.energy", NARRATION_ENERGY, TEXT_DURATION, List.of(tierText(source)));
        }
        return text("text.energy", NARRATION_ENERGY_NONE, TEXT_DURATION, List.of());
    }

    /** ⑥ 常见坑：手写优先，否则过压 / 堵塞 / 缺料注意事项。 */
    private static SceneStep pitfallsNarration(SingleBlockMachineSource source) {
        if (curated(source)) {
            return text("text.pitfalls", curatedKey(source, MachineDescriptions.Field.PITFALLS),
                    TEXT_DURATION, List.of());
        }
        if (source.hasEnergy()) {
            return text("text.pitfalls", NARRATION_PITFALLS, TEXT_DURATION, List.of(tierText(source)));
        }
        return text("text.pitfalls", NARRATION_PITFALLS_NONE, TEXT_DURATION, List.of());
    }

    private static String tierText(SingleBlockMachineSource source) {
        return source.tierName().isBlank() ? String.valueOf(source.tier()) : source.tierName();
    }

    private static String recipeTypesText(SingleBlockMachineSource source) {
        return source.recipeTypeIds().isEmpty() ? "—" : String.join(", ", source.recipeTypeIds());
    }
}
