# GTSNPonder

**GTSN（GregTech Star Navigation）项目群的「思索」mod** —— 把类《机械动力》思索（Ponder）的**游戏内动画教程体验**带给格雷科技：
对着机器 / 多方块一键进入脚本化演示（分步搭建、原理解说、输入 → 输出流程、旁白字幕），让玩家在游戏内自学 GT，不翻 Wiki。

- 平台：Minecraft **1.20.1** Forge（47.4.0）｜ Java 17 ｜ ModDevGradle
- 目标格雷科技：组织 fork **`com.gregtechceu.gtceu:gtceu-1.20.1:7.5.4-patch01`**
- 前置：**GTSNLib**（`com.gtsn.lib:gtsnlib`）
- **零额外运行时依赖**：玩家只需 GT + GTSN 体系 mod（不要求 Create / Ponder / Flywheel）
- 领域词汇：`CONTEXT.md` ｜ 架构决策：`docs/adr/`
- License：**LGPL-3.0**

> 状态：**立项 / 规格阶段**（尚无实现）。

## 设计要点（已对齐）

- **引擎自研**：借鉴 Ponder（MIT）的设计，代码自有；场景渲染基于 LDLib（GT 已强制内置，不增加玩家依赖）
- **界面**：全部基于 GTSNLib 自研 UI 框架（外壳与场景页统一）
- **内容生产**：混合 —— 机器 / 多方块的「搭建演示」由引擎从 GT 结构数据自动生成；概念课、使用流程、原理讲解由作者精作
- **作者链**：自定义数据格式（JSON / DSL）+ 游戏内可视化编辑器（热重载、作者零代码）
- **入口**：注视方块快捷键 · JEI/EMI 机器页按钮 · 思索图鉴目录 · GT 机器界面按钮

## 与 GTSNLib 的关系

本 mod 是 GTSNLib 的**附属内容 mod**（独立仓库、独立发布）。GTSNLib 提供自研 UI 框架、GT 机器只读桥接与 GT 适配层纪律。

- GTSNLib：https://github.com/GregTech-Star-Navigation/GTSNLib
- 组织 GTM fork：https://github.com/GregTech-Star-Navigation/GregTech-Modern
