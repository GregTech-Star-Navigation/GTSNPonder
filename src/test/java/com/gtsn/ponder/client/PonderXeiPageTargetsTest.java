package com.gtsn.ponder.client;

import net.minecraft.client.gui.screens.Screen;
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

    // --- 页面入口锚点（工单 #20）---------------------------------------------------------

    /** 页面按钮列里的入口矩形（与右上角悬浮按钮无关）。 */
    private static final MachinePonderButton.Box ANCHOR = MachinePonderButton.Box.of(240, 87, 20, 18);

    /** 一个完整来源：本页锚点 + 机器目标。 */
    private static PonderXeiPageTargets.Source pageSource(
            MachinePonderButton.Box anchor, String target) {
        return new PonderXeiPageTargets.Source() {
            @Override
            public Optional<String> machineTargetFor(Screen screen) {
                return Optional.ofNullable(target);
            }

            @Override
            public Optional<MachinePonderButton.Box> entryAnchor(
                    Screen screen) {
                return Optional.ofNullable(anchor);
            }
        };
    }

    @Test
    void entryForCarriesThePageAnchorAndTheHoveredTargetTogether() {
        PonderXeiPageTargets.get().register(pageSource(ANCHOR, "gtceu:coke_oven"));

        assertEquals(Optional.of(new PonderXeiPageTargets.Entry("gtceu:coke_oven", ANCHOR)),
                PonderXeiPageTargets.get().entryFor(null));
        assertEquals(Optional.of(ANCHOR), PonderXeiPageTargets.get().anchorFor(null));
    }

    @Test
    void entryForKeepsANullTargetWhenThePointerIsNotOverAMachine() {
        // 指针已移开物品、正走向入口：锚点仍在（页面身份未变），目标为空——覆盖层据锚点保留入口矩形。
        PonderXeiPageTargets.get().register(pageSource(ANCHOR, null));

        assertEquals(Optional.of(new PonderXeiPageTargets.Entry(null, ANCHOR)),
                PonderXeiPageTargets.get().entryFor(null));
        assertTrue(PonderXeiPageTargets.get().resolve(null).isEmpty(),
                "no hovered machine means no target, even though the page anchor exists");
    }

    @Test
    void sourceWithoutAnAnchorDoesNotOwnThePage() {
        // 旧式来源（只有目标、无页面锚点）保持 `resolve` 可用，但不构成页面入口。
        PonderXeiPageTargets.get().register(screen -> Optional.of("gtceu:coke_oven"));

        assertEquals(Optional.of("gtceu:coke_oven"), PonderXeiPageTargets.get().resolve(null));
        assertTrue(PonderXeiPageTargets.get().entryFor(null).isEmpty());
        assertTrue(PonderXeiPageTargets.get().anchorFor(null).isEmpty());
    }

    @Test
    void emptyOrDegenerateAnchorsAreNotAPageOwner() {
        PonderXeiPageTargets.get().register(pageSource(MachinePonderButton.Box.of(10, 10, 0, 18), "gtceu:coke_oven"));

        assertTrue(PonderXeiPageTargets.get().entryFor(null).isEmpty(),
                "a zero-width anchor cannot be a clickable page entry");
    }

    @Test
    void throwingAnchorSourceIsSkippedGracefully() {
        PonderXeiPageTargets.get().register(new PonderXeiPageTargets.Source() {
            @Override
            public Optional<String> machineTargetFor(Screen screen) {
                return Optional.of("gtceu:steam_grinder");
            }

            @Override
            public Optional<MachinePonderButton.Box> entryAnchor(
                    Screen screen) {
                throw new IllegalStateException("EMI not ready");
            }
        });
        PonderXeiPageTargets.get().register(pageSource(ANCHOR, "gtceu:coke_oven"));

        assertEquals(Optional.of(new PonderXeiPageTargets.Entry("gtceu:coke_oven", ANCHOR)),
                PonderXeiPageTargets.get().entryFor(null));
    }
}

