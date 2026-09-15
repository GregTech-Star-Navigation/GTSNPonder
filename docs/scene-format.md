# 场景数据格式（v1，已冻结）

> 关联：ADR-0003（数据格式 + 游戏内编辑器，反 DSL）· `docs/spec-gtsnponder.md` §Implementation Decisions 场景数据 · `docs/constraints.md` §3（数据格式约束）
> 状态：**v1 FROZEN**。本文档是场景 JSON 的权威 schema；解析器 / 生成器 / 序列化器 / 编辑器共用同一 DTO（`com.gtsn.ponder.engine.model`，单一事实源）。

## 变更策略（冻结的含义）

- v1 的**结构**（字段名、类型、节点形状、封闭步骤枚举）已冻结。作者可以自由添加**内容**（新场景、新元素、新步骤实例），**不得**改变形状。
- 任何对已冻结结构的变更（新增必填字段、改变类型、新增节点形状、删除 / 重命名字段、扩展步骤枚举语义）**必须先开 issue；涉及架构决策须落 ADR**；然后：
  1. 提升 `com.gtsn.ponder.engine.model.SceneFormat.CURRENT_VERSION`（**单一事实源**，解析器 / 生成器均引用它）；
  2. 在 `com.gtsn.ponder.engine.model.SceneMigrations` 的生产链中登记一条 `N → N+1` 迁移；
  3. 更新本文档并标注新版本。
- **前向兼容**（无需提升版本即可容忍）：未知步骤 `type` → 跳过并告警；未知键（顶层 / 元素 / 步骤）→ 忽略。这两条允许「新数据跑在旧解析器上」不崩。
- **反 DSL 铁律**（不可协商）：不允许表达式、条件、循环、用户函数与算术；`params` / `keyframe` **只接受字面量**（string / number / boolean / null）。扩展只能通过注册 Java `StepType`，而非脚本解释器。嵌套对象 / 数组会被解析器拒绝（见下）。

## 版本与迁移

- `formatVersion` 为**强制**字段：缺失 / 非整数 / `< 1` / **高于当前版本** → `SceneFormatException`（清晰拒绝，不静默降级）。
- 合法旧版本：解析器按 `SceneMigrations` 的 `N → N+1` 有序链逐级应用，直到抵达 `SceneFormat.CURRENT_VERSION`；每级后把文档 `formatVersion` 前进到 `N+1`。解析器上游只面对当前版本的结构。
- v1 是最早版本，故生产迁移链当前为空（无可迁移的历史版本）；机制与合成迁移由 `SceneMigrationsTest` 证明。

## 顶层结构

```jsonc
{
  "formatVersion": 1,             // 强制；整数；1..CURRENT_VERSION
  "id": "gtceu:test_scene",       // 可选；场景稳定 ID
  "title": "ponder.gtxxx.title",  // 可选；本地化键（运行时经 Component.translatable 渲染）
  "target": "gtceu:test_machine", // 可选；所讲解的目标（机器 / 多方块 / 物品 / 概念）
  "variant": "default",           // 可选；同一目标的版本页
  "source": "hand",               // 可选；auto | hand | mixed（缺省 auto）
  "generatorVersion": "hand-1",   // 可选；可重生成产物标识
  "elements": [ ... ],            // 可选；元素数组（见下）
  "steps": [ ... ],               // 可选；有序步骤数组（见下）
  "futureTopLevelKey": { }        // 未知顶层键 → 忽略（前向兼容）
}
```

## `elements[]`：稳定字符串 ID 的元素（分段 / 锚点）

```jsonc
{
  "id": "section.layer.0", // 强制；非空字符串（场景内唯一，供 targets 引用）
  "kind": "section", // 可选；如 section / anchor
  "params": { "selector": "layer", "y": 0 }, // 可选；仅字面量
  "futureElementKey": true, // 未知键 → 忽略
}
```

- 每个元素必须是 JSON 对象；`id` 缺失 / 空白 → 拒绝。
- `elements` 必须是数组（缺失即空）。
- `params.selector` 是**封闭词汇**（`all`（缺省）/ `controller` / `block` / `layer` / `role` / `moduleslot`；语义见 `com.gtsn.ponder.bridge.SceneElementResolver`）。单方块机器「使用场景」（#15）把机器本体表达为 **1×1×1** 结构源并复用选择器 `all`（在该结构上恒等于机器本体），故 **v1 未新增选择器**；若未来确需新选择器（如 `self`），按「变更策略」开 issue / 落 ADR 后再加，只扩展封闭词汇、不改 schema 形状。

