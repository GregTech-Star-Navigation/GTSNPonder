package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 观看进度（不可变值对象，纯 Java）：已看场景的稳定键集合。
 *
 * <p>键优先取场景 {@code id}（稳定），无 id 时回退 {@code target}；空白键忽略。所有派生返回新值，
 * 不修改自身（持久化由 {@link ProgressStore} 负责）。</p>
 */
public final class WatchedProgress {

    private final Set<String> keys;

    private WatchedProgress(Set<String> keys) {
        this.keys = Collections.unmodifiableSet(new TreeSet<>(keys));
    }

    public static WatchedProgress empty() {
        return new WatchedProgress(Set.of());
    }

    public static WatchedProgress of(Collection<String> keys) {
        WatchedProgress progress = empty();
        if (keys != null) {
            for (String key : keys) {
                progress = progress.with(key);
            }
        }
        return progress;
    }

    /** 场景的稳定进度键：{@code id}（非空白）否则 {@code target}。 */
    public static String keyOf(SceneData scene) {
        if (scene == null) {
            return null;
        }
        if (scene.id() != null && !scene.id().isBlank()) {
            return scene.id();
        }
        return scene.target() != null && !scene.target().isBlank() ? scene.target() : null;
    }

    public boolean isWatched(String key) {
        return key != null && keys.contains(key);
    }

    public WatchedProgress with(String key) {
        if (key == null || key.isBlank() || keys.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(keys);
        next.add(key);
        return new WatchedProgress(next);
    }

    public WatchedProgress without(String key) {
        if (key == null || !keys.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(keys);
        next.remove(key);
        return new WatchedProgress(next);
    }

    /** 已看键（只读、字典序）。 */
    public Set<String> keys() {
        return keys;
    }

    public int size() {
        return keys.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WatchedProgress that && keys.equals(that.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys);
    }

    @Override
    public String toString() {
        return "WatchedProgress" + keys;
    }
}
