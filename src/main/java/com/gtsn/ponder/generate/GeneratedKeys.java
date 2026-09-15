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

    /**
     * 仓口 / 总线角色语言键前缀（工单 #16 缺陷 A2）：完整键 = 前缀 + <b>大写的枚举名</b>，
     * 例如 {@code ponder.gtsnponder.generated.role.ENERGY_INPUT}。
     */
    public static final String ROLE_PREFIX = "ponder.gtsnponder.generated.role.";

    /**
     * GT 电压层级名语言键前缀（工单 #17 缺陷 B）：完整键 = 前缀 + <b>小写的层级短码</b>，
     * 例如 {@code OpV} → {@code ponder.gtsnponder.tier.opv}。层级短码表见 {@link GtTierNames}。
     */
    public static final String TIER_PREFIX = "ponder.gtsnponder.tier.";

    /**
     * 旁白模板参数中「列表分隔符」的本地化键（工单 #18）：自动旁白把仓口角色 / 配方类型等以
     * {@code ", "} 连接成一个参数（生成器与语言无关）。渲染前 {@code NarrationLocalization} 把该
     * 分隔符解析为本键，使中文显示顿号「、」、英文显示逗号「, 」，避免中文旁白出现半角逗号堆砌。
     */
    public static final String LIST_SEPARATOR = "ponder.gtsnponder.narration.list_separator";

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

    /**
     * 仓口 / 总线角色的本地化键：完整键 = {@link #ROLE_PREFIX} + 大写的枚举名，
     * 如 {@code roleKey("ENERGY_INPUT")} → {@code ponder.gtsnponder.generated.role.ENERGY_INPUT}。
     */
    public static String roleKey(String roleName) {
        return ROLE_PREFIX + roleName;
    }

    /**
     * GT 电压层级名语言键（工单 #17 缺陷 B）：{@code tierKey("OpV")} →
     * {@code ponder.gtsnponder.tier.opv}。
     */
    public static String tierKey(String tierCode) {
        return TIER_PREFIX + tierCode.toLowerCase(Locale.ROOT);
    }

    /**
     * GT 方块名语言键（工单 #17 缺陷 B）：把 {@code gtceu:lv_centrifuge} 映射为 GT 自身的方块名键
     * {@code block.gtceu.lv_centrifuge}。单方块机器没有 datagen 机器标题键，但其方块名键由 GT 随包
     * lang 提供（中英双语），故单方块旁白经此键本地化。
     */
    public static String blockNameKey(String targetId) {
        int colon = targetId.indexOf(':');
        String namespace = colon > 0 ? targetId.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? targetId.substring(colon + 1) : targetId;
        return "block." + namespace + "." + path;
    }

    /**
     * GT 配方类型名语言键（工单 #17 缺陷 B）：把 {@code gtceu:centrifuge} 映射为 GT 随包 lang 的
     * {@code gtceu.centrifuge}（中英双语：离心机 / Centrifuge）。
     */
    public static String recipeTypeKey(String targetId) {
        int colon = targetId.indexOf(':');
        String namespace = colon > 0 ? targetId.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? targetId.substring(colon + 1) : targetId;
        return namespace + "." + path;
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
