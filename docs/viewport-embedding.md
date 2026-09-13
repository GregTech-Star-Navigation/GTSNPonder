# 视口嵌入契约（GTSN UI × LDLib）

> 关联：#4（视口嵌入 spike）· `docs/adr/0004-ui-on-gtsn-ui-framework.md` · `docs/spec-gtsnponder.md`（§Testing Decisions 集成缝）
> 证据：`run/screenshots/gtsnponder-viewport.png` · GameTest（`runGameTestServer`）· 客户端自动测试日志

GTSNPonder 的全部二维界面基于 GTSNLib 自研 UI 框架（ADR-0004），3D 场景由 LDLib `SceneWidget`
渲染（GT 已强制内置）。本文件固化两者之间的接缝约定，供后续 Presenter / 世界桥工单复用。

## 契约（`com.gtsn.ponder.viewport.SceneViewport`）

| 关注点       | 约定                                                                                                                                                                            |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 宿主预留矩形 | 宿主控件布局后调用 `setBounds(Rect)`（GUI 像素）；窗口尺寸 / GUI 缩放变化后重新下发                                                                                             |
| 输入转发     | 仅**矩形内**的 move / press / scroll 转发，坐标为**视口局部**（`local = gui - bounds.{x,y}`）；矩形内按下后，拖拽 / 释放即使移出矩形仍继续转发（鼠标捕获，`isDragging()` 表征） |
| 裁剪         | 场景只绘制在 `clipRect()` 内；实现把 GPU 视口钳到矩形（LDLib `WorldSceneRenderer` 的 `RenderSystem.viewport` + 局部 clear），矩形外零溢出                                       |
| z 序         | 视口在 GTSN 控件树中**先于**覆盖层添加 → 渲染在下、命中在后；与视口重叠的覆盖控件优先接收输入并绘于场景之上                                                                     |
| resize       | 窗口尺寸 / GUI 缩放变化 → GTSN 重排 → 再次 `setBounds`；相机角度与缩放保留                                                                                                      |
| partial-tick | 宿主每帧渲染前调用 `partialTick(float)`，转交 LDLib                                                                                                                             |

## 实现映射

| 层         | 类                                            | 说明                                                   |
| ---------- | --------------------------------------------- | ------------------------------------------------------ |
| 契约       | `com.gtsn.ponder.viewport.SceneViewport`      | 纯 Java 接口（无 MC / LDLib 类型）                     |
| 宿主控件   | `com.gtsn.ponder.viewport.ViewportWidget`     | 纯逻辑：布局下发 + 命中 / 坐标换算 / 捕获              |
| 相机状态机 | `com.gtsn.ponder.viewport.ViewportController` | 纯 Java：拖拽旋转 / 滚轮缩放（语义对齐 `SceneWidget`） |
| 几何       | `com.gtsn.ponder.viewport.ViewportGeometry`   | 局部映射 / 命中 / 裁剪矩形                             |
| 渲染钩子   | `com.gtsn.ponder.viewport.ViewportRenderer`   | 把 3D 绘制从纯控件中拆出                               |
| LDLib 实现 | `com.gtsn.ponder.client.LdlibSceneViewport`   | `SceneWidget` + `TrackedDummyWorld`；相机机械映射      |
| 宿主屏幕   | `com.gtsn.ponder.client.PonderViewportScreen` | `GtsnScreen` + 右下重叠覆盖面板                        |
| 中立 DTO   | `com.gtsn.ponder.structure.StructureSource`   | 纯 Java（方块 id + 局部坐标），适配包产出              |
| GT 适配    | `com.gtsn.ponder.gt.GtStructureAdapter`       | 读 `GTRegistries.MACHINES` / `getMatchingShapes()`     |

## 结构索引约定（陷阱）

`MultiblockShapeInfo` 的字段注释写作 `BlockInfo[][][] blocks; // [z][y][x]`，但**产出侧**
（`BlockPattern#getPreview`：`result[pos.x-minX][pos.y-minY][pos.z-minZ]`）与**消费侧**
（GT `PatternPreviewWidget#initializePattern`）都按 **index0 = X、index1 = Y、index2 = Z** 处理，
注释是错的。适配器按 index0 = X 迭代，并由 GameTest `GtStructureGameTests` 以非对称 2×3×4 夹具
（断言特殊方块落在 index0 轴）+ 真实结构页尺寸比对锁定，防静默转置。

## 证据

- **headless 单测**：`ViewportControllerTest` / `ViewportGeometryTest` / `ViewportWidgetTest`
  （真实 `WidgetHost` + `InputRouter` 验证转发 / 捕获 / 覆盖层优先命中 / 渲染顺序）/
  `StructureSourceTest` / `ViewportImportIsolationTest`（viewport 与 structure 包保持纯 Java）。
- **GameTest**：`GtStructureGameTests`（适配器可达 GT、索引约定锁定、尺寸不转置）；
  `runGameTestServer` 4/4 通过。
- **客户端自动测试**：`$env:GTSNPONDER_UI_AUTOTEST="viewport"` →
  加载真实 GT 多方块 `gtceu:coke_oven`（26 方块）渲染；注入拖拽（相机 yaw/pitch 改变，
  且 LDLib 侧实际值同步）、滚轮（zoom 5.0→4.5）、覆盖层点击（覆盖层消费、相机不变）、
  窗口 resize（视口 608×328 → 448×238 且新矩形输入仍转发）；抓图
  `run/screenshots/gtsnponder-viewport.png` 后自动退出。

## 已知观察（非阻塞）

- 虚世界渲染的方块偏蓝着色（dummy world 的天光 / 光源色），并非缺失模型（无紫黑棋盘格）。
  属预览观感问题，后续世界桥工单可调整光源 / 光照。
- 本 spike 只渲染结构页本身，不触发 GT 的「成型（formed）」状态与模块合并；后者属自动生成 /
  模块演示工单。
