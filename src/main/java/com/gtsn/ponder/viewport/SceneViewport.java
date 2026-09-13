package com.gtsn.ponder.viewport;

import com.gtsn.lib.ui.layout.Rect;

/**
 * 场景视口接缝（SceneViewport）：GTSN UI 屏幕与 3D 场景渲染之间的唯一契约。
 *
 * <p>GTSNPonder 的全部二维界面基于 GTSNLib 自研 UI 框架（ADR-0004），而 3D 场景
 * 由 LDLib {@code SceneWidget} 渲染（GT 已强制内置，不新增运行时依赖）。本接口是两者
 * 之间的薄接缝：宿主屏幕把一个矩形交给本视口，并把落在该矩形内的输入按
 * <b>视口局部坐标</b>转发进来；本视口负责相机交互、把场景限制在矩形内渲染、并在
 * resize 与逐帧 tick 时保持正确。具体实现见 {@code com.gtsn.ponder.client.LdlibSceneViewport}。</p>
 *
 * <p>本接口刻意保持纯 Java（无 {@code net.minecraft} / LDLib 类型），使输入映射、
 * 裁剪几何与相机状态可 headless 单测（沿用导演核心纪律）。矩形使用 GTSNLib 的
 * {@link Rect}（同为纯几何类型）。</p>
 *
 * <h2>1. 宿主预留矩形（bounds）</h2>
 * <p>宿主（GTSN UI 的视口控件）在布局完成后调用 {@link #setBounds(Rect)}，把布局引擎
 * 算出的<b>GUI 像素</b>矩形交给视口；窗口尺寸 / GUI 缩放变化时布局引擎重排，宿主再次
 * 调用 {@link #setBounds}，视口据此重定位 / 重设尺寸（见下「4. resize」）。视口不在
 * 矩形之外绘制、也不在矩形之外接受输入。</p>
 *
 * <h2>2. 输入转发（视口局部坐标，仅矩形内）</h2>
 * <p>宿主负责命中测试：只有当指针位于 {@link #bounds()} 内时，才把
 * {@link InputEventKind#PRESS} / {@link InputEventKind#MOVE} / {@link InputEventKind#SCROLL}
 * 转发给视口；坐标一律换算为<b>视口局部坐标</b>（{@code local = gui - bounds.{x,y}}），
 * 即矩形左上角为 {@code (0,0)}。一次在矩形内按下后，拖拽与释放<b>即使移出矩形</b>也会
 * 继续转发（鼠标捕获语义），使旋转不因指针越界而中断；宿主以 {@link #isDragging()} 判定
 * 是否处于捕获态。</p>
 *
 * <p>所有输入方法的返回值表示「本视口是否消费该事件」。宿主据此决定是否继续向上冒泡 /
 * 交给其它 GTSN UI 控件；视口外的输入不得被本视口吞掉（宿主在矩形外根本不调用）。</p>
 *
 * <h2>3. 裁剪（clip）与 z 序</h2>
 * <p>场景<b>只允许</b>绘制在 {@link #clipRect()} 内（默认等于 {@link #bounds()}），矩形外
 * 一个像素都不能溢出。实现通过把 3D 渲染的 GPU 视口（viewport / scissor）钳到该矩形来
 * 达成；宿主无需额外裁剪。</p>
 *
 * <p>z 序：视口在 GTSN UI 控件树中的位置决定其前后关系——作为较早添加的子控件，它在
 * 同层中先于后添加的覆盖层渲染，因此任何与视口重叠的 GTSN UI 覆盖控件（面板 / 按钮 / 文本）
 * 都绘制在场景之上并优先命中输入。视口自身不绘制任何超出矩形的内容，也不会盖住其后渲染的
 * 覆盖控件。</p>
 *
 * <h2>4. resize（窗口尺寸 / GUI 缩放变化）</h2>
 * <p>该场景下 GTSN UI 重新布局并再次调用 {@link #setBounds(Rect)}；视口必须据此重设内部
 * 渲染器尺寸与相机纵横比，且<b>保留</b>相机角度与缩放（resize 不改相机朝向）。局部坐标
 * 映射随之以新矩形为基准。</p>
 *
 * <h2>5. partial-tick 传递</h2>
 * <p>宿主屏幕每帧把 Minecraft 的 {@code partialTick}（帧间插值因子）经
 * {@link #partialTick(float)} 传入；视口据此推进动画 / 插值并渲染当前帧。调用约定：一帧内
 * 在渲染视口之前调用一次。</p>
 */
public interface SceneViewport {

    /** 输入事件类别（用于契约文档与实现分派；不引入 MC / GLFW 类型）。 */
    enum InputEventKind {
        MOVE,
        PRESS,
        DRAG,
        RELEASE,
        SCROLL
    }

    /**
     * 由宿主在布局完成后调用，声明视口占用的 GUI 像素矩形。窗口尺寸 / GUI 缩放变化时会被
     * 重新调用；实现须保留相机状态、只更新几何与渲染视口。
     *
     * @param bounds 视口矩形（GUI 像素，左上角 + 宽高）；宽高可为 0（视口被折叠），此时不渲染
     */
    void setBounds(Rect bounds);

    /** 当前视口矩形（GUI 像素）；尚未 {@link #setBounds} 时实现可返回 {@link Rect#ZERO}。 */
    Rect bounds();

    /**
     * 场景允许绘制的矩形（GUI 像素）。默认等于 {@link #bounds()}；实现可返回其子区域以留出
     * 边框 / 内边距。宿主不对此矩形额外裁剪。
     */
    Rect clipRect();

    /**
     * 指针移动（视口局部坐标）。仅当指针在矩形内时由宿主调用。
     *
     * @return 是否消费该事件
     */
    boolean mouseMoved(double localX, double localY);

    /**
     * 指针在矩形内按下。
     *
     * @return 是否消费该事件（返回 {@code true} 表示视口接管随后的拖拽 / 释放）
     */
    boolean mousePressed(double localX, double localY, int button);

    /**
     * 指针拖拽。按下被消费后，即使指针移出矩形也会继续由宿主转发（鼠标捕获语义）。
     *
     * @param dragX 自上次拖拽事件以来的水平位移（GUI 像素）
     * @param dragY 自上次拖拽事件以来的垂直位移（GUI 像素）
     * @return 是否消费该事件
     */
    boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY);

    /**
     * 指针释放（结束捕获）。
     *
     * @return 是否消费该事件
     */
    boolean mouseReleased(double localX, double localY, int button);

    /**
     * 滚轮滚动（视口局部坐标）。仅当指针在矩形内时由宿主调用；用于缩放。
     *
     * @param scrollDelta 滚动量（正 / 负代表方向；约定与 Minecraft 一致：向上为正）
     * @return 是否消费该事件
     */
    boolean mouseScrolled(double localX, double localY, double scrollDelta);

    /**
     * 每帧传入 Minecraft {@code partialTick}（帧间插值因子，通常 {@code [0,1)}）。宿主在渲染
     * 视口之前调用一次；实现据此推进插值并按当前相机渲染。
     */
    void partialTick(float partialTick);

    /** 当前是否处于拖拽捕获态（已按下且尚未释放）。 */
    boolean isDragging();

    /** 相机水平旋转角（度）。仅作观测 / 测试断言用。 */
    double cameraYaw();

    /** 相机俯仰角（度）。仅作观测 / 测试断言用。 */
    double cameraPitch();

    /** 相机缩放 / 距离。仅作观测 / 测试断言用。 */
    double cameraZoom();

    /** 复位相机到默认朝向与缩放（用于重播 / 自动测试的确定性起点）。 */
    void resetCamera();
}
