package com.gtsn.ponder.generate;

import java.util.Locale;

/**
 * 自动生成场景所用的<b>本地化键</b>（单一事实源）。
 *
 * <p>自动生成的场景把 {@code title} 写成机器标题键（{@link #machineTitleKey(String)}），
 * 而非硬编码显示名；该键随自动文案一起由 datagen 批量产出（见
 * {@code com.gtsn.ponder.datagen}）。颜色图例键同理集中于此，供播放屏与 datagen 共用。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（自动生成缝可在 headless 单测中引用）。</p>
 */
public final class GeneratedKeys {

    /** 机器标题键前缀；完整键 = 前缀 + <b>清洗后的 target id</b> + {@code .title}。 */
    public static final String MACHINE_TITLE_PREFIX = "ponder.gtsnponder.generated.machine.";

    /** 常驻颜色图例的本地化键。 */
    public static final String LEGEND_TITLE = "ponder.gtsnponder.legend.title";
    public static final String LEGEND_CONTROLLER = "ponder.gtsnponder.legend.controller";
    public static final String LEGEND_HATCH = "ponder.gtsnponder.legend.hatch";
    public static final String LEGEND_MODULE_SLOT = "ponder.gtsnponder.legend.module_slot";

    private GeneratedKeys() {
    }

    /**
     * 机器标题本地化键：把 target id（如 {@code gtceu:coke_oven}）清洗为稳定片段
     * （{@code gtceu_coke_oven}）后拼成 {@code ponder.gtsnponder.generated.machine.<id>.title}。
     */
    public static String machineTitleKey(String targetId) {
        return MACHINE_TITLE_PREFIX + sanitize(targetId) + ".title";
    }

    /** 结构 id → 稳定键片段：小写，非 {@code [a-z0-9_]} 的字符替换为 {@code _}。 */
    public static String sanitize(String id) {
        StringBuilder builder = new StringBuilder(id.length());
        for (char character : id.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean alphanumeric = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9');
            builder.append(alphanumeric || character == '_' ? character : '_');
        }
        return builder.toString();
    }
}
