package com.gtsn.ponder.datagen;

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
        keys.put(SceneGenerator.NARRATION_FORMED,
                "%s (%s) formed demo: %s hatch/bus block(s) (%s), %s module slot(s). "
                        + "Hidden then shown again: unformed -> formed.");

        keys.put(GeneratedKeys.LEGEND_TITLE, "Legend");
        keys.put(GeneratedKeys.LEGEND_CONTROLLER, "Controller (gold)");
        keys.put(GeneratedKeys.LEGEND_HATCH, "Hatch / bus (blue)");

        keys.put("ponder.gtsnponder.message.no_target", "No GT machine or multiblock in view (within reach).");
        keys.put("ponder.gtsnponder.message.no_scene", "No ponder scene is registered for %s.");
        keys.put("ponder.gtsnponder.message.no_world", "Join a world before opening ponder.");
        keys.put("ponder.gtsnponder.message.dump", "Generated scene written to %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "Could not write the generated scene: %s");
        return keys;
    }

    private static Map<String, String> chinese() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.categories.gtsnponder", "格雷科技·思索");
        keys.put("key.gtsnponder.ponder", "打开思索");

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
        keys.put(SceneGenerator.NARRATION_FORMED,
                "%s（%s）成型演示：本机 %s 个仓口 / 总线（%s）、%s 个模块位。先隐藏再重现，即未成型 → 成型。");

        keys.put(GeneratedKeys.LEGEND_TITLE, "图例");
        keys.put(GeneratedKeys.LEGEND_CONTROLLER, "控制器（金色）");
        keys.put(GeneratedKeys.LEGEND_HATCH, "仓口 / 总线（蓝色）");

        keys.put("ponder.gtsnponder.message.no_target", "视线内没有格雷科技机器 / 多方块（需在触及范围内）。");
        keys.put("ponder.gtsnponder.message.no_scene", "%s 暂无思索场景。");
        keys.put("ponder.gtsnponder.message.no_world", "请先进入世界再打开思索。");
        keys.put("ponder.gtsnponder.message.dump", "已把生成场景写入 %s");
        keys.put("ponder.gtsnponder.message.dump_failed", "写入生成场景失败：%s");
        return keys;
    }
}
