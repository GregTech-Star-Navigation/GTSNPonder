# 模块系统演示：真实数据路径缺席时的注入缝

模块系统演示（#14）需要「模块位区域高亮 → 可接受模块 → 安装 → 效果汇总」全部可播。组织 fork 的模块系统**存在**（`ModuleDefinition` / `ModuleSlot` / `ModuleRegion` / `ModuleSlotInfo` / `ModuleEffectSummary`），但**现行 fork 无任何生产机器声明模块位**（`MultiblockMachineDefinition#setModuleSlots` 只有定义类自身与 builder 引用；`GtStructureAdapter#countMachinesDeclaringModuleSlots() == 0`，由 GameTest `adapterReportsNoProductionModuleSlotsYet` latching 自证）。因此演示以**夹具 `StructureSource` + 注入缝**验证，并如实记录偏差。

**Status**: accepted

**Decision**:

- **效果信息提取**收敛在唯一适配包 `com.gtsn.ponder.gt`：`GtStructureAdapter` 用 fork 的
  `ModuleEffectSummary.of(module.getEffects())` 读出并行 / 速度 / 能耗 / 输入 / 输出 / 等级，翻译为
  中性 DTO `ModuleEffectInfo` / `ModuleOption`（`com.gtsn.ponder.structure`），随 `ModuleSlot` 携带。
  任何异常退化为 `ModuleEffectInfo.EMPTY`（不抛出，沿用适配层降级纪律）。
- **模块位区域**在场景中以新元素选择器 `moduleslot`（`params.index`）表达，解析为区域覆盖的**全部单元**
  （空穴以合成占位 id 计），生成器以 `outline` 高亮整片区域（世界桥用绿色第 3 色区分控制器金 / 仓口蓝）。
- **安装是有意义的世界效果**：`INSTALL_MODULE` 把槽位区域单元替换为「已安装模块」的外观方块并把该区域
  并入可见集（空槽安装后模块才出现）；快照 / 重放一致（`DummySceneWorld` / `FakeSceneWorld` 均记录
  `槽位 → 模块` 占用）。
- **等价注入缝**：真实数据路径缺席时，客户端演示 / 自动测试（`GTSNPONDER_UI_AUTOTEST=modules`）直接构造
  声明模块位的夹具 `StructureSource`；适配器侧的模块位 / 效果提取由 GameTest 用**合成
  `MultiblockMachineDefinition` 注入模块位**、经真实 `GtStructureAdapter.toSource` 路径覆盖。
  `adapterReportsNoProductionModuleSlotsYet` 在计数变为非零时**故意失败**，提醒切换到真实数据路径。

**Consequences**:

- 演示内容与质量目前以夹具为准；一旦 fork 出现声明模块位的生产机器，应把演示 / 自动测试切到真实机器并
  更新本 ADR 与 `AGENTS.md`。
- 场景数据格式**不变**（不新增步骤类型 / 元素形状）；`moduleslot` 是选择器词汇扩展（解析器封闭白名单内的
  字面量取值），不触冻结 schema。

---

_关联：ticket #14；ADR-0002（混合内容生产）；`CONTEXT.md`（模块位）；`AGENTS.md`（自动测试命令）。_
