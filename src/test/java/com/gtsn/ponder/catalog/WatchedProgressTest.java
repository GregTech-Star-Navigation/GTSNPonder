package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 观看进度值对象的行为测试：稳定键、幂等标记、不可变派生。
 */
class WatchedProgressTest {

    @Test
    void emptyProgressWatchesNothing() {
        WatchedProgress progress = WatchedProgress.empty();
        assertTrue(progress.keys().isEmpty());
        assertFalse(progress.isWatched("anything"));
        assertEquals(0, progress.size());
    }

    @Test
    void markIsIdempotentAndImmutable() {
        WatchedProgress base = WatchedProgress.empty();
        WatchedProgress once = base.with("a");
        WatchedProgress twice = once.with("a");

        assertTrue(base.keys().isEmpty(), "original value must stay immutable");
        assertEquals(1, once.size());
        assertEquals(once.keys(), twice.keys());
        assertTrue(twice.isWatched("a"));
    }

    @Test
    void blankKeysAreIgnored() {
        WatchedProgress progress = WatchedProgress.empty().with("  ").with(null);
        assertTrue(progress.keys().isEmpty());
    }

    @Test
    void keysAreSortedAndDefensive() {
        WatchedProgress progress = WatchedProgress.of(List.of("b", "a", "c", "a"));
        assertEquals(List.of("a", "b", "c"), List.copyOf(progress.keys()));
    }

    @Test
    void sceneKeyPrefersStableIdThenTarget() {
        SceneData withId = SceneData.builder().formatVersion(1).id("gtceu:coke_oven.scene")
                .target("gtceu:coke_oven").build();
        assertEquals("gtceu:coke_oven.scene", WatchedProgress.keyOf(withId));

        SceneData withoutId = SceneData.builder().formatVersion(1).target("gtceu:coke_oven").build();
        assertEquals("gtceu:coke_oven", WatchedProgress.keyOf(withoutId));
    }
}
