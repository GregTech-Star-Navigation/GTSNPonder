# 入口矩阵，以及 GT 机器界面按钮的受控例外

思索入口为：**① 注视方块 + 快捷键；② JEI/EMI 机器页按钮；③ 思索图鉴 / 目录；④ GT 机器界面内按钮**。前三条为主路径。**④ 是对 GTSNLib ADR-0004「不接管 GT 机器界面」的一次受控例外**：不在 GT 的 UI 树中挂载任何内容，只经组织 fork 侧一个**版本化 hook** 打开 GTSN 屏幕。

**Status**: accepted

**Considered Options**:

- **四入口（选定）**：覆盖"就地看 / 查配方时看 / 系统浏览"三类场景。
- 不含 ④：完全守 ADR；但从机器界面就地学习的路径缺失。
- 接管 / 重做 GT 机器界面：被 GTSNLib ADR-0004 明确排除。

**Consequences**:

- ④ 排到最后阶段评估；实现依赖组织 fork 的 hook（**非 Mixin**），并随 fork 版本化。
- 需登记为对 GTSNLib ADR-0004 的例外，并在该 ADR 中回指。

---

_关联：GTSNLib `docs/adr/0004-self-built-ui-framework.md`；规格 §Out of Scope。_
