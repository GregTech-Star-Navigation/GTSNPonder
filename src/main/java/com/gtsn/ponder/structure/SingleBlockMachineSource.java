package com.gtsn.ponder.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单方块机器源（SingleBlockMachineSource）：本 mod 自有的**中立机器使用数据 DTO**（见 {@code CONTEXT.md}）。
 *
 * <p>多方块用 {@link StructureSource}（分层方块 + 结构页）讲解「怎么搭」；单方块机器没有结构页，
 * 只有机器元数据，本 DTO 就是它的中立表达——用于生成「怎么用」场景（{@code SingleBlockUsageGenerator}）。
 * 它与 {@link StructureSource} 一样刻意**不携带任何 GT 或 Minecraft 类型**：方块以注册名表达，
 * 能力以计数表达（输入 / 输出物品槽、输入 / 输出流体罐、是否接电），配方类型以注册名列表表达，
 * 因此可在 headless 单测 / datagen 中构造与断言。</p>
 *
 * <p><b>数据来源</b>：由唯一允许访问格雷科技的适配包 {@code com.gtsn.ponder.gt}
 * （{@code GtSingleBlockAdapter}）从 {@code MachineDefinition} 的机器元数据产出（机器 id / 等级 /
 * 配方类型的能力计数），**不需要多方块结构页**。所有能力计数在无法读取时退化为 {@code 0}
 * （明确表达「该机器没有此类输入 / 输出」，绝不猜测）。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖（自动生成缝可在 headless 单测中以夹具断言）。</p>
 */
public final class SingleBlockMachineSource {

    private final String id;
    private final String displayName;
    private final String blockId;
    private final int tier;
    private final String tierName;
    private final int itemInputs;
    private final int itemOutputs;
    private final int fluidInputs;
    private final int fluidOutputs;
    private final boolean energy;
    private final List<String> recipeTypeIds;

    private SingleBlockMachineSource(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        if (builder.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.blockId = Objects.requireNonNull(builder.blockId, "blockId");
        if (builder.blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must not be blank");
        }
        this.displayName = builder.displayName == null || builder.displayName.isBlank()
                ? builder.id : builder.displayName;
        if (builder.tier < 0) {
            throw new IllegalArgumentException("tier must be >= 0, got " + builder.tier);
        }
        this.tier = builder.tier;
        this.tierName = builder.tierName == null ? "" : builder.tierName;
        this.itemInputs = requireNonNegative("itemInputs", builder.itemInputs);
        this.itemOutputs = requireNonNegative("itemOutputs", builder.itemOutputs);
        this.fluidInputs = requireNonNegative("fluidInputs", builder.fluidInputs);
        this.fluidOutputs = requireNonNegative("fluidOutputs", builder.fluidOutputs);
        this.energy = builder.energy;
        this.recipeTypeIds = List.copyOf(builder.recipeTypeIds);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 稳定标识（GT 机器定义 id，如 {@code gtceu:lv_macerator}）。 */
    public String id() {
        return id;
    }

    /** 展示名（本地化键或原文；视口 / UI 自行决定是否本地化）。 */
    public String displayName() {
        return displayName;
    }

    /** 机器方块注册名（单方块场景在虚世界里渲染的那一块）。 */
    public String blockId() {
        return blockId;
    }

    /** 电压等级下标（GT tier 索引）；{@code 0} = 最低等级 / 蒸汽。 */
    public int tier() {
        return tier;
    }

    /** 电压等级显示名（如 {@code LV} / {@code MV}）；未知时为空串。 */
    public String tierName() {
        return tierName;
    }

    public int itemInputs() {
        return itemInputs;
    }

    public int itemOutputs() {
        return itemOutputs;
    }

    public int fluidInputs() {
        return fluidInputs;
    }

    public int fluidOutputs() {
        return fluidOutputs;
    }

    /** 是否接入电网（配方类型声明了 EU 输入或输出）。 */
    public boolean hasEnergy() {
        return energy;
    }

    /** 该机器声明的配方类型 id（有序、去重、稳定）；无配方类型时为空列表。 */
    public List<String> recipeTypeIds() {
        return recipeTypeIds;
    }

    public boolean hasRecipeTypes() {
        return !recipeTypeIds.isEmpty();
    }

    public boolean hasItemIo() {
        return itemInputs > 0 || itemOutputs > 0;
    }

    public boolean hasFluidIo() {
        return fluidInputs > 0 || fluidOutputs > 0;
    }

    private static int requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        return "SingleBlockMachineSource[" + id + " tier=" + tier + (tierName.isEmpty() ? "" : "/" + tierName)
                + " block=" + blockId + " items=" + itemInputs + "/" + itemOutputs
                + " fluids=" + fluidInputs + "/" + fluidOutputs + (energy ? " energy" : "")
                + " recipes=" + recipeTypeIds + "]";
    }

    public static final class Builder {

        private final String id;
        private String displayName;
        private String blockId;
        private int tier;
        private String tierName;
        private int itemInputs;
        private int itemOutputs;
        private int fluidInputs;
        private int fluidOutputs;
        private boolean energy;
        private final List<String> recipeTypeIds = new ArrayList<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder blockId(String blockId) {
            this.blockId = blockId;
            return this;
        }

        public Builder tier(int tier, String tierName) {
            this.tier = tier;
            this.tierName = tierName;
            return this;
        }

        public Builder itemInputs(int itemInputs) {
            this.itemInputs = itemInputs;
            return this;
        }

        public Builder itemOutputs(int itemOutputs) {
            this.itemOutputs = itemOutputs;
            return this;
        }

        public Builder fluidInputs(int fluidInputs) {
            this.fluidInputs = fluidInputs;
            return this;
        }

        public Builder fluidOutputs(int fluidOutputs) {
            this.fluidOutputs = fluidOutputs;
            return this;
        }

        public Builder energy(boolean energy) {
            this.energy = energy;
            return this;
        }

        public Builder recipeTypeIds(List<String> recipeTypeIds) {
            this.recipeTypeIds.clear();
            if (recipeTypeIds != null) {
                this.recipeTypeIds.addAll(recipeTypeIds);
            }
            return this;
        }

        public Builder addRecipeType(String recipeTypeId) {
            this.recipeTypeIds.add(Objects.requireNonNull(recipeTypeId, "recipeTypeId"));
            return this;
        }

        public SingleBlockMachineSource build() {
            return new SingleBlockMachineSource(this);
        }
    }
}
