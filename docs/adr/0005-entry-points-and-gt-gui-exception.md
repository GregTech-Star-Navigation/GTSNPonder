# 入口矩阵，以及 GT 机器界面按钮的受控例外

思索入口为：**① 注视方块 + 快捷键；② JEI/EMI 机器页按钮；③ 思索图鉴 / 目录；④ GT 机器界面内按钮**。前三条为主路径。**④ 是对 GTSNLib ADR-0004「不接管 GT 机器界面」的一次受控例外**：**不改动 GT 的 UI 树**，只经 Forge 官方的 `ScreenEvent` 在 GT 机器屏**之上**自绘一个小按钮（客户端覆盖层）。GT 代码 / 制品不变。

**Status**: accepted

**Considered Options**:

- **四入口（选定）**：覆盖"就地看 / 查配方时看 / 系统浏览"三类场景。
- 不含 ④：完全守 ADR；但从机器界面就地学习的路径缺失。
- 接管 / 重做 GT 机器界面：被 GTSNLib ADR-0004 明确排除。

**Decision（④ 的实现方式 = 客户端覆盖层）**:

- **识别**：`com.gtsn.ponder.gt.GtMachineScreenAdapter`（唯一 GT 适配包）判定当前屏是否为 GT 机器屏——
  即 LDLib `ModularUIGuiContainer` 且其 `modularUI.holder` 为 GT 的 `MetaMachine`；若该机器定义是
  `MultiblockMachineDefinition`，则解析出思索目标 id（如 `gtceu:coke_oven`）。非 GT 屏 / 非多方块一律
  返回空（优雅 no-op）。
- **绘制与交互**：`com.gtsn.ponder.client.GtMachineOverlayClientEvents` 监听 Forge `ScreenEvent`
  （`Render.Post` 自绘右上角「思索」按钮、`MouseButtonPressed.Pre` / `KeyPressed.Pre` 处理点击与快捷键、
  `Opening` 切屏清理）；点击经既有 `PonderEntrypoints` 打开播放屏。GT 的 UI 树未被挂载 / 修改，
  不用 mixin，不引入自定义网络包。
- **不变量**：GT import 仍仅出现在 `com.gtsn.ponder.gt`；不改 GT fork 仓库、不发布新 GT 制品。

**Consequences**:

- ④ 的自动化证据：`GTSNPONDER_UI_AUTOTEST=gtgui` 客户端自动测试——在开发世界放置真实 GT 多方块、
  经 GT 标准路径打开其 GUI，断言覆盖按钮出现在真实 GT 机器屏上、点击打开正确目标的播放屏、且非 GT 屏
  不出现（截图 `run/screenshots/gtsnponder-gtgui*.png`）。
- 覆盖层随 GT 的机器屏类（LDLib `ModularUIGuiContainer`）演进；该耦合收敛于 `com.gtsn.ponder.gt`。
- 需登记为对 GTSNLib ADR-0004 的例外，并在该 ADR 中回指。

**Superseded（偏离记录）**:

- ticket #12 的 AC 原文包含「经 fork 侧版本化 hook 打开 GTSN 屏幕」。经用户批准，**该措辞作废**，
  改用上述覆盖层方案（不改 fork、不用 mixin）。原决策段中"只经组织 fork 侧一个版本化 hook"的表述同样作废。

---

_关联：GTSNLib `docs/adr/0004-self-built-ui-framework.md`；规格 §Out of Scope、入口 ④；`docs/constraints.md` §7。_
