package com.gtsn.ponder.engine.model;

/**
 * 场景数据格式错误（{@code formatVersion} 缺失 / 非法、必要字段缺失、JSON 结构非法等）。
 *
 * <p>非受检异常：解析失败即拒绝整个场景，错误信息必须清晰可定位。</p>
 */
public class SceneFormatException extends RuntimeException {

    public SceneFormatException(String message) {
        super(message);
    }

    public SceneFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
