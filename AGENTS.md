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
```

### 自动生成（#7）

- 纯生成器 `com.gtsn.ponder.generate.SceneGenerator`：`StructureSource → SceneData`；`CELL_BUDGET=64`
  以上切换「按角色分组 + LOD / 淡入」，否则逐层（Y 升序）；控制器优先、其后仓口 / 总线高亮；
  成型演示 = 隐藏 → 重现 → 成型脉冲。产物 `source=auto` + `generatorVersion=auto-1`，确定性（同源两次
  生成字节相等 JSON，由 `SceneDataWriter` 序列化）。
- 运行时接线：`/gtsnponder scene <target>` 无手作场景时按需自动生成；`/gtsnponder generate <target>`
  强制生成并播放；`/gtsnponder dump <target>` 导出生成 JSON 到 `run/gtsnponder-generated/`。
- 取景：相机距离 = 包围盒对角线 × `FIT_FACTOR`（下限 `MIN_CAMERA_DISTANCE`），使结构占据视口主要区域；
  相机步骤排在搭建之前（全程同一取景）。
- 高亮可读：控制器金色**线框盒**（全 6 面），仓口 / 总线蓝色细轮廓（按角色分批、总数 ≤ `HATCH_OUTLINE_LIMIT=4`）；
  旁白含颜色图例（`...narration.legend`，成型文案亦复述）。
- 旁白机器特定：`narrationArgs` 模板参数（机器标识 / 结构尺寸 / 仓口数量与角色 / 模块位数量），中英双语键。
- 人类评审门：自动生成的教学质量由人评审截图（`autogen` 自动测试产出），自动化只覆盖结构与确定性。

### 依赖与类加载纪律

- **GT import 隔离**：`import com.gregtechceu` 仅允许出现在 `com.gtsn.ponder.gt`；由
  `src/test/java/com/gtsn/ponder/GtImportIsolationTest.java` 自动扫描 `src/main/java` 自证（越界即失败）。
- **开发期依赖**：组织 fork GTCEu 以 `:slim` 消费，该工件**不含** `META-INF/jarjar`，故 GT / GTSNLib
  声明的内嵌依赖（`ldlib` / `configuration`；GT 另引用 `registrate`）必须在 dev classpath 显式声明，
  见 `build.gradle` 注释。生产由完整 jarJar 工件提供。
- **客户端 / 服务端分离**：当前所有类均为 common-safe（无客户端专用类）。专职服务端启动（`runServer`）
  日志已验证无客户端类加载 / `invalid dist`；引入客户端类后需按 `runServer` 日志持续核验（沿用体系纪律）。
