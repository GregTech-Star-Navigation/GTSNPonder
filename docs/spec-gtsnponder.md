# 规格：GTSNPonder —— 格雷科技的思索（游戏内动画教程系统）

> 关联：`CONTEXT.md`（术语）｜`docs/adr/`（架构决策）｜`docs/constraints.md`（硬约束）
> 来源：立项对齐（8 项决策）+ Oracle 架构预评审 + 四份外部调研

## Problem Statement

格雷科技（GT）的机器与多方块学习曲线极陡：结构怎么搭、仓口 / 总线 / 线缆装在哪、朝向如何、电压与能量网怎么算、输入输出与并行、工艺流程——玩家只能靠外部 Wiki、视频或反复试错。GT 整合包内缺少"对着机器一键进入、分步动画讲解"的游戏内教学。姊妹作品《机械动力》的"思索（Ponder）"已证明这种体验能显著降低学习成本，但格雷科技（含本组织 fork 独有的多方块模块系统）没有任何等价物。

## Solution

GTSNPonder：**GTSN 体系内的独立附属 mod**，为 GT 的机器、多方块与概念提供"思索"体验——选中目标（机器 / 多方块 / 物品 / 概念）后进入脚本化动画演示：分步搭建、控制器与仓口高亮、成型演示、输入 → 输出流程、旁白字幕，配目录 / 图鉴与观看进度。

- **零额外运行时依赖**：玩家只需 GT + GTSN 体系 mod（不要求 Create / 独立 Ponder 库 / Flywheel）。
- **内容生产混合**：搭建 / 成型 / 装模块类场景由引擎从 GT 结构数据**自动生成**；概念、使用流程、原理类场景由作者以**数据格式 + 游戏内可视化编辑器**精作。
- **界面**：全部基于 GTSNLib 自研 UI 框架（含场景页）；3D 场景视口用 LDLib（GT 已强制内置，不新增依赖）。

## User Stories

玩家：

1. As a 玩家, I want 注视机器 / 多方块后按快捷键直接进入思索, so that 我不用离开游戏去查 Wiki。
2. As a 玩家, I want 在 JEI/EMI 的机器页点击「思索」按钮, so that 我查配方时顺便学会这台机器。
3. As a 玩家, I want 在 GT 机器界面里点击「思索」按钮, so that 我在操作现场就能看教学。
4. As a 玩家, I want 一个可搜索、按类别浏览的思索图鉴目录, so that 我能系统性地学习全部内容。
5. As a 玩家, I want 看到当前步骤进度与可拖动时间轴（seek）, so that 我能随时回到某一细节。
6. As a 玩家, I want 暂停 / 上一步 / 下一步 / 重播 / 变速, so that 我能按自己的节奏学习。
7. As a 玩家, I want 旁白字幕（中英）, so that 我始终知道当前在讲什么。
8. As a 玩家, I want 已看 / 未看标记与总进度, so that 我知道自己还差哪些没学。
9. As a 玩家, I want 在场景里拖拽旋转、缩放视口, so that 我能从任意角度观察结构。
10. As a 玩家, I want 超大机器用「整层 / 整组」呈现而非逐方块动画, so that 我不会看晕或卡顿。
11. As a 玩家, I want 自动生成的场景能明确标出控制器、仓口与总线, so that 我能照着把它搭起来。
12. As a 玩家, I want 成型前 / 后的对比演示, so that 我知道怎样才算搭对。
13. As a 玩家, I want 模块系统演示（模块位在哪、可装什么、效果如何叠加）, so that 我能用好本 fork 的独有玩法。
14. As a 玩家, I want 发电与能量网演示（电压、超压、线缆熔断）, so that 我不会炸线炸机。
15. As a 玩家, I want 物流与管网演示（物品 / 流体管道、线缆、覆盖板）, so that 我能把产线自动接起来。
16. As a 玩家, I want 从一台机器跳到相关流程 / 上下游机器, so that 我的学习是连贯的。
17. As a 玩家, I want 不安装 Create/Ponder/Flywheel 也能用, so that 我的 GT 整合包保持干净。
18. As a 玩家, I want 在多人服务器里照常使用（客户端功能）, so that 服务器玩家也能自学。

