package com.gtsn.ponder.engine.model;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

/**
 * 场景数据格式的<b>有序版本迁移链</b>（{@code N → N+1}）。
 *
 * <p>解析器读到文档的 {@code formatVersion} 后调用 {@link #migrateToCurrent(JsonObject, int)}：
 * 从文档版本开始，逐级查找并应用一条 {@code N → N+1} 迁移，直到抵达
 * {@link SceneFormat#CURRENT_VERSION}；每应用一级后把文档的 {@code formatVersion} 前进到
 * {@code N+1}。这样版本演进只需追加一条迁移，历史文档无需改动，且解析器上游只面对「当前版本」的
 * 结构。</p>
 *
 * <p><b>边界</b>：文档版本 {@code >} 目标版本（未来版本 / 更新版 mod 写出的数据）→
 * {@link SceneFormatException}（清晰拒绝，而非静默降级）；链中缺少某一级迁移 → 同样清晰失败。
 * 当前 {@link SceneFormat#CURRENT_VERSION} 为 {@code 1}，生产链为空（v1 无历史版本）。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneMigrations {

    /** 单条 {@code fromVersion → fromVersion + 1} 的迁移。 */
    public interface Migration {

        /** 本迁移的源版本 {@code N}（应用后文档升至 {@code N + 1}）。 */
        int fromVersion();

        /**
         * 把文档从 {@link #fromVersion()} 迁移到下一版本；返回迁移后的文档（不得为 {@code null}）。
         * {@code formatVersion} 字段由 {@link SceneMigrations} 统一推进，迁移本体无需自增。
         */
        JsonObject apply(JsonObject document);
    }

    /**
     * 生产迁移链：按 {@code fromVersion} 升序登记。v1 无历史版本，故当前为空；提升
     * {@link SceneFormat#CURRENT_VERSION} 时在此追加相应迁移。
     */
    private static final List<Migration> MIGRATIONS = List.of();

    private SceneMigrations() {
    }

    /** 把文档迁移到 {@link SceneFormat#CURRENT_VERSION}（使用生产链）。 */
    public static JsonObject migrateToCurrent(JsonObject document, int fromVersion) {
        return migrate(document, fromVersion, SceneFormat.CURRENT_VERSION, MIGRATIONS);
    }

    /**
     * 通用迁移执行器：把 {@code document} 从 {@code fromVersion} 逐级迁移到 {@code toVersion}，
     * 每级使用 {@code chain} 中 {@code fromVersion} 匹配的迁移。
     *
     * @throws SceneFormatException 源版本高于目标版本，或链中缺少所需的 {@code N → N+1} 迁移
     */
    public static JsonObject migrate(JsonObject document, int fromVersion, int toVersion,
            List<Migration> chain) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(chain, "migration chain must not be null");
        if (fromVersion > toVersion) {
            throw new SceneFormatException("unsupported formatVersion " + fromVersion
                    + " (this build supports up to formatVersion " + toVersion + ")");
        }
        JsonObject current = document;
        for (int version = fromVersion; version < toVersion; version++) {
            Migration migration = find(chain, version);
            if (migration == null) {
                throw new SceneFormatException("no scene format migration registered from version "
                        + version + " to " + (version + 1));
            }
            current = migration.apply(current);
            if (current == null) {
                throw new SceneFormatException("scene format migration from version " + version
                        + " returned no document");
            }
            current.addProperty("formatVersion", version + 1);
        }
        return current;
    }

    private static Migration find(List<Migration> chain, int fromVersion) {
        for (Migration migration : chain) {
            if (migration.fromVersion() == fromVersion) {
                return migration;
            }
        }
        return null;
    }
}
