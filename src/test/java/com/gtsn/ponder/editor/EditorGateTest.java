package com.gtsn.ponder.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EditorGate} 的外部可观察行为：编辑器门控（ADR-0003「编辑器须门控」）。
 * 开发态恒可见；生产态默认隐藏，仅配置显式选择加入或开发覆盖时可见——正式玩家看不到编辑器。
 */
class EditorGateTest {

    @Test
    void developmentAlwaysAllowsTheEditor() {
        assertTrue(EditorGate.isEnabled(false, false, false));
        assertTrue(EditorGate.isEnabled(false, true, false));
        assertTrue(EditorGate.isEnabled(false, false, true));
    }

    @Test
    void productionHidesTheEditorByDefault() {
        assertFalse(EditorGate.isEnabled(true, false, false),
                "production without opt-in must hide the editor from normal players");
    }

    @Test
    void productionAllowsExplicitConfigOptIn() {
        assertTrue(EditorGate.isEnabled(true, true, false));
    }

    @Test
    void productionAllowsDevOverride() {
        assertTrue(EditorGate.isEnabled(true, false, true));
    }
}
