package com.gtsn.ponder.datagen;

import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.catalog.SingleBlockScenes;
import com.gtsn.ponder.content.SystemSceneKeys;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.GtTierNames;
import com.gtsn.ponder.generate.SceneGenerator;
import com.gtsn.ponder.generate.SingleBlockUsageGenerator;
import com.gtsn.ponder.gt.GtMultiblockCatalog;
import com.gtsn.ponder.structure.StructureRole;

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
        keys.put("ponder.gtsnponder.message.no_item",
                "No ponderable item under the cursor (hover a GT machine item in your inventory / JEI / EMI).");
        keys.put("ponder.gtsnponder.message.no_scene", "No ponder scene is registered for %s.");
        keys.put("ponder.gtsnponder.message.no_world", "Join a world before opening ponder.");
        keys.put("ponder.gtsnponder.message.dump", "Generated scene written to %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "Could not write the generated scene: %s");
        keys.put(CatalogKeys.MACHINE_ITEM_TOOLTIP, "[%s] Ponder");
        editorKeys(keys, false);
        catalogKeys(keys, false);
        systemSceneKeys(keys, false);
        usageSceneKeys(keys, false);
        roleKeys(keys, false);
        tierKeys(keys, false);
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
        keys.put(SceneGenerator.NARRATION_HATCHES, "%s 个仓口 / 总线 (%s)。");
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
                "%s(%s)成型演示：本机 %s 个仓口 / 总线 (%s)、%s 个模块位。先隐藏再重现，即未成型 → 成型。");

        keys.put(GeneratedKeys.LEGEND_TITLE, "图例");
        keys.put(GeneratedKeys.LEGEND_CONTROLLER, "控制器（金色）");
        keys.put(GeneratedKeys.LEGEND_HATCH, "仓口 / 总线（蓝色）");
        keys.put(GeneratedKeys.LEGEND_MODULE_SLOT, "模块位（绿色）");

        keys.put("ponder.gtsnponder.message.no_target", "视线内没有格雷科技机器 / 多方块（需在触及范围内）。");
        keys.put("ponder.gtsnponder.message.no_item", "鼠标下没有可思索的物品（把光标移到背包 / JEI / EMI 中的格雷科技机器物品上）。");
        keys.put("ponder.gtsnponder.message.no_scene", "%s 暂无思索场景。");
        keys.put("ponder.gtsnponder.message.no_world", "请先进入世界再打开思索。");
        keys.put("ponder.gtsnponder.message.dump", "已把生成场景写入 %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "写入生成场景失败：%s");
        keys.put(CatalogKeys.MACHINE_ITEM_TOOLTIP, "[%s] 思索");
        editorKeys(keys, true);
        catalogKeys(keys, true);
        systemSceneKeys(keys, true);
        usageSceneKeys(keys, true);
        roleKeys(keys, true);
        tierKeys(keys, true);
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
                    "格雷科技的电按电压等级划分(ULV、LV、MV、HV……)。机器只接受自己那一级，变压器负责在等级之间转换。");
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
                    "覆盖板可装在机器与管道上：传送带(物品)、泵(流体)、机器控制器(自动进出)等。");
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
     * 单方块机器「使用场景」（工单 #15）的文案键（中英双语）：使用流程模板（本体 / 输入 / 输出 /
     * 能量 / 进度 / 覆盖板 / 常见坑）与两台精选手作机器的讲解。模板键集中定义在
     * {@link SingleBlockUsageGenerator}，手作键集中在 {@link SingleBlockScenes}。
     */
    private static void usageSceneKeys(Map<String, String> keys, boolean chinese) {
        if (chinese) {
            keys.put(SingleBlockUsageGenerator.NARRATION_INTRO,
                    "这是 %s(%s)，配方类型：%s。下面讲解如何使用它。");
            keys.put(SingleBlockUsageGenerator.NARRATION_MACHINE,
                    "%s 本身就是整台机器——所有部件都在这一个方块里。");
            keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS,
                    "输入：%s 个物品槽、%s 个流体罐。把配方原料放进来。");
            keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS_NONE,
                    "这台机器没有物品 / 流体输入(%s 个物品槽、%s 个流体罐)，它只对自身工作。");
            keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS,
                    "输出：%s 个物品槽、%s 个流体罐。从这里取出产物。");
            keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS_NONE,
                    "这台机器没有物品 / 流体输出(%s 个物品槽、%s 个流体罐)。");
            keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY,
                    "能量：它的电压等级是 %s。接上匹配的线缆或发电机即可供电。");
            keys.put(SingleBlockUsageGenerator.NARRATION_PROGRESS,
                    "配方运行时进度条会填充；若停滞，说明在等待原料或电力。");
            keys.put(SingleBlockUsageGenerator.NARRATION_COVERS,
                    "覆盖板可装在机器侧面：传送带(物品)、泵(流体)、机器控制器(自动进出)等。");
            keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS,
                    "常见坑：通入 %s 或更高电压会损坏机器；产物槽满则处理停止。");
            keys.put(SingleBlockScenes.HAND_STEAM_FURNACE_INTRO,
                    "这是低压蒸汽熔炉——一台靠蒸汽驱动的青铜时代熔炉。");
            keys.put(SingleBlockScenes.HAND_STEAM_FURNACE_USAGE,
                    "放入矿石或食物并供应充足的蒸汽：它一次熔炼一个物品，不需要电力线缆。");
            keys.put(SingleBlockScenes.HAND_LV_MACERATOR_INTRO,
                    "这是基础粉碎机——把矿石磨成粉的 LV 主力机器。");
            keys.put(SingleBlockScenes.HAND_LV_MACERATOR_USAGE,
                    "把矿石放进输入槽，通入 LV 电力，再从输出槽取出粉末。");
            return;
        }
        keys.put(SingleBlockUsageGenerator.NARRATION_INTRO,
                "This is %s (%s). Recipe types: %s. Here is how to use it.");
        keys.put(SingleBlockUsageGenerator.NARRATION_MACHINE,
                "%s is the machine itself - the whole machine fits in this one block.");
        keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS,
                "Inputs: %s item slot(s) and %s fluid tank(s). Insert the recipe ingredients here.");
        keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS_NONE,
                "This machine takes no item or fluid inputs (%s item slot(s), %s fluid tank(s)); "
                        + "it works on its own.");
        keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS,
                "Outputs: %s item slot(s) and %s fluid tank(s). Collect the products here.");
        keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS_NONE,
                "This machine produces no item or fluid outputs (%s item slot(s), %s fluid tank(s)).");
        keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY,
                "Energy: its voltage tier is %s. Connect a matching cable or generator.");
        keys.put(SingleBlockUsageGenerator.NARRATION_PROGRESS,
                "While a recipe runs the progress bar fills; if it stalls it is waiting for inputs or power.");
        keys.put(SingleBlockUsageGenerator.NARRATION_COVERS,
                "Covers mount on the machine's sides: conveyor (items), pump (fluids), machine controller "
                        + "(auto I/O) and more.");
        keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS,
                "Common pitfalls: %s or higher voltage destroys the machine, and a full output slot stops "
                        + "processing.");
        keys.put(SingleBlockScenes.HAND_STEAM_FURNACE_INTRO,
                "This is the Low Pressure Steam Furnace - a bronze-age smelter that runs on steam.");
        keys.put(SingleBlockScenes.HAND_STEAM_FURNACE_USAGE,
                "Feed in ore or food and plenty of steam: it smelts one item at a time and needs no power cable.");
        keys.put(SingleBlockScenes.HAND_LV_MACERATOR_INTRO,
                "This is the Basic Macerator - the LV workhorse that grinds ores into dusts.");
        keys.put(SingleBlockScenes.HAND_LV_MACERATOR_USAGE,
                "Put ore in the input slot, give it LV power, and pull the dust from the output slot.");
    }

    /**
     * 仓口 / 总线角色的本地化名（工单 #16 缺陷 A2）：自动生成旁白原本直接渲染 {@code StructureRole}
     * 的枚举名（{@code ENERGY_INPUT} / {@code MAINTENANCE} / {@code OTHER_HATCH}），本方法为每个角色
     * 产出中英键（{@link GeneratedKeys#roleKey(String)}），播放屏经 {@code NarrationLocalization} 解析。
     */
    private static void roleKeys(Map<String, String> keys, boolean chinese) {
        for (StructureRole role : StructureRole.values()) {
            String key = GeneratedKeys.roleKey(role.name());
            keys.put(key, chinese ? roleNameZh(role) : roleNameEn(role));
        }
    }

    /**
     * GT 电压层级名（工单 #17 缺陷 B）：单方块使用场景旁白原本露出层级短码（{@code OpV} / {@code LV}），
     * 本方法为 {@link GtTierNames} 的每个层级产出中英键（{@link GeneratedKeys#tierKey(String)}），
     * 播放屏经 {@code NarrationLocalization} 解析。
     */
    private static void tierKeys(Map<String, String> keys, boolean chinese) {
        for (GtTierNames.Tier tier : GtTierNames.ALL) {
            keys.put(GeneratedKeys.tierKey(tier.code()), chinese ? tier.zh() : tier.en());
        }
    }

    private static String roleNameZh(StructureRole role) {
        return switch (role) {
            case PLAIN -> "普通方块";
            case CONTROLLER -> "控制器";
            case ITEM_INPUT -> "物品输入总线";
            case ITEM_OUTPUT -> "物品输出总线";
            case FLUID_INPUT -> "流体输入仓";
            case FLUID_OUTPUT -> "流体输出仓";
            case ENERGY_INPUT -> "能量输入仓";
            case ENERGY_OUTPUT -> "能量输出仓";
            case MUFFLER -> "消声仓";
            case MAINTENANCE -> "维护仓";
            case PASSTHROUGH -> "直通仓";
            case OTHER_HATCH -> "其它仓口";
        };
    }

    private static String roleNameEn(StructureRole role) {
        return switch (role) {
            case PLAIN -> "Plain block";
            case CONTROLLER -> "Controller";
            case ITEM_INPUT -> "Item input bus";
            case ITEM_OUTPUT -> "Item output bus";
            case FLUID_INPUT -> "Fluid input hatch";
            case FLUID_OUTPUT -> "Fluid output hatch";
            case ENERGY_INPUT -> "Energy input hatch";
            case ENERGY_OUTPUT -> "Energy output hatch";
            case MUFFLER -> "Muffler hatch";
            case MAINTENANCE -> "Maintenance hatch";
            case PASSTHROUGH -> "Passthrough hatch";
            case OTHER_HATCH -> "Other hatch";
        };
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
