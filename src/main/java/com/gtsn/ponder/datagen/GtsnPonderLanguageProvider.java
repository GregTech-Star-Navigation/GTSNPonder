package com.gtsn.ponder.datagen;

import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.content.SystemSceneKeys;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.gt.GtMultiblockCatalog;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GTSNPonder 的语言数据 provider（每个 locale 一个实例）：把全部文案键写入
 * {@code src/generated/resources/assets/gtsnponder/lang/<locale>.json}。
 *
 * <p>覆盖三类键：① 手作 / 界面固定键（快捷键、控制条、消息、焦炉手作场景）；② 自动生成场景的
 * 模板文案键（含计数模板，如 {@code hatches} / {@code modules}）与颜色图例键；③ 经
 * {@link GtMultiblockCatalog} 枚举出的每台多方块的机器标题键（{@code ...machine.<id>.title}）。
 * GT 访问只在 {@code com.gtsn.ponder.gt} 内，本类不 import {@code com.gregtechceu}。</p>
 */
public final class GtsnPonderLanguageProvider extends LanguageProvider {

    private final String locale;

    public GtsnPonderLanguageProvider(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        translations(locale).forEach(this::add);
        for (GtMultiblockCatalog.Multiblock machine : GtMultiblockCatalog.all()) {
            add(GeneratedKeys.machineTitleKey(machine.id()), machine.name(locale));
        }
    }

    private static Map<String, String> translations(String locale) {
        return "zh_cn".equals(locale) ? chinese() : english();
    }

    private static Map<String, String> english() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.categories.gtsnponder", "GTSN Ponder");
        keys.put("key.gtsnponder.ponder", "Open Ponder");
        keys.put("key.gtsnponder.catalog", "Open Ponder Catalog");

        keys.put("ponder.gtsnponder.coke_oven.title", "Coke Oven");
        keys.put("ponder.gtsnponder.coke_oven.narration.intro",
                "This is the coke oven: a 3x3x3 multiblock that turns coal into coke.");
        keys.put("ponder.gtsnponder.coke_oven.narration.controller",
                "The controller sits in the front-center. Power it to form the structure.");
        keys.put("ponder.gtsnponder.coke_oven.narration.usage",
                "Feed coal from the top, then collect coke and creosote from the output hatch.");

        keys.put("ponder.gtsnponder.player.title", "Ponder");
        keys.put("ponder.gtsnponder.control.prev", "Prev");
        keys.put("ponder.gtsnponder.control.play", "Play");
        keys.put("ponder.gtsnponder.control.pause", "Pause");
        keys.put("ponder.gtsnponder.control.next", "Next");
        keys.put("ponder.gtsnponder.control.replay", "Replay");
        keys.put("ponder.gtsnponder.player.seekhint", "Click the bar to seek");

