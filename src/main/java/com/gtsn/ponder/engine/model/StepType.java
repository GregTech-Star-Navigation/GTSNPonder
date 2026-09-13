package com.gtsn.ponder.engine.model;

import java.util.Optional;

/**
 * 封闭的步骤类型枚举（ADR-0003 的反 DSL 铁律）。
 *
 * <p>每种类型有稳定的 JSON {@code name}（camelCase）。数据格式的扩展只能通过新增 Java
 * 步骤实现，而非脚本解释器；未知类型在解析时被跳过并告警（前向兼容）。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public enum StepType {
    SHOW_SECTION("showSection"),
    HIDE_SECTION("hideSection"),
    REPLACE_BLOCKS("replaceBlocks"),
    HIGHLIGHT("highlight"),
    OUTLINE("outline"),
    TEXT("text"),
    CAMERA("camera"),
    IDLE("idle"),
    INSTALL_MODULE("installModule"),
    FORMED_PULSE("formedPulse"),
    PARTICLES("particles");

    private final String jsonName;

    StepType(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    /**
     * 按 JSON 字面量解析；未知 / {@code null} 返回 {@link Optional#empty()}（调用方跳过并告警）。
     */
    public static Optional<StepType> fromJson(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        for (StepType type : values()) {
            if (type.jsonName.equalsIgnoreCase(trimmed)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
