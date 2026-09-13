package com.gtsn.ponder.engine.model;

import java.util.List;

/**
 * 解析结果：成功的 {@link SceneData} + 前向兼容告警（如未知步骤类型被跳过）。
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class SceneParseResult {

    private final SceneData scene;
    private final List<String> warnings;

    public SceneParseResult(SceneData scene, List<String> warnings) {
        this.scene = scene;
        this.warnings = List.copyOf(warnings);
    }

    public SceneData scene() {
        return scene;
    }

    public List<String> warnings() {
        return warnings;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
