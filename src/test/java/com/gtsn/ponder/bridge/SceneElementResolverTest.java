package com.gtsn.ponder.bridge;

import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯逻辑行为测试：场景元素（{@link SceneElement}）的选择器语义 → 结构源坐标。
 *
 * <p>只断言外部可观察的解析结果（元素 → 方块集合），不测内部实现。选择器是反 DSL 的字面量
 * 词汇（{@code all} / {@code controller} / {@code block} / {@code layer}），未知取值返回空集
 * 而非抛异常（前向兼容）。</p>
 */
class SceneElementResolverTest {

    private static StructureSource fixture() {
        return StructureSource.builder("gtceu:test_machine")
                .displayName("Test Machine")
                .size(3, 2, 3)
                .controller(1, 0, 1)
                .addBlock(0, 0, 0, "gtceu:test_casing")
                .addBlock(1, 0, 0, "gtceu:test_casing")
                .addBlock(2, 0, 0, "gtceu:test_casing")
                .addBlock(0, 0, 1, "gtceu:test_casing")
                .addBlock(1, 0, 1, "gtceu:test_controller")
                .addBlock(2, 0, 1, "gtceu:test_casing")
                .addBlock(1, 1, 1, "gtceu:test_casing")
                .build();
    }

    private static StructureSource withoutController() {
        return StructureSource.builder("gtceu:no_controller")
                .size(1, 1, 1)
                .addBlock(0, 0, 0, "gtceu:test_casing")
                .build();
    }

    /** 显式角色夹具：控制器 / 外壳 / 物品输入总线 / 流体输出仓各一。 */
    private static StructureSource withRoles() {
        return StructureSource.builder("gtceu:role_machine")
                .displayName("Role Machine")
                .size(2, 1, 2)
                .controller(0, 0, 0)
                .addBlock(0, 0, 0, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addBlock(1, 0, 0, "gtceu:test_casing", StructureRole.PLAIN)
                .addBlock(0, 0, 1, "gtceu:input_bus", StructureRole.ITEM_INPUT)
                .addBlock(1, 0, 1, "gtceu:output_hatch", StructureRole.FLUID_OUTPUT)
                .build();
    }

    @Test
    void allSelectorReturnsEveryBlock() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        List<StructureBlock> resolved = resolver.resolve(SceneElement.of("shell", "section", Map.of()));

        assertEquals(7, resolved.size());
    }

    @Test
    void explicitAllSelectorReturnsEveryBlock() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        List<StructureBlock> resolved = resolver.resolve(
                SceneElement.of("shell", "section", Map.of("selector", "all")));

