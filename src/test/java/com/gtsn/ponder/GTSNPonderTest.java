package com.gtsn.ponder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 入口常量的行为验证（外部可观察：ModID 必须与资源 / 依赖声明一致）。
 */
class GTSNPonderTest {

    @Test
    void modIdIsStable() {
        assertEquals("gtsnponder", GTSNPonder.MODID);
    }
}
