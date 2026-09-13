package com.gtsn.ponder.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HatchClassifier} 的外部可观察行为：仅凭方块注册名把「仓口 / 总线」类方块识别为
 * {@link StructureRole#OTHER_HATCH}，绝不猜测具体 I/O 角色（那需要 GT 的 {@code PartAbility} 特征，
 * 由适配包在 GameTest 中验证）。纯 Java、零 MC 依赖。
 */
class HatchClassifierTest {

    @Test
    void classifiesHatchAndBusTokens() {
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("gtceu:coke_oven_hatch"));
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("gtceu:lv_input_bus"));
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("gtceu:muffler_hatch"));
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("gtceu:me_output_hatch"));
    }

    @Test
    void ignoresNonHatchNames() {
        assertEquals(StructureRole.PLAIN, HatchClassifier.classifyByName("gtceu:coke_oven"));
        assertEquals(StructureRole.PLAIN, HatchClassifier.classifyByName("minecraft:stone"));
        // 'busbar' is a single token, not a 'bus' token — must not be treated as a bus.
        assertEquals(StructureRole.PLAIN, HatchClassifier.classifyByName("gtceu:some_busbar"));
    }

    @Test
    void isCaseInsensitiveAndStripsNamespace() {
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("GTCEU:Coke_Oven_HATCH"));
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("hatch"));
        assertEquals(StructureRole.OTHER_HATCH, HatchClassifier.classifyByName("bus"));
    }

    @Test
    void handlesNullOrBlankAsPlain() {
        assertEquals(StructureRole.PLAIN, HatchClassifier.classifyByName(null));
        assertEquals(StructureRole.PLAIN, HatchClassifier.classifyByName("  "));
    }
}
