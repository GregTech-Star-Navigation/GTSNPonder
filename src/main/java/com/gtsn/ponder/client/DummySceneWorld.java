package com.gtsn.ponder.client;

import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.director.CameraState;
import com.gtsn.ponder.engine.director.SceneWorld;
import com.gtsn.ponder.engine.director.SceneWorldState;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneElement;
import com.gtsn.ponder.engine.model.SceneParams;
import com.gtsn.ponder.structure.ModuleSlot;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.ViewportController;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link SceneWorld} 的客户端实现：把导演核心的效果施加到 LDLib {@link TrackedDummyWorld}
 * （由 {@link LdlibSceneViewport} 持有）上。这是「纯导演核心驱动真实场景世界」的桥。
 *
 * <h2>元素 → 坐标</h2>
 * <p>场景的 {@code elements[]}（稳定字符串 ID）经 {@link SceneElementResolver} 解析为结构局部
 * 坐标（见 {@link StructureSource} 的索引约定）。分段显隐通过 LDLib 渲染器的「已渲染集合」实现：
 * 未在集合内的坐标 {@code getBlockState} 返回空气，因而不绘制。</p>
 *
 * <h2>效果语义</h2>
 * <ul>
 *   <li>{@code setSectionVisible}：把分段坐标并入 / 移出已渲染集合（分段出现 / 隐藏）；</li>
 *   <li>{@code replaceBlocks}：把元素坐标处的方块替换为给定注册名的方块；</li>
 *   <li>{@code setHighlight} / {@code setOutline}：记录目标并在视口 {@code afterWorldRender} 钩子中
 *       为命中方块绘制边框（高亮 / 轮廓）；</li>
 *   <li>{@code setNarration}：记录旁白本地化键（表现层据此渲染）；</li>
 *   <li>{@code setCamera}：映射到视口相机（yaw / pitch / distance）。</li>
 * </ul>
 *
 * <h2>模块安装（有意义的世界效果）</h2>
 * <p>{@code installModule} 会把模块位区域的单元替换为「已安装模块」的外观方块（候选见
 * {@link #MODULE_BLOCK_CANDIDATES}；最终方块 id 属世界桥关注点，不进入冻结的场景数据），
 * 并把该槽位区域并入可见集——空模块位安装后模块才出现。{@code pulseFormed} / {@code emitParticles}
 * 仍<b>只记录状态</b>（成型状态与粒子系统属后续工单），记录它们是为保证
 * {@link #snapshot()} / {@link #restore(SceneWorldState)} 的<b>完整还原</b>：任何 seek / rewind
 * 之后这些状态也与「顺序播放到该时刻」一致。</p>
 *
 * <h2>快照</h2>
 * <p>快照深拷贝虚世界方块状态 + 全部分段 / 高亮 / 轮廓 / 模块 / 脉冲 / 粒子 / 旁白 / 相机状态；
 * {@code restore} 先清空并重建虚世界，再恢复集合与相机，随后刷新视口。这是 ticket #3
 * 「seek = 从基线重放」确定性的客户端基座。</p>
 *
 * <p>客户端专用类（引用 LDLib 与 Minecraft）。</p>
 */
public final class DummySceneWorld implements SceneWorld {

    /** 控制器高亮色（金）——与播放屏常驻图例共用。 */
    public static final int HIGHLIGHT_COLOR = 0xFFFFC000;
    /** 仓口 / 总线轮廓色（蓝）——与播放屏常驻图例共用。 */
    public static final int OUTLINE_COLOR = 0xFF40C0FF;
    /** 模块位区域轮廓色（绿）——与播放屏常驻图例共用。 */
    public static final int MODULE_SLOT_COLOR = 0xFF5CE65C;

    /**
     * 已安装模块在虚世界里的<b>外观方块</b>候选（按顺序取第一个已注册者）：首选 GT 的计算机机壳，
     * 依次回退到钢 / 青铜机壳与原版青金石块，保证任何环境下「模块出现」都有可见几何。
     */
    private static final List<String> MODULE_BLOCK_CANDIDATES = List.of(
            "gtceu:computer_casing",
            "gtceu:steel_casing",
            "gtceu:bronze_casing",
            "minecraft:lapis_block");

    private final StructureSource structure;
    private final SceneData scene;
    private final LdlibSceneViewport viewport;
    private final SceneWidget sceneWidget;
    private final TrackedDummyWorld dummy;
    private final SceneElementResolver resolver;
    private final Map<String, List<BlockPos>> elementPositions;
    /** 属于「模块位区域」的元素 id（选择器为 {@link ModuleSlot#SELECTOR}），用于单独着色。 */
    private final Set<String> moduleSlotElementIds;

    private final Set<String> visibleSections = new LinkedHashSet<>();
    private final Map<String, Boolean> highlights = new LinkedHashMap<>();
    private final Map<String, Boolean> outlines = new LinkedHashMap<>();
    private final Map<String, String> modules = new LinkedHashMap<>();
    private final List<String> formedPulses = new ArrayList<>();
    private final List<String> particles = new ArrayList<>();
    private String narration;
    private CameraState camera;
    private List<BlockPos> highlightedPositions = List.of();
    private List<BlockPos> outlinedPositions = List.of();
    private List<BlockPos> moduleSlotOutlinedPositions = List.of();

    public DummySceneWorld(StructureSource structure, SceneData scene, LdlibSceneViewport viewport) {
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
        this.viewport = Objects.requireNonNull(viewport, "viewport must not be null");
        this.sceneWidget = viewport.sceneWidgetForOverlay();
        this.dummy = viewport.dummyWorld();
        this.resolver = new SceneElementResolver(structure);
        this.elementPositions = resolveElementPositions(scene);
        this.moduleSlotElementIds = resolveModuleSlotElementIds(scene);
        this.sceneWidget.setAfterWorldRender(this::drawOverlays);
        // 分段默认隐藏：由场景的 showSection 步骤逐段揭示（经典「思索」搭建叙事）。
        applyVisibility();
    }

    public StructureSource structure() {
        return structure;
    }

    public SceneData scene() {
        return scene;
    }

    private Map<String, List<BlockPos>> resolveElementPositions(SceneData scene) {
        Map<String, List<BlockPos>> positions = new LinkedHashMap<>();
        for (SceneElement element : scene.elements()) {
            List<StructureBlock> blocks = resolver.resolve(element);
            List<BlockPos> pos = new ArrayList<>(blocks.size());
            for (StructureBlock block : blocks) {
                pos.add(new BlockPos(block.x(), block.y(), block.z()));
            }
            positions.put(element.id(), List.copyOf(pos));
        }
        return Map.copyOf(positions);
    }

    /** 某元素解析出的坐标（诊断 / 自动测试）。 */
    public List<BlockPos> elementPositions(String elementId) {
        return elementPositions.getOrDefault(elementId, List.of());
    }

    /** 场景里以「模块位区域」选择器声明的元素 id 集合（诊断 / 自动测试）。 */
    public Set<String> moduleSlotElementIds() {
        return moduleSlotElementIds;
    }

    /** 已安装模块的槽位占用（槽位元素 id → 模块 id）；快照 / 重放一致。 */
    public Map<String, String> installedModules() {
        return Map.copyOf(modules);
    }

    /** 模块位区域轮廓当前覆盖的单元数（诊断 / 自动测试）。 */
    public int moduleSlotOutlineCount() {
        return moduleSlotOutlinedPositions.size();
    }

    private static Set<String> resolveModuleSlotElementIds(SceneData scene) {
        Set<String> ids = new LinkedHashSet<>();
        for (SceneElement element : scene.elements()) {
            if (ModuleSlot.SELECTOR.equals(SceneParams.string(element.params(), "selector", null))) {
                ids.add(element.id());
            }
        }
        return Set.copyOf(ids);
    }

    /** 当前实际渲染的方块数量（分段显隐的可观察量）。 */
    public int visibleBlockCount() {
        return sceneWidget.getCore().size();
    }

    /** 当前处于可见状态的分段 ID（诊断 / 自动测试）。 */
    public Set<String> visibleSections() {
        return Set.copyOf(visibleSections);
    }

    /** 高亮状态（目标 ID → 是否高亮）。 */
    public Map<String, Boolean> highlights() {
        return Map.copyOf(highlights);
    }

    /** 轮廓状态（目标 ID → 是否勾勒）。 */
    public Map<String, Boolean> outlines() {
        return Map.copyOf(outlines);
    }

    public String narration() {
        return narration;
    }

    public CameraState camera() {
        return camera;
    }

    // --- SceneWorld 效果 -----------------------------------------------------

    @Override
    public void setSectionVisible(String sectionId, boolean visible) {
        if (sectionId == null) {
            return;
        }
        if (visible) {
            visibleSections.add(sectionId);
        } else {
            visibleSections.remove(sectionId);
        }
        applyVisibility();
    }

    @Override
    public void replaceBlocks(String elementId, String blockId) {
        if (blockId == null) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location == null) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (block == Blocks.AIR) {
            return;
        }
        BlockState state = block.defaultBlockState();
        for (BlockPos pos : elementPositions.getOrDefault(elementId, List.of())) {
            dummy.addBlock(pos, BlockInfo.fromBlockState(state));
        }
    }

    @Override
    public void setHighlight(String targetId, boolean active) {
        if (targetId == null) {
            return;
        }
        highlights.put(targetId, active);
        recomputeOverlays();
    }

    @Override
    public void setOutline(String targetId, boolean active) {
        if (targetId == null) {
            return;
        }
        outlines.put(targetId, active);
        recomputeOverlays();
    }

    @Override
    public void setNarration(String narrationKey) {
        this.narration = narrationKey;
    }

    @Override
    public void setCamera(CameraState camera) {
        this.camera = camera;
        if (camera == null) {
            return;
        }
        if (camera.fits()) {
            // 取景自适应：视口按包围盒 + 纵横比反算距离，使结构占满视口（自动生成场景）。
            viewport.applySceneCameraFit(camera.yaw(), camera.pitch(), camera.fitMargin());
            return;
        }
        double zoom = camera.distance() > 0.0d ? camera.distance() : ViewportController.DEFAULT_ZOOM;
        viewport.applySceneCamera(camera.yaw(), camera.pitch(), zoom);
    }

    @Override
    public void installModule(String slotId, String moduleId) {
        if (slotId == null) {
            return;
        }
        // 有意义的世界效果：把模块位的区域单元替换为「已安装模块」的外观方块，槽位随即被占用；
        // 空模块位安装后模块才出现（区域单元原本不在任何可建分段内，故此处一并纳入可见集）。
        modules.put(slotId, moduleId);
        BlockState moduleState = moduleBlockState();
        if (moduleState != null) {
            BlockInfo moduleInfo = BlockInfo.fromBlockState(moduleState);
            for (BlockPos pos : elementPositions.getOrDefault(slotId, List.of())) {
                dummy.addBlock(pos, moduleInfo);
            }
        }
        applyVisibility();
    }

    @Override
    public void pulseFormed(String controllerId) {
        // 记录状态：成型状态与模块合并属后续工单；此处保证快照 / 重放一致性。
        formedPulses.add(controllerId);
    }

    @Override
    public void emitParticles(String targetId) {
        // 记录状态：粒子系统属后续工单；此处保证快照 / 重放一致性。
        particles.add(targetId);
    }

    // --- 快照 / 还原 ---------------------------------------------------------

    @Override
    public SceneWorldState snapshot() {
        return new BridgeState(
                new LinkedHashMap<>(dummy.getRenderedBlocks()),
                List.copyOf(visibleSections),
                new LinkedHashMap<>(highlights),
                new LinkedHashMap<>(outlines),
                new LinkedHashMap<>(modules),
                List.copyOf(formedPulses),
                List.copyOf(particles),
                narration,
                camera);
    }

    @Override
    public void restore(SceneWorldState state) {
        if (!(state instanceof BridgeState snapshot)) {
            throw new IllegalArgumentException("unexpected scene world state: " + state);
        }
        dummy.clear();
        dummy.addBlocks(snapshot.blocks());

        replaceSet(visibleSections, snapshot.visibleSections());
        highlights.clear();
        highlights.putAll(snapshot.highlights());
        outlines.clear();
        outlines.putAll(snapshot.outlines());
        modules.clear();
        modules.putAll(snapshot.modules());
        formedPulses.clear();
        formedPulses.addAll(snapshot.formedPulses());
        particles.clear();
        particles.addAll(snapshot.particles());
        narration = snapshot.narration();
        camera = snapshot.camera();
        recomputeOverlays();

        applyVisibility();
        if (camera != null) {
            setCamera(camera);
        }
    }

    // --- 内部 ----------------------------------------------------------------

    private void applyVisibility() {
        Set<BlockPos> union = new LinkedHashSet<>();
        for (String section : visibleSections) {
            union.addAll(elementPositions.getOrDefault(section, List.of()));
        }
        // 已安装模块的槽位区域始终可见：空模块位安装后模块才出现。
        for (String slot : modules.keySet()) {
            union.addAll(elementPositions.getOrDefault(slot, List.of()));
        }
        viewport.setVisibleBlocks(new ArrayList<>(union));
    }

    /**
     * 依据当前高亮 / 轮廓状态重算覆盖层坐标：模块位区域（选择器 {@link ModuleSlot#SELECTOR}）
     * 单独归类，以便用第三种颜色（绿）与普通仓口轮廓（蓝）区分。
     */
    private void recomputeOverlays() {
        highlightedPositions = unionOfEnabled(highlights);
        Set<BlockPos> plain = new LinkedHashSet<>();
        Set<BlockPos> slotRegions = new LinkedHashSet<>();
        for (Map.Entry<String, Boolean> entry : outlines.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            List<BlockPos> positions = elementPositions.getOrDefault(entry.getKey(), List.of());
            if (moduleSlotElementIds.contains(entry.getKey())) {
                slotRegions.addAll(positions);
            } else {
                plain.addAll(positions);
            }
        }
        outlinedPositions = List.copyOf(plain);
        moduleSlotOutlinedPositions = List.copyOf(slotRegions);
    }

    /** 已安装模块的外观方块状态；候选全部缺席时返回 {@code null}（不改变几何）。 */
    private static BlockState moduleBlockState() {
        for (String blockId : MODULE_BLOCK_CANDIDATES) {
            ResourceLocation location = ResourceLocation.tryParse(blockId);
            if (location == null) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(location);
            if (block != Blocks.AIR) {
                return block.defaultBlockState();
            }
        }
        return null;
    }

    private List<BlockPos> unionOfEnabled(Map<String, Boolean> flags) {
        Set<BlockPos> union = new LinkedHashSet<>();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                union.addAll(elementPositions.getOrDefault(entry.getKey(), List.of()));
            }
        }
        return List.copyOf(union);
    }

    private void drawOverlays(SceneWidget widget) {
        // 控制器高亮：画全 6 面形成金色线框盒，保证从任意相机角度都能读到（单面在侧视时会被压扁）。
        if (!highlightedPositions.isEmpty()) {
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : highlightedPositions) {
                for (Direction face : Direction.values()) {
                    widget.drawFacingBorder(poseStack, new BlockPosFace(pos, face), HIGHLIGHT_COLOR);
                }
            }
        }
        // 仓口 / 总线轮廓：6 面蓝色细线框（inner=1 更细），从任意相机角度都可读；数量由生成器收敛
        // （≤ HATCH_OUTLINE_LIMIT）并均匀抽样以减少重叠。drawFacingBorder 关闭深度测试，故远侧仓口
        // 的轮廓也不会被结构遮挡。
        if (!outlinedPositions.isEmpty()) {
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : outlinedPositions) {
                for (Direction face : Direction.values()) {
                    widget.drawFacingBorder(poseStack, new BlockPosFace(pos, face), OUTLINE_COLOR, 1);
                }
            }
        }
        // 模块位区域：6 面绿色细线框覆盖区域的每个单元，使「槽位是区域而非单块」在视觉上成立，
        // 并与控制器（金）/ 仓口（蓝）区分。
        if (!moduleSlotOutlinedPositions.isEmpty()) {
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : moduleSlotOutlinedPositions) {
                for (Direction face : Direction.values()) {
                    widget.drawFacingBorder(poseStack, new BlockPosFace(pos, face), MODULE_SLOT_COLOR, 1);
                }
            }
        }
    }

    private static void replaceSet(Set<String> target, List<String> source) {
        target.clear();
        target.addAll(source);
    }

    /** 不可变快照：虚世界方块状态 + 全部效果状态（相机 / 旁白 / 模块 / 脉冲 / 粒子）。 */
    private record BridgeState(
            Map<BlockPos, BlockInfo> blocks,
            List<String> visibleSections,
            Map<String, Boolean> highlights,
            Map<String, Boolean> outlines,
            Map<String, String> modules,
            List<String> formedPulses,
            List<String> particles,
            String narration,
            CameraState camera) implements SceneWorldState {

        private BridgeState {
            blocks = Map.copyOf(blocks);
            visibleSections = List.copyOf(visibleSections);
            highlights = Map.copyOf(highlights);
            outlines = Map.copyOf(outlines);
            modules = Map.copyOf(modules);
            formedPulses = List.copyOf(formedPulses);
            particles = List.copyOf(particles);
        }
    }
}
