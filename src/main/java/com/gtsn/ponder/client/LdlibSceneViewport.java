package com.gtsn.ponder.client;

import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.render.GuiGraphicsRenderContext;
import com.gtsn.lib.ui.render.RenderContext;
import com.gtsn.ponder.structure.StructureBlock;
import com.gtsn.ponder.structure.StructureSource;
import com.gtsn.ponder.viewport.CameraFraming;
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

import org.joml.Vector3f;

import java.util.Collection;
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
    /**
     * 承载结构方块的假世界（{@link TrackedDummyWorld}，<b>无 proxy 世界</b>）。必须保持强引用，
     * 因为 {@code SceneWidget} 内部假世界仅以 {@link java.lang.ref.WeakReference} 指向它。
     */
    private final TrackedDummyWorld dummyWorld;
    private final ViewportController controller = new ViewportController();
    /** 结构包围盒中心（首次 {@code setRenderedCore} 后捕获），分段显隐时保持取景不漂移。 */
    private final Vector3f fixedCenter;
    /** 实际视线中心：包围盒中心 + 取景居中的偏移（未启用自适应时等于 {@link #fixedCenter}）。 */
    private Vector3f framingCenter;

    private Rect bounds = Rect.ZERO;
    private float partialTick;
    /**
     * 最近一次指针位置（<b>GUI 绝对坐标</b>）。渲染时转交给 {@link SceneWidget#drawInBackground}，
     * 使 LDLib 以该坐标为鼠标位置做拾取（{@code hoverPosFace}）——真实游玩时由屏幕的
     * {@code mouseMoved} / {@code mouseClicked} 写入，自动测试可直接写入以获得确定性（工单 #21 反馈 3）。
     */
    private double pointerX = Double.NaN;
    private double pointerY = Double.NaN;
    /** 取景自适应目标填充比例；{@code <= 0} 表示按场景给的距离（不做自适应）。 */
    private double fitMargin;
    private int appliedX = Integer.MIN_VALUE;
    private int appliedY = Integer.MIN_VALUE;
    private int appliedWidth = Integer.MIN_VALUE;
    private int appliedHeight = Integer.MIN_VALUE;

    private LdlibSceneViewport(StructureSource structure, SceneWidget sceneWidget, TrackedDummyWorld dummyWorld) {
        this.structure = structure;
        this.sceneWidget = sceneWidget;
        this.dummyWorld = dummyWorld;
        Vector3f center = sceneWidget.getCenter();
        this.fixedCenter = center == null ? new Vector3f(0.0f, 0.0f, 0.0f) : new Vector3f(center);
        this.framingCenter = new Vector3f(fixedCenter);
        applyCamera();
    }

    /**
     * 创建视口并载入结构。结构方块放入一个<b>无 proxy 世界</b>的假世界（其 {@code getBlockState}
     * 返回结构方块而非玩家世界方块）；{@code proxyLevel} 仅用于校验「必须在世界内打开」。
     *
     * @param proxyLevel 客户端世界（{@code Minecraft.getInstance().level}）；不得为空
     * @param structure  要渲染的结构（由 GT 适配器产出）
     */
    public static LdlibSceneViewport create(Level proxyLevel, StructureSource structure) {
        if (proxyLevel == null) {
            throw new IllegalArgumentException("proxyLevel must not be null (open the viewport in a world)");
        }
        // 关键：用一个「无 proxy 世界」的假世界承接结构方块。TrackedDummyWorld#getBlockState 在有 proxy
        // 世界时返回 proxy 的方块状态；若把真实客户端世界当 proxy（旧实现），预览会渲染玩家世界里同坐标
        // 的方块（表现为一块通用灰色石头 / 水面），而非目标结构。GT 自身的多方块预览同样以假世界作 proxy。
        TrackedDummyWorld dummy = new TrackedDummyWorld();
        Map<BlockPos, BlockInfo> blocks = resolveBlocks(structure);
        if (!blocks.isEmpty()) {
            dummy.addBlocks(blocks);
        }
        SceneWidget widget = new SceneWidget(0, 0, 1, 1, dummy);
        widget.setClientSideWidget();
        if (widget.getRenderer() == null) {
            // 控件未挂在 ModularUI 上时 isRemote() 可能为 false，显式建场景更确定。
            widget.createScene(dummy);
        }
        widget.setClearColor(0xFF101418);
        widget.setRenderFacing(false);
        widget.setRenderSelect(false);
        widget.setDraggable(true);
        widget.setScalable(true);
        widget.setIntractable(true);
        widget.setHoverTips(false);

        if (!blocks.isEmpty()) {
            widget.setRenderedCore(blocks.keySet());
        }
        return new LdlibSceneViewport(structure, widget, dummy);
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

    /** 已载入假世界的方块数量（自动测试用）。 */
    public int renderedBlockCount() {
        return dummyWorld == null ? 0 : dummyWorld.getRenderedBlocks().size();
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

    /**
     * 设定当前指针位置（GUI 绝对坐标）。渲染时以它作为 LDLib 的鼠标坐标，从而 {@code hoverPosFace}
     * 反映玩家当前指向的方块（用于「点击方块看名称」，工单 #21 反馈 3）。非有限值表示未知（不拾取）。
     */
    public void setPointer(double guiX, double guiY) {
        this.pointerX = guiX;
        this.pointerY = guiY;
    }

    /**
     * 当前指针指向的方块（结构局部坐标 == 虚世界坐标）。LDLib 每帧按 {@link #setPointer} 的坐标做
     * 射线拾取并写入 {@code hoverPosFace}；无命中（空处 / 指针未知）返回空。
     */
    public java.util.Optional<BlockPos> pickedBlock() {
        var face = sceneWidget.getHoverPosFace();
        return face == null ? java.util.Optional.empty() : java.util.Optional.of(face.pos);
    }

    @Override
    public void setBounds(Rect bounds) {
        this.bounds = bounds;
        // resize / 首次布局后：若场景请求了取景自适应，按新的视口纵横比重新反算距离。
        refitCamera();
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

    /**
     * 世界桥（{@link DummySceneWorld}）据此增删 / 替换方块的假世界。返回<b>承载结构的那个假世界</b>
     * （无 proxy 世界），而非 {@code SceneWidget} 内部的 delegate 世界——后者仅把
     * {@code getBlockState} 转发到本世界。
     */
    public TrackedDummyWorld dummyWorld() {
        return dummyWorld;
    }

    /**
     * 设定当前应渲染的方块集合（分段显隐）。LDLib 渲染由「已渲染集合」驱动，未在集合内的坐标
     * {@code getBlockState} 返回空气、不绘制；取景中心固定为结构包围盒中心（避免分段增减时画面
     * 漂移），相机角度 / 缩放保持不变。
     */
    public void setVisibleBlocks(Collection<BlockPos> positions) {
        sceneWidget.setRenderedCore(positions);
        sceneWidget.setCenter(framingCenter);
        applyCamera();
    }

    /** 应用场景 {@code CameraState}（yaw / pitch / distance）到视口相机。 */
    public void applySceneCamera(double yaw, double pitch, double zoom) {
        controller.set(yaw, pitch, zoom);
        applyCamera();
    }

    /**
     * 应用「取景自适应」相机：设定角度后按结构包围盒 + 当前视口纵横比反算恰好容纳结构的距离
     * （{@link CameraFraming}）。{@code margin} 为目标填充比例（{@code <= 0} 退回普通距离）。
     * 视口尚未布局（{@code bounds} 为零）时仅记录目标，待 {@link #setBounds} 触发重算。
     */
    public void applySceneCameraFit(double yaw, double pitch, double margin) {
        this.fitMargin = margin;
        controller.set(yaw, pitch, controller.zoom());
        refitCamera();
    }

    /** 当前是否处于取景自适应模式。 */
    public boolean isFramingFit() {
        return fitMargin > 0.0d;
    }

    /** 当前取景自适应目标填充比例（{@code 0} 表示未启用）。 */
    public double framingMargin() {
        return fitMargin;
    }

    /** 按结构包围盒 + 视口纵横比反算相机距离并应用；未启用自适应 / 视口未就绪时为空操作。 */
    private void refitCamera() {
        if (fitMargin <= 0.0d) {
            return;
        }
        Rect current = bounds;
        if (current.width() <= 0 || current.height() <= 0) {
            return;
        }
        CameraFraming.Fit fit = CameraFraming.fit(
                structure.sizeX(), structure.sizeY(), structure.sizeZ(),
                controller.rotationYaw(), controller.rotationPitch(),
                current.width(), current.height(), CameraFraming.DEFAULT_FOV_Y_DEGREES, fitMargin);
        if (fit.isValid()) {
            framingCenter = new Vector3f(
                    fixedCenter.x + (float) fit.centerOffsetX(),
                    fixedCenter.y + (float) fit.centerOffsetY(),
                    fixedCenter.z + (float) fit.centerOffsetZ());
            sceneWidget.setCenter(framingCenter);
            controller.set(controller.rotationYaw(), controller.rotationPitch(), fit.distance());
            applyCamera();
        }
    }

    /** 场景内高亮 / 轮廓绘制所需的底层控件（世界桥在 {@code afterWorldRender} 钩子中画边框）。 */
    public SceneWidget sceneWidgetForOverlay() {
        return sceneWidget;
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
        int pointerGuiX = Double.isFinite(pointerX) ? (int) Math.round(pointerX) : 0;
        int pointerGuiY = Double.isFinite(pointerY) ? (int) Math.round(pointerY) : 0;
        sceneWidget.drawInBackground(graphicsContext.graphics(), pointerGuiX, pointerGuiY, partialTick);
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
