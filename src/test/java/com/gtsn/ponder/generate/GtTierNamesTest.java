package com.gtsn.ponder.generate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GT 电压层级名本地化表测试（纯 Java，零 MC / GT，工单 #17 缺陷 B）。
 *
 * <p>旁白里出现的层级短码（{@code OpV} / {@code LV}）不应裸露为不可读的缩写：本表把已知层级短码映射到
 * 本地化键（{@link GeneratedKeys#tierKey(String)}），随 datagen 产出中英双语名（对照 {@code GTValues.VN}）。</p>
 */
class GtTierNamesTest {

    @Test
    void knowsEveryGtVoltageTierCode() {
        // GTValues.VN 的 15 个层级短码（ULV..MAX）；缺失即旁白回退为裸露短码。
        for (String code : java.util.List.of("ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV",
                "UHV", "UEV", "UIV", "UXV", "OpV", "MAX")) {
            assertTrue(GtTierNames.byCode(code).isPresent(), "missing tier code " + code);
        }
        assertEquals(15, GtTierNames.ALL.size(), "GT has 15 voltage tiers");
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertEquals(GtTierNames.byCode("OpV"), GtTierNames.byCode("opv"));
        assertEquals(GtTierNames.byCode("LuV"), GtTierNames.byCode("LUV"));
    }

    @Test
    void unknownCodesAreEmptyAndKeyForIsSafe() {
        assertTrue(GtTierNames.byCode("T99").isEmpty());
        assertTrue(GtTierNames.byCode("").isEmpty());
        assertTrue(GtTierNames.byCode(null).isEmpty());
        assertEquals(Optional.empty(), GtTierNames.keyFor("T99"));
    }

    @Test
    void everyTierHasDistinctNonBlankLocalizedNamesAndKey() {
        Set<String> codes = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (GtTierNames.Tier tier : GtTierNames.ALL) {
            assertTrue(codes.add(tier.code()), "duplicate tier code " + tier.code());
            assertTrue(keys.add(GeneratedKeys.tierKey(tier.code())),
                    "duplicate tier key for " + tier.code());
            assertFalse(tier.en().isBlank(), tier.code() + " english name is blank");
            assertFalse(tier.zh().isBlank(), tier.code() + " chinese name is blank");
            assertEquals(Optional.of(GeneratedKeys.tierKey(tier.code())), GtTierNames.keyFor(tier.code()));
        }
    }
}
