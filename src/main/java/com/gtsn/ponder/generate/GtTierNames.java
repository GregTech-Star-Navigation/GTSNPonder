package com.gtsn.ponder.generate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * GT 电压层级名本地化表（纯 Java，零 MC / GT；工单 #17 缺陷 B）。
 *
 * <p>自动生成 / 使用场景的旁白把 {@link com.gtsn.ponder.structure.SingleBlockMachineSource#tierName()
 * 层级短码}（{@code GTValues.VN} 的值，如 {@code OpV} / {@code LV}）作为 {@code narrationArgs}
 * 的字面量写入冻结场景数据。渲染前 {@code com.gtsn.ponder.presenter.NarrationLocalization} 经本表把短码
 * 映射到本地化键 {@link GeneratedKeys#tierKey(String)}，故旁白显示可读的层级名而非裸露缩写，
 * 且不出现 {@code gtceu:} 形式的原始注册名。</p>
 *
 * <p>本表镜像 GT 的 15 个电压层级短码（{@code GTValues.VN}）；若 fork 新增层级而本表未覆盖，旁白会
 * 优雅回退为原始短码（不伪造键）。代表性机器的层级一致性由 GameTest 守卫。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖。</p>
 */
public final class GtTierNames {

    /** 一个电压层级的短码与中英显示名。 */
    public record Tier(String code, String en, String zh) {

        public Tier {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(en, "en");
            Objects.requireNonNull(zh, "zh");
        }
    }

    /** 全部已知 GT 电压层级（顺序对应 {@code GTValues.VN} 下标）。 */
    public static final List<Tier> ALL = List.of(
            new Tier("ULV", "Ultra Low Voltage", "超低压"),
            new Tier("LV", "Low Voltage", "低压"),
            new Tier("MV", "Medium Voltage", "中压"),
            new Tier("HV", "High Voltage", "高压"),
            new Tier("EV", "Extreme Voltage", "超高压"),
            new Tier("IV", "Insane Voltage", "巨高压"),
            new Tier("LuV", "Ludicrous Voltage", "狂高压"),
            new Tier("ZPM", "Zero Point Module Voltage", "零点压"),
            new Tier("UV", "Ultimate Voltage", "极限压"),
            new Tier("UHV", "Ultra High Voltage", "极高压"),
            new Tier("UEV", "Ultra Extreme Voltage", "超级压"),
            new Tier("UIV", "Ultra Immense Voltage", "无穷压"),
            new Tier("UXV", "Ultra X Voltage", "未知压"),
            new Tier("OpV", "Overpowered Voltage", "过载压"),
            new Tier("MAX", "Maximum Voltage", "最高压"));

    private GtTierNames() {
    }

    /** 按层级短码查找（大小写不敏感）；未知 / {@code null} 返回空。 */
    public static Optional<Tier> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (Tier tier : ALL) {
            if (tier.code().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /** 层级短码 → 本地化语言键；未知短码返回空（调用方回退原始短码）。 */
    public static Optional<String> keyFor(String code) {
        return byCode(code).map(tier -> GeneratedKeys.tierKey(tier.code()));
    }
}
