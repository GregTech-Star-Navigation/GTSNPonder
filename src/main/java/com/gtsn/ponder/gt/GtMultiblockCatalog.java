package com.gtsn.ponder.gt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多方块目录：枚举全部已注册的多方块定义，给出稳定 id 与<b>中英显示名</b>。
 *
 * <p>属于唯一允许 import {@code com.gregtechceu} 的适配包 {@code com.gtsn.ponder.gt}；datagen 的
 * 本地化 provider 只依赖本类的中立 {@link Multiblock} 记录来批量产出机器标题键，绝不直接触碰 GT 类型。
 * 枚举顺序按 id 排序，保证 datagen 产物字节稳定（可 diff）。</p>
 *
 * <p><b>中英名来源</b>：优先读取 GT 随包资源 {@code assets/gtceu/lang/{en_us,zh_cn}.json} 的
 * {@code block.gtceu.<path>} 键（GT 自带 datagen 产出的权威名称）；英文再回退
 * {@link MachineDefinition#getLangValue()}，最后回退 id；中文回退英文名。资源缺失 / 解析失败一律静默
 * 降级，绝不抛错。</p>
 */
public final class GtMultiblockCatalog {

    /** 一个多方块条目：稳定 id + 英文名 + 中文名。 */
    public record Multiblock(String id, String englishName, String chineseName) {

        /** 按 locale 选择显示名（非 {@code zh_cn} 一律英文）。 */
        public String name(String locale) {
            return "zh_cn".equals(locale) ? chineseName : englishName;
        }
    }

    private static final String EN_LANG_RESOURCE = "assets/gtceu/lang/en_us.json";
    private static final String ZH_LANG_RESOURCE = "assets/gtceu/lang/zh_cn.json";

    private static volatile Map<String, String> englishLang;
    private static volatile Map<String, String> chineseLang;

    private GtMultiblockCatalog() {
    }

    /** 按 id 排序的全部多方块定义（GT 缺席 / 未注册时返回空列表）。 */
    public static List<Multiblock> all() {
        List<Multiblock> result = new ArrayList<>();
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            if (!(definition instanceof MultiblockMachineDefinition) || definition.getId() == null) {
                continue;
            }
            String id = definition.getId().toString();
            String blockKey = "block." + definition.getId().getNamespace() + "." + definition.getId().getPath();
            String english = firstNonBlank(englishLang().get(blockKey), definition.getLangValue(), id);
            result.add(new Multiblock(id, english, firstNonBlank(chineseLang().get(blockKey), english)));
        }
        result.sort(Comparator.comparing(Multiblock::id));
        return List.copyOf(result);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private static Map<String, String> englishLang() {
        Map<String, String> cached = englishLang;
        if (cached == null) {
            synchronized (GtMultiblockCatalog.class) {
                if (englishLang == null) {
                    englishLang = loadLang(EN_LANG_RESOURCE);
                }
                cached = englishLang;
            }
        }
        return cached;
    }

    private static Map<String, String> chineseLang() {
        Map<String, String> cached = chineseLang;
        if (cached == null) {
            synchronized (GtMultiblockCatalog.class) {
                if (chineseLang == null) {
                    chineseLang = loadLang(ZH_LANG_RESOURCE);
                }
                cached = chineseLang;
            }
        }
        return cached;
    }

    private static Map<String, String> loadLang(String resource) {
        Map<String, String> result = new HashMap<>();
        try (InputStream stream = GtMultiblockCatalog.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return Map.of();
            }
            JsonObject object = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return result;
    }
}
