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
$env:GTSNPONDER_UI_AUTOTEST="catalog"; .\gradlew.bat runClient  # 图鉴目录自动测试（#11）：载入存档 → 清空进度并按需补种子作者场景 → 打开目录 → 断言列表与场景库一致 + 类别覆盖 → 搜索断言（过滤 / 不可能命中 / 清空）→ 播放某条目并断言进度标记已看 → 重开目录断言进度持久 → 多尺寸行布局断言（#19：1280x720@2 / 1920x1080@2 / 1280x720@3，每条可见行的标记 / 名称 / id /「相关机器」/「播放」都在行内、两个按钮在屏幕内；窗口被桌面钳制时按实际尺寸告警继续）→ 主流程截图 3 张 + 每尺寸截图 run/screenshots/gtsnponder-catalog-layout-<WxH>-g<S>.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="gtgui"; .\gradlew.bat runClient  # GT 机器界面覆盖按钮自动测试（#12，入口 ④）：载入存档 → 放置真实 gtceu:coke_oven 多方块并以 GT 标准路径打开其 GUI → 断言覆盖按钮出现在真实 GT 机器屏上且目标正确 → 经事件总线投递 MouseButtonPressed.Pre 断言点击打开该目标的播放屏 → 断言非 GT 屏不出现 → 截图 run/screenshots/gtsnponder-gtgui{,-player,-nongt}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="modules"; .\gradlew.bat runClient  # 模块系统演示自动测试（#14）：载入存档 → 以夹具结构源（两个模块位：具名带效果 / 任意模块）生成场景并播放 → 推进到效果汇总帧断言「模块位区域高亮（多单元）+ 安装生效（空槽被占用）+ 效果汇总旁白」→ 截图 run/screenshots/gtsnponder-modules.png → 重播断言 rewind 清除安装、再播断言确定性重现 → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="systems"; .\gradlew.bat runClient  # 发电·能量网 / 物流管网内容自动测试（#10）：载入存档 → 断言两类内容各 2 个真实 GT 目标经适配器解析且随包场景为 source=mixed → 打开目录断言「发电与能量 / 物流与管网」两类别各含期望目标 → 点击「相关机器」断言相关导航切到同类别兄弟、「全部」恢复 → 分别播放两类主场景断言步骤数与推进到「线缆熔断 / 覆盖板」概念旁白 → 截图 run/screenshots/gtsnponder-systems-{catalog,related,power,logistics}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="coverage"; .\gradlew.bat runClient  # 全量覆盖自动测试（#13）：载入存档 → 枚举全部注册多方块（71 台）→ 断言「每台都有可播场景 + 精选关键机器（5 台）有手作讲解 + 零死链」→ 批量重生成全部生成场景到 run/gtsnponder-generated/ → 打开目录断言 71 条全覆盖 + 每条目可解析 → 播放一台按需生成场景断言 source=auto → 截图 run/screenshots/gtsnponder-coverage-{catalog,generated}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="singleblock"; .\gradlew.bat runClient  # 单方块机器使用场景 + 入口自动测试（#15）：载入存档 → 断言代表性单方块机器（8 台）零死链 + 精选手作（2 台）齐全 + 目录合成使用场景条目 → 放置真实 gtceu:lp_steam_furnace 并以 GT 标准路径打开其 GUI → 断言覆盖按钮出现在真实单方块机器屏上且 target 正确（修复前 resolveTarget 对单方块返回空）→ 投递 MouseButtonPressed.Pre 断言点击打开手作使用场景（source=hand）并断言播放到期望手作旁白 → 断言生成目标 gtceu:lv_centrifuge 解析为 source=auto 使用场景并播放 → 截图 run/screenshots/gtsnponder-singleblock{,-scene,-generated}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="xeipage"; .\gradlew.bat runClient  # EMI 页内入口位置 + 点击可用性自动测试（#17 / #20）：载入存档 → 经 EMI 官方 API 打开 gtceu:lv_macerator 的真实配方页 → 断言入口登记且落在**页面左侧页面按钮列**（与 GtXeiPageProbe.recipePageBounds 的真实 RecipeScreen.getBounds() 比较）→ **复现 #20 根因**：把中性悬停缝置空并推进若干帧，断言入口仍在且矩形不变 → 投递 MouseButtonPressed.Pre 命中该矩形断言事件被取消且打开的是该机器的播放屏 → 非机器物品（minecraft:stone）重开页断言不绘制入口 → 截图 run/screenshots/gtsnponder-xeipage{,-player,-nontarget}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="variants"; .\gradlew.bat runClient  # 多变体播放自动测试（#21 反馈 2）：载入存档 → 找一台可重复结构段多方块（assembly_line 等，适配器返回 ≥2 结构页）→ 断言长变体结构更大 / 步骤不同且生成字节确定 → 播放短变体（default）断言变体按钮存在 → 点击「变体」按钮切到下一变体断言结构更大 → 截图 run/screenshots/gtsnponder-variants-{short,long}.png → 退出（用后清除该环境变量）
$env:GTSNPONDER_UI_AUTOTEST="blockinfo"; .\gradlew.bat runClient  # 点击方块看名称自动测试（#21 反馈 3）：载入存档 → 打开 gtceu:lv_macerator 使用场景 → 指针移到视口中心并点击 → 断言覆盖层显示该方块本地化名称（block.gtceu.lv_macerator）且选中单元为机器本体 → 再点同一方块断言覆盖层关闭 → 再点显示并截图 run/screenshots/gtsnponder-blockinfo.png → 退出（用后清除该环境变量）
```

### 自动生成（#7）

- 纯生成器 `com.gtsn.ponder.generate.SceneGenerator`：`StructureSource → SceneData`；`CELL_BUDGET=64`
  以上切换「按角色分组 + LOD / 淡入」，否则逐层（Y 升序）；控制器优先、其后仓口 / 总线高亮；
  成型演示 = 隐藏 → 重现 → 成型脉冲。产物 `source=auto` + `generatorVersion=auto-1`，确定性（同源两次
  生成字节相等 JSON，由 `SceneDataWriter` 序列化）。
- 运行时接线：`/gtsnponder scene <target>` 无手作场景时按需自动生成；`/gtsnponder generate <target>`
  强制生成并播放；`/gtsnponder dump <target>` 导出单个生成 JSON 到 `run/gtsnponder-generated/`；
  `/gtsnponder dumpall` 批量导出**全部注册多方块**的生成 JSON 到同一目录（见「全量覆盖（#13）」）。
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
- **页面入口位置与点击可用性（#20）**：XEI 页内「思索」入口从**屏幕右上角悬浮**改为**页面左侧的页面按钮列**
  （EMI：`RecipeScreen.getBounds()` 配方面板左缘内缩 5、面板顶之下 30；`workstationLocation=LEFT` 时先还原
  22px 左侧工作台列；JEI：`RecipesGui.getArea()` + 配方区左内缩 6、头部 32，与 EMI 同列 / 同样式 / 同点击）。
  几何集中在纯 Java `com.gtsn.ponder.client.XeiPageEntryLayout`（`leftColumnBox` / `labelScale`，`labelScale`
  把英文 "Ponder" 缩进按钮）；EMI/JEI 适配器只把各自布局换算成「配方面板左上角」，EMI/JEI 类型访问仍只在
  `com.gtsn.ponder.gt`。**点击失效根因**：入口原先的存在与否取决于「鼠标下的物品」，而用户必须把指针**移开**
  那件物品才能点到它——指针一离开，悬停解析即返回空，覆盖层当帧把矩形清空（`present(null,…)`），点击自然
  落空（用户所见「EMI 页内思索不能用」）。**修法**：`PonderXeiPageTargets.Source.entryAnchor` 提供**与悬停
  无关**的页面锚点（`anchorFor` / `entryFor`），覆盖层 `MachinePonderOverlay.presentPage(target, anchor,
pageToken)` 把入口**锁存到本页**（页面身份 = 屏幕实例）：指针移向入口期间目标为空也不清除，换页 / 非 XEI 页 /
  切屏即清除。来源缺席 / 无锚点 / 空矩形 → 不绘制、不消费输入（非目标规则不变）。Forge 的
  `ScreenEvent.MouseButtonPressed.Pre` 在屏幕自身 `mouseClicked`（EMI/JEI 的处理）**之前**派发，命中即先于
  EMI/JEI 消费，故入口区域的点击不会被它们吞掉。
- **自动测试（#20）**：`GTSNPONDER_UI_AUTOTEST=xeipage`（见上）额外断言入口落在**页面左侧按钮列**——与
  `GtXeiPageProbe.recipePageBounds`（真实 `RecipeScreen.getBounds()` 原始布局，不含本 mod 几何）比较；
  并**复现根因**：把中性悬停缝置空、推进若干帧，断言入口**仍在且矩形不变**，再投递 `MouseButtonPressed.Pre`
  命中该矩形、断言事件被取消且打开的是该机器（`gtceu:lv_macerator`）的 `ScenePlayerScreen`。该断言**可失败**：
  临时把 `presentPage` 的锁存改回「目标空即清空」后，自动测试在「入口消失」一步 FAIL（实测留档）。
- **图鉴目录**：纯逻辑 `com.gtsn.ponder.catalog`（`SceneCatalog` / `SceneCategories` / `CatalogEntry` /
  `WatchedProgress` / `ProgressStore`，零 MC）；屏幕 `SceneCatalogScreen`（GTSN UI）：类别栏
  （`SceneCategories` 关键词派生：模块 / 概念 / 蒸汽 / 发电 / 物流 / 机器）+ 实时搜索 + 每场景已看 /
  未看标记 + 「播放」。条目只收有 `target` 的场景（无目标无法从目录播放）；类别由目标 id 关键词派生
  （v1 格式无类别字段，规则由 `SceneCategoriesTest` 锁定）。入口：快捷键 `key.gtsnponder.catalog`
  （默认 O）、`/gtsnponder catalog`、`PonderEntrypoints.openCatalog()`。
- **条目行布局（#16 / #19）**：列宽集中在纯 Java `CatalogRowLayout.columnsFor(行可用宽)`——宽敞时 id / 按钮
  保持设计宽度、名称列吸收余量；窄屏按 id → 相关 → 播放 → 名称 收缩（各自先到可读最小），保证整行
  （标记 + 名称 + id +「相关机器」+「播放」+ 间距）恒 ≤ 可用宽、尾部按钮不被挤出视口。行可用宽 =
  屏幕宽 − `SceneCatalogScreen.ROW_CHROME_WIDTH`（164 = 根内边距 16 + 侧栏 132 + 列间距 6 + 滚动条 6 +
  列表内边距 4；具名常量与 `buildRoot()` 实际布局同步，由 catalog 自动测试在多窗口 / GUI 缩放下按真实
  控件边界自证）。修复前名称列用 `Sizing.fill()`：余量 ≤ 0 时它撑满整行、把 id 与两个按钮排到视口外
  （1280x720@GUI 缩放 3 的症状：每行只见标记 + 机器名）。
- **进度持久化**：`<gameDir>/gtsnponder-progress.json`（`ProgressStore.FILE_NAME`），规范化 JSON
  `{"formatVersion":1,"watched":[…]}`；键取场景 `id`（无则 `target`）。在播放屏打开即标记已看（幂等，
  `ScenePlayerScreen` 构造时写盘）；`PonderProgress`（客户端单例）读写，缺失 / 非法文件退化为空进度。

### GT 机器界面入口按钮（#12，覆盖层）

- **方案（用户批准，偏离原 AC）**：GT 机器界面内「思索」按钮以**客户端覆盖层**实现——经 Forge
  `ScreenEvent`（`Render.Post` 自绘 / `MouseButtonPressed.Pre` / `KeyPressed.Pre` 处理 / `Opening` 清理）
  在 GT 机器屏**之上**自绘按钮；**不改 GT fork、不用 mixin、不动 GT 的 UI 树**。ADR-0005 记录该例外与
  「fork 侧 hook」原 AC 的作废。
- **识别与目标解析（GT 侧，唯一适配包）**：`com.gtsn.ponder.gt.GtMachineScreenAdapter`——GT 机器屏 =
  LDLib `ModularUIGuiContainer` 且 `modularUI.holder` 为 `MetaMachine`；若机器定义是
  `MultiblockMachineDefinition` 则返回其 id 作为思索目标（如 `gtceu:coke_oven`）。非 GT 屏 / 非多方块
  返回空（no-op，不绘制、不消费输入）。
- **覆盖层（客户端，零 MC 纯逻辑 + 事件接线）**：纯几何 / 点击交接 `MachinePonderButton` +
  `MachinePonderOverlay`（headless 单测锁定），事件接线 `GtMachineOverlayClientEvents`；按钮文案键
  `CatalogKeys.GT_MACHINE_OPEN{,_SHORT}`（datagen 产出中英）；点击走 `PonderEntrypoints.openForTarget`。
- **自动测试**：`GTSNPONDER_UI_AUTOTEST=gtgui`（见上）。GT 侧探针 `com.gtsn.ponder.gt.GtMachineUiProbe`
  在开发世界放置机器并经 `MachineUIFactory.openUI` 打开真实 GUI；覆盖层识别 / 绘制由真实渲染循环驱动，
  点击由 `MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Pre(...))` 投递（与生产
  `MouseHandler` 同型事件）。

### 模块系统演示（#14）

- **数据路径（偏差，见 ADR-0006）**：组织 fork 的模块系统存在，但**现行 fork 无任何生产机器声明模块位**
  （`GtStructureAdapter.countMachinesDeclaringModuleSlots() == 0`，由 GameTest
  `adapterReportsNoProductionModuleSlotsYet` latching 自证）。故模块演示以**夹具 `StructureSource` + 注入缝**
  验证；适配器侧的模块位 / 效果提取由 GameTest 用**合成 `MultiblockMachineDefinition` 注入模块位**、经真实
  `toSource` 路径覆盖。
- **效果信息**：`GtStructureAdapter` 用 fork 的 `ModuleEffectSummary.of(module.getEffects())` 读出
  并行 / 速度 / 能耗 / 输入 / 输出 / 等级，翻译为中性 DTO `ModuleEffectInfo` / `ModuleOption`（随 `ModuleSlot`
  携带）。GT 访问仍只在 `com.gtsn.ponder.gt`；异常退化为 `ModuleEffectInfo.EMPTY`。
- **模块位区域**：生成器为每个模块位声明元素 `moduleslot.<i>`（选择器 `moduleslot`，参数 `index`；
  解析为区域覆盖的**全部单元**，空穴以占位 id 计）并发出 `outline` 覆盖整片区域（非单块）。世界桥用
  **绿色**（`DummySceneWorld.MODULE_SLOT_COLOR`）区分控制器（金）/ 仓口（蓝）；播放屏常驻图例加一行。
- **安装演示**：每个可安装模块位——区域高亮 → 可接受模块旁白（具名 `NARRATION_MODULE_SLOT` /
  任意 `...slot.any` / 无 `...slot.none`）→ `INSTALL_MODULE`（空槽 → 安装一个模块）→ 效果汇总旁白
  （`NARRATION_MODULE_INSTALLED` / 无效果 `...installed.none`）。取值确定：槽按下标、模块按 id 升序、
  安装取字典序最小可接受模块；「任意模块」槽安装代表模块 `GENERIC_MODULE_ID`。
- **有意义的世界效果**：世界桥把槽位区域单元替换为「已安装模块」的外观方块（候选
  `MODULE_BLOCK_CANDIDATES`，方块 id 属世界桥关注点、不进冻结数据），并把该区域并入可见集（**空槽安装后
  模块才出现**）；`槽位 → 模块` 占用随 `snapshot` / `restore` 完整还原（seek / rewind 一致）。
- **证据**：headless 单测 `SceneGeneratorModuleTest`（区域高亮 / 安装顺序 / 效果汇总 / 确定性）、
  `ModuleSceneSemanticsTest`（安装状态快照 / 重放）、`ModuleSlotTest` / `SceneElementResolverTest`（区域解析）；
  GameTest `adapterExtractsModuleEffectSummary`；客户端自动测试 `GTSNPONDER_UI_AUTOTEST=modules`（见上）。
- **文案**：新增模块叙述 / 图例键经 `runData` 产出中英（改动生成器文案后必须重跑并提交产物）。

### 发电·能量网 / 物流管网内容（#10）

- **两类内容**：`发电与能量网` 与 `物流管网`（目录类别键 `power` / `logistics`，显示名 `发电与能量` / `物流与管网`；类别由目标 id 关键词派生，规则见 `SceneCategories`）。每类**两条随包场景**（各至少一条，另加一条同类别「相关机器」以支撑目录跳转），均为 `source=mixed`：结构揭示用稳定选择器（`all` / `controller`）**在运行时从真实 GT 多方块解析**（与自动生成器同源的结构数据），旁白为手作概念课。
- **真实目标（全部经适配器解析，入口 ①②③④ 均可解析）**：
  - 发电：`gtceu:large_combustion_engine`（`power_energy.json`，发电 / 电压等级 / 超压 / 线缆熔断）、`gtceu:active_transformer`（`power_transformer.json`，变压器换压）。
  - 物流：`gtceu:steel_multiblock_tank`（`logistics_network.json`，流体储罐 / 管网）、`gtceu:primitive_pump`（`logistics_pump.json`，泵送）。
  - 每台目标同时可被 `SceneGenerator` 自动生成「结构演示」（`source=auto`），见 GameTest `systemContentTargetsResolveAndAutoGenerate`。
- **文案**：机器特定（title / intro）+ 概念特定（电压 / 超压 / 熔断、物品 / 流体管道 / 线缆 / 覆盖板）键集中在纯 Java `com.gtsn.ponder.content.SystemSceneKeys`，经 `runData` 产出中英；`SystemScenesTest`（文件驱动 + 播放 / rewind）与 `GeneratedLangKeysTest`（datagen 产物覆盖）守卫。**改动场景 / 文案后必须重跑 `runData` 并提交产物**。
- **相关机器导航（不落 schema 变更）**：v1 冻结格式无 `related` 字段，故「相关机器」由纯逻辑 `SceneCatalog.relatedTo(entry)` **派生**（同类别，或目标 id 共享显著词元，如 `pyrolyse_oven` 与 `coke_oven` 共享 `oven`），`SceneCatalogScreen` 每条目给「相关机器」按钮，点击即在目录内切换到相关条目（点「全部」/ 选类别 / 搜索退出该视图）。**跨场景「跳转」需新增数据字段（须先开 issue + 落 ADR），本工单不落 schema 变更**，只做目录内导航。
- **证据**：`SystemScenesTest`（文件驱动：头 / 步骤 / 旁白键 / 类别 / 目录 / 相关 / 播放 / rewind）、`SceneCatalogTest`（相关导航派生规则）、`GeneratedLangKeysTest`（中英产物）、GameTest `systemContentTargetsResolveAndAutoGenerate`（真实目标解析 + 自动生成确定性 + 元素可解析）、客户端自动测试 `GTSNPONDER_UI_AUTOTEST=systems`（见上）。

### 全量覆盖：本体 GT 全部多方块（#13）

- **覆盖契约**：全部注册多方块（`GtMultiblockCatalog.all()`，本 fork 实测 **71 台**）都至少有一条可播场景；无手作者**按需自动生成**（`SceneGenerator`），手作者（`source=hand|mixed`）覆盖之手。实测数字：**71 注册 / 71 可解析 / 5 手作（精选）/ 66 生成 / 0 死链**。
- **解析缝（单一事实源）**：`PonderEntrypoints.resolveSceneForTarget(target)`——手作场景优先（`SceneLibrary`），否则经 `GtStructureAdapter.byId` + `SceneGenerator.generate` 生成；返回空即死链（目标不是可解析多方块）。播放入口 `openForTarget` 走同一解析，故「目录里能解析 = 真能播」。
- **目录覆盖**：`SceneCatalog.of(scenes, registeredTargets, progress)` 为**无场景的注册多方块**合成 `source=auto` 条目——键取 `SceneGenerator.sceneIdFor(target)`（与按需生成产物的 `id` 一致，故播放后已看标记能点亮），标题取 datagen 已产出的机器标题键。故图鉴 71 条对全部注册多方块可达、无死链（场景本身仍按需生成，不预先物化）。
- **覆盖分析（纯 Java，零 MC）**：`com.gtsn.ponder.catalog.SceneCoverage`——`CURATED`（5 台精选关键机器 + 其随包场景资源路径）与 `analyze(registeredTargets, resolve)` 折叠为数字（注册 / 可解析 / 手作 / 生成 / 精选手作讲解 / 死链）。headless 单测、GameTest、客户端自动测试共用该分析器，断言同一套数字。
- **重生成缝（fork 升级后重生成并 diff）**：客户端命令 **`/gtsnponder dumpall`**（无参数）把全部注册多方块的自动生成场景批量写到 **`run/gtsnponder-generated/`**（稳定文件名 = 清洗后的目标 id + `.json`，共 71 个）。GT fork 升级后：`.\gradlew.bat --offline runClient` → `/gtsnponder dumpall` → `git diff --no-index`（或任意 diff 工具）比较旧 / 新目录即可看到结构变化。单机入口 `/gtsnponder dump <target>` 仍可用。
- **证据**：headless `SceneCoverageTest`（分析器 + **读取真实随包场景文件**核对精选清单不漂移）、`SceneCatalogTest`（覆盖重载：合成 / 去重 / 已看键）= 单元测试（当前 **265** 项）；GameTest `CoverageGameTests.everyRegisteredMultiblockHasAPlayableScene`（枚举真实注册表：每台结构可解析 → 生成场景每个元素可解析 → 确定性；再经共享分析器核算数字）= **17** 项 GameTest；客户端自动测试 `GTSNPONDER_UI_AUTOTEST=coverage`（见上：数字 + 重生成 + 目录全可达 + 播放按需生成场景 + 截图）。

### 单方块机器使用场景 + 入口（#15）

- **缺口**：原 `GtMachineScreenAdapter.resolveTarget` 只对 `MultiblockMachineDefinition` 返回目标，故**单方块机器**（如 `gtceu:lp_steam_furnace`）在 GT 机器界面无「思索」覆盖按钮、也无场景（既有 71 台覆盖全是多方块）。
- **入口（① 注视 / ② 命令 / ③ 目录 / ④ GT GUI）**：`resolveTarget` 改为对**任意** `MetaMachine`（多方块或单方块）返回其定义 id；`PonderEntrypoints.resolve` 先试多方块 `GtStructureAdapter`，否则试单方块 `GtSingleBlockAdapter`。非 GT 屏照旧 no-op。
- **无可思索目标时的行为（本工单决定）**：覆盖按钮对任意 GT 机器屏**照常绘制**；点击后若目标解析不出场景，`openForTarget` 显示本地化空态提示（`ponder.gtsnponder.message.no_scene`）且**不消费**该次点击（即「绘制 + 点击时本地化空态提示」，而非静默不绘制）。见 `GtMachineScreenAdapter` javadoc。
- **中立 DTO**：`com.gtsn.ponder.structure.SingleBlockMachineSource`（机器 id / 方块 / 等级 / 物品·流体输入输出计数 / 是否接电 / 配方类型 id 列表）——由唯一适配包 `GtSingleBlockAdapter` 从 `MachineDefinition` 元数据产出（**不需要多方块结构页**：等级取 `getTier()`+`GTValues.VN`，槽 / 罐 / 电能力由配方类型的 `maxInputs`/`maxOutputs` 按能力族聚合）。
- **使用场景生成器（纯 Java）**：`com.gtsn.ponder.generate.SingleBlockUsageGenerator`——`SingleBlockMachineSource → SceneData`。机器本体表达为 **1×1×1** 的多方块结构源（`structureOf`：唯一方块 = 机器方块且为控制器单元），场景只声明一个元素 `machine`（选择器 `all`），**不新增元素选择器**（封闭词汇不变，见 `docs/scene-format.md`）。步骤用**既有封闭 `StepType`**：`intro` 文本 → 相机取景（`fit`）→ `showSection` 揭示本体 → `highlight` 本体 → 本体 / 输入 / 输出 / 能量 / 进度 / 覆盖板 / 常见坑旁白。**不含** `build.layer/role`、`installModule`、`formedPulse`（区别于多方块搭建演示）。旁白键 `ponder.gtsnponder.generated.usage.*`（带 `narrationArgs` 模板参数），场景 `title` 复用 GT 自身方块名键 `block.<ns>.<path>`（`titleKeyFor`），故任意单方块机器都有本地化标题、无需额外 datagen 枚举。产物 `source=auto` + `generatorVersion=usage-1`，确定性。
- **代表性机器（8 台）**：蒸汽 `gtceu:lp_steam_furnace` / `lp_steam_macerator` / `lp_steam_alloy_smelter` / `lp_steam_solid_boiler`，基础电力 `gtceu:lv_macerator` / `lv_electric_furnace` / `lv_centrifuge` / `lv_electrolyzer`；其中 **2 台手作**（`lp_steam_furnace`、`lv_macerator`，`source=hand`，随包 JSON `assets/gtsnponder/ponder/{steam_furnace,lv_macerator}.json`），其余 6 台按需生成。清单 / 分析器为纯 Java `com.gtsn.ponder.catalog.SingleBlockScenes`（`REPRESENTATIVE` / `CURATED` / `analyze`），并加入随包手作场景守卫（`SceneCoverageTest.curatedListMatchesEveryBundledHandAuthoredScene` 现核验多方块 + 单方块两套精选清单之并集）。
- **目录接线**：`SceneCatalog.of(scenes, registeredTargets, usageTargets, progress)` 为代表性单方块目标合成条目——键取 `SingleBlockUsageGenerator.sceneIdFor(target)`（`gtsnponder:usage_<sanitized>`，与按需产物 `id` 一致故已看标记可点亮），标题取 GT 方块名键；`SceneCatalogScreen` 新增 `usageTargets` 形参（旧签名保留）。类别由目标 id 关键词派生（蒸汽 → `steam`，电力 → `machines`）。
- **文案**：`SingleBlockUsageGenerator` 的模板键与手作键经 `runData` 产出中英（**改动后必须重跑 `runData` 并提交产物**）；`GeneratedLangKeysTest` 守卫覆盖。
- **证据**：headless 单测 `SingleBlockMachineSourceTest` / `SingleBlockUsageGeneratorTest`（使用场景形状 / 确定性 / 可播放 / 旁白键与参数）、`SingleBlockScenesTest`（代表性 / 手作文件 / 分析器）、`SceneCatalogTest`（使用条目合成 / 已看键）= **288** 项单元测试；GameTest `SingleBlockGameTests`（代表性 8 台真实注册表解析 + 生成确定性 + 元素可解析；精选手作随包文件 + 覆盖数字）= **19** 项 GameTest（含 #13 的 17 项）；客户端自动测试 `GTSNPONDER_UI_AUTOTEST=singleblock`（见上：真实单方块机器 GUI + 覆盖按钮 + 点击打开手作使用场景 + 生成使用场景 + 截图）。

### 多变体 / 点击方块看名称 / 搭建序列一致性（#21）

- **背景（三条用户反馈）**：① 部分机器看不到搭建过程；② 可重复结构段的机器只能看默认（短）变体；
  ③ 无法点击场景内方块查看名称。
- **搭建序列审计**：GameTest `VariantGameTests.everyRegisteredMultiblockBuildsIncrementallyAndVariantsAreEnumerated`
  枚举全部注册多方块（71 台）并断言**每台默认生成场景都含 `build.*` 揭示序列**（一次性审计留档：71/71 通过）。
  审计发现的真实缺口是**随包手作场景覆盖**：4 条 `source=mixed` 概念场景（`power_energy` / `power_transformer` /
  `logistics_network` / `logistics_pump`）原先只有一次 `showSection`（无搭建过程），已按既有策略补成
  **逐层揭示**（`layer.0/1/2`，对应目标结构均为 3 层）；`coke_oven.json` 本就是多段 reveal 不在此列。
  单方块机器（`SingleBlockUsageGenerator`）**不伪造结构搭建**，而是补一段 `outline.machine`（蓝色本体轮廓），
  与既有 `highlight.machine`（金色）形成「高亮 → 轮廓」两段式节奏；`generatorVersion` 提升为 `usage-3`，
  两条手作单方块场景（`steam_furnace` / `lv_macerator`）同步加该步。
- **多变体**：`GtStructureAdapter.variantsById(id)` 把 `getMatchingShapes()` 的**每一页**翻译为独立结构源
  （默认 `byId` 仍只展开第一页，不引入按需解析的性能回归）；纯逻辑 `com.gtsn.ponder.generate.SceneVariants`
  为每页产出稳定 `Spec`（id + 本地化标签键 + 参数）：单页 `default`、两页 `default`/`long`（短 / 长）、
  多页按变化轴长度 `slices_<n>`（「n 节」），尺寸无规律变化时退化为 `v<i>`（「变体 i」）。`SceneGenerator` 新增
  `generate(source, variantId, sceneId)` 重载（默认路径不变，第一页仍是 `sceneIdFor(target)` = 目录 / 进度键兼容）。
  播放屏（`ScenePlayerScreen`）在变体 >1 时显示「变体」按钮循环切换；入口 `PonderEntrypoints.variantOptions/
