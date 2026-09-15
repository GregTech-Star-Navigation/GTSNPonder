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
 * <h2>可播放性</h2>
 * <p>机器本体以 1×1×1 的 {@link StructureSource} 表达（{@link #structureOf}）：唯一方块即机器方块，
 * 同时是控制器单元。场景只声明一个元素 {@code machine}（选择器 {@code all}，在 1×1×1 结构上恒等于
 * 机器本体），故世界桥 / 元素解析器无需任何新选择器即可渲染与高亮它（封闭词汇不变）。</p>
 *
 * <h2>文案</h2>
 * <p>全部旁白走本地化键（{@code NARRATION_USAGE_*}），并携带 {@code narrationArgs} 模板参数
 * （机器名 / 等级 / 配方类型 / 槽位与罐数），使同一模板产出机器特定文案；键随 datagen 批量产出中英
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
    public static final String GENERATOR_VERSION = "usage-1";

    /** 单方块使用场景 id 前缀（后缀为清洗后的目标 id）；目录 / 进度键与产物 id 共用此推导。 */
    public static final String SCENE_ID_PREFIX = "gtsnponder:usage_";

    /** 机器本体元素 ID（单方块结构上选择器 {@code all} 恒等于机器方块）。 */
    public static final String ELEMENT_MACHINE = "machine";

    /** 旁白键（自动文案经 datagen 批量产出）。参数见各步骤 narrationArgs。 */
    public static final String NARRATION_INTRO = "ponder.gtsnponder.generated.usage.intro";
    public static final String NARRATION_MACHINE = "ponder.gtsnponder.generated.usage.machine";
    public static final String NARRATION_INPUTS = "ponder.gtsnponder.generated.usage.inputs";
    public static final String NARRATION_INPUTS_NONE = "ponder.gtsnponder.generated.usage.inputs.none";
    public static final String NARRATION_OUTPUTS = "ponder.gtsnponder.generated.usage.outputs";
    public static final String NARRATION_OUTPUTS_NONE = "ponder.gtsnponder.generated.usage.outputs.none";
    public static final String NARRATION_ENERGY = "ponder.gtsnponder.generated.usage.energy";
    public static final String NARRATION_PROGRESS = "ponder.gtsnponder.generated.usage.progress";
    public static final String NARRATION_COVERS = "ponder.gtsnponder.generated.usage.covers";
    public static final String NARRATION_PITFALLS = "ponder.gtsnponder.generated.usage.pitfalls";

    private static final int INTRO_TEXT_DURATION = 50;
    private static final int TEXT_DURATION = 50;
    private static final int SHOW_DURATION = 12;
    private static final int HIGHLIGHT_DURATION = 30;

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
        int colon = targetId.indexOf(':');
        String namespace = colon > 0 ? targetId.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? targetId.substring(colon + 1) : targetId;
        return "block." + namespace + "." + path;
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
        steps.add(text("intro", NARRATION_INTRO, INTRO_TEXT_DURATION,
                List.of(source.id(), tierText(source), recipeTypesText(source))));
        steps.add(SceneStep.builder()
                .id("highlight.machine")
                .type(StepType.HIGHLIGHT)
                .duration(HIGHLIGHT_DURATION)
                .targets(List.of(ELEMENT_MACHINE))
                .param("visible", Boolean.TRUE)
                .build());
        steps.add(text("text.machine", NARRATION_MACHINE, TEXT_DURATION, List.of(source.id())));
        steps.add(text("text.inputs", inputsKey(source), TEXT_DURATION,
                List.of(String.valueOf(source.itemInputs()), String.valueOf(source.fluidInputs()))));
        steps.add(text("text.outputs", outputsKey(source), TEXT_DURATION,
                List.of(String.valueOf(source.itemOutputs()), String.valueOf(source.fluidOutputs()))));
        steps.add(text("text.energy", NARRATION_ENERGY, TEXT_DURATION, List.of(tierText(source))));
        steps.add(text("text.progress", NARRATION_PROGRESS, TEXT_DURATION, List.of()));
        steps.add(text("text.covers", NARRATION_COVERS, TEXT_DURATION, List.of()));
        steps.add(text("text.pitfalls", NARRATION_PITFALLS, TEXT_DURATION, List.of(tierText(source))));

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

    /** 输入旁白：有物品 / 流体输入走通用键，否则走「无输入」键（明确表达退化，不误导）。 */
    private static String inputsKey(SingleBlockMachineSource source) {
        return source.itemInputs() > 0 || source.fluidInputs() > 0 ? NARRATION_INPUTS : NARRATION_INPUTS_NONE;
    }

    /** 输出旁白：有物品 / 流体输出走通用键，否则走「无输出」键。 */
    private static String outputsKey(SingleBlockMachineSource source) {
        return source.itemOutputs() > 0 || source.fluidOutputs() > 0
                ? NARRATION_OUTPUTS : NARRATION_OUTPUTS_NONE;
    }

    private static String tierText(SingleBlockMachineSource source) {
        return source.tierName().isBlank() ? String.valueOf(source.tier()) : source.tierName();
    }

    private static String recipeTypesText(SingleBlockMachineSource source) {
        return source.recipeTypeIds().isEmpty() ? "—" : String.join(", ", source.recipeTypeIds());
    }
}
