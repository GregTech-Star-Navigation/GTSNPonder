# GTSNPonder 约束清单

> 本文件汇总 GTSNPonder 的**硬性约束**（平台 / 依赖 / 架构 / 内容 / 发布 / 工程）。
> 与本文冲突的改动：先开 issue 说明 → 涉及架构则落 ADR → 更新本文 → 再实施。
> 相关：`CONTEXT.md`（词汇）· `docs/adr/`（决策）· `AGENTS.md`（规则 / 命令）· `docs/spec-gtsnponder.md`（规格）

## 1. 平台与工具链

- Minecraft **1.20.1**（不迁 1.21、不迁 NeoForge）
- Forge **47.4.0**（mods.toml 硬依赖 `versionRange="[47,)"`）
- Java **17**（toolchain）
- Gradle wrapper **8.14**（参照 GTSNLib；升级需先验证 ModDevGradle 兼容）
- ModDevGradle `net.neoforged.moddev.legacyforge`（版本参照 GTSNLib：2.0.86）
- Mappings：Parchment **2023.09.03**

## 2. 依赖约束

- **GTSNLib 强依赖**：`com.gtsn.lib:gtsnlib:<ver>`（开发联调用 mavenLocal，组织 GitHub Packages 分发）
- **组织 fork GTCEu 硬依赖**：`com.gregtechceu.gtceu:gtceu-1.20.1:7.5.4-patch01:slim`（`transitive = false`），来源 `https://maven.pkg.github.com/GregTech-Star-Navigation/GregTech-Modern`（读取需 `read:packages` 凭据）；mods.toml `versionRange="[7.5.4-patch01,8.0.0)"`
- **LDLib / configuration / Registrate（dev classpath）**：组织 fork GTCEu 以 `:slim` 消费，该工件**不含** `META-INF/jarjar`，故 GT / GTSNLib 的 `embedded=true` 内嵌依赖在开发期缺失——dev classpath **必须**显式声明 `ldlib` / `configuration`（GT 另引用 `registrate`），与 GTSNLib 既有构建一致（T1 实测：不声明则 `runGameTestServer` 以 `ldlib [MISSING], configuration [MISSING]` 失败）。玩家侧由 GT 的完整 jarJar 工件提供；**禁止自行升级版本**
- **零额外运行时依赖**：不得要求 Create / 独立 Ponder 库 / Flywheel
- 软依赖（JEI/EMI 等）一律 `mandatory=false` + `versionRange="[0,)"` + `ordering="AFTER"`；缺席时功能降级、**零 `NoClassDefFoundError`**

## 3. 架构约束

- **引擎分层**：导演核心（纯 Java，零 MC）/ 世界桥 / 视口 / 表现层；**导演核心不得 import `net.minecraft`**
- **GT 隔离**：`import com.gregtechceu` **仅允许**出现在 `com.gtsn.ponder.gt`；其余包越界计数必须为 0（可用搜索 / 测试自证）
- **客户端 / 服务端分离**：客户端类不得在专职服务端加载
- **不使用** LDLib `CompassScene` 作为引擎骨架
- **数据格式**：封闭步骤枚举；**反 DSL**（无表达式 / 条件 / 循环 / 用户函数 / 算术）；未知步骤类型跳过并告警
- **覆盖只按场景粒度**；禁止步骤级字段合并
- **结构索引约定**以往返测试锁定（防静默转置）
- **超大机器**须有单元格预算 + 角色分组 / LOD + 缓存脏失效
- **编辑器**为开发 / 作者门控，正式玩家不可见

## 4. 内容约束

- 内容范围：**本体 GT（含组织 fork）** —— 机器、多方块、模块系统、发电与能量网、物流与管网、基础概念
- 自动生成**仅限**：搭建顺序 / 控制器与仓口高亮 / 成型演示 / 模块安装演示；**使用流程与原理必须手作**
- **不做 MVP / demo**：分期交付，但每期均为最终质量
- 全部文案走本地化键（中英）；自动文案经 datagen 批量产出

## 5. 发布与消费约束

- 发布物必须是 **reobf 变体**，并含 sources jar
- 版本不可覆盖（GitHub Packages 同版本重复发布 → 409）；升级须提升 `mod_version`
- 凭据仅走环境变量 / `-P` 参数，**严禁**入库

## 6. 工程与协作约束

- 提交署名**仅 `sanjiu2024`**；**禁止**任何 co-author / 署名 trailer
- 提交信息：**中文主题 + `(#issue)`**；**原子提交**（一单一提交，验证通过才提交）
- **证据要求**：任何"完成"声明必须有真实证据（编译 / 单测 / GameTest / 客户端自动测试输出或截图）；**无证据不得声称完成**
- **TDD**：行为改动先写失败测试（RED→GREEN 留档）
- 禁止类型压制、禁止空 catch、**禁止删除测试来"通过"**
- UI / 视觉改动需**放大截图目视核验**（≥2x）
- 长任务**小步提交**

## 7. 已知不做 / 限制

- **不接管 / 不替换** GT 原生机器界面（唯一例外：入口按钮，见 ADR-0005）。该例外以**客户端覆盖层**实现
  ——经 Forge `ScreenEvent` 在 GT 机器屏之上自绘按钮，**不改 GT 的 UI 树、不用 mixin、不改 GT fork**；
  GT import 仍仅限 `com.gtsn.ponder.gt`
- 不含第三方附属 mod 内容；不含不同 MC 版本；不引入自定义网络包（除非后续证明必要）
