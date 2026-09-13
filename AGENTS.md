# GTSNPonder — 项目规则

GTSNPonder 是 **GTSN（GregTech Star Navigation）** 项目群的「思索」mod（Minecraft 1.20.1 Forge）：把类《机械动力》思索（Ponder）的**游戏内动画教程**带给格雷科技。本 mod 是 GTSNLib 的附属内容 mod（独立仓库、独立发布）。

- 全局偏好见 `~/.config/opencode/AGENTS.md`
- 领域词汇见 `CONTEXT.md`；架构决策见 `docs/adr/`
- **硬性约束见 `docs/constraints.md`**（随规格落地：依赖版本区间 / GT import 隔离 / 类加载纪律 / 发布与提交规则）

## Agent skills

### Issue tracker

GitHub Issues（`gh` CLI），仓库 `GregTech-Star-Navigation/GTSNPonder`。见 `docs/agents/issue-tracker.md`。

### Triage labels

五角色默认标签：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文：根 `CONTEXT.md` + `docs/adr/`。见 `docs/agents/domain.md`。

## 常用命令

> 待脚手架工单落地后补（ModDevGradle legacyforge + JDK17；参照 GTSNLib 与组织 GTM fork 的构建约定）。
