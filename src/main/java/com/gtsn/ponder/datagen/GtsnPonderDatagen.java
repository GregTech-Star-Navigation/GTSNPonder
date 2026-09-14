package com.gtsn.ponder.datagen;

import com.gtsn.ponder.GTSNPonder;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * datagen 接线：把 {en_us, zh_cn} 语言 provider 注册进 Forge 数据生成流水线
 * （{@code .\gradlew.bat runData} → {@code src/generated/resources/}）。
 *
 * <p>本类只在模组事件总线上监听 {@link GatherDataEvent}；正式游戏不执行。GT 枚举经
 * {@code com.gtsn.ponder.gt.GtMultiblockCatalog} 间接完成，本类不 import {@code com.gregtechceu}。</p>
 */
@Mod.EventBusSubscriber(modid = GTSNPonder.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GtsnPonderDatagen {

    private GtsnPonderDatagen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (!event.includeClient()) {
            return;
        }
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(true, new GtsnPonderLanguageProvider(output, GTSNPonder.MODID, "en_us"));
        generator.addProvider(true, new GtsnPonderLanguageProvider(output, GTSNPonder.MODID, "zh_cn"));
    }
}
