# GTSNPonder — 项目规则

GTSNPonder 是 **GTSN（GregTech Star Navigation）** 项目群的「思索」mod（Minecraft 1.20.1 Forge）：把类《机械动力》思索（Ponder）的**游戏内动画教程**带给格雷科技。本 mod 是 GTSNLib 的附属内容 mod（独立仓库、独立发布）。

- 全局偏好见 `~/.config/opencode/AGENTS.md`
- 领域词汇见 `CONTEXT.md`；架构决策见 `docs/adr/`
- **硬性约束见 `docs/constraints.md`**（随规格落地：依赖版本区间 / GT import 隔离 / 类加载纪律 / 发布与提交规则）
- **视口嵌入契约见 `docs/viewport-embedding.md`**（GTSN UI × LDLib：输入 / 裁剪 / z 序 / resize / partial-tick）

## Agent skills

### Issue tracker

GitHub Issues（`gh` CLI），仓库 `GregTech-Star-Navigation/GTSNPonder`。见 `docs/agents/issue-tracker.md`。

### Triage labels

五角色默认标签：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文：根 `CONTEXT.md` + `docs/adr/`。见 `docs/agents/domain.md`。

## 常用命令

> Windows（PowerShell）下使用 `.\gradlew.bat`；其它平台用 `./gradlew`。
> 需 JDK 17：`gradle.properties` 已固定 `org.gradle.java.home`（Eclipse Adoptium `jdk-17.0.20.101-hotspot`），Gradle wrapper 8.14。
> 硬依赖 `com.gtsn.lib:gtsnlib:<ver>` 走 mavenLocal（先在 GTSNLib 执行 `publishToMavenLocal`）；
> `gtceu-1.20.1:7.5.4-patch01:slim` 走组织 GitHub Packages。凭据缺失时用 `--offline`（Gradle 缓存已就绪）。

```bash
.\gradlew.bat build               # 编译 + 单元测试 + 产出 reobf jar（build/libs/gtsnponder-<version>.jar）
.\gradlew.bat test                # 仅跑 JUnit5 单元测试（src/test/java；含 GT import 隔离检查）
.\gradlew.bat runClient           # 启动开发态客户端（主菜单）
.\gradlew.bat runServer           # 启动开发态服务端（--nogui；日志 run/server/logs/latest.log）
.\gradlew.bat runData             # 数据生成，输出到 src/generated/resources/
.\gradlew.bat runGameTestServer   # 运行 GameTest（forge.enabledGameTestNamespaces=gtsnponder）
.\gradlew.bat publishToMavenLocal # 发布 reobf 变体到 ~/.m2（供下游 mod 以 modImplementation 消费）
$env:GTSNPONDER_UI_AUTOTEST="viewport"; .\gradlew.bat runClient  # 视口嵌入自动测试（#4）：载入存档 → 渲染真实 GT 多方块 → 拖拽/缩放/覆盖层点击/resize 断言 → 截图 run/screenshots/gtsnponder-viewport.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="scene"; .\gradlew.bat runClient  # 场景播放自动测试（#5）：载入存档 → 打开 gtceu:coke_oven 的思索屏 → 步骤/旁白/暂停/seek/重播断言 → 截图 run/screenshots/gtsnponder-scene.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="autogen"; .\gradlew.bat runClient  # 自动生成自动测试（#7）：载入存档 → 解析小/中/大三台真实 GT 多方块 → 强制自动生成并播放 → 确定性断言 → 每台两张截图 run/screenshots/gtsnponder-autogen-<n>-<machine>-reveal.png 与 -formed.png（揭示中 / 成型）→ 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="editor"; .\gradlew.bat runClient  # 游戏内编辑器自动测试（#9）：载入存档 → 打开编辑器 → 录制 2 步 + 微调时长 → 保存 → 场景库热重载 → 重放并断言编辑后的旁白 → 截图 run/screenshots/gtsnponder-editor.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="catalog"; .\gradlew.bat runClient  # 图鉴目录自动测试（#11）：载入存档 → 清空进度并按需补种子作者场景 → 打开目录 → 断言列表与场景库一致 + 类别覆盖 → 搜索断言（过滤 / 不可能命中 / 清空）→ 播放某条目并断言进度标记已看 → 重开目录断言进度持久 → 截图 3 张 → 退出（用后清除该环境变量）
```

