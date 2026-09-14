package com.gtsn.ponder.gt;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;

import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/**
 * GT 机器界面（GUI）识别与「思索」目标解析——入口 ④（GT 机器界面内按钮）的 GT 侧耦合点（#12）。
 *
 * <p><b>实现方式（用户批准的覆盖层方案）</b>：<b>不</b>改动 GT fork、<b>不</b>使用 mixin，而是在 GT 自己的
 * 机器屏上叠加一层客户端覆盖按钮（见 {@code com.gtsn.ponder.client.GtMachineOverlayClientEvents}）。
 * 本类是唯一知道 GT 机器屏长什么样的地方（与 {@link GtStructureAdapter} 同属唯一允许 import
 * {@code com.gregtechceu} 的适配包 {@code com.gtsn.ponder.gt}）。</p>
 *
 * <h2>识别依据（GTCEu 7.5.4-patch01 实测）</h2>
 * <p>GT 的机器 GUI 由 LDLib 的 {@link ModularUIGuiContainer} 承载（{@code AbstractContainerScreen} 子类），
 * 其 {@code public final ModularUI modularUI} 的 {@code public final IUIHolder holder} 就是该机器的
 * {@link MetaMachine}（GT 的 {@code MachineUIFactory} 经 {@code IUIMachine.createUI(player)} 构造，
 * {@code IUIMachine extends IUIHolder}）。故：</p>
 * <ol>
 *   <li>当前屏是 {@link ModularUIGuiContainer}，且其 {@code modularUI.holder} 是 {@link MetaMachine} →
 *       判定为 GT 机器屏（{@link #isMachineScreen(Screen)}）；</li>
 *   <li>该机器的 {@link MachineDefinition} 是 {@link MultiblockMachineDefinition} → 它是可「思索」的
 *       多方块，返回其注册 id（如 {@code gtceu:coke_oven}）作为思索目标（{@link #resolveTarget(Screen)}）。
 *       单方块机器 / 覆盖层 / 物品 UI 等同样走 LDLib 屏，但无结构可思索，故<b>不</b>返回目标、不显按钮。</li>
 * </ol>
 * <p>任何非 GT 屏（含 {@code null}）、无 holder、解析异常一律返回空 / {@code false}，绝不抛出。</p>
 *
 * <p><b>线程 / 端</b>：仅客户端加载。专职服务端不会引用本类，故 {@code net.minecraft.client} /
 * LDLib 客户端屏类型不会在服务端被加载（沿用「客户端类不在专职服务端加载」纪律）。</p>
 */
public final class GtMachineScreenAdapter {

    private GtMachineScreenAdapter() {
    }

    /** 当前屏是否为 GT 机器界面（LDLib 模块化屏 + 持有 {@link MetaMachine}）。 */
    public static boolean isMachineScreen(Screen screen) {
        return metaMachineOf(screen) != null;
    }

    /**
     * 解析当前 GT 机器屏的「思索」目标 id。仅当机器是<b>多方块</b>（可调用 {@link GtStructureAdapter} 生成结构）
     * 时返回；否则空。用于覆盖层决定是否绘制按钮。
     */
    public static Optional<String> resolveTarget(Screen screen) {
        MetaMachine machine = metaMachineOf(screen);
        if (machine == null) {
            return Optional.empty();
        }
        MachineDefinition definition;
        try {
            definition = machine.getDefinition();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        if (!(definition instanceof MultiblockMachineDefinition) || definition.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(definition.getId().toString());
    }

    /** 从 LDLib 屏上取出承载的 {@link MetaMachine}；非机器屏返回 {@code null}。 */
    private static MetaMachine metaMachineOf(Screen screen) {
        if (!(screen instanceof ModularUIGuiContainer gui)) {
            return null;
        }
        ModularUI ui = gui.modularUI;
        if (ui == null) {
            return null;
        }
        IUIHolder holder = ui.holder;
        return holder instanceof MetaMachine machine ? machine : null;
    }
}
