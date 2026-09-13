package com.gtsn.ponder.bridge;

import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.structure.StructureBlock;
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