### 自动生成（#7）

- 纯生成器 `com.gtsn.ponder.generate.SceneGenerator`：`StructureSource → SceneData`；`CELL_BUDGET=64`
  以上切换「按角色分组 + LOD / 淡入」，否则逐层（Y 升序）；控制器优先、其后仓口 / 总线高亮；
  成型演示 = 隐藏 → 重现 → 成型脉冲。产物 `source=auto` + `generatorVersion=auto-1`，确定性（同源两次
  生成字节相等 JSON，由 `SceneDataWriter` 序列化）。
- 运行时接线：`/gtsnponder scene <target>` 无手作场景时按需自动生成；`/gtsnponder generate <target>`
  强制生成并播放；`/gtsnponder dump <target>` 导出生成 JSON 到 `run/gtsnponder-generated/`。
- 取景：相机步骤声明 `fit=true` + `margin`（`FIT_MARGIN=0.90`）；视口层 `CameraFraming` 按结构包围盒
  **与视口纵横比**反算距离并居中，使结构占据视口窄轴约 90%（实测居中、高度 ~92%，消除顶部大黑边）；
  相机步骤排在搭建之前（全程同一取景），`distance` 仍作为不含视口尺寸时的确定性回退值。**关键**：结构方块
  放入「无 proxy 世界」的 `TrackedDummyWorld`——若把真实客户端世界当 proxy，`getBlockState` 会返回玩家世界
  同坐标方块（表现为通用灰石），预览必须用假世界承载结构（与 GT 自带预览一致）。
- 高亮可读：控制器金色**线框盒**（全 6 面），仓口 / 总线蓝色细线框盒（全 6 面、`inner=1`，从任意角度可见；
  按角色分批、总数 ≤ `HATCH_OUTLINE_LIMIT=4`，并在角色内 `spread` 均匀抽样避免重叠）。
- 颜色图例：播放屏左上角**常驻图例区**（金 = 控制器、蓝 = 仓口 / 总线），不再占用旁白句子。
- 旁白机器特定：`narrationArgs` 模板参数（机器标识 / 结构尺寸 / 仓口数量与角色 / 模块位数量），中英双语键；
  **成型旁白同样机器特定**（标识 / 尺寸 / 仓口数量与角色 / 模块位数量）。
- 人类评审门：自动生成的教学质量由人评审截图（`autogen` 自动测试产出），自动化只覆盖结构与确定性。

### 场景格式与本地化 datagen（#8）

- **格式冻结**：schema 见 `docs/scene-format.md`（v1 FROZEN）。版本号单一事实源 =
  `com.gtsn.ponder.engine.model.SceneFormat.CURRENT_VERSION`；已冻结结构的变更须先开 issue / 落 ADR
  再提升版本并在 `SceneMigrations` 登记 `N → N+1` 迁移。解析器只接受白名单节点形状（反 DSL：
  `params` / `keyframe` 仅字面量，嵌套对象 / 数组被拒）；未知步骤 `type` 跳过并告警、未知键忽略
  （前向兼容，且未知步骤在其字段校验之前跳过）。