        assertEquals(7, resolved.size());
    }

    @Test
    void controllerSelectorReturnsOnlyTheControllerCell() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        List<StructureBlock> resolved = resolver.resolve(
                SceneElement.of("controller", "anchor", Map.of("selector", "controller")));

        assertEquals(List.of(new StructureBlock(1, 0, 1, "gtceu:test_controller")), resolved);
    }

    @Test
    void controllerSelectorIsEmptyWhenStructureHasNoController() {
        SceneElementResolver resolver = new SceneElementResolver(withoutController());

        List<StructureBlock> resolved = resolver.resolve(
                SceneElement.of("controller", "anchor", Map.of("selector", "controller")));

        assertTrue(resolved.isEmpty());
    }

    @Test
    void blockSelectorFiltersByBlockId() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        List<StructureBlock> resolved = resolver.resolve(SceneElement.of("casing", "section",
                Map.of("selector", "block", "block", "gtceu:test_casing")));

        assertEquals(6, resolved.size());
        assertTrue(resolved.stream().allMatch(block -> block.blockId().equals("gtceu:test_casing")));
        assertTrue(resolved.stream().noneMatch(block -> block.x() == 1 && block.y() == 0 && block.z() == 1));
    }

    @Test
    void blockSelectorWithoutBlockParamIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        assertTrue(resolver.resolve(SceneElement.of("casing", "section", Map.of("selector", "block"))).isEmpty());
    }

    @Test
    void layerSelectorFiltersByLocalY() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        List<StructureBlock> base = resolver.resolve(
                SceneElement.of("base", "section", Map.of("selector", "layer", "y", 0.0d)));
        List<StructureBlock> top = resolver.resolve(
                SceneElement.of("top", "section", Map.of("selector", "layer", "y", 1.0d)));

        assertEquals(6, base.size());
        assertTrue(base.stream().allMatch(block -> block.y() == 0));
        assertEquals(List.of(new StructureBlock(1, 1, 1, "gtceu:test_casing")), top);
    }

    @Test
    void roleSelectorFiltersByStructureRole() {
        SceneElementResolver resolver = new SceneElementResolver(withRoles());

        List<StructureBlock> itemInput = resolver.resolve(SceneElement.of("hatch_input", "anchor",
                Map.of("selector", "role", "role", "ITEM_INPUT")));
        List<StructureBlock> shell = resolver.resolve(SceneElement.of("shell", "section",
                Map.of("selector", "role", "role", "PLAIN")));

        assertEquals(List.of(new StructureBlock(0, 0, 1, "gtceu:input_bus", StructureRole.ITEM_INPUT)), itemInput);
        assertEquals(1, shell.size());
        assertTrue(shell.stream().allMatch(block -> block.role() == StructureRole.PLAIN));
    }

    @Test
    void roleSelectorIsCaseInsensitive() {
        SceneElementResolver resolver = new SceneElementResolver(withRoles());

        assertEquals(1, resolver.resolve(SceneElement.of("c", "anchor",
                Map.of("selector", "role", "role", "controller"))).size());
    }

    @Test
    void roleSelectorSpreadSamplesEvenlyAcrossMatches() {
        StructureSource.Builder builder = StructureSource.builder("gtceu:spread").size(6, 1, 1).controller(0, 0, 0);
        builder.addBlock(0, 0, 0, "gtceu:test_controller", StructureRole.CONTROLLER);
        for (int x = 1; x < 6; x++) {
            builder.addBlock(x, 0, 0, "gtceu:input_bus", StructureRole.ITEM_INPUT);
        }
        SceneElementResolver resolver = new SceneElementResolver(builder.build());

        List<StructureBlock> spread = resolver.resolve(SceneElement.of("hatch", "anchor",
                Map.of("selector", "role", "role", "ITEM_INPUT", "limit", 3.0d, "spread", true)));
        List<StructureBlock> firstN = resolver.resolve(SceneElement.of("hatch", "anchor",
                Map.of("selector", "role", "role", "ITEM_INPUT", "limit", 3.0d)));

        assertEquals(List.of(1, 3, 5), spread.stream().map(StructureBlock::x).toList(),
                "spread must sample evenly across the whole role, including the last unit");
        assertEquals(List.of(1, 2, 3), firstN.stream().map(StructureBlock::x).toList(),
                "without spread the first N units are used");
    }

    /** 含一个 2×1×2 模块位的夹具（区域外仍有普通方块，模块位区域以空穴表达）。 */
    private static StructureSource withModuleSlots() {
        return StructureSource.builder("gtceu:slotted")
                .displayName("Slotted Machine")
                .size(4, 2, 4)
                .controller(3, 0, 3)
                .addBlock(3, 0, 3, "gtceu:test_controller", StructureRole.CONTROLLER)
                .addBlock(0, 1, 0, "gtceu:test_casing")
                .addModuleSlot(new ModuleSlot(0, 0, 0, 2, 1, 2, true, List.of()))
                .addModuleSlot(new ModuleSlot(2, 0, 0, 1, 1, 1, false, List.of("gtceu:test_module")))
                .build();
    }

    @Test
    void moduleSlotSelectorReturnsRegionCells() {
        SceneElementResolver resolver = new SceneElementResolver(withModuleSlots());

        List<StructureBlock> cells = resolver.resolve(
                SceneElement.of("moduleslot.0", "region", Map.of("selector", "moduleslot", "index", 0.0d)));

        assertEquals(4, cells.size(), "2x1x2 slot region must resolve to 4 cells");
        assertEquals(List.of(
                new StructureBlock(0, 0, 0, SceneElementResolver.MODULE_SLOT_CELL_BLOCK_ID),
                new StructureBlock(0, 0, 1, SceneElementResolver.MODULE_SLOT_CELL_BLOCK_ID),
                new StructureBlock(1, 0, 0, SceneElementResolver.MODULE_SLOT_CELL_BLOCK_ID),
                new StructureBlock(1, 0, 1, SceneElementResolver.MODULE_SLOT_CELL_BLOCK_ID)), cells);
    }

    @Test
    void moduleSlotSelectorResolvesSecondSlotRegion() {
        SceneElementResolver resolver = new SceneElementResolver(withModuleSlots());

        List<StructureBlock> cells = resolver.resolve(
                SceneElement.of("moduleslot.1", "region", Map.of("selector", "moduleslot", "index", 1.0d)));

        assertEquals(List.of(new StructureBlock(2, 0, 0, SceneElementResolver.MODULE_SLOT_CELL_BLOCK_ID)), cells);
    }

    @Test
    void moduleSlotSelectorWithoutIndexIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(withModuleSlots());

        assertTrue(resolver.resolve(
                SceneElement.of("moduleslot.0", "region", Map.of("selector", "moduleslot"))).isEmpty());
    }

    @Test
    void moduleSlotSelectorOutOfRangeIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(withModuleSlots());

        assertTrue(resolver.resolve(SceneElement.of("moduleslot.9", "region",
                Map.of("selector", "moduleslot", "index", 9.0d))).isEmpty());
        assertTrue(resolver.resolve(SceneElement.of("moduleslot.-1", "region",
                Map.of("selector", "moduleslot", "index", -1.0d))).isEmpty());
    }

    @Test
    void moduleSlotSelectorWithoutSlotsIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        assertTrue(resolver.resolve(SceneElement.of("moduleslot.0", "region",
                Map.of("selector", "moduleslot", "index", 0.0d))).isEmpty());
    }

    @Test
    void roleSelectorWithoutRoleParamIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(withRoles());

        assertTrue(resolver.resolve(SceneElement.of("x", "section", Map.of("selector", "role"))).isEmpty());
    }

    @Test
    void roleSelectorWithUnknownRoleIsEmpty() {
        SceneElementResolver resolver = new SceneElementResolver(withRoles());

        assertTrue(resolver.resolve(SceneElement.of("x", "section",
                Map.of("selector", "role", "role", "NOT_A_ROLE"))).isEmpty());
    }

    @Test
    void unknownSelectorIsEmptyAndForwardCompatible() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        assertTrue(resolver.resolve(
                SceneElement.of("mystery", "section", Map.of("selector", "quantum"))).isEmpty());
    }

    @Test
    void resolveAllKeepsElementIdsAndOrder() {
        SceneElementResolver resolver = new SceneElementResolver(fixture());

        Map<String, List<StructureBlock>> resolved = resolver.resolveAll(List.of(
                SceneElement.of("base", "section", Map.of("selector", "layer", "y", 0.0d)),
                SceneElement.of("controller", "anchor", Map.of("selector", "controller"))));

        assertEquals(List.of("base", "controller"), List.copyOf(resolved.keySet()));
        assertEquals(6, resolved.get("base").size());
        assertEquals(1, resolved.get("controller").size());
    }
}
