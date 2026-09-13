# GTSN（GregTech Star Navigation）· GTSNPonder

GTSNPonder 是 GTSN 项目群的「思索」mod（Minecraft 1.20.1 Forge）：为格雷科技提供类《机械动力》思索（Ponder）的游戏内动画教程。本 mod 是 GTSNLib 的附属内容 mod（独立仓库、独立发布）。

## Language

**思索（Ponder）**:
本项目对"游戏内、脚本化、分步动画教学"这一体验的统称（借自《机械动力》）。
_Avoid_: 教程文档、Wiki、指南（那些是静态文本）

**场景（Scene）**:
一段可播放的思索演示单元，绑定一个目标，由有序的**步骤**组成。
_Avoid_: 页面、章节

**步骤（Step）**:
场景中的单个可执行单元，声明类型、时长、目标与参数；是数据格式的原子。

**步骤类型（StepType）**:
封闭枚举中的一种步骤语义（如展示分段 / 替换方块 / 高亮 / 旁白 / 镜头 / 装模块 / 成型脉冲）；扩展须注册 Java 实现。

**元素（Element）**:
场景内可被步骤引用的实体（分段 / 锚点 / 区域），以**稳定字符串 ID** 命名。

**目标（Target）**:
场景所讲解的对象：GT 机器、多方块、物品或概念。

**变体（Variant）**:
同一目标的多个版本页（如可重复结构的页数）。

**自动生成（Generated）**:
由引擎从 GT 结构数据产出的场景（搭建顺序 / 高亮 / 成型 / 模块安装）；视为可重生成的构建产物，携带 `generatorVersion`。
_Avoid_: 自动场景（同义，但避免歧义）

**手作（Hand-authored）**:
由作者（数据格式 + 游戏内编辑器）编写的场景（概念课、使用流程、原理）。

**覆盖（Override）**:
手作场景以**场景粒度**替换 / 扩展同 `target+variant` 的自动场景；不做步骤级字段合并。

**导演核心（Director）**:
引擎中零 MC 依赖的纯 Java 部分：按步骤推进场景、处理 seek/rewind 与效果重放。

**场景世界（SceneWorld）**:
导演核心操作场景世界的抽象接口；客户端由 LDLib 虚世界（dummy world）实现。

**视口（SceneViewport）**:
承载 3D 场景渲染与相机 / 拖拽交互的接缝；GTSN UI 屏幕经它嵌入 LDLib 场景控件。

**结构源（StructureSource）**:
本 mod 自有的结构数据 DTO（分层方块、控制器、仓口 / 总线、模块位区域），由唯一 GT 适配包产出。

**模块位（Module Slot）**:
组织 GT fork 独有的多方块模块系统特性：机器声明的区域，可安装匹配结构的模块；本 mod 需为其生成安装 / 效果演示。

**仓口 / 总线（Hatch / Bus）**:
多方块上承担输入输出（能量 / 物品 / 流体）的部件；自动生成的搭建演示需高亮其位置与类型。

**适配包（Adapter Package）**:
唯一允许 import `com.gregtechceu` 的包（`com.gtsn.ponder.gt`）；所有 GT 版本耦合收敛于此。