- **本地化 datagen**：`com.gtsn.ponder.datagen`（`GtsnPonderDatagen` + `GtsnPonderLanguageProvider`）
  把全部 lang 键写入 `src/generated/resources/assets/gtsnponder/lang/{en_us,zh_cn}.json`——
  含自动文案模板键（计数模板如 hatch / module-slot）、颜色图例键、以及每台多方块的机器标题键
  `ponder.gtsnponder.generated.machine.<id>.title`（**自动场景 title 即此键**）。多个方块枚举经
  `com.gtsn.ponder.gt.GtMultiblockCatalog`（GT 访问只在该包），中英名取自 GT 随包 lang 资源。
  **改动生成器文案 / 新增机器后必须重跑 `runData` 并提交产物**；`GeneratedLangKeysTest` 守卫覆盖。
- datagen 产物提交在 `src/generated/resources/`（`main.resources` 已含该 srcDir）；`src/main/resources`
  不再放 lang 文件（避免与生成文件同路径冲突）。

### 游戏内可视化编辑器（#9）

- **单一 DTO**：编辑器编辑运行时同一 v1 DTO（`com.gtsn.ponder.engine.model`）。纯逻辑核心在
  `com.gtsn.ponder.editor`（加入 import 隔离纯包）：`SceneDraft`（可变草稿，`toSceneData()` 以
  `SceneFormat.CURRENT_VERSION` 产出冻结 v1）、`SceneRecorder`（边做边录：显示 / 隐藏分段、高亮、
  轮廓、旁白、相机 → 受封闭 `StepType` 约束的有序 `SceneStep`）、`DraftStore`（经 `SceneDataWriter`
  / `SceneDataParser` 读写）、`EditorGate`（门控谓词）、`EditorSession`（录制 → 保存 → 重载回路）。
  界面 `com.gtsn.ponder.client.SceneEditorScreen` 用 GTSN UI 构建（ADR-0004）：左侧 LDLib 视口预览，
  右侧录制动作 + 步骤选择 + 属性表单（时长 / 目标 / 旁白键 / 相机），底部保存 / 导出生成 / 保存并
  热重载重放。属性表单文本框为 `EditorTextField`（GTSN UI 控件；库无内置输入框）。反 DSL：只写字段
  字面量，无表达式 / 循环 / 条件。
- **保存位置与热重载**：编辑器保存写到手写作者目录 `<gameDir>/gtsnponder-scenes/`（`SceneLibrary`
  除资源包外**第二个扫描来源**，作者场景后加载故覆盖随包场景）。`SceneLibrary.saveAuthorScene()` 写出
  后自动 `reload()`，故保存即重编译、无需重启即可重放编辑后的场景（`source=hand`）。
- **导出自动场景为草稿**：编辑器「导出生成」按钮 / `/gtsnponder export <target>` 把
  `SceneGenerator` 产物经 `SceneDraft.from(...)`（AUTO → HAND）写成作者草稿，作者从生成基线起步编辑。
- **门控**：`EditorGate.isEnabled(production, configOptIn, devOverride)`——开发态（`runClient` /
  客户端自动测试）恒可见；正式玩家默认隐藏（`FMLEnvironment.production=true` 且未选择加入）。
  `PonderEditorAccess` 运行时解析：配置 `editor.editorEnabled`（Forge 客户端配置，默认 false）或
  系统属性 `-Dgtsnponder.editor=true`。入口 `/gtsnponder editor [target]`（客户端命令，未通过门控仅提示）。
- **自动测试**：`GTSNPONDER_UI_AUTOTEST=editor`（见上）：打开编辑器 → 录制 2 步 + 时长 +5 → 保存 →
  断言文件为合法 v1 且步骤/旁白齐全 → 场景库热重载 → 断言重放的是 `source=hand` 手作场景且
  seek 到录制步骤时旁白键为录制键 → 截图。目标选 `gtceu:steam_grinder` 等（规避 `scene` 的焦炉目标，
  避免作者场景覆盖随包场景造成相互污染）。

### JEI/EMI 入口 + 思索图鉴目录（#11）