openVariant`。实测：**16 台**机器有 ≥2 变体（装配线 13 页 50→170 方块、蒸馏塔 11、分馏塔 12、合金冶炼炉 / 裂化器 /
  高炉 / 工业熔炉 / 大型化学反应釜 8 等）。
- **点击方块看名称**：纯逻辑 `com.gtsn.ponder.bridge.BlockInfoResolver`（结构坐标 → 方块名键 `block.<ns>.<path>`
  - 角色键），GT 等级附加信息走 `com.gtsn.ponder.gt.GtBlockInfo.tierKeyFor`（GT import 仍只在 `.gt`）。
    `LdlibSceneViewport` 记录指针坐标并交给 LDLib `drawInBackground` 做拾取（`pickedBlock()` 读 `hoverPosFace`）；
    `ScenePlayerScreen` 在「视口内按下 + 未拖拽」时选中方块并在点击处绘制覆盖层（名称 + 角色 + 等级），
    再点同一方块 / 点空处关闭。
- **证据**：headless 单测 `SceneVariantsTest`（命名 / 变体 id / 每变体确定性 / 变体场景仍是冻结 v1）、
  `BlockInfoResolverTest`（名称 / 角色 / 空态）；GameTest `VariantGameTests`（合成两页定义 → 两个尺寸不同的变体；
  71 台搭建序列审计；162 个变体场景可解析 + 确定性）= 23 项 GameTest；客户端自动测试 `variants` / `blockinfo`（见上）。
  文案：变体标签键（`ponder.gtsnponder.variant.*`）经 `runData` 产出中英。

### 依赖与类加载纪律

- **GT import 隔离**：`import com.gregtechceu` 仅允许出现在 `com.gtsn.ponder.gt`；由
  `src/test/java/com/gtsn/ponder/GtImportIsolationTest.java` 自动扫描 `src/main/java` 自证（越界即失败）。
- **开发期依赖**：组织 fork GTCEu 以 `:slim` 消费，该工件**不含** `META-INF/jarjar`，故 GT / GTSNLib
  声明的内嵌依赖（`ldlib` / `configuration`；GT 另引用 `registrate`）必须在 dev classpath 显式声明，
  见 `build.gradle` 注释。生产由完整 jarJar 工件提供。
- **客户端 / 服务端分离**：当前所有类均为 common-safe（无客户端专用类）。专职服务端启动（`runServer`）
  日志已验证无客户端类加载 / `invalid dist`；引入客户端类后需按 `runServer` 日志持续核验（沿用体系纪律）。