## `steps[]`：有序的步骤

```jsonc
{
  "id": "s1", // 强制；非空字符串
  "type": "showSection", // 强制；封闭枚举（见下）；未知 → 跳过并告警
  "duration": 10, // 可选；tick，非负整数（缺省 0；0 = 瞬时非阻塞）
  "targets": ["section_a"], // 可选；字符串数组，元素 ID 的稳定引用
  "params": { "visible": true }, // 可选；仅字面量
  "narration": "ponder...key", // 可选；旁白本地化键
  "narrationArgs": ["Coke Oven"], // 可选；模板参数（字符串数组）
  "keyframe": { "frame": 3 }, // 可选；关键帧字面量（供视口插值）
  "futureStepKey": 42, // 未知键 → 忽略
}
```

- 每个步骤必须是 JSON 对象；`id` 缺失 / 空白 → 拒绝。
- `targets` / `narrationArgs` 必须是字符串数组（任何非字符串元素 → 拒绝）。
- `duration` 必须是非负整数（小数 / 负数 → 拒绝）。
- `params` / `keyframe` 必须是对象，且**值只能是字面量**；嵌套对象 / 数组 → 拒绝。

### `StepType`（封闭枚举，冻结）

JSON 名（`jsonName`）与 Java 枚举常量一一对应；新增类型=新增枚举常量 + 注册 Java 步骤实现。

| JSON `type`     | Java 常量        | 语义               |
| --------------- | ---------------- | ------------------ |
| `showSection`   | `SHOW_SECTION`   | 显示分段（搭建）   |
| `hideSection`   | `HIDE_SECTION`   | 隐藏分段           |
| `replaceBlocks` | `REPLACE_BLOCKS` | 替换方块           |
| `highlight`     | `HIGHLIGHT`      | 高亮（金色线框盒） |
| `outline`       | `OUTLINE`        | 轮廓（蓝色线框盒） |
| `text`          | `TEXT`           | 旁白字幕           |
| `camera`        | `CAMERA`         | 相机               |
| `idle`          | `IDLE`           | 空转               |
| `installModule` | `INSTALL_MODULE` | 安装模块           |
| `formedPulse`   | `FORMED_PULSE`   | 成型脉冲           |
| `particles`     | `PARTICLES`      | 粒子               |

## schema 白名单（可测）

`SceneDataParser` 只接受上述形状；下列一律 `SceneFormatException`：

- 根不是对象；`elements` / `steps` 不是数组；元素 / 步骤不是对象。
- 元素 `id` 缺失 / 空白；步骤 `id` 缺失 / 空白。
- `targets` / `narrationArgs` 不是字符串数组。
- `duration` 非整数或为负。
- `params` / `keyframe` 不是对象，或含嵌套对象 / 数组（反 DSL）。
- `formatVersion` 缺失 / 非整数 / `< 1` / 高于当前版本。

反例锁在 `SceneSchemaWhitelistTest`；未知步骤类型 + 未知键的前向兼容锁在 `SceneDataParserTest`。

## 本地化与自动文案（datagen）

- 全部文案走本地化键（中英）；自动生成场景的模板文案经 datagen 批量产出到
  `src/generated/resources/assets/gtsnponder/lang/{en_us,zh_cn}.json`（provider：`com.gtsn.ponder.datagen`）。
- 计数模板（如 `...narration.hatches` = `%s hatch / bus block(s), across %s.`、`...narration.modules` = `%s module slot(s)...`）随同一份 lang 产出。
- 自动生成场景的 `title` 是机器标题键（`GeneratedKeys.machineTitleKey(targetId)` =
  `ponder.gtsnponder.generated.machine.<sanitized-id>.title`），由 datagen 经
  `com.gtsn.ponder.gt.GtMultiblockCatalog` 枚举全部多方块产出中英名（GT 访问只在该包内）。
- 产物守卫：`GeneratedLangKeysTest` 断言生成文件覆盖全部叙事 / 图例 / 计数模板键与至少一台机器标题键。

## 序列化（往返稳定）

`SceneDataWriter` 是解析器的逆：字段顺序固定、参数映射按键名排序、`params` 与 `keyframe` 各写独立键，
故 `write(parse(write(scene)))` 字节相等（自动生成器的「两次生成字节相等」证据基于此）。
