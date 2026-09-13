package com.gtsn.ponder.gametest;

import com.gtsn.ponder.GTSNPonder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 最小 GameTest 集：证明 {@code runGameTestServer} 可运行、GTSNPonder 已被加载。
 * 后续业务测试在此包内扩展。
 */
@GameTestHolder(GTSNPonder.MODID)
@PrefixGameTestTemplate(false)
public final class GtsnPonderGameTests {
    private GtsnPonderGameTests() {
    }

    @GameTest(template = "empty")
    public static void modIsLoaded(GameTestHelper helper) {
        if (!ModList.get().isLoaded(GTSNPonder.MODID)) {
            helper.fail("GTSNPonder is not loaded");
            return;
        }
        helper.succeed();
    }
}
