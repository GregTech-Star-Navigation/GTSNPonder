# 零额外运行时依赖的思索引擎：借 Ponder 设计、代码自有、渲染落 LDLib

GTSNPonder 自带一套思索引擎（场景 / 分镜 / 指令 / 元素 / 导演核心），**运行时不依赖任何第三方 mod**（不要求 Create、独立 Ponder 库或 Flywheel）。实现采取「借设计、自实现」：借鉴《机械动力》Ponder（MIT）的 instruction 契约与交互概念，代码 100% 自有（包根 `com.gtsn.ponder`），**不 vendor** 第三方源码；3D 场景渲染落在 **LDLib**（GT 的强制依赖、随包内置），二维界面全部走 GTSN UI。**不采用** LDLib 自带的 `CompassScene` 作为骨架。

**Status**: accepted

**Considered Options**:

- **(A) 自研（选定）**：零额外依赖、完全自主、贴合 GT 数据与 GTSN UI；代价是引擎工程量最大（时间轴 / 镜头 / 动画 / 编辑器自建）。
- (B) 依赖官方独立 Ponder 库（MIT）：开发量最小、观感 1:1；代价是玩家需额外携带 Ponder + Flywheel，违背零依赖目标。
- (C) 移植 Ponder 源码（去 Flywheel 化）：保留成熟引擎；代价是维护一份第三方代码并替换其 Flywheel 工具类。

**Consequences**:

- 图像 / 时间轴 / 编辑器全部自建；对 GT（含 fork）的访问收敛在唯一适配包。
- 必须自建 **rewind / seek** 与效果重放语义（Ponder 原实现不支持倒放）。
- 引擎架构先经 Oracle 评审（已做），并以「视口嵌入 spike」先行验证。

---

_关联：规格 `docs/spec-gtsnponder.md`；约束 `docs/constraints.md`；术语 `CONTEXT.md`。_