        keys.put(SceneGenerator.NARRATION_INTRO,
                "This is %s, a %s multiblock. Watch how the blocks come together.");
        keys.put(SceneGenerator.NARRATION_CONTROLLER,
                "The controller is at %s - power it to form the structure.");
        keys.put(SceneGenerator.NARRATION_CONTROLLER_NONE,
                "This structure page declares no controller block.");
        keys.put(SceneGenerator.NARRATION_HATCHES, "%s hatch / bus block(s), across %s.");
        keys.put(SceneGenerator.NARRATION_HATCHES_NONE,
                "This structure declares no hatches or buses.");
        keys.put(SceneGenerator.NARRATION_MODULES,
                "%s module slot(s) - install matching modules here.");
        keys.put(SceneGenerator.NARRATION_MODULES_NONE, "This structure declares no module slots.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT, "Slot %s: accepts %s.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_ANY, "Slot %s: accepts any module.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_NONE, "Slot %s: accepts no module.");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED,
                "Installed %s into slot %s - parallel %s, speed x%s, energy x%s, input x%s, "
                        + "output x%s, tier +%s.");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT,
                "Installed %s into slot %s - no recipe effects.");
        keys.put(SceneGenerator.NARRATION_FORMED,
                "%s (%s) formed demo: %s hatch/bus block(s) (%s), %s module slot(s). "
                        + "Hidden then shown again: unformed -> formed.");

        keys.put(GeneratedKeys.LEGEND_TITLE, "Legend");
        keys.put(GeneratedKeys.LEGEND_CONTROLLER, "Controller (gold)");
        keys.put(GeneratedKeys.LEGEND_HATCH, "Hatch / bus (blue)");
        keys.put(GeneratedKeys.LEGEND_MODULE_SLOT, "Module slot (green)");

        keys.put("ponder.gtsnponder.message.no_target", "No GT machine or multiblock in view (within reach).");
        keys.put("ponder.gtsnponder.message.no_scene", "No ponder scene is registered for %s.");
        keys.put("ponder.gtsnponder.message.no_world", "Join a world before opening ponder.");
        keys.put("ponder.gtsnponder.message.dump", "Generated scene written to %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "Could not write the generated scene: %s");
        editorKeys(keys, false);
        catalogKeys(keys, false);
        systemSceneKeys(keys, false);
        return keys;
    }

    private static Map<String, String> chinese() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.categories.gtsnponder", "格雷科技·思索");
        keys.put("key.gtsnponder.ponder", "打开思索");
        keys.put("key.gtsnponder.catalog", "打开思索图鉴");

        keys.put("ponder.gtsnponder.coke_oven.title", "焦炉");
        keys.put("ponder.gtsnponder.coke_oven.narration.intro", "这是焦炉：一台 3×3×3 的多方块，把煤炭炼成焦炭。");
        keys.put("ponder.gtsnponder.coke_oven.narration.controller", "控制器位于正面中央，通电后多方块即成型。");
        keys.put("ponder.gtsnponder.coke_oven.narration.usage", "从顶部输入煤炭，再从输出仓口取出焦炭与杂酚油。");

        keys.put("ponder.gtsnponder.player.title", "思索");
        keys.put("ponder.gtsnponder.control.prev", "上一步");
        keys.put("ponder.gtsnponder.control.play", "播放");
        keys.put("ponder.gtsnponder.control.pause", "暂停");
        keys.put("ponder.gtsnponder.control.next", "下一步");
        keys.put("ponder.gtsnponder.control.replay", "重播");
        keys.put("ponder.gtsnponder.player.seekhint", "点击进度条跳转");

        keys.put(SceneGenerator.NARRATION_INTRO, "这是 %s，一台 %s 的多方块。看看方块如何拼合。");
        keys.put(SceneGenerator.NARRATION_CONTROLLER, "控制器位于 %s，通电后多方块即成型。");
        keys.put(SceneGenerator.NARRATION_CONTROLLER_NONE, "该结构页未声明控制器方块。");
        keys.put(SceneGenerator.NARRATION_HATCHES, "%s 个仓口 / 总线（%s）。");
        keys.put(SceneGenerator.NARRATION_HATCHES_NONE, "该结构未声明仓口或总线。");
        keys.put(SceneGenerator.NARRATION_MODULES, "%s 个模块位——在此安装匹配的模块。");
        keys.put(SceneGenerator.NARRATION_MODULES_NONE, "该结构未声明模块位。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT, "槽 %s：接受 %s。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_ANY, "槽 %s：接受任意模块。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_NONE, "槽 %s：不接受任何模块。");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED,
                "安装 %s 到槽 %s——并行 %s、速度 ×%s、能耗 ×%s、输入 ×%s、输出 ×%s、等级 +%s。");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT, "安装 %s 到槽 %s——无配方效果。");
        keys.put(SceneGenerator.NARRATION_FORMED,
                "%s（%s）成型演示：本机 %s 个仓口 / 总线（%s）、%s 个模块位。先隐藏再重现，即未成型 → 成型。");

        keys.put(GeneratedKeys.LEGEND_TITLE, "图例");
        keys.put(GeneratedKeys.LEGEND_CONTROLLER, "控制器（金色）");
        keys.put(GeneratedKeys.LEGEND_HATCH, "仓口 / 总线（蓝色）");
        keys.put(GeneratedKeys.LEGEND_MODULE_SLOT, "模块位（绿色）");

        keys.put("ponder.gtsnponder.message.no_target", "视线内没有格雷科技机器 / 多方块（需在触及范围内）。");
        keys.put("ponder.gtsnponder.message.no_scene", "%s 暂无思索场景。");
        keys.put("ponder.gtsnponder.message.no_world", "请先进入世界再打开思索。");
        keys.put("ponder.gtsnponder.message.dump", "已把生成场景写入 %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "写入生成场景失败：%s");
        editorKeys(keys, true);
        catalogKeys(keys, true);
        systemSceneKeys(keys, true);
        return keys;
    }

    /**
     * 思索图鉴目录（#11）与 XEI（JEI/EMI）入口的界面键（中英双语）：标题 / 搜索 / 类别 / 观看标记 /
     * 进度 / 空态 / 关闭 / XEI 按钮，以及全部类别显示名（{@link SceneCategories}）。键集中定义在
     * {@link CatalogKeys}。
     */
    private static void catalogKeys(Map<String, String> keys, boolean chinese) {
        if (chinese) {
            keys.put(CatalogKeys.TITLE, "思索图鉴");
            keys.put(CatalogKeys.SEARCH, "搜索");
            keys.put(CatalogKeys.ALL, "全部");
            keys.put(CatalogKeys.PLAY, "播放");
            keys.put(CatalogKeys.WATCHED, "[已看]");
            keys.put(CatalogKeys.UNWATCHED, "[未看]");
            keys.put(CatalogKeys.PROGRESS, "已看 %s / %s");
            keys.put(CatalogKeys.EMPTY, "没有匹配的思索场景。");
            keys.put(CatalogKeys.CLOSE, "关闭");
            keys.put(CatalogKeys.RELATED, "相关机器");
            keys.put(CatalogKeys.RELATED_TO, "与 %s 相关");
            keys.put(CatalogKeys.XEI_OPEN, "打开这台机器的思索教程");
            keys.put(CatalogKeys.XEI_OPEN_SHORT, "思索");
            keys.put(CatalogKeys.GT_MACHINE_OPEN, "打开这台机器的思索教程");
            keys.put(CatalogKeys.GT_MACHINE_OPEN_SHORT, "思索");
            keys.put(CatalogKeys.categoryKey(SceneCategories.MODULE), "模块系统");
            keys.put(CatalogKeys.categoryKey(SceneCategories.CONCEPT), "概念");
            keys.put(CatalogKeys.categoryKey(SceneCategories.STEAM), "蒸汽机器");
            keys.put(CatalogKeys.categoryKey(SceneCategories.POWER), "发电与能量");
            keys.put(CatalogKeys.categoryKey(SceneCategories.LOGISTICS), "物流与管网");
            keys.put(CatalogKeys.categoryKey(SceneCategories.MACHINES), "机器");
            return;
        }
        keys.put(CatalogKeys.TITLE, "Ponder Catalog");
        keys.put(CatalogKeys.SEARCH, "Search");
        keys.put(CatalogKeys.ALL, "All");
        keys.put(CatalogKeys.PLAY, "Play");
        keys.put(CatalogKeys.WATCHED, "[watched]");
        keys.put(CatalogKeys.UNWATCHED, "[unwatched]");
        keys.put(CatalogKeys.PROGRESS, "Watched %s / %s");
        keys.put(CatalogKeys.EMPTY, "No ponder scene matches this filter.");
        keys.put(CatalogKeys.CLOSE, "Close");
        keys.put(CatalogKeys.RELATED, "Related");
        keys.put(CatalogKeys.RELATED_TO, "Related to %s");
        keys.put(CatalogKeys.XEI_OPEN, "Open this machine's ponder tutorial");
        keys.put(CatalogKeys.XEI_OPEN_SHORT, "Ponder");
        keys.put(CatalogKeys.GT_MACHINE_OPEN, "Open this machine's ponder tutorial");
        keys.put(CatalogKeys.GT_MACHINE_OPEN_SHORT, "Ponder");
        keys.put(CatalogKeys.categoryKey(SceneCategories.MODULE), "Module system");
        keys.put(CatalogKeys.categoryKey(SceneCategories.CONCEPT), "Concepts");
        keys.put(CatalogKeys.categoryKey(SceneCategories.STEAM), "Steam machines");
        keys.put(CatalogKeys.categoryKey(SceneCategories.POWER), "Power & energy");
        keys.put(CatalogKeys.categoryKey(SceneCategories.LOGISTICS), "Logistics & pipes");
        keys.put(CatalogKeys.categoryKey(SceneCategories.MACHINES), "Machines");
    }

    /**
     * 内容工单 #10（发电·能量网 / 物流管网）的随包场景文案键（中英双语）：机器特定标题 / 开场旁白，
     * 以及概念旁白（电压等级 / 超压 / 线缆熔断；物品 / 流体管道 / 线缆 / 覆盖板）。键集中定义在
     * {@link SystemSceneKeys}。
     */
    private static void systemSceneKeys(Map<String, String> keys, boolean chinese) {
        if (chinese) {
            keys.put(SystemSceneKeys.POWER_COMBUSTION_TITLE, "发电网：大型燃烧引擎");
            keys.put(SystemSceneKeys.POWER_TRANSFORMER_TITLE, "发电网：有源变压器");
            keys.put(SystemSceneKeys.POWER_COMBUSTION_INTRO,
                    "这是 %s——一台燃烧燃料发电的引擎，为电网产出 EU。");
            keys.put(SystemSceneKeys.POWER_TRANSFORMER_INTRO,
                    "这是 %s——它在不同电压等级之间变换能量，让机器接到正确的电网上。");
            keys.put(SystemSceneKeys.POWER_CONTROLLER,
                    "控制器位于正面中央：通入正确电压后多方块成型。");
            keys.put(SystemSceneKeys.POWER_VOLTAGE,
                    "格雷科技的电按电压等级划分（ULV、LV、MV、HV……）。机器只接受自己那一级，变压器负责在等级之间转换。");
            keys.put(SystemSceneKeys.POWER_OVERVOLTAGE,
                    "超压：给机器通入高于它支持的电压，能耗变为 4 倍，并可能损坏甚至炸毁机器。");
            keys.put(SystemSceneKeys.POWER_BURNING,
                    "线缆熔断：线缆承载的电流超过线径允许值时会被烧断——按安培数选择 1x / 2x / 4x 线缆。");
            keys.put(SystemSceneKeys.LOGISTICS_TANK_TITLE, "物流管网：钢制多方块储罐");
            keys.put(SystemSceneKeys.LOGISTICS_PUMP_TITLE, "物流管网：原始水泵");
            keys.put(SystemSceneKeys.LOGISTICS_TANK_INTRO,
                    "这是 %s——一台流体储存多方块，是物流管网中流体侧的枢纽。");
            keys.put(SystemSceneKeys.LOGISTICS_PUMP_INTRO,
                    "这是 %s——它把世界中的流体泵入管道与储罐。");
            keys.put(SystemSceneKeys.LOGISTICS_CONTROLLER,
                    "控制器让结构成为可用的机器，之后管网才能接入并工作。");
            keys.put(SystemSceneKeys.LOGISTICS_ITEM_PIPES,
                    "物品管道在容器之间搬运物品：用传送带模块从源头抽取，再送往目的地。");
            keys.put(SystemSceneKeys.LOGISTICS_FLUID_PIPES,
                    "流体管道搬运流体；大口径管道吞吐更高，泵覆盖板 / 阀门负责进出。");
            keys.put(SystemSceneKeys.LOGISTICS_CABLES,
                    "线缆在发电机、变压器与机器之间输送 EU——按电压与安培数选用合适的线缆。");
            keys.put(SystemSceneKeys.LOGISTICS_COVERS,
                    "覆盖板可装在机器与管道上：传送带（物品）、泵（流体）、机器控制器（自动进出）等。");
            return;
        }
        keys.put(SystemSceneKeys.POWER_COMBUSTION_TITLE, "Power grid: Large Combustion Engine");
        keys.put(SystemSceneKeys.POWER_TRANSFORMER_TITLE, "Power grid: Active Transformer");
        keys.put(SystemSceneKeys.POWER_COMBUSTION_INTRO,
                "This is %s - a generator that burns fuel to produce EU for the power grid.");
        keys.put(SystemSceneKeys.POWER_TRANSFORMER_INTRO,
                "This is %s - it steps energy between voltage tiers so machines join the right grid.");
        keys.put(SystemSceneKeys.POWER_CONTROLLER,
                "The controller is the front-center block: supply the correct voltage to form the structure.");
        keys.put(SystemSceneKeys.POWER_VOLTAGE,
                "GT power comes in voltage tiers (ULV, LV, MV, HV...). A machine only accepts its own tier; "
                        + "a transformer converts between tiers.");
        keys.put(SystemSceneKeys.POWER_OVERVOLTAGE,
                "Overvoltage: feeding a machine a tier above its rating quadruples its energy cost and can "
                        + "damage or destroy it.");
        keys.put(SystemSceneKeys.POWER_BURNING,
                "Cable burning: a cable carrying more current than its gauge allows burns out - size the "
                        + "cable (1x / 2x / 4x...) to the amperage.");
        keys.put(SystemSceneKeys.LOGISTICS_TANK_TITLE, "Logistics: Steel Multiblock Tank");
        keys.put(SystemSceneKeys.LOGISTICS_PUMP_TITLE, "Logistics: Primitive Pump");
        keys.put(SystemSceneKeys.LOGISTICS_TANK_INTRO,
                "This is %s - a fluid storage multiblock; it anchors the fluid side of your logistics network.");
        keys.put(SystemSceneKeys.LOGISTICS_PUMP_INTRO,
                "This is %s - it pumps fluids out of the world into pipes and tanks.");
        keys.put(SystemSceneKeys.LOGISTICS_CONTROLLER,
                "The controller turns the structure into a working machine; only then can the network connect.");
        keys.put(SystemSceneKeys.LOGISTICS_ITEM_PIPES,
                "Item pipes move items between inventories: use a conveyor module to extract from a source "
                        + "and route to the destination.");
        keys.put(SystemSceneKeys.LOGISTICS_FLUID_PIPES,
                "Fluid pipes move fluids; larger pipes have higher throughput, and a pump cover or valve "
                        + "lets fluid in or out.");
        keys.put(SystemSceneKeys.LOGISTICS_CABLES,
                "Cables carry EU between generators, transformers and machines - pick a cable for the "
                        + "voltage and amperage you need.");
        keys.put(SystemSceneKeys.LOGISTICS_COVERS,
                "Covers mount on machines and pipes: conveyor (items), pump (fluids), machine controller "
                        + "(auto I/O) and more.");
    }

    /**
     * 游戏内编辑器的界面 / 状态键（中英双语）：屏幕标题、录制动作、属性字段、控制按钮、状态与门控提示，
     * 以及「录制旁白」动作写入的默认旁白键。键集中定义在 {@link EditorKeys}。
     */
    private static void editorKeys(Map<String, String> keys, boolean chinese) {
        if (chinese) {
            keys.put(EditorKeys.TITLE, "思索编辑器");
            keys.put(EditorKeys.RECORD, "录制");
            keys.put(EditorKeys.ACTION_SHOW, "显示分段");
            keys.put(EditorKeys.ACTION_HIDE, "隐藏分段");
            keys.put(EditorKeys.ACTION_HIGHLIGHT, "高亮");
            keys.put(EditorKeys.ACTION_NARRATE, "旁白");
            keys.put(EditorKeys.ACTION_CAMERA, "相机");
            keys.put(EditorKeys.SECTION_HEADER, "场景头部");
            keys.put(EditorKeys.SECTION_RECORD, "录制动作");
            keys.put(EditorKeys.SECTION_STEPS, "步骤");
            keys.put(EditorKeys.SECTION_PROPERTIES, "属性");
            keys.put(EditorKeys.FIELD_ID, "ID");
            keys.put(EditorKeys.FIELD_TITLE, "标题");
            keys.put(EditorKeys.FIELD_TARGET, "目标");
            keys.put(EditorKeys.FIELD_DURATION, "时长");
            keys.put(EditorKeys.FIELD_NARRATION, "旁白键");
            keys.put(EditorKeys.FIELD_YAW, "偏航");
            keys.put(EditorKeys.FIELD_PITCH, "俯仰");
            keys.put(EditorKeys.FIELD_DISTANCE, "距离");
            keys.put(EditorKeys.STEP_PREV, "<");
            keys.put(EditorKeys.STEP_NEXT, ">");
            keys.put(EditorKeys.STEP_NONE, "（无步骤）");
            keys.put(EditorKeys.SAVE, "保存");
            keys.put(EditorKeys.EXPORT, "导出生成");
            keys.put(EditorKeys.RELOAD, "保存并重载重放");
            keys.put(EditorKeys.CLOSE, "关闭");
            keys.put(EditorKeys.STATUS_READY, "就绪");
            keys.put(EditorKeys.STATUS_SAVED, "已保存：%s");
            keys.put(EditorKeys.STATUS_SAVE_FAILED, "保存失败：%s");
            keys.put(EditorKeys.STATUS_EXPORTED, "已载入自动生成基线：%s");
            keys.put(EditorKeys.STATUS_RECORD_ON, "录制已开启：动作将写入场景");
            keys.put(EditorKeys.STATUS_RECORD_OFF, "录制未开启（请先勾选「录制」）");
            keys.put(EditorKeys.STATUS_RECORDED, "已录制 %s 步（共 %s 步）");
            keys.put(EditorKeys.GATED, "编辑器仅作者 / 开发可用（正式玩家不可见）。");
            keys.put(EditorKeys.RECORD_NARRATION, "作者录制的旁白：在此填写讲解内容。");
            return;
        }
        keys.put(EditorKeys.TITLE, "Ponder Editor");
        keys.put(EditorKeys.RECORD, "Record");
        keys.put(EditorKeys.ACTION_SHOW, "Show section");
        keys.put(EditorKeys.ACTION_HIDE, "Hide section");
        keys.put(EditorKeys.ACTION_HIGHLIGHT, "Highlight");
        keys.put(EditorKeys.ACTION_NARRATE, "Narrate");
        keys.put(EditorKeys.ACTION_CAMERA, "Camera");
        keys.put(EditorKeys.SECTION_HEADER, "Scene header");
        keys.put(EditorKeys.SECTION_RECORD, "Record actions");
        keys.put(EditorKeys.SECTION_STEPS, "Steps");
        keys.put(EditorKeys.SECTION_PROPERTIES, "Properties");
        keys.put(EditorKeys.FIELD_ID, "ID");
        keys.put(EditorKeys.FIELD_TITLE, "Title");
        keys.put(EditorKeys.FIELD_TARGET, "Target");
        keys.put(EditorKeys.FIELD_DURATION, "Duration");
        keys.put(EditorKeys.FIELD_NARRATION, "Narration key");
        keys.put(EditorKeys.FIELD_YAW, "Yaw");
        keys.put(EditorKeys.FIELD_PITCH, "Pitch");
        keys.put(EditorKeys.FIELD_DISTANCE, "Distance");
        keys.put(EditorKeys.STEP_PREV, "<");
        keys.put(EditorKeys.STEP_NEXT, ">");
        keys.put(EditorKeys.STEP_NONE, "(no steps)");
        keys.put(EditorKeys.SAVE, "Save");
        keys.put(EditorKeys.EXPORT, "Export generated");
        keys.put(EditorKeys.RELOAD, "Save + reload + replay");
        keys.put(EditorKeys.CLOSE, "Close");
        keys.put(EditorKeys.STATUS_READY, "Ready");
        keys.put(EditorKeys.STATUS_SAVED, "Saved: %s");
        keys.put(EditorKeys.STATUS_SAVE_FAILED, "Save failed: %s");
        keys.put(EditorKeys.STATUS_EXPORTED, "Loaded generated baseline: %s");
        keys.put(EditorKeys.STATUS_RECORD_ON, "Recording on: actions become steps");
        keys.put(EditorKeys.STATUS_RECORD_OFF, "Recording is off (enable the Record toggle first)");
        keys.put(EditorKeys.STATUS_RECORDED, "Recorded %s step(s) (scene has %s)");
        keys.put(EditorKeys.GATED, "The editor is available to authors / developers only.");
        keys.put(EditorKeys.RECORD_NARRATION, "Author-recorded narration: describe the step here.");
    }
}
