package com.gtsn.ponder.engine.model;

/**
 * 场景数据格式的<b>单一事实源</b>：当前版本常量与冻结的版本号。
 *
 * <p>schema 见 {@code docs/scene-format.md}（v1 已冻结）。任何已冻结版本的结构变更都必须先开 issue /
 * 落 ADR，再提升 {@link #CURRENT_VERSION} 并注册一条 {@code N → N+1} 迁移
 * （{@link SceneMigrations}）。解析器 / 生成器 / 序列化器统一引用本常量，禁止各自散写版本号。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneFormat {

    /** 冻结的 v1 版本号。 */
    public static final int V1 = 1;

    /** 当前解析器 / 生成器写出的版本号（迁移链的终点）。 */
    public static final int CURRENT_VERSION = V1;

    private SceneFormat() {
    }
}
