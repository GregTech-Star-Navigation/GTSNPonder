package com.gtsn.ponder.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 结构源（StructureSource）：本 mod 自有的**中立结构数据 DTO**（见 {@code CONTEXT.md}）。
 *
 * <p>由唯一允许访问格雷科技的适配包 {@code com.gtsn.ponder.gt} 从 GT 结构数据产出，
 * 供视口 / 世界桥（把方块塞进虚世界）与后续的自动生成器消费。它刻意**不携带任何
 * GT 或 Minecraft 类型**：方块以注册名（字符串）表达，坐标以 0 基局部整数表达，
 * 因此可在无 MC 环境（headless 单测 / 专职服务端）中以夹具构造与断言。</p>
 *
 * <p><b>坐标 / 索引约定</b>：{@link StructureBlock} 的 {@code (x, y, z)} 是结构局部坐标，
 * 与 GT {@code BlockInfo[][][]} 的消费约定一致——数组 index0 = X、index1 = Y、index2 = Z
 * （注意 GT {@code MultiblockShapeInfo} 字段注释写作 {@code [z][y][x]}，但产出与消费两侧
 * 都按 index0 = X 处理；详见 {@code GtStructureAdapter} 的说明）。</p>
 *
 * <p>本 DTO 承载「分层方块（含仓口 / 总线角色）+ 控制器位置 + 模块位区域及其可接受模块」。
 * 仓口 / 总线角色由 {@link StructureRole} 表达（方块级），模块位由 {@link ModuleSlot} 表达
 * （区域级，坐标与方块同一结构坐标系）。</p>
 */
public final class StructureSource {

    private final String id;
    private final String displayName;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final List<StructureBlock> blocks;
    private final List<ModuleSlot> moduleSlots;

    private StructureSource(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.displayName = builder.displayName == null ? builder.id : builder.displayName;
        if (builder.sizeX <= 0 || builder.sizeY <= 0 || builder.sizeZ <= 0) {
            throw new IllegalArgumentException("structure size must be positive: "
                    + builder.sizeX + "x" + builder.sizeY + "x" + builder.sizeZ);
        }
        this.sizeX = builder.sizeX;
        this.sizeY = builder.sizeY;
        this.sizeZ = builder.sizeZ;
        this.controllerX = builder.controllerX;
        this.controllerY = builder.controllerY;
        this.controllerZ = builder.controllerZ;
        this.blocks = List.copyOf(builder.blocks);
        for (StructureBlock block : this.blocks) {
            if (block.x() >= sizeX || block.y() >= sizeY || block.z() >= sizeZ) {
                throw new IllegalArgumentException("block " + block + " is outside the declared size "
                        + sizeX + "x" + sizeY + "x" + sizeZ);
            }
        }
        this.moduleSlots = List.copyOf(builder.moduleSlots);
        for (ModuleSlot slot : this.moduleSlots) {
            if (slot.offsetX() + slot.sizeX() > sizeX
                    || slot.offsetY() + slot.sizeY() > sizeY
                    || slot.offsetZ() + slot.sizeZ() > sizeZ) {
                throw new IllegalArgumentException("module slot " + slot + " is outside the declared size "
                        + sizeX + "x" + sizeY + "x" + sizeZ);
            }
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 稳定标识（通常是 GT 机器 / 多方块 id，如 {@code gtceu:coke_oven}）。 */
    public String id() {
        return id;
    }

    /** 展示名（本地化键或原文；视口 / UI 自行决定是否本地化）。 */
    public String displayName() {
        return displayName;
    }

    /** 结构包围盒在 X 轴上的尺寸。 */
    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** 是否声明了控制器单元（多方块的主机方块位置）。 */
    public boolean hasController() {
        return controllerX >= 0 && controllerY >= 0 && controllerZ >= 0;
    }

    /** 控制器单元局部坐标；无控制器时抛异常（先用 {@link #hasController()} 判定）。 */
    public ControllerCell controller() {
        if (!hasController()) {
            throw new IllegalStateException("structure '" + id + "' declares no controller cell");
        }
        return new ControllerCell(controllerX, controllerY, controllerZ);
    }

    /** 非空格方块单元（有序，与适配器产出顺序一致）；空方块不进入本列表。 */
    public List<StructureBlock> blocks() {
        return blocks;
    }

    public int blockCount() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** 仓口 / 总线单元（{@link StructureRole#isHatch()} 为真者），保持 {@link #blocks()} 的相对顺序。 */
    public List<StructureBlock> hatches() {
        List<StructureBlock> hatches = new ArrayList<>();
        for (StructureBlock block : blocks) {
            if (block.isHatch()) {
                hatches.add(block);
            }
        }
        return List.copyOf(hatches);
    }

    /** 模块位区域（有序，与适配器产出顺序一致）；无模块位时为空列表。 */
    public List<ModuleSlot> moduleSlots() {
        return moduleSlots;
    }

    public boolean hasModuleSlots() {
        return !moduleSlots.isEmpty();
    }

    public int moduleSlotCount() {
        return moduleSlots.size();
    }

    /** 结构包围盒体积（用于「挑最小多方块」等选择策略）。 */
    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    @Override
    public String toString() {
        return "StructureSource[" + id + " " + sizeX + "x" + sizeY + "x" + sizeZ
                + " blocks=" + blocks.size() + " hatches=" + hatches().size()
                + " moduleSlots=" + moduleSlots.size()
                + (hasController() ? " controller=" + controllerX + "," + controllerY
                + "," + controllerZ : " no-controller") + "]";
    }

    /** 控制器单元局部坐标（0 基）。 */
    public record ControllerCell(int x, int y, int z) {
    }

    public static final class Builder {

        private final String id;
        private String displayName;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private int controllerX = -1;
        private int controllerY = -1;
        private int controllerZ = -1;
        private final List<StructureBlock> blocks = new ArrayList<>();
        private final List<ModuleSlot> moduleSlots = new ArrayList<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder size(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            return this;
        }

        public Builder controller(int x, int y, int z) {
            this.controllerX = x;
            this.controllerY = y;
            this.controllerZ = z;
            return this;
        }

        public Builder addBlock(StructureBlock block) {
            this.blocks.add(Objects.requireNonNull(block, "block"));
            return this;
        }

        /** 便捷添加：按局部坐标与方块注册名加入一个单元。 */
        public Builder addBlock(int x, int y, int z, String blockId) {
            return addBlock(new StructureBlock(x, y, z, blockId));
        }

        /** 便捷添加：按局部坐标、方块注册名与角色加入一个单元。 */
        public Builder addBlock(int x, int y, int z, String blockId, StructureRole role) {
            return addBlock(new StructureBlock(x, y, z, blockId, role));
        }

        /** 加入一个模块位区域（坐标与方块同一结构坐标系）。 */
        public Builder addModuleSlot(ModuleSlot moduleSlot) {
            this.moduleSlots.add(Objects.requireNonNull(moduleSlot, "moduleSlot"));
            return this;
        }

        public StructureSource build() {
            return new StructureSource(this);
        }
    }
}
