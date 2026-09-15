package com.gtsn.ponder.datagen;

import com.gtsn.ponder.catalog.CatalogKeys;
import com.gtsn.ponder.catalog.SceneCategories;
import com.gtsn.ponder.catalog.SingleBlockScenes;
import com.gtsn.ponder.content.SystemSceneKeys;
import com.gtsn.ponder.editor.EditorKeys;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.GtTierNames;
import com.gtsn.ponder.generate.MachineDescriptions;
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
        // 旁白列表分隔符（工单 #18）：生成器写 ", "，渲染时替换为本键。
        keys.put(GeneratedKeys.LIST_SEPARATOR, ", ");

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

        keys.put(SceneGenerator.NARRATION_PURPOSE, "This is %s, a %s multiblock machine.");
        keys.put(SceneGenerator.NARRATION_SETUP,
                "The gold-outlined block is the controller - once its forming conditions are met "
                        + "(power, steam or fuel), the whole machine forms.");
        keys.put(SceneGenerator.NARRATION_SETUP_NONE,
                "This structure has no controller; just place every block as shown.");
        keys.put(SceneGenerator.NARRATION_INPUTS, "Feed the ingredients in through the %s.");
        keys.put(SceneGenerator.NARRATION_INPUTS_NONE, "This machine takes no external inputs.");
        keys.put(SceneGenerator.NARRATION_OUTPUTS, "The products come out of the %s.");
        keys.put(SceneGenerator.NARRATION_OUTPUTS_NONE, "This machine has no outputs of its own.");
        keys.put(SceneGenerator.NARRATION_ENERGY,
                "For power, connect a cable of the matching tier to the %s and it will run.");
        keys.put(SceneGenerator.NARRATION_ENERGY_NONE,
                "It does not hook up to power - steam or fuel-driven machines work through their own inputs.");
        keys.put(SceneGenerator.NARRATION_ENERGY_OUTPUT,
                "It is a generator: connect the output energy hatch to your grid to send the power out.");
        keys.put(SceneGenerator.NARRATION_PITFALLS,
                "Common pitfalls: a clogged output stops processing, keep the %s ready, and the voltage "
                        + "tier must match the machine.");
        keys.put(SceneGenerator.NARRATION_PITFALLS_NONE,
                "Common pitfalls: a clogged output stops processing, and the voltage tier must match the machine.");
        keys.put(SceneGenerator.NARRATION_MODULES,
                "This machine has %s module slot(s); fitting the right modules upgrades it.");
        keys.put(SceneGenerator.NARRATION_MODULES_NONE, "This machine has no module slots.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT, "Slot %s: accepts %s.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_ANY, "Slot %s: accepts any module.");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_NONE, "Slot %s: accepts no module.");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED,
                "Installed %s into slot %s - parallel %s, speed x%s, energy x%s, input x%s, "
                        + "output x%s, tier +%s.");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT,
                "Installed %s into slot %s - no recipe effects.");
        keys.put(SceneGenerator.NARRATION_FORMED,
                "%s (%s) is now formed - this is how it looks when running.");

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
        curatedDescriptions(keys, false);
        roleKeys(keys, false);
        tierKeys(keys, false);
        return keys;
    }

    private static Map<String, String> chinese() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.categories.gtsnponder", "格雷科技·思索");
        keys.put("key.gtsnponder.ponder", "打开思索");
        keys.put("key.gtsnponder.catalog", "打开思索图鉴");
        // 旁白列表分隔符（工单 #18）：中文用顿号，避免半角逗号堆砌。
        keys.put(GeneratedKeys.LIST_SEPARATOR, "、");

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

        keys.put(SceneGenerator.NARRATION_PURPOSE, "这是 %s——一台 %s 的多方块机器。");
        keys.put(SceneGenerator.NARRATION_SETUP,
                "金色高亮的是控制器——满足它的成型条件 (供电、蒸汽或燃料等) 后，整台机器就会成型。");
        keys.put(SceneGenerator.NARRATION_SETUP_NONE, "该结构没有控制器，按结构页把方块摆齐即可。");
        keys.put(SceneGenerator.NARRATION_INPUTS, "把原料送入%s。");
        keys.put(SceneGenerator.NARRATION_INPUTS_NONE, "这台机器不需要外部原料输入。");
        keys.put(SceneGenerator.NARRATION_OUTPUTS, "产物会从%s输出。");
        keys.put(SceneGenerator.NARRATION_OUTPUTS_NONE, "这台机器没有对外的产物输出。");
        keys.put(SceneGenerator.NARRATION_ENERGY, "供能：把对应等级的线缆接到%s上，它就能运转。");
        keys.put(SceneGenerator.NARRATION_ENERGY_NONE, "它不接电力——蒸汽或燃料驱动的机器靠各自的接口工作。");
        keys.put(SceneGenerator.NARRATION_ENERGY_OUTPUT, "它是一台发电机——把能量输出仓接到电网，就能把电力送出去。");
        keys.put(SceneGenerator.NARRATION_PITFALLS,
                "常见坑：输出口堵塞时加工会停止；%s要记得保持就绪；接入的电压等级要和机器匹配。");
        keys.put(SceneGenerator.NARRATION_PITFALLS_NONE,
                "常见坑：输出口堵塞时加工会停止；接入的电压等级要和机器匹配。");
        keys.put(SceneGenerator.NARRATION_MODULES, "这台机器有 %s 个模块位，装上匹配的模块可以强化它。");
        keys.put(SceneGenerator.NARRATION_MODULES_NONE, "这台机器没有模块位。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT, "槽 %s：接受 %s。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_ANY, "槽 %s：接受任意模块。");
        keys.put(SceneGenerator.NARRATION_MODULE_SLOT_NONE, "槽 %s：不接受任何模块。");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED,
                "安装 %s 到槽 %s——并行 %s、速度 ×%s、能耗 ×%s、输入 ×%s、输出 ×%s、等级 +%s。");
        keys.put(SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT, "安装 %s 到槽 %s——无配方效果。");
        keys.put(SceneGenerator.NARRATION_FORMED, "%s (%s) 已经成型——这就是它工作时的样子。");

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
        curatedDescriptions(keys, true);
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
            keys.put(SingleBlockUsageGenerator.NARRATION_PURPOSE,
                    "这是%s，一台%s的机器；它能处理%s配方。");
            keys.put(SingleBlockUsageGenerator.NARRATION_SETUP,
                    "%s 整台机器就是这一个方块——所有部件都在里面。");
            keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS,
                    "使用时把原料放进输入槽 (%s 个物品槽、%s 个流体罐)。");
            keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS_NONE,
                    "它没有物品 / 流体输入 (%s 个物品槽、%s 个流体罐)，只对自身工作。");
            keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS,
                    "产物会出现在输出槽 (%s 个物品槽、%s 个流体罐)，记得及时取走。");
            keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS_NONE,
                    "它没有物品 / 流体产物输出 (%s 个物品槽、%s 个流体罐)。");
            keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY,
                    "它用%s电力运行——接上对应等级的线缆或发电机即可。");
            keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY_NONE,
                    "它不接电力——蒸汽机器靠蒸汽驱动。");
            keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS,
                    "常见坑：输出槽满或原料不足时加工会停住；接入的电压等级要和机器匹配。");
            keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS_NONE,
                    "常见坑：输出槽满或原料不足时加工会停住，进度条会卡住不动。");
            keys.put(SingleBlockUsageGenerator.NARRATION_COVERS,
                    "进阶：给机器侧面装上覆盖板就能自动化——传送带搬物品、泵抽流体、机器控制器自动进出。");
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
        keys.put(SingleBlockUsageGenerator.NARRATION_PURPOSE,
                "This is %s, a %s machine; it handles %s recipes.");
        keys.put(SingleBlockUsageGenerator.NARRATION_SETUP,
                "%s is the whole machine - everything sits inside this single block.");
        keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS,
                "Put the ingredients into the input slots (%s item slot(s), %s fluid tank(s)).");
        keys.put(SingleBlockUsageGenerator.NARRATION_INPUTS_NONE,
                "It takes no item or fluid inputs (%s item slot(s), %s fluid tank(s)); it works on its own.");
        keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS,
                "The products appear in the output slots (%s item slot(s), %s fluid tank(s)); collect them.");
        keys.put(SingleBlockUsageGenerator.NARRATION_OUTPUTS_NONE,
                "It produces no item or fluid output (%s item slot(s), %s fluid tank(s)).");
        keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY,
                "It runs on %s power - connect a cable or generator of the matching tier.");
        keys.put(SingleBlockUsageGenerator.NARRATION_ENERGY_NONE,
                "It needs no power hookup - steam machines run on steam.");
        keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS,
                "Common pitfalls: a full output or an empty input stalls processing, and the voltage tier "
                        + "must match the machine.");
        keys.put(SingleBlockUsageGenerator.NARRATION_PITFALLS_NONE,
                "Common pitfalls: a full output or an empty input stalls processing and freezes the progress bar.");
        keys.put(SingleBlockUsageGenerator.NARRATION_COVERS,
                "Advanced: covers automate it - conveyor for items, pump for fluids, machine controller for auto I/O.");
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
     * 关键机器<b>手写教学解说</b>（工单 #18）：用途 / 怎么搭 / 输入 / 输出 / 供能 / 常见坑，中英双语。
     * 键由 {@link MachineDescriptions} 的登记表推导（{@link MachineDescriptions#key}），生成器命中登记
     * 表时优先使用，未命中回退结构化模板。文案事实以组织 fork 的 lang / 机器定义为准，不臆造格雷科技行为。
     */
    private static void curatedDescriptions(Map<String, String> keys, boolean chinese) {
        if (chinese) {
            // --- 多方块 ---------------------------------------------------------
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.PURPOSE,
                    "焦炉把煤炭炼成焦炭，同时副产杂酚油，是开局就能搭出来的第一台多方块。");
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.SETUP,
                    "用焦炉砖砌成 3x3x3 的空心方块并装上控制器即可成型；它既不需要通电，也不需要燃料。");
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.INPUTS,
                    "把煤炭从顶部或装料口放进中间的空腔。");
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.OUTPUTS,
                    "炼出的焦炭留在空腔里，杂酚油则存进机器自带的 32 桶内胆。");
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.ENERGY,
                    "它完全不用电，是蒸汽时代之前就能使用的原始机器。");
            desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.PITFALLS,
                    "常见坑：结构必须全部用焦炉砖，仓口最多装 5 个焦炉仓口；杂酚油内胆只有 32 桶容量，满了要记得排空。");

            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.PURPOSE,
                    "电力高炉能炼出铝合金、不锈钢、钛等高级材料，是中期最重要的熔炼设备。");
            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.SETUP,
                    "它靠线圈提供温度：控制器通电后成型，线圈等级越高能达到的炉温越高。");
            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.INPUTS,
                    "把矿石或原料从输入总线送进炉膛，也可以通入流体参与反应。");
            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.OUTPUTS,
                    "成品从输出总线取出，另外还有灰烬可以通过消声仓回收。");
            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.ENERGY,
                    "给能量输入仓接上对应等级的电力就能运行；电压等级越高，可支持的炉温也越高。");
            desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.PITFALLS,
                    "常见坑：一定要装消声仓，而且它正前方要留一格空气，否则炉子会出问题；线圈等级不够就炼不了需要更高温度的配方。");

            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.PURPOSE,
                    "大型化学反应釜用百分之百的能耗效率执行化学反应釜的配方，适合大规模化工。");
            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.SETUP,
                    "成型的关键是正中心那根聚四氟乙烯管道旁边恰好放一块白铜线圈。");
            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.INPUTS,
                    "原料从输入总线进入，最多能同时处理 3 种物品和 5 种流体。");
            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.OUTPUTS,
                    "产物从输出总线取出，最多支持 3 种物品和 4 种流体。");
            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.ENERGY,
                    "给能量输入仓接入电力即可；超频会同时提高速度和能耗。");
            desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.PITFALLS,
                    "常见坑：白铜线圈必须恰好一块，多了少了都成型不了；开启环境危害时它还会要求安全的工作环境。");

            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.PURPOSE,
                    "装配线是放大版的组装机，用来批量制造高级合成部件。");
            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.SETUP,
                    "它由可重复的切片组成，5 到 16 节都能成型，线越长一次能处理的配方越多。");
            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.INPUTS,
                    "输入总线要放在起始切片，最多能放进 16 种物品和 4 种流体。");
            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.OUTPUTS,
                    "产物从另一端末尾的输出总线取出，一次只输出一种物品。");
            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.ENERGY,
                    "给能量仓接入电力即可运行。");
            desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.PITFALLS,
                    "常见坑：它带有研究槽，很多配方要先用扫描仪研究出数据才能做，所以要准备好数据仓。");

            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.PURPOSE,
                    "超净间本身不加工东西，而是让放进去的机器能够执行需要无尘环境的配方。");
            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.SETUP,
                    "它是一个 5x5x5 到 15x15x15 的密闭房间，天花板要铺洁净过滤方块。");
            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.INPUTS,
                    "物品、流体和电力都通过墙上的直通仓、机壳或二极管进出。");
            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.OUTPUTS,
                    "房间本身不产出物品，产物由里面的机器负责。");
            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.ENERGY,
                    "它自己耗电很少：脏的时候约 30 EU/t，干净后约 4 EU/t。");
            desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.PITFALLS,
                    "常见坑：门最多开 4 个；发电机、消声仓、钻机和原始机器太脏，不能放进房间里面。");

            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.PURPOSE,
                    "蒸馏塔把原油这类流体分馏成多种产物，是石化产线和中后期的核心设备。");
            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.SETUP,
                    "它由一截一截的塔节组成，每层都要留一个输出仓口，才能把不同馏分分开收集。");
            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.INPUTS,
                    "原料流体从底层输入，一次处理一种流体。");
            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.OUTPUTS,
                    "上方各层的输出仓口会分别吐出不同馏分，最多支持 12 种流体。");
            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.ENERGY,
                    "给能量仓接入电力即可运行；塔越高能处理的配方越多。");
            desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.PITFALLS,
                    "常见坑：从第二层开始，每层必须恰好有一个输出仓口，否则无法成型。");

            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.PURPOSE,
                    "大型燃气涡轮烧掉可燃气体来发电，是 EV 时代的主力发电机。");
            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.SETUP,
                    "它需要一块涡轮转子装在转子支架上，还要配上齿轮箱机壳和消声仓。");
            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.INPUTS,
                    "把可燃气体从输入仓通进去。");
            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.OUTPUTS,
                    "它只产出电力，没有物品或流体产物。");
            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.ENERGY,
                    "它是 EV 级发电机，把能量输出仓接到电网上就能供电。");
            desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.PITFALLS,
                    "常见坑：涡轮转子会磨损，耐久耗尽就必须更换；消声仓前方要留一格空气，否则涡轮受阻、效率骤降。");

            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.PURPOSE,
                    "大型搅拌机把多种材料混成合金或混合物，适合大批量生产。");
            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.SETUP,
                    "按结构页搭好外壳和输入输出口，通电即可成型。");
            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.INPUTS,
                    "最多能同时投入 6 种物品和 2 种流体。");
            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.OUTPUTS,
                    "混合好的产物从输出口取出，一般是 1 种物品或 1 种流体。");
            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.ENERGY,
                    "接上对应等级的电力即可运行；装上并行控制仓还能一次处理更多。");
            desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.PITFALLS,
                    "常见坑：原料种类超过上限时就搅拌不起来；并行仓能提升吞吐，但也会加大能耗。");

            // --- 单方块 ---------------------------------------------------------
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.PURPOSE,
                    "基础研磨机把矿石磨成粉，是电力时代最常用的第一台加工机。");
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.SETUP,
                    "整台机器就是这一个方块，放下并接上电就能用。");
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.INPUTS,
                    "把矿石放进输入槽。");
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.OUTPUTS,
                    "磨好的粉从输出槽取出。");
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.ENERGY,
                    "它是 LV 机器，接上低压线缆或发电机即可。");
            desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.PITFALLS,
                    "常见坑：LV 和 MV 的研磨机只有 1 个输出槽，出不了副产物；想要副产物要升级到 HV 以上。");

            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.PURPOSE,
                    "基础离心机把混合的原料甩开、分离出不同成分，常用于提纯。");
            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.SETUP,
                    "整台机器就是这一个方块，放下并接上电就能用。");
            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.INPUTS,
                    "把要分离的物品或流体放进输入槽。");
            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.OUTPUTS,
                    "分离出的成分从输出槽取出，最多能得到 6 种物品和 6 种流体。");
            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.ENERGY,
                    "它是 LV 机器，接上低压线缆或发电机即可。");
            desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.PITFALLS,
                    "常见坑：一次分离出的产物很多，输出槽满了就会停工，要记得及时清空。");

            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.PURPOSE,
                    "基础压缩机把物品压成更致密的形式，比如把锭压成块。");
            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.SETUP,
                    "整台机器就是这一个方块，放下并接上电就能用。");
            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.INPUTS,
                    "把要压缩的物品放进输入槽。");
            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.OUTPUTS,
                    "压好的产物从输出槽取出。");
            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.ENERGY,
                    "它是 LV 机器，接上低压线缆或发电机即可。");
            desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.PITFALLS,
                    "常见坑：别把它和聚爆压缩机搞混——那台是消耗炸药的多方块，专门用来做特殊材料。");

            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.PURPOSE,
                    "基础搅拌机把多种原料混成合金或混合物。");
            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.SETUP,
                    "整台机器就是这一个方块，放下并接上电就能用。");
            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.INPUTS,
                    "最多能同时投入 6 种物品和 2 种流体。");
            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.OUTPUTS,
                    "搅拌好的产物从输出槽取出。");
            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.ENERGY,
                    "它是 LV 机器，接上低压线缆或发电机即可。");
            desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.PITFALLS,
                    "常见坑：同时投入的原料种类有上限，配方需要更多种类时要改用大型搅拌机。");

            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.PURPOSE,
                    "基础电解机用电把物品或流体分解成组成它的元素。");
            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.SETUP,
                    "整台机器就是这一个方块，放下并接上电就能用。");
            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.INPUTS,
                    "把要电解的物品或流体放进输入槽。");
            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.OUTPUTS,
                    "分解出的元素从输出槽取出，最多能得到 6 种物品和 6 种流体。");
            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.ENERGY,
                    "它是 LV 机器，接上低压线缆或发电机即可。");
            desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.PITFALLS,
                    "常见坑：电解产物种类多，输出槽很容易塞满，要留出足够的取出空间。");
            return;
        }

        // --- Multiblocks --------------------------------------------------------
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.PURPOSE,
                "The Coke Oven turns coal into coke and yields creosote as a by-product - your first multiblock.");
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.SETUP,
                "Build a hollow 3x3x3 out of Coke Oven Bricks and fit the controller; it needs no power and no fuel.");
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.INPUTS,
                "Drop coal into the hollow center from the top or through a loading hatch.");
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.OUTPUTS,
                "The coke stays in the chamber and creosote collects in the machine's built-in 32-bucket tank.");
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.ENERGY,
                "It uses no power at all - a primitive machine from before the steam age.");
        desc(keys, "gtceu:coke_oven", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: the structure must be all Coke Oven Bricks and at most 5 Coke Oven Hatches; "
                        + "the creosote tank holds only 32 buckets, so drain it when full.");

        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.PURPOSE,
                "The Electric Blast Furnace smelts advanced materials such as aluminium, stainless steel and "
                        + "titanium - a key mid-game machine.");
        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.SETUP,
                "It needs heating coils for temperature: power the controller to form it, and higher-tier coils "
                        + "allow higher furnace temperatures.");
        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.INPUTS,
                "Feed ores or ingredients through the input bus; fluids can also be piped in for the reaction.");
        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.OUTPUTS,
                "Collect the products from the output bus; ash can be recovered through the muffler hatch.");
        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.ENERGY,
                "Power the energy input hatch at the matching tier; a higher voltage supports higher furnace "
                        + "temperatures.");
        desc(keys, "gtceu:electric_blast_furnace", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: fit a muffler hatch with an air block in front of it, and low-tier coils "
                        + "cannot reach the temperatures some recipes need.");

        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.PURPOSE,
                "The Large Chemical Reactor runs Chemical Reactor recipes at 100% energy efficiency - built for "
                        + "bulk chemistry.");
        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.SETUP,
                "The key to forming it is exactly one Cupronickel Coil Block next to the PTFE pipe at the center.");
        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.INPUTS,
                "Feed ingredients through the input bus - up to 3 items and 5 fluids at once.");
        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.OUTPUTS,
                "Collect products from the output bus - up to 3 items and 4 fluids.");
        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.ENERGY,
                "Power the energy input hatch; overclocking raises both speed and energy use.");
        desc(keys, "gtceu:large_chemical_reactor", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: exactly one Cupronickel Coil is required - too many or too few will not form; "
                        + "with environmental hazards enabled it also demands a safe environment.");

        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.PURPOSE,
                "The Assembly Line is an oversized Assembler for mass-producing advanced crafting components.");
        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.SETUP,
                "It is built from repeatable slices - 5 to 16 of them - and a longer line handles more recipes.");
        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.INPUTS,
                "The input bus goes at the starting slice and takes up to 16 items and 4 fluids.");
        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.OUTPUTS,
                "Products come out of the output bus at the far end - one item per craft.");
        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.ENERGY,
                "Power the energy hatch to run it.");
        desc(keys, "gtceu:assembly_line", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: it has a research slot, so many recipes need data researched first - bring a "
                        + "data hatch.");

        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.PURPOSE,
                "The Cleanroom does not process anything itself - it lets machines placed inside run "
                        + "cleanroom-only recipes.");
        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.SETUP,
                "It is a sealed room from 5x5x5 to 15x15x15, with Cleanroom Filter casings in the ceiling.");
        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.INPUTS,
                "Items, fluids and power pass through passthrough hatches, Hulls or Diodes in the walls.");
        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.OUTPUTS,
                "The room produces nothing itself; the machines inside do the work.");
        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.ENERGY,
                "It draws very little power: about 30 EU/t while dirty and about 4 EU/t once clean.");
        desc(keys, "gtceu:cleanroom", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: at most 4 doors, and generators, mufflers, drills and primitive machines are "
                        + "too dirty to place inside.");

        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.PURPOSE,
                "The Distillation Tower fractionates fluids like oil into many products - the heart of "
                        + "petrochemistry.");
        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.SETUP,
                "It is built from repeated tower layers, and every layer needs its own output hatch so each "
                        + "fraction can be collected separately.");
        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.INPUTS,
                "Feed the source fluid in at the bottom - one fluid at a time.");
        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.OUTPUTS,
                "Each upper layer's output hatch yields a different fraction - up to 12 fluids.");
        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.ENERGY,
                "Power the energy hatch; a taller tower can run more recipes.");
        desc(keys, "gtceu:distillation_tower", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: starting from the second layer, every layer must have exactly one output "
                        + "hatch or it will not form.");

        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.PURPOSE,
                "The Large Gas Turbine burns flammable gas to produce EU - a mainstay generator of the EV age.");
        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.SETUP,
                "It needs a turbine rotor in the rotor holder, plus gearbox casings and a muffler hatch.");
        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.INPUTS,
                "Feed flammable gas into the input hatch.");
        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.OUTPUTS,
                "It only produces EU - no item or fluid output.");
        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.ENERGY,
                "It is an EV-tier generator; hook the output energy hatch to your grid.");
        desc(keys, "gtceu:gas_large_turbine", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: the turbine rotor wears out and must be replaced; leave an air block in front "
                        + "of the muffler or the turbine face gets obstructed and efficiency drops.");

        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.PURPOSE,
                "The Large Mixer blends materials into alloys and mixtures, made for bulk production.");
        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.SETUP,
                "Build the casing and I/O hatches as shown; power it to form.");
        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.INPUTS,
                "It accepts up to 6 items and 2 fluids at once.");
        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.OUTPUTS,
                "Collect the mixture from the output - usually one item or one fluid.");
        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.ENERGY,
                "Power it at the matching tier; adding a parallel control hatch processes more at once.");
        desc(keys, "gtceu:large_mixer", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: too many ingredient types will stall it; a parallel hatch boosts throughput "
                        + "but also energy use.");

        // --- Single blocks ------------------------------------------------------
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.PURPOSE,
                "The Basic Macerator grinds ores into dusts - the first workhorse of the electric age.");
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.SETUP,
                "The whole machine is this one block; place it and power it to use it.");
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.INPUTS,
                "Put the ore into the input slot.");
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.OUTPUTS,
                "Take the dust from the output slot.");
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.ENERGY,
                "It is an LV machine - connect a low-voltage cable or generator.");
        desc(keys, "gtceu:lv_macerator", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: LV and MV macerators have only one output slot and cannot produce by-products "
                        + "- go HV or higher for those.");

        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.PURPOSE,
                "The Basic Centrifuge spins mixed materials apart to separate their components.");
        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.SETUP,
                "The whole machine is this one block; place it and power it to use it.");
        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.INPUTS,
                "Put the item or fluid to separate into the input.");
        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.OUTPUTS,
                "Collect the separated parts from the outputs - up to 6 items and 6 fluids.");
        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.ENERGY,
                "It is an LV machine - connect a low-voltage cable or generator.");
        desc(keys, "gtceu:lv_centrifuge", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: separation yields many outputs, so a full output slot stalls it - keep them "
                        + "cleared.");

        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.PURPOSE,
                "The Basic Compressor squeezes items into denser forms, such as turning ingots into blocks.");
        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.SETUP,
                "The whole machine is this one block; place it and power it to use it.");
        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.INPUTS,
                "Put the item to compress into the input slot.");
        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.OUTPUTS,
                "Take the compressed product from the output slot.");
        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.ENERGY,
                "It is an LV machine - connect a low-voltage cable or generator.");
        desc(keys, "gtceu:lv_compressor", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: do not confuse it with the Implosion Compressor - that is an "
                        + "explosive-consuming multiblock for special materials.");

        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.PURPOSE,
                "The Basic Mixer blends several ingredients into alloys or mixtures.");
        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.SETUP,
                "The whole machine is this one block; place it and power it to use it.");
        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.INPUTS,
                "It accepts up to 6 items and 2 fluids at once.");
        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.OUTPUTS,
                "Take the mixture from the output slot.");
        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.ENERGY,
                "It is an LV machine - connect a low-voltage cable or generator.");
        desc(keys, "gtceu:lv_mixer", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: there is a cap on ingredient types; recipes needing more should use the "
                        + "Large Mixer.");

        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.PURPOSE,
                "The Basic Electrolyzer uses electricity to break items or fluids down into their elements.");
        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.SETUP,
                "The whole machine is this one block; place it and power it to use it.");
        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.INPUTS,
                "Put the item or fluid to electrolyze into the input.");
        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.OUTPUTS,
                "Collect the elements from the outputs - up to 6 items and 6 fluids.");
        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.ENERGY,
                "It is an LV machine - connect a low-voltage cable or generator.");
        desc(keys, "gtceu:lv_electrolyzer", MachineDescriptions.Field.PITFALLS,
                "Common pitfalls: electrolysis yields many products, so the outputs fill up quickly - leave "
                        + "room to collect them.");
    }

    /** 写入一条手写说明键（键由 {@link MachineDescriptions} 推导）。 */
    private static void desc(Map<String, String> keys, String machineId, MachineDescriptions.Field field,
            String text) {
        keys.put(MachineDescriptions.key(machineId, field), text);
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