作者：19. As an 作者, I want 用声明式数据格式（JSON/DSL）定义场景, so that 我无需写代码。20. As an 作者, I want 在游戏内可视化编辑器里摆放、录制动作、写文案并导出, so that 制作高效且所见即所得。21. As an 作者, I want 场景热重载, so that 我能边改边看。22. As an 作者, I want 把自动生成的场景导出为草稿再改, so that 我不必从零开始。23. As an 作者, I want 以「场景粒度」覆盖或扩展自动场景, so that 手作能盖过自动产物。24. As an 作者, I want 用稳定字符串 ID 引用场景与元素, so that 数据可读、可维护、可重排。25. As an 作者, I want 一个封闭且文档化的步骤类型集合, so that 数据格式不会退化成编程语言。26. As an 作者, I want 全部文案走本地化键, so that 中英双语与后续语言可扩展。27. As an 作者, I want 编辑器在正式版对普通玩家隐藏, so that 不影响正常游玩。

引擎 / 开发：28. As a 开发者, I want 一个零 MC 依赖的纯 Java 导演核心, so that 场景行为可 headless 单测。29. As a 开发者, I want 确定性的 tick / seek / rewind, so that 编辑器、进度条与截图测试可靠。30. As a 开发者, I want 效果可重放、世界可快照恢复, so that 回退与快进语义正确。31. As a 开发者, I want 所有 GT 访问收敛在唯一适配包, so that 上游升级影响可控（并可用 import 隔离测试自证）。32. As a 开发者, I want 自动生成器以 DTO 夹具测试, so that 其行为不依赖 GT 运行时。33. As a 开发者, I want 明确的「GTSN UI × LDLib 视口」嵌入契约（输入转发 / 裁剪 / z 序 / resize）, so that 两套 UI 能稳定共存。34. As a 开发者, I want 先用垂直切片验证视口嵌入, so that 最高风险尽早排除。35. As a 开发者, I want 场景数据携带 formatVersion 与 generatorVersion, so that 格式演进与自动重生成可控。36. As a 开发者, I want 结构 / 场景数据缓存与脏失效, so that 超大机器仍流畅。37. As a 开发者, I want 截图与自动测试证据, so that 视觉回归可被捕获。38. As a 开发者, I want 结构数据的索引用例被往返测试锁定, so that 不会出现整体转置的静默错误。

维护 / 发布：39. As a 维护者, I want 独立仓库 + GitHub Issues + CONTEXT/ADR, so that 决策与词汇可追溯。40. As a 维护者, I want 强依赖 GTSNLib 与组织 GT fork 制品, so that 全体系一致、单一上游。41. As a 维护者, I want LGPL-3.0 许可与统一提交署名, so that 合规且历史清晰。42. As a 维护者, I want 分期交付但每期最终质量（不做 MVP/demo）, so that 不留下半成品。

## Implementation Decisions

平台与身份：

- MC 1.20.1 / Forge 47.4.0 / Java 17 / ModDevGradle（legacyforge）/ Parchment 2023.09.03。
- ModID `gtsnponder`；包根 `com.gtsn.ponder`；显示名 GTSN Ponder（中文：格雷科技·思索）；License **LGPL-3.0**；客户端为主。
- 依赖：GTSNLib（强依赖）+ 组织 fork GTCEu `7.5.4-patch01`（slim、`transitive=false`；mods.toml 区间 `[7.5.4-patch01,8.0.0)`）。**零额外运行时依赖**。

引擎（四层，自有代码，不 vendor 第三方源码）：

