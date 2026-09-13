# 界面统一基于 GTSN UI（含场景页）

GTSNPonder 的**全部二维界面**——思索图鉴目录、旁白框、控制条、进度 / 时间轴、编辑器——统一使用 **GTSNLib 自研 UI 框架**（主题 / 字体 / 数据同步），与体系一致并复用既有资产；3D 场景视口通过 `SceneViewport` 适配 LDLib `SceneWidget` 嵌入。

**Status**: accepted

**Considered Options**:

- **全 GTSN UI（选定）**：一致性最强、复用资产最多。
- 复刻 Ponder 原版界面：最像《机械动力》思索，但与自研 UI 体系脱节。
- 混合（GTSN UI 外壳 + Ponder 式场景页）：折中，但两套视觉语言并存。

**Consequences**:

- 场景页（含拖动时间轴、变速、步骤列表）需在 GTSN UI 上实现。
- **关键集成风险**：GTSN UI 屏幕内嵌 LDLib 视口的契约（输入转发 / 裁剪 / z 序 / resize）须先行 spike 验证。

---

_关联：ADR-0001；规格 §Testing Decisions（集成缝）。_
