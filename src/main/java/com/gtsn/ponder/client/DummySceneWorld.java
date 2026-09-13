package com.gtsn.ponder.client;

import com.gtsn.ponder.bridge.SceneElementResolver;
import com.gtsn.ponder.engine.director.CameraState;
import com.gtsn.ponder.engine.director.SceneWorld;
import com.gtsn.ponder.engine.director.SceneWorldState;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneElement;
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
 * <h2>尚未可真实呈现的效果</h2>
 * <p>{@code installModule} / {@code pulseFormed} / {@code emitParticles} 目前<b>只记录状态</b>
 * （模块映射 / 成型脉冲列表 / 粒子目标列表），不改变虚世界几何——模块合并、成型状态与粒子系统
 * 属后续工单。记录它们是为保证 {@link #snapshot()} / {@link #restore(SceneWorldState)} 的
 * <b>完整还原</b>：任何 seek / rewind 之后这些状态也与「顺序播放到该时刻」一致。</p>
 *
 * <h2>快照</h2>
 * <p>快照深拷贝虚世界方块状态 + 全部分段 / 高亮 / 轮廓 / 模块 / 脉冲 / 粒子 / 旁白 / 相机状态；
 * {@code restore} 先清空并重建虚世界，再恢复集合与相机，随后刷新视口。这是 ticket #3
 * 「seek = 从基线重放」确定性的客户端基座。</p>
 *
 * <p>客户端专用类（引用 LDLib 与 Minecraft）。</p>
 */
public final class DummySceneWorld implements SceneWorld {

    private static final int HIGHLIGHT_COLOR = 0xFFFFC000;
    private static final int OUTLINE_COLOR = 0xFF40C0FF;

    private final StructureSource structure;
    private final SceneData scene;
    private final LdlibSceneViewport viewport;
    private final SceneWidget sceneWidget;
    private final TrackedDummyWorld dummy;
    private final SceneElementResolver resolver;
    private final Map<String, List<BlockPos>> elementPositions;

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

    public DummySceneWorld(StructureSource structure, SceneData scene, LdlibSceneViewport viewport) {
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
        this.viewport = Objects.requireNonNull(viewport, "viewport must not be null");
        this.sceneWidget = viewport.sceneWidgetForOverlay();
        this.dummy = viewport.dummyWorld();
        this.resolver = new SceneElementResolver(structure);
        this.elementPositions = resolveElementPositions(scene);
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
        highlightedPositions = unionOfEnabled(highlights);
    }

    @Override
    public void setOutline(String targetId, boolean active) {
        if (targetId == null) {
            return;
        }
        outlines.put(targetId, active);
        outlinedPositions = unionOfEnabled(outlines);
    }

    @Override
    public void setNarration(String narrationKey) {
        this.narration = narrationKey;
    }

    @Override
    public void setCamera(CameraState camera) {
        this.camera = camera;
        if (camera != null) {
            double zoom = camera.distance() > 0.0d ? camera.distance() : ViewportController.DEFAULT_ZOOM;
            viewport.applySceneCamera(camera.yaw(), camera.pitch(), zoom);
        }
    }

    @Override
    public void installModule(String slotId, String moduleId) {
        // 记录状态：模块合并属后续工单；此处保证快照 / 重放一致性。
        modules.put(slotId, moduleId);
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
        highlightedPositions = unionOfEnabled(highlights);
        outlinedPositions = unionOfEnabled(outlines);

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
        viewport.setVisibleBlocks(new ArrayList<>(union));
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
        if (!highlightedPositions.isEmpty()) {
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : highlightedPositions) {
                widget.drawFacingBorder(poseStack, new BlockPosFace(pos, Direction.UP), HIGHLIGHT_COLOR);
            }
        }
        if (!outlinedPositions.isEmpty()) {
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : outlinedPositions) {
                widget.drawFacingBorder(poseStack, new BlockPosFace(pos, Direction.UP), OUTLINE_COLOR, 1);
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
