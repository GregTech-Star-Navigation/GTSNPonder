package com.gtsn.ponder.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XEI（JEI/EMI）普通页面「思索」目标登记缝的点击 / 解析交接测试（工单 #17 缺陷 A）。
 *
 * <p>与 {@code PonderXeiEntryTest} 同构：来源由 {@code com.gtsn.ponder.gt} 的集成类注册，解析逻辑
 * （首个非空命中、异常来源跳过、空白目标忽略、注销 / 重置）在此 headless 锁定（XEI 集成本体无法
 * headless 启动）。屏幕参数以 {@code null} 传入——登记缝对屏幕类型不做假设（类型判断在来源内）。</p>
 */
class PonderXeiPageTargetsTest {

    @AfterEach
    void reset() {
        PonderXeiPageTargets.get().reset();
    }

    @Test
    void noSourcesResolveToEmpty() {
        assertTrue(PonderXeiPageTargets.get().resolve(null).isEmpty(),
                "with no XEI plugin loaded the overlay must resolve nothing");
        assertEquals(0, PonderXeiPageTargets.get().sourceCount());
    }

    @Test
    void firstNonBlankTargetWins() {
        PonderXeiPageTargets.get().register(screen -> Optional.empty());
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:coke_oven"));
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:lv_macerator"));

        assertEquals(Optional.of("gtceu:coke_oven"), PonderXeiPageTargets.get().resolve(null));
    }

    @Test
    void blankTargetsAreIgnored() {
        PonderXeiPageTargets.get().register(screen -> Optional.of("   "));
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:lv_macerator"));

        assertEquals(Optional.of("gtceu:lv_macerator"), PonderXeiPageTargets.get().resolve(null));
    }

    @Test
    void throwingSourceIsSkippedGracefully() {
        PonderXeiPageTargets.get().register(screen -> {
            throw new IllegalStateException("XEI not ready");
        });
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:coke_oven"));

        assertEquals(Optional.of("gtceu:coke_oven"), PonderXeiPageTargets.get().resolve(null));
    }

    @Test
    void registerIsIdempotentAndUnregisterRemovesTheSource() {
        PonderXeiPageTargets.Source source = screen -> Optional.of("gtceu:coke_oven");
        PonderXeiPageTargets.get().register(source);
        PonderXeiPageTargets.get().register(source);
        assertEquals(1, PonderXeiPageTargets.get().sourceCount());

        PonderXeiPageTargets.get().unregister(source);
        assertEquals(0, PonderXeiPageTargets.get().sourceCount());
        assertTrue(PonderXeiPageTargets.get().resolve(null).isEmpty());
    }

    @Test
    void resetDropsEverySource() {
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:coke_oven"));
        PonderXeiPageTargets.get().reset();

        assertEquals(0, PonderXeiPageTargets.get().sourceCount());
        assertTrue(PonderXeiPageTargets.get().resolve(null).isEmpty());
    }
}
