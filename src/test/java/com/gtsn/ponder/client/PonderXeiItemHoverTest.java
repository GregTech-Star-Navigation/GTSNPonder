package com.gtsn.ponder.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XEI 悬停物品登记缝测试（纯 Java，零 MC，工单 #16 缺陷 C）：JEI/EMI 集成类注册来源，快捷键入口只查询
 * 本登记缝。锁定「多来源按序取首个非空 / 空与异常来源被跳过 / 无来源即空」的可失败行为，使无法 headless
 * 启动 JEI/EMI 的集成本体仍有自动化覆盖。
 */
class PonderXeiItemHoverTest {

    @AfterEach
    void reset() {
        PonderXeiItemHover.get().reset();
    }

    @Test
    void returnsEmptyWhenNoSourceIsRegistered() {
        assertFalse(PonderXeiItemHover.get().hasSources());
        assertTrue(PonderXeiItemHover.get().hoveredItemId().isEmpty());
    }

    @Test
    void returnsTheFirstNonEmptyHoveredItem() {
        PonderXeiItemHover.get().register(Optional::empty);
        PonderXeiItemHover.get().register(() -> Optional.of("gtceu:lp_steam_furnace"));

        assertEquals(Optional.of("gtceu:lp_steam_furnace"), PonderXeiItemHover.get().hoveredItemId());
        assertEquals(2, PonderXeiItemHover.get().sourceCount());
    }

    @Test
    void blankItemsAreSkipped() {
        PonderXeiItemHover.get().register(() -> Optional.of("  "));
        PonderXeiItemHover.get().register(() -> Optional.of("gtceu:lv_macerator"));

        assertEquals(Optional.of("gtceu:lv_macerator"), PonderXeiItemHover.get().hoveredItemId());
    }

    @Test
    void aFailingSourceDoesNotBreakTheEntry() {
        PonderXeiItemHover.get().register(() -> {
            throw new IllegalStateException("XEI runtime not ready");
        });
        PonderXeiItemHover.get().register(() -> Optional.of("gtceu:coke_oven"));

        assertEquals(Optional.of("gtceu:coke_oven"), PonderXeiItemHover.get().hoveredItemId());
    }

    @Test
    void unregisterRemovesTheSource() {
        PonderXeiItemHover.Source source = () -> Optional.of("gtceu:coke_oven");
        PonderXeiItemHover.get().register(source);
        PonderXeiItemHover.get().unregister(source);

        assertFalse(PonderXeiItemHover.get().hasSources());
        assertTrue(PonderXeiItemHover.get().hoveredItemId().isEmpty());
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        PonderXeiItemHover.Source source = () -> Optional.of("gtceu:coke_oven");
        PonderXeiItemHover.get().register(source);
        PonderXeiItemHover.get().register(source);

        assertEquals(1, PonderXeiItemHover.get().sourceCount());
    }
}