- **软依赖（dev-only 编译期）**：JEI `mezz.jei:jei-1.20.1-forge` 与 EMI `dev.emi:emi-forge` 以
  `modCompileOnly { transitive = false }` 声明（**不进入运行时 classpath**），`mods.toml` 各加
  `mandatory=false` + `versionRange="[0,)"` + `ordering="AFTER"` + `side="CLIENT"` 的 optional 块。
  两者缺席时集成类（`GtJeiPlugin` / `GtEmiPlugin` 等）**永不加载**，目录 / 快捷键 / 注视入口照常工作
  （零 `NoClassDefFoundError`；由无 JEI/EMI 的 `runClient` 自动测试与 `runGameTestServer` 自证）。
- **入口机制（无 mixin）**：JEI 用官方 `IAdvancedRegistration.addRecipeCategoryDecorator(
MultiblockInfoCategory.RECIPE_TYPE, …)` 在 GT 多方块信息页叠加「思索」按钮；因装饰器接口无输入钩子，
  点击经 Forge `ScreenEvent.MouseButtonPressed.Pre`（`PonderXeiClientEvents`）命中按钮矩形并打开播放屏。
  EMI 用官方 `EmiRegistry.addRecipeDecorator(MultiblockInfoEmiCategory.CATEGORY, …)` 注入一个自绘可点击
  `Widget`（EMI 自行处理点击）。目标取自 GT 机器定义 id（`MultiblockInfoWrapper.definition` /
  `MultiblockInfoEmiRecipe.getId()`）。点击解析缝合在纯 Java `PonderXeiEntry`（装饰器登记目标 + 按钮屏幕
  矩形，点击处理器命中），由 `PonderXeiEntryTest` headless 锁定。**JEI/EMI 集成本工单为编译期覆盖
  （代码评审）+ 点击解析单测**：dev 未加载 JEI/EMI，故不追加运行时自动测试。
- **图鉴目录**：纯逻辑 `com.gtsn.ponder.catalog`（`SceneCatalog` / `SceneCategories` / `CatalogEntry` /
  `WatchedProgress` / `ProgressStore`，零 MC）；屏幕 `SceneCatalogScreen`（GTSN UI）：类别栏
  （`SceneCategories` 关键词派生：模块 / 概念 / 蒸汽 / 发电 / 物流 / 机器）+ 实时搜索 + 每场景已看 /
  未看标记 + 「播放」。条目只收有 `target` 的场景（无目标无法从目录播放）；类别由目标 id 关键词派生
  （v1 格式无类别字段，规则由 `SceneCategoriesTest` 锁定）。入口：快捷键 `key.gtsnponder.catalog`
  （默认 O）、`/gtsnponder catalog`、`PonderEntrypoints.openCatalog()`。
- **进度持久化**：`<gameDir>/gtsnponder-progress.json`（`ProgressStore.FILE_NAME`），规范化 JSON
  `{"formatVersion":1,"watched":[…]}`；键取场景 `id`（无则 `target`）。在播放屏打开即标记已看（幂等，
  `ScenePlayerScreen` 构造时写盘）；`PonderProgress`（客户端单例）读写，缺失 / 非法文件退化为空进度。

### 依赖与类加载纪律

- **GT import 隔离**：`import com.gregtechceu` 仅允许出现在 `com.gtsn.ponder.gt`；由
  `src/test/java/com/gtsn/ponder/GtImportIsolationTest.java` 自动扫描 `src/main/java` 自证（越界即失败）。
- **开发期依赖**：组织 fork GTCEu 以 `:slim` 消费，该工件**不含** `META-INF/jarjar`，故 GT / GTSNLib
  声明的内嵌依赖（`ldlib` / `configuration`；GT 另引用 `registrate`）必须在 dev classpath 显式声明，
  见 `build.gradle` 注释。生产由完整 jarJar 工件提供。
- **客户端 / 服务端分离**：当前所有类均为 common-safe（无客户端专用类）。专职服务端启动（`runServer`）
  日志已验证无客户端类加载 / `invalid dist`；引入客户端类后需按 `runServer` 日志持续核验（沿用体系纪律）。