- **Model/Director（纯 Java，零 MC）**：`Scene` + 有序 `Step` + `SceneRunner`。借鉴 Ponder 的 instruction 契约（isBlocking / reset / onScheduled / tick / isComplete），并**补齐 rewind/seek、稳定字符串目标 ID、静态 totalTime**。
- **World bridge（客户端）**：以 LDLib `TrackedDummyWorld` 实现 `SceneWorld`；方块增删、分段显隐 / 淡入、高亮、轮廓、粒子。
- **Viewport（客户端）**：以 `SceneWidget` 封装 `SceneViewport` 接口；承载相机 / 拾取 / 拖拽旋转与缩放。
- **Presenter（GTSN UI）**：旁白框、控制条、进度 / 时间轴、步骤列表；绑定导演状态的可观测值。
- **不使用 LDLib `CompassScene` 作为骨架**（其时间轴绑定 LDLib XML / 编辑器与自家 UI，且对 GT 结构无感知）；仅借鉴其关键帧 / 插值思路。

场景数据（声明式 JSON DTO）：

- 头部：`formatVersion`（强制）、`id`、`title`、`target`（机器 / 多方块 / 物品 / 概念）、`variant`、`source`（auto/hand/mixed）、`generatorVersion`。
- `elements[]`：**稳定字符串 ID** 的元素（分段 / 锚点），供步骤引用。
- `steps[]`：`{ id, type, duration, targets[], params, narration, keyframe }`。
- **步骤类型为封闭枚举**（如 showSection / hideSection / replaceBlocks / highlight / outline / text / camera / idle / installModule / formedPulse / particles）。
- **反 DSL 铁律**：不允许表达式、条件、循环、用户函数与算术（除字面量参数）；扩展只能通过注册 Java `StepType`（稳定 id），不是脚本解释器。
- 合成规则：**场景粒度**覆盖（auto base 与 hand overlay/replace 按 target+variant 键合并），支持 `include` 与按 `stepId` 的 `skip`；**不做步骤级字段合并**。
- 版本迁移：`formatVersion` 逐级迁移；未知 `type` → 跳过并告警（前向兼容）；未知键忽略。

自动生成（结构 → 场景）：

- 输入：`StructureSource`（本 mod 自有 DTO，来自唯一 GT 适配包），包含分层结构、控制器位置、仓口 / 总线类型、模块位区域与其可接受模块。
- 生成内容**仅限**：搭建顺序（默认按层升序；超大机器切换按角色分组 + LOD / 淡入）、控制器与仓口 / 总线高亮、成型前 / 后演示、模块安装演示。
- **使用流程与原理不自动生成**（必须手作）。
- 大机器设**单元格预算**；统一固定朝向；固定随机种子（确定性）；无控制器页不可锚定模块区域（守卫）。

UI / 入口：

- 全部界面基于 GTSN UI（外壳与场景页统一）；视口经 `SceneViewport` 嵌入。
- 入口：① 注视方块 + 快捷键；② JEI/EMI 机器页按钮；③ 思索图鉴 / 目录；④ GT 机器界面按钮（**对 GTSNLib ADR-0004 的受控例外**，最后阶段评估；以**客户端覆盖层**实现——经 Forge `ScreenEvent` 在 GT 机器屏之上自绘按钮，绝不把内容挂进 / 改动 GT 的 UI 树，不用 mixin、不改 GT fork；见 ADR-0005）。

适配与纪律：

- `com.gtsn.ponder.gt` 是**唯一**允许 import `com.gregtechceu` 的包（import 隔离测试自证）。
- 客户端类不得在专职服务端加载（沿用体系类加载纪律）。

编辑器：

- 游戏内可视化编辑器（录制动作 + 属性表单），编辑 `MutableSceneDraft`（与运行时同一 DTO）；热重载 = 重编译 + 从快照重放。
- 编辑器**门控**（仅作者 / 开发可见）。
- 提供「导出自动场景为草稿」的入口。

本地化：

- 全部文案走键；自动生成场景使用模板化文案（如 "Place N× Casing"）并**经 datagen 批量产出**；仅对自动文案不佳的机器提供覆盖键。

