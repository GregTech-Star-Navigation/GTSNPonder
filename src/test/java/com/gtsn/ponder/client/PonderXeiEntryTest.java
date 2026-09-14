package com.gtsn.ponder.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JEI/EMI「思索」入口的点击交接逻辑测试（纯 Java，零 MC）：装饰器在渲染时登记
 * 「当前目标 + 按钮屏幕矩形」，屏幕点击处理器据此解析目标。本测试锁定该可失败行为，
 * 使 XEI 集成（无法 headless 启动 JEI/EMI）的点击解析部分仍被自动覆盖。
 */
class PonderXeiEntryTest {

    @AfterEach
    void reset() {
        PonderXeiEntry.get().clear();
    }

    @Test
    void targetAtResolvesOnlyInsideTheRegisteredBox() {
        PonderXeiEntry.get().present("gtceu:coke_oven", PonderXeiEntry.Box.of(100, 20, 46, 14));

        assertEquals(Optional.of("gtceu:coke_oven"), PonderXeiEntry.get().targetAt(120, 27));
        assertTrue(PonderXeiEntry.get().targetAt(99, 27).isEmpty(), "left of the box is a miss");
        assertTrue(PonderXeiEntry.get().targetAt(146, 27).isEmpty(), "right edge is exclusive");
        assertTrue(PonderXeiEntry.get().targetAt(120, 34).isEmpty(), "below the box is a miss");
    }

    @Test
    void clearForgetsTheRegisteredTarget() {
        PonderXeiEntry.get().present("gtceu:coke_oven", PonderXeiEntry.Box.of(0, 0, 10, 10));
        PonderXeiEntry.get().clear();

        assertTrue(PonderXeiEntry.get().targetAt(5, 5).isEmpty());
        assertEquals(null, PonderXeiEntry.get().target());
        assertFalse(PonderXeiEntry.get().box().isPresent());
    }

    @Test
    void presentOverwritesThePreviousTarget() {
        PonderXeiEntry.get().present("gtceu:coke_oven", PonderXeiEntry.Box.of(0, 0, 10, 10));
        PonderXeiEntry.get().present("gtceu:steam_grinder", PonderXeiEntry.Box.of(50, 50, 10, 10));

        assertTrue(PonderXeiEntry.get().targetAt(5, 5).isEmpty());
        assertEquals(Optional.of("gtceu:steam_grinder"), PonderXeiEntry.get().targetAt(55, 55));
    }

    @Test
    void blankTargetIsIgnored() {
        PonderXeiEntry.get().present("  ", PonderXeiEntry.Box.of(0, 0, 10, 10));
        assertFalse(PonderXeiEntry.get().box().isPresent());
        assertTrue(PonderXeiEntry.get().targetAt(5, 5).isEmpty());
    }
}
