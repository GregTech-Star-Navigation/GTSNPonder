package com.gtsn.ponder.generate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 关键机器<b>手写教学解说</b>的注册表（纯 Java，零 MC / GT；工单 #18）。
 *
 * <p>自动生成的旁白要「讲得明白」，单靠结构数据不够——机器<b>用途 / 输入 / 输出 / 常见坑</b>属于
 * 领域知识，无法从结构页推出。本类登记一批<b>常用关键机器</b>的 id（多方块 + 单方块），并为每台
 * 定义一组稳定的本地化键（{@link #key(String, Field)}）；具体中英文案由 datagen provider 产出
 * （{@code ponder.gtsnponder.desc.<sanitized-id>.<field>}）。生成器命中登记表时优先使用手写文案
 * （{@link #isCurated(String)}），未命中则回退结构化模板（话题更泛，但仍是完整句子）。</p>
 *
 * <h2>教学模式字段（固定顺序）</h2>
 * <p>{@link Field} 即固定叙事顺序：{@link Field#PURPOSE 用途} → {@link Field#SETUP 怎么搭 / 成型要点}
 * → {@link Field#INPUTS 输入什么} → {@link Field#OUTPUTS 输出什么} → {@link Field#ENERGY 供能与层级}
 * → {@link Field#PITFALLS 常见坑}。每个字段一句完整人话（无原始 {@code gtceu:} id、无枚举名堆砌）。</p>
 *
 * <p>登记表只是「键的存在性来源」：{@link #allKeys()} 供 datagen 与测试守卫遍历；键缺失时生成器会
 * 生成一个渲染为空白的旁白——{@code GeneratedLangKeysTest} 守卫两者一致，防止漂移。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（headless 单测可确定性覆盖命中 / 回退）。</p>
 */
public final class MachineDescriptions {

    /** 手写说明键前缀；完整键 = 前缀 + <b>清洗后的 target id</b> + {@code .} + 小写字段名。 */
    public static final String PREFIX = "ponder.gtsnponder.desc.";

    /** 机器形态：多方块讲「怎么搭」，单方块讲「怎么用」。 */
    public enum Kind {
        MULTIBLOCK,
        SINGLE_BLOCK
    }

    /** 固定教学叙事字段（顺序即讲解顺序）。 */
    public enum Field {
        PURPOSE,
        SETUP,
        INPUTS,
        OUTPUTS,
        ENERGY,
        PITFALLS;

        /** 键片段（小写字段名）。 */
        public String keySuffix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 一条登记：目标 id + 形态。 */
    public record Curated(String id, Kind kind) {

        public Curated {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * 手写解说的关键机器（≥12 台；多方块 + 单方块混合）。id 必须是真实注册的 GT 机器 id；
     * 由 datagen / 单测 / GameTest 守卫其文案键齐全。
     */
    public static final List<Curated> CURATED = List.of(
            // 多方块
            new Curated("gtceu:coke_oven", Kind.MULTIBLOCK),
            new Curated("gtceu:electric_blast_furnace", Kind.MULTIBLOCK),
            new Curated("gtceu:large_chemical_reactor", Kind.MULTIBLOCK),
            new Curated("gtceu:assembly_line", Kind.MULTIBLOCK),
            new Curated("gtceu:cleanroom", Kind.MULTIBLOCK),
            new Curated("gtceu:distillation_tower", Kind.MULTIBLOCK),
            new Curated("gtceu:gas_large_turbine", Kind.MULTIBLOCK),
            new Curated("gtceu:large_mixer", Kind.MULTIBLOCK),
            // 单方块
            new Curated("gtceu:lv_macerator", Kind.SINGLE_BLOCK),
            new Curated("gtceu:lv_centrifuge", Kind.SINGLE_BLOCK),
            new Curated("gtceu:lv_compressor", Kind.SINGLE_BLOCK),
            new Curated("gtceu:lv_mixer", Kind.SINGLE_BLOCK),
            new Curated("gtceu:lv_electrolyzer", Kind.SINGLE_BLOCK));

    private MachineDescriptions() {
    }

    /** 该 target id 是否有手写解说。 */
    public static boolean isCurated(String targetId) {
        return find(targetId).isPresent();
    }

    /** 按 id 查找登记（未知 / {@code null} 返回空）。 */
    public static Optional<Curated> find(String targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        for (Curated curated : CURATED) {
            if (curated.id().equals(targetId)) {
                return Optional.of(curated);
            }
        }
        return Optional.empty();
    }

    /**
     * 某台机器某字段的本地化键：
     * {@code ponder.gtsnponder.desc.<sanitized-id>.<field>}。
     */
    public static String key(String targetId, Field field) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(field, "field");
        return PREFIX + GeneratedKeys.sanitize(targetId) + "." + field.keySuffix();
    }

    /** 全部手写说明键（datagen / 测试守卫遍历用，稳定顺序：登记序 × 字段序）。 */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>(CURATED.size() * Field.values().length);
        for (Curated curated : CURATED) {
            for (Field field : Field.values()) {
                keys.add(key(curated.id(), field));
            }
        }
        return List.copyOf(keys);
    }

    /** 全部登记的目标 id（稳定顺序）。 */
    public static List<String> curatedIds() {
        List<String> ids = new ArrayList<>(CURATED.size());
        for (Curated curated : CURATED) {
            ids.add(curated.id());
        }
        return List.copyOf(ids);
    }
}
