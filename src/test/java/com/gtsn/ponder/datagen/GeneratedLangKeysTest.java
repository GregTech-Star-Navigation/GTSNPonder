package com.gtsn.ponder.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.generate.SceneGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * datagen 产物守卫：断言 {@code runData} 生成的中英 lang 文件（提交在
 * {@code src/generated/resources/}）覆盖自动文案的全部模板键（含计数模板）与颜色图例键，并至少
 * 枚举出一台多方块的机器标题键——防止「改了生成器却忘了重跑 datagen」的文案漂移。
 *
 * <p>断言的是已提交产物（外部可观察），不触碰 provider 内部实现。</p>
 */
class GeneratedLangKeysTest {

    private static final Path LANG_DIR = Path.of("src", "generated", "resources", "assets", "gtsnponder", "lang");
    private static final Path EN = LANG_DIR.resolve("en_us.json");
    private static final Path ZH = LANG_DIR.resolve("zh_cn.json");

    private static final List<String> NARRATION_KEYS = List.of(
            SceneGenerator.NARRATION_INTRO,
            SceneGenerator.NARRATION_CONTROLLER,
            SceneGenerator.NARRATION_CONTROLLER_NONE,
            SceneGenerator.NARRATION_HATCHES,
            SceneGenerator.NARRATION_HATCHES_NONE,
            SceneGenerator.NARRATION_MODULES,
            SceneGenerator.NARRATION_MODULES_NONE,
            SceneGenerator.NARRATION_MODULE_SLOT,
            SceneGenerator.NARRATION_MODULE_SLOT_ANY,
            SceneGenerator.NARRATION_MODULE_SLOT_NONE,
            SceneGenerator.NARRATION_MODULE_INSTALLED,
            SceneGenerator.NARRATION_MODULE_INSTALLED_NO_EFFECT,
            SceneGenerator.NARRATION_FORMED);

    private static final List<String> LEGEND_KEYS = List.of(
            GeneratedKeys.LEGEND_TITLE,
            GeneratedKeys.LEGEND_CONTROLLER,
            GeneratedKeys.LEGEND_HATCH,
            GeneratedKeys.LEGEND_MODULE_SLOT);

    /** GT 机器界面覆盖层入口（#12）的按钮文案键，须经 datagen 产出中英双语。 */
    private static final List<String> GT_MACHINE_OVERLAY_KEYS = List.of(
            com.gtsn.ponder.catalog.CatalogKeys.GT_MACHINE_OPEN,
            com.gtsn.ponder.catalog.CatalogKeys.GT_MACHINE_OPEN_SHORT);

    private static JsonObject read(Path path, String locale) throws IOException {
        assertTrue(Files.isRegularFile(path),
                "generated lang file missing for " + locale + " (run .\\gradlew.bat --offline runData): " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void bothLocalesExistAndCoverNarrationKeys() throws IOException {
        for (Map.Entry<String, JsonObject> locale : Map.of("en_us", read(EN, "en_us"), "zh_cn", read(ZH, "zh_cn"))
                .entrySet()) {
            for (String key : NARRATION_KEYS) {
                assertTrue(locale.getValue().has(key), locale.getKey() + " is missing narration key " + key);
                assertFalse(locale.getValue().get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test
    void bothLocalesCoverLegendKeys() throws IOException {
        for (Map.Entry<String, JsonObject> locale : Map.of("en_us", read(EN, "en_us"), "zh_cn", read(ZH, "zh_cn"))
                .entrySet()) {
            for (String key : LEGEND_KEYS) {
                assertTrue(locale.getValue().has(key), locale.getKey() + " is missing legend key " + key);
            }
        }
    }

    @Test
    void bothLocalesCoverGtMachineOverlayKeys() throws IOException {
        for (Map.Entry<String, JsonObject> locale : Map.of("en_us", read(EN, "en_us"), "zh_cn", read(ZH, "zh_cn"))
                .entrySet()) {
            for (String key : GT_MACHINE_OVERLAY_KEYS) {
                assertTrue(locale.getValue().has(key),
                        locale.getKey() + " is missing GT machine overlay key " + key);
                assertFalse(locale.getValue().get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test
    void countTemplatesCarryPlaceholders() throws IOException {
        JsonObject en = read(EN, "en_us");
        assertTrue(en.get(SceneGenerator.NARRATION_HATCHES).getAsString().contains("%s"),
                "hatch count template must carry a count placeholder");
        assertTrue(en.get(SceneGenerator.NARRATION_MODULES).getAsString().contains("%s"),
                "module-slot count template must carry a count placeholder");
    }

    @Test
    void enumeratesMultiblockMachineTitleKeys() throws IOException {
        JsonObject en = read(EN, "en_us");
        JsonObject zh = read(ZH, "zh_cn");

        boolean hasAnyMachineTitle = en.keySet().stream()
                .anyMatch(key -> key.startsWith(GeneratedKeys.MACHINE_TITLE_PREFIX));
        assertTrue(hasAnyMachineTitle,
                "datagen must enumerate multiblocks and emit at least one machine title key");

        String cokeOven = GeneratedKeys.machineTitleKey("gtceu:coke_oven");
        assertTrue(en.has(cokeOven), "missing machine title key " + cokeOven);
        assertFalse(en.get(cokeOven).getAsString().isBlank(), cokeOven);
        assertTrue(zh.has(cokeOven), "zh_cn missing machine title key " + cokeOven);

        for (JsonElement value : en.asMap().values()) {
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                    "all lang values must be strings");
        }
    }
}
