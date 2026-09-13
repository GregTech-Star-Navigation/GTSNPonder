package com.gtsn.ponder.client;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.render.GuiGraphicsRenderContext;
import com.gtsn.lib.ui.render.RenderContext;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.SceneViewport;
import com.gtsn.ponder.viewport.ViewportController;
import com.gtsn.ponder.viewport.ViewportRenderer;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SceneViewport} 的 LDLib 实现：以 {@link SceneWidget} 渲染一个 {@link TrackedDummyWorld}，
 * 世界内容由中立 DTO {@link StructureSource} 逐格构建（方块 id → 原版 {@code BlockState}）。
 *
 * <p>契约落实：</p>
 * <ul>
 *   <li><b>矩形 / resize</b>：{@link #setBounds} 记录宿主矩形；每帧渲染前把 LDLib 控件的
 *       绝对位置 / 尺寸同步为该矩形，实现 resize（窗口尺寸 / GUI 缩放变化）后的重定位。</li>
 *   <li><b>输入</b>：宿主把视口局部坐标传入；相机状态由纯 Java {@link ViewportController}
 *       维护（拖拽旋转 / 滚轮缩放），再机械映射到 {@link SceneWidget#setCameraYawAndPitch} /
 *       {@link SceneWidget#setZoom}。因此行为可在 headless 单测断言，渲染侧不掺逻辑。</li>
 *   <li><b>裁剪</b>：LDLib 世界渲染器在 {@code setupCamera} 内把 GPU 视口
 *       （{@code RenderSystem.viewport}）钳到矩形并只在其中 clear，故场景不会溢出矩形
 *       （不额外叠加 GTSN 裁剪，避免与渲染器自身的视口/矩阵状态冲突）。</li>
 *   <li><b>z 序</b>：不绘制任何超出矩形的内容；在 GTSN 控件树中先于覆盖层渲染 → 覆盖层在上。</li>
 *   <li><b>partial-tick</b>：{@link #partialTick(float)} 记录后转交 {@link SceneWidget}。</li>
 * </ul>
 *
 * <p>客户端专用类（引用 LDLib 客户端渲染与 {@code GuiGraphics}）：专职服务端不得加载。</p>
 */
public final class LdlibSceneViewport implements SceneViewport, ViewportRenderer {

    private final StructureSource structure;
    private final SceneWidget sceneWidget;
    private final ViewportController controller = new ViewportController();

    private Rect bounds = Rect.ZERO;
    private float partialTick;
    private int appliedX = Integer.MIN_VALUE;
    private int appliedY = Integer.MIN_VALUE;
    private int appliedWidth = Integer.MIN_VALUE;
    private int appliedHeight = Integer.MIN_VALUE;

    private LdlibSceneViewport(StructureSource structure, SceneWidget sceneWidget) {
        this.structure = structure;
        this.sceneWidget = sceneWidget;
        applyCamera();
    }

    /**
     * 在给定客户端世界（作为 LDO 虚世界的 biome / tint 代理）上创建视口，并载入结构。
     *
     * @param proxyLevel 客户端世界（{@code Minecraft.getInstance().level}）；不得为空
     * @param structure  要渲染的结构（由 GT 适配器产出）
     */
    public static LdlibSceneViewport create(Level proxyLevel, StructureSource structure) {
        if (proxyLevel == null) {
            throw new IllegalArgumentException("proxyLevel must not be null (open the viewport in a world)");
        }
        SceneWidget widget = new SceneWidget(0, 0, 1, 1, proxyLevel);
        widget.setClientSideWidget();
        if (widget.getRenderer() == null) {
            // 控件未挂在 ModularUI 上时 isRemote() 可能为 false，显式建场景更确定。
            widget.createScene(proxyLevel);
        }
        widget.setClearColor(0xFF101418);
        widget.setRenderFacing(false);
        widget.setRenderSelect(false);
        widget.setDraggable(true);
        widget.setScalable(true);
        widget.setIntractable(true);
        widget.setHoverTips(false);

        TrackedDummyWorld dummy = widget.getDummyWorld();
        Map<BlockPos, BlockInfo> blocks = resolveBlocks(structure);
        if (dummy != null && !blocks.isEmpty()) {
            dummy.addBlocks(blocks);
            widget.setRenderedCore(blocks.keySet());
        }
        return new LdlibSceneViewport(structure, widget);
    }

    /** 把 DTO 的方块 id 解析为实际方块状态（缺资源 / 空气跳过）。 */
    private static Map<BlockPos, BlockInfo> resolveBlocks(StructureSource structure) {
        Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
        for (StructureBlock block : structure.blocks()) {
            ResourceLocation location = ResourceLocation.tryParse(block.blockId());
            if (location == null) {
                continue;
            }
            Block target = BuiltInRegistries.BLOCK.get(location);
            if (target == Blocks.AIR) {
                continue;
            }
            BlockState state = target.defaultBlockState();
            blocks.put(new BlockPos(block.x(), block.y(), block.z()), BlockInfo.fromBlockState(state));
        }
        return blocks;
    }

    public StructureSource structure() {
        return structure;
    }

    /** 已渲染进虚世界的方块数量（自动测试用）。 */
    public int renderedBlockCount() {
        TrackedDummyWorld dummy = sceneWidget.getDummyWorld();
        return dummy == null ? 0 : dummy.getRenderedBlocks().size();
    }

    /** LDLib 侧实际生效的缩放（证明相机状态确实写入了渲染器）。 */
    public float ldlibZoom() {
        return sceneWidget.getZoom();
    }

    /** LDLib 侧实际生效的水平旋转。 */
    public float ldlibRotationYaw() {
        return sceneWidget.getRotationYaw();
    }

    /** LDLib 侧实际生效的俯仰。 */
    public float ldlibRotationPitch() {
        return sceneWidget.getRotationPitch();
    }

    @Override
    public void setBounds(Rect bounds) {
        this.bounds = bounds;
    }

    @Override
    public Rect bounds() {
        return bounds;
    }

    @Override
    public Rect clipRect() {
        return bounds;
    }

    @Override
    public boolean mouseMoved(double localX, double localY) {
        return controller.moved(localX, localY);
    }

    @Override
    public boolean mousePressed(double localX, double localY, int button) {
        return controller.press(localX, localY, button);
    }

    @Override
    public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) {
        boolean consumed = controller.dragged(localX, localY, dragX, dragY);
        if (consumed) {
            applyCamera();
        }
        return consumed;
    }

    @Override
    public boolean mouseReleased(double localX, double localY, int button) {
        return controller.released(localX, localY, button);
    }

    @Override
    public boolean mouseScrolled(double localX, double localY, double scrollDelta) {
        boolean consumed = controller.scrolled(localX, localY, scrollDelta);
        if (consumed) {
            applyCamera();
        }
        return consumed;
    }

    @Override
    public void partialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    @Override
    public boolean isDragging() {
        return controller.isDragging();
    }

    @Override
    public double cameraYaw() {
        return controller.rotationYaw();
    }

    @Override
    public double cameraPitch() {
        return controller.rotationPitch();
    }

    @Override
    public double cameraZoom() {
        return controller.zoom();
    }

    @Override
    public void resetCamera() {
        controller.reset();
        applyCamera();
    }

    /** 把纯状态机的角度 / 缩放机械映射到 LDLib 控件。 */
    private void applyCamera() {
        sceneWidget.setCameraYawAndPitch((float) controller.rotationYaw(), (float) controller.rotationPitch());
        sceneWidget.setZoom((float) controller.zoom());
    }

    /**
     * 在宿主控件树中的 z 位置绘制场景。仅接受 GTSN 客户端渲染上下文；矩形为空时跳过。
     * 裁剪由 LDLib 渲染器的 GPU 视口保证（见类 javadoc）。
     */
    @Override
    public void renderViewport(RenderContext context, Rect bounds) {
        if (!(context instanceof GuiGraphicsRenderContext graphicsContext)) {
            return;
        }
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        syncWidgetGeometry(bounds);
        sceneWidget.updateScreen();
        sceneWidget.drawInBackground(graphicsContext.graphics(), 0, 0, partialTick);
    }

    /** 只在几何变化时改 LDLib 控件位置 / 尺寸（避免每帧触发内部重算）。 */
    private void syncWidgetGeometry(Rect bounds) {
        if (bounds.x() != appliedX || bounds.y() != appliedY) {
            appliedX = bounds.x();
            appliedY = bounds.y();
            sceneWidget.setSelfPosition(appliedX, appliedY);
        }
        if (bounds.width() != appliedWidth || bounds.height() != appliedHeight) {
            appliedWidth = bounds.width();
            appliedHeight = bounds.height();
            sceneWidget.setSize(appliedWidth, appliedHeight);
        }
    }

    /** 暴露底层控件（自动测试 / 诊断）。 */
    public SceneWidget sceneWidget() {
        return sceneWidget;
    }

    /** 便于日志：结构 id 与尺寸。 */
    @Override
    public String toString() {
        return "LdlibSceneViewport[" + structure.id() + " " + structure.blockCount() + " blocks @ "
                + bounds + "]";
    }

    /** 仅用于测试 / 诊断：当前结构页的方块列表。 */
    public List<StructureBlock> structureBlocks() {
        return structure.blocks();
    }
}
