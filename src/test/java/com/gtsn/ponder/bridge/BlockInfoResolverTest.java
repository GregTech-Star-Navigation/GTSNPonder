package com.gtsn.ponder.bridge;

import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.structure.StructureRole;
import com.gtsn.ponder.structure.StructureSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方块信息解析（{@link BlockInfoResolver}）的纯逻辑行为（工单 #21 反馈 3）：
 * 结构坐标 → 可显示的本地化键（方块名 / 角色名），以及缺失 / 越界时的空态。
 */
class BlockInfoResolverTest {

    /** 3×3×3 夹具：原点为控制器、另一处为物品输入仓口、其余为普通外壳。 */
    private static StructureSource fixture() {
        StructureSource.Builder builder = StructureSource.builder("gtceu:fixture").size(3, 3, 3);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        builder.addBlock(x, y, z, "gtceu:test_controller", StructureRole.CONTROLLER);
                    } else if (x == 1 && y == 1 && z == 1) {
                        builder.addBlock(x, y, z, "gtceu:test_input_bus", StructureRole.ITEM_INPUT);
                    } else {
                        builder.addBlock(x, y, z, "gtceu:test_casing", StructureRole.PLAIN);
                    }
                }
            }
        }
        return builder.build();
    }

    @Test
    void resolvesControllerNameAndRoleKeys() {
        BlockInfoResolver.BlockInfo info = BlockInfoResolver.resolveAt(fixture(), 0, 0, 0).orElseThrow();

        assertEquals("gtceu:test_controller", info.blockId());
        assertEquals(GeneratedKeys.blockNameKey("gtceu:test_controller"), info.nameKey());
        assertEquals(GeneratedKeys.roleKey("CONTROLLER"), info.roleKey());
        assertTrue(info.controller());
        assertFalse(info.hatch());
        assertTrue(info.hasRole());
    }

    @Test
    void resolvesHatchRoleKey() {
        BlockInfoResolver.BlockInfo info = BlockInfoResolver.resolveAt(fixture(), 1, 1, 1).orElseThrow();

        assertEquals(GeneratedKeys.roleKey("ITEM_INPUT"), info.roleKey());
        assertTrue(info.hatch());
        assertFalse(info.controller());
    }

    @Test
    void plainCasingHasNoRoleKey() {
        BlockInfoResolver.BlockInfo info = BlockInfoResolver.resolveAt(fixture(), 2, 2, 2).orElseThrow();

        assertEquals("gtceu:test_casing", info.blockId());
        assertNull(info.roleKey());
        assertFalse(info.hasRole());
        assertFalse(info.hatch());
        assertFalse(info.controller());
    }

    @Test
    void blockNameKeyFallsBackToTheMinecraftNamespace() {
        BlockInfoResolver.BlockInfo info = BlockInfoResolver
                .resolve(new com.gtsn.ponder.structure.StructureBlock(0, 0, 0, "stone"))
                .orElseThrow();

        assertEquals("block.minecraft.stone", info.nameKey());
    }

    @Test
    void unknownPositionAndNullInputsAreEmpty() {
        assertTrue(BlockInfoResolver.resolveAt(fixture(), 9, 9, 9).isEmpty());
        assertTrue(BlockInfoResolver.resolveAt(null, 0, 0, 0).isEmpty());
        assertTrue(BlockInfoResolver.resolve(null).isEmpty());
        assertTrue(BlockInfoResolver.blockAt(fixture(), 4, 4, 4).isEmpty());
        assertEquals(Optional.empty(), BlockInfoResolver.resolveAt(fixture(), -1, 0, 0));
    }
}
