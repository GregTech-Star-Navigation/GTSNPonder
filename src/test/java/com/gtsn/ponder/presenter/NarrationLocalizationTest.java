package com.gtsn.ponder.presenter;

import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.structure.StructureRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旁白模板参数本地化测试（纯 Java，零 MC，工单 #16 缺陷 A1/A2）。
 *
 * <p>自动生成器把机器标识（如 {@code gtceu:coke_oven}）与仓口 / 总线角色名（如 {@code ENERGY_INPUT}）
 * 作为 {@code narrationArgs} 的<b>字面量</b>写入冻结数据（格式不变）。播放屏渲染时经本解析器把它们
 * 翻译为本地化键（机器标题键 / 角色键），键缺失时回退到原始字面量（「没有标题键的机器照常工作」）。</p>
 *
 * <p>以注入的 {@code keyExists} 断言可失败行为：不依赖 MC 的 I18n，也不需要 GT / MC 运行时。</p>
 */
class NarrationLocalizationTest {

    /** 假语言表：仅有标题键的一台机器 + 全部角色键存在，其它键不存在。 */
    private static boolean allKnownKeys(String key) {
        if (key.equals(GeneratedKeys.machineTitleKey("gtceu:coke_oven"))) {
            return true;
        }
        for (StructureRole role : StructureRole.values()) {
            if (key.equals(GeneratedKeys.roleKey(role.name()))) {
                return true;
            }
        }
        return false;
    }

    private static List<NarrationLocalization.Part> resolve(String arg, boolean keysExist) {
        return NarrationLocalization.resolveArg(arg, keysExist ? NarrationLocalizationTest::allKnownKeys : key -> false);
    }

    @Test
    void rawRegistryIdBecomesTheLocalizedMachineTitleKey() {
        List<NarrationLocalization.Part> parts = resolve("gtceu:coke_oven", true);

        assertEquals(List.of(NarrationLocalization.Part.key(GeneratedKeys.machineTitleKey("gtceu:coke_oven"))),
                parts, "formed narration must resolve the raw id to the machine title key");
    }

    @Test
    void machineWithoutATitleKeyKeepsTheRawId() {
        List<NarrationLocalization.Part> parts = resolve("gtceu:no_title_key", false);

        assertEquals(List.of(NarrationLocalization.Part.literal("gtceu:no_title_key")), parts,
                "machines without a title key must keep working and fall back to the raw id");
    }

    @Test
    void roleEnumNamesAreLocalized() {
        List<NarrationLocalization.Part> parts = resolve("ENERGY_INPUT, MAINTENANCE", true);

        assertEquals(List.of(
                NarrationLocalization.Part.key(GeneratedKeys.roleKey("ENERGY_INPUT")),
                NarrationLocalization.Part.literal(", "),
                NarrationLocalization.Part.key(GeneratedKeys.roleKey("MAINTENANCE"))),
                parts, "hatch/bus role names must be localized, keeping the list separator");
    }

    @Test
    void roleWithoutALangKeyKeepsTheEnumName() {
        List<NarrationLocalization.Part> parts = resolve("OTHER_HATCH", false);

        assertEquals(List.of(NarrationLocalization.Part.literal("OTHER_HATCH")), parts,
                "a role without a lang key must fall back to the enum name");
    }

    @Test
    void plainLiteralsAreUntouched() {
        assertEquals(List.of(NarrationLocalization.Part.literal("3x3x3")), resolve("3x3x3", true));
        assertEquals(List.of(NarrationLocalization.Part.literal("—")), resolve("—", true));
        assertEquals(List.of(NarrationLocalization.Part.literal("1,2,3")), resolve("1,2,3", true),
                "coordinate literals (no space after the comma) must not be split");
    }

    @Test
    void moduleIdsThatAreNotMachinesFallBackToTheRawId() {
        List<NarrationLocalization.Part> parts = resolve("gtsnponder:generic_module", true);

        // 键不存在于假语言表（MACHINE_TITLE_PREFIX 只覆盖真实机器），故回退原始 id。
        assertEquals(List.of(NarrationLocalization.Part.literal("gtsnponder:generic_module")), parts);
    }

    @Test
    void resolveArgsKeepsOneEntryPerTemplateArgument() {
        List<List<NarrationLocalization.Part>> args = NarrationLocalization.resolveArgs(
                List.of("gtceu:coke_oven", "3x3x3", "ENERGY_INPUT"), NarrationLocalizationTest::allKnownKeys);

        assertEquals(3, args.size(), "each %s slot must stay a single substituent");
        assertEquals(List.of(NarrationLocalization.Part.key(GeneratedKeys.machineTitleKey("gtceu:coke_oven"))),
                args.get(0));
        assertEquals(List.of(NarrationLocalization.Part.literal("3x3x3")), args.get(1));
        assertEquals(List.of(NarrationLocalization.Part.key(GeneratedKeys.roleKey("ENERGY_INPUT"))), args.get(2));
    }

    @Test
    void roleLookupKnowsEveryEnumNameAndRejectsOthers() {
        for (StructureRole role : StructureRole.values()) {
            assertTrue(StructureRole.fromName(role.name()).isPresent(), role.name());
        }
        assertTrue(StructureRole.fromName("NOT_A_ROLE").isEmpty());
        assertTrue(StructureRole.fromName("gtceu:coke_oven").isEmpty());
        assertFalse(StructureRole.fromName(null).isPresent());
    }

    @Test
    void everyRoleHasADistinctLangKey() {
        Set<String> keys = new java.util.HashSet<>();
        for (StructureRole role : StructureRole.values()) {
            assertTrue(keys.add(GeneratedKeys.roleKey(role.name())),
                    "duplicate role key for " + role.name());
        }
        assertEquals(StructureRole.values().length, keys.size());
    }
}