## Testing Decisions

- **好测试的定义**：只断言外部可观测行为（给定场景数据与假世界 → 世界状态 / 时间 / 旁白 / 相机等输出），不测内部类结构或私有实现。
- **主缝（唯一行为缝）**：`SceneData` × `SceneWorld` → `SceneRunner`，JUnit5 headless 单测（内存假世界）；覆盖步骤时序、seek/rewind、旁白、镜头状态、方块增删、成型演示。
- **自动生成缝**：`StructureSource` DTO → `SceneGenerator` → 断言输出的 `SceneData`（夹具驱动，不跑 GT 运行时）；**含转置往返测试**（锁定结构索引约定）。
- **集成缝**：GTSN UI 屏幕 × LDLib 视口 → GameTest + 客户端自动测试 / 截图（沿用 `GTSNLIB_UI_AUTOTEST` 式无人值守证据模式）。
- **纪律检查**：import 隔离测试（`com.gregtechceu` 仅出现在适配包）。
- **证据要求**：任何"完成"声明必须有真实证据（编译 / 单测 / GameTest / 客户端自动测试输出或截图）；无证据不算完成。

## Out of Scope

- 不接管 / 替换 GT 原生机器界面（唯一例外：入口按钮，最后阶段单独评估；以客户端覆盖层实现，不改 GT 的 UI 树，见 ADR-0005）。
- 不依赖 Create / 独立 Ponder 库 / Flywheel（运行时零额外依赖）。
- 不以 LDLib `CompassScene` 作为引擎骨架。
- 不含第三方附属 mod（如 GTOCore 等）内容；覆盖边界为本体 GT（含组织 fork）。
- 不含 1.21 / NeoForge 等其它 MC 版本支持。
- 不含具体逐机器内容清单（由后续内容工单承接）。
- 不引入自定义网络包（除非后续证明必要）。
- 不做 MVP / demo 版本。

## Further Notes

外部调研结论（供参考）：

- 《机械动力》的 Ponder 已独立成 MIT 库（`net.createmod.ponder`），其与 Flywheel 的耦合经核实为**浅**（仅 14 个工具类；场景实际走原版渲染路径）——本规格只**借鉴设计**，不搬运源码。
- 组织 GT fork 上游本就声明了 Ponder 依赖，但零场景实现 → 格雷科技"思索"是空白领域。
- LDLib 为 GT 强制依赖且随包内置，并自带未被启用的 `CompassScene` 分步序列器与场景编辑器。

架构预评审（Oracle）要点：

- 采用四层 + 纯导演核心；最高风险是「GTSN UI 中嵌 LDLib 视口」→ **第一件事先做垂直切片 spike**。
- 必须从第一天内建 rewind/seek；效果可重放、世界可快照恢复。
- 自动生成边界如上述（仅搭建 / 成型 / 装模块）。
- 数据格式须封闭步骤枚举 + 反 DSL 铁律；覆盖按场景粒度。
- 已知陷阱：结构索引约定（消费方把 index0 当 x）须往返测试；超大机器需单元格预算与角色分组；谓词语义不在 server-safe 结构中，需在适配层做角色分类。

建议交付顺序（用于拆工单）：

1. 视口嵌入 spike（GTSN UI × LDLib）
2. 纯导演核心 + 模型（headless 测试）
3. 最小效果集 + 首个手作 JSON 场景可播
4. Presenter（GTSN UI：旁白 / 控制 / 进度 / 时间轴）→ 首个完整垂直切片（快捷键 → 目录 → 播放）
5. 自动生成 v1（小 / 中 / 大三台机器 + 人工教学质量评审）
6. 冻结格式 / 编译器 / 版本化 + datagen 文案
7. 游戏内编辑器
8. 模块 / 发电能量网 / 物流管网内容扩量
9. JEI/EMI 入口、图鉴目录完善
10. GT 机器界面入口按钮（ADR 例外，最后）
