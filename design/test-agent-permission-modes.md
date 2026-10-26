# Test Agent 权限模式与正式资产写入设计

## 1. 文档目的

本文定义 Test Agent 对测试资产的三种权限模式，以及后续开发时必须遵守的服务端授权、Draft 生命周期、正式 Scenario 写入、前端交互、审计和测试规则。

本文既是设计决策，也是供后续开发模型执行的实施合同。实现者不得仅通过 Prompt 或前端控件模拟权限；所有权限和副作用必须由服务端确定性代码控制。

本文描述的是 V1 完成后的能力演进。V1 的 `DRAFT_ONLY` 行为将被 `REVIEW_REQUIRED` 替代。

相关设计基线：

- [Test Agent 产品愿景](test-agent-vision.md)
- [Test Agent 产品与架构设计](test-agent-architecture.md)
- [Test Agent 工具与执行治理](test-agent-runtime-governance.md)
- [Test Agent 能力边界](ai-assistance-boundaries.md)

## 2. 核心决策

### 2.1 三种模式

平台只提供以下三种 Agent 权限模式：

```text
READ_ONLY
REVIEW_REQUIRED
AUTO_APPLY
```

不提供 `UNRESTRICTED`、`ADMIN`、`DIRECT_WRITE` 等绕过 Draft、Validation、Diff、revision 检查或审计的模式。

### 2.2 Draft 不是权限级别

Draft 是所有写操作的中间表示，不是权限模式本身。

`REVIEW_REQUIRED` 和 `AUTO_APPLY` 都必须经过：

```text
Scenario source
  -> Draft
  -> typed Command Plan
  -> deterministic Validation
  -> structured Diff
  -> revision and policy checks
  -> user review or guarded apply
```

`AUTO_APPLY` 不能直接修改数据库、拼接 YAML 或绕过现有 Scenario 保存规则。

### 2.3 不兼容旧权限数据

本项目仍处于开发阶段，本次改造不迁移历史 Agent Task 数据。

实现要求：

- 删除代码、测试和文档中的 `DRAFT_ONLY` 权限值
- 不编写将 `DRAFT_ONLY` 更新为 `REVIEW_REQUIRED` 的 Flyway 数据迁移
- 直接更新基线 schema、测试夹具和默认值
- 开发环境数据库由开发者重建
- 不保留旧 API 字段、旧枚举别名或兼容分支

`platform_agent_task.permission_mode` 字段可以继续使用，长度保持 `varchar(32)`。

## 3. 模式语义

### 3.1 READ_ONLY

用途：分析、解释、覆盖评审、API Contract 阅读和执行结果诊断。

允许：

- 读取当前授权范围内的 Scenario、Suite 和 Execution
- 按需搜索并读取当前 Project 的 API Operation
- 读取已发布且在作用域内的 Test Knowledge
- 输出自然语言分析和只读 Artifact

禁止：

- 创建 Scenario Draft
- 修改 Draft
- 应用 Command Plan
- 写入正式 Scenario
- 通过写 Tool 间接产生 Draft 或正式资产副作用

该模式下，模型不应看到 Draft 写工具。

### 3.2 REVIEW_REQUIRED

用途：Agent 完成结构化修改，用户审查后决定是否应用。

允许：

- 包含 `READ_ONLY` 的全部能力
- 创建和刷新 Scenario Draft
- 使用 typed Command 修改 Draft
- 执行 Validation 和 Diff
- 在 Chat 中展示最新 Draft 结果

禁止：

- Agent 自动写入正式 Scenario
- Agent 将用户点击 Apply 描述为自己完成了正式保存

用户流程：

```text
Agent prepares Draft
  -> user reviews Plan, Diff and Validation
  -> user selects Apply to editor
  -> editor receives Draft
  -> user saves formal Scenario
```

当前前端 `onApplyDraft` 是“应用到编辑器”，不是服务端正式发布操作。实现者必须保留这个语义差异。

### 3.3 AUTO_APPLY

用途：在严格规则内自动应用低风险、确定性、可审计的 Scenario 修改。

允许：

- 包含 `REVIEW_REQUIRED` 的 Draft 能力
- 对满足 Guard Policy 的 Command Plan 调用正式发布 Tool

必须自动降级为 `REVIEW_REQUIRED` 的情况：

- Command Plan 包含不允许自动应用的 Command
- Draft Validation 失败
- Draft source revision 与正式 Scenario 不一致
- Draft、Task、用户、Project 或目标 Scenario 所属关系不匹配
- 目标类型不是当前允许自动写入的 `SCENARIO`
- 无法确定风险等级
- Guard Policy 或审计写入失败

降级不是任务失败。Agent 应保留可审查 Draft，并明确说明需要用户检查的原因。

## 4. 权限与 Tool 分类

### 4.1 Tool 类别

Tool 的 `category` 使用以下受控值：

```text
READ
DRAFT_WRITE
VALIDATE
FORMAL_WRITE
```

`sideEffect` 必须准确描述副作用，不能把正式写入工具标记为 `NONE`。

建议值：

```text
NONE
DRAFT
FORMAL_ASSET
```

### 4.2 权限矩阵

| Permission Mode | READ | DRAFT_WRITE | VALIDATE | FORMAL_WRITE |
|---|---:|---:|---:|---:|
| `READ_ONLY` | Allow | Deny | Deny | Deny |
| `REVIEW_REQUIRED` | Allow | Allow | Allow | Deny |
| `AUTO_APPLY` | Allow | Allow | Allow | Guarded |

`Guarded` 表示 Tool 通过第一层模式授权后，仍必须通过 Command 风险、Draft 状态、revision、所有权和 Validation 检查。

### 4.3 双层控制

权限必须同时在两个位置生效：

1. `AgentToolRegistry.toolsFor(...)` 只向模型暴露当前模式允许的 Tool
2. `AgentToolPolicy.authorize(...)` 在实际调用时再次校验

不能只隐藏 UI，也不能只依赖 Skill 指令。

## 5. 领域模型改造

### 5.1 权限枚举

新增强类型枚举，禁止在业务代码中散落字符串比较：

```java
public enum AgentPermissionMode {
    READ_ONLY,
    REVIEW_REQUIRED,
    AUTO_APPLY
}
```

建议位置：

```text
platform-api/src/main/java/com/example/poc/platform/agent/AgentPermissionMode.java
```

`AgentTaskRecord` 和 `CreateAgentTaskRequest` 使用该枚举，而不是 `String`。

### 5.2 Task 创建规则

`CreateAgentTaskRequest` 增加：

```java
AgentPermissionMode permissionMode
```

默认规则：

- Scenario Chat 默认 `REVIEW_REQUIRED`
- Project Chat 默认 `READ_ONLY`
- Suite Chat 在没有 Suite Draft 写能力前默认 `READ_ONLY`
- 请求未指定时由服务端根据 target type 计算默认值
- 前端传值不能绕过服务端 target type 限制

Task 可以切换 `permissionMode`，但不能覆盖历史权限语义。每次切换必须创建新的权限阶段：

- Task 保留当前 `permissionMode` 和递增的 `permissionModeRevision`
- 通过专用权限切换 API 在服务端加锁完成
- 切换时写入 `task.permission_mode.changed` 审计事件，记录旧模式、新模式和权限阶段版本
- 运行中的 Task 不允许切换；当前 Turn 必须结束或取消后再切换
- 新模式只影响之后创建的 Turn；历史消息、Tool 调用和 Draft 仍按当时的权限阶段解释

### 5.3 数据库策略

直接更新权限基线和开发数据，不新增旧权限兼容迁移：

- 保留 `platform_agent_task.permission_mode varchar(32) not null`
- 增加 `platform_agent_task.permission_mode_revision integer not null`，初始值为 `1`
- 更新创建 Task 的默认逻辑
- 更新测试 fixture 中的 `DRAFT_ONLY`
- 不编写 `DRAFT_ONLY -> REVIEW_REQUIRED` 的数据迁移

不要增加旧值解析：

```java
// 禁止
if ("DRAFT_ONLY".equals(value)) return REVIEW_REQUIRED;
```

## 6. Command 风险模型

### 6.1 风险等级

新增：

```java
public enum AgentCommandRisk {
    LOW,
    MEDIUM,
    HIGH
}
```

每种 `ScenarioCommand` 必须由平台代码映射到风险等级。未知 Command 默认 `HIGH`，不能默认允许。

### 6.2 初始风险建议

低风险候选：

- `RenameStep`
- `SetRequestHeader`
- `RemoveRequestHeader`
- `AddAssertion`
- `RemoveAssertion`
- `AddExtraction`
- `RemoveExtraction`

中风险候选：

- `SetQueryParameter`
- `SetPathParameter`
- `SetRequestBody`
- `SetRequestBodyValue`
- `SetResponseStatus`
- `MoveStep`
- `AddBeforeAction`
- `AddAfterAction`

高风险候选：

- `AddStep`
- `RemoveStep`
- `SelectOperation`
- 改变加密、签名或身份相关语义的 Action
- 任何平台尚未分类的新 Command

初始 POC 的 `AUTO_APPLY` 只允许配置白名单中的 `LOW` Command。风险等级只是输入，最终授权以 allowlist 为准。

### 6.3 Guard Policy

建议在 `AgentProperties` 中增加：

```yaml
agent:
  auto-apply:
    enabled: false
    allowed-command-types:
      - RenameStep
      - SetRequestHeader
      - RemoveRequestHeader
      - AddAssertion
      - RemoveAssertion
      - AddExtraction
      - RemoveExtraction
```

默认 `enabled: false`。只有服务端显式启用后，前端才允许选择 `AUTO_APPLY`。

## 7. 正式 Scenario 发布服务

### 7.1 新增服务端能力

`AUTO_APPLY` 需要新增正式发布服务和 Tool。建议命名：

```text
ScenarioDraftApplyService
applyScenarioDraft
```

Tool 定义：

```text
name: applyScenarioDraft
category: FORMAL_WRITE
sideEffect: FORMAL_ASSET
```

Tool 输入至少包含：

```json
{
  "draftId": "...",
  "expectedDraftRevision": 2,
  "expectedScenarioRevision": 5
}
```

模型不能提交 Scenario YAML 作为正式写入内容；服务端只能读取已经持久化且属于该 Task 的 Draft。

### 7.2 原子事务

正式应用必须在同一个服务端事务中完成：

1. 加锁读取 Agent Task
2. 确认 Task 为 `AUTO_APPLY`
3. 确认 Task target 为 `SCENARIO`
4. 加锁读取 Draft 和正式 Scenario
5. 校验 Draft 为 `ACTIVE`
6. 校验 Draft 属于 Task、用户、Project 和目标 Scenario
7. 校验 Draft revision 等于请求值
8. 校验正式 Scenario revision 等于 Draft `sourceRevision` 和请求值
9. 重新执行 Draft Validation
10. 读取 Command Plan 并执行风险 allowlist 检查
11. 写入正式 Scenario content
12. 正式 Scenario revision 加一
13. Draft 标记为 `APPLIED`
14. 持久化 Apply Result、修改前快照和审计事件
15. 提交事务

任何一步失败都不得部分写入。

### 7.3 冲突规则

正式 Scenario revision 已变化时：

- 不自动 rebase
- 不覆盖用户修改
- 不自动重复调用 Apply
- 返回结构化 `SOURCE_SCENARIO_CHANGED`
- 保留 Draft 供重新生成或人工审查

Draft revision 已变化时返回 `DRAFT_REVISION_CHANGED`。

### 7.4 应用结果

建议返回：

```json
{
  "applied": true,
  "scenarioId": "...",
  "previousRevision": 5,
  "currentRevision": 6,
  "draftId": "...",
  "appliedCommandTypes": ["AddAssertion"]
}
```

若降级，应返回可被 Agent 和 UI 识别的结果，而不是伪装成成功：

```json
{
  "applied": false,
  "reviewRequired": true,
  "reason": "COMMAND_NOT_AUTO_APPLY_ALLOWED",
  "draftId": "..."
}
```

## 8. Skill 与 Runtime 行为

### 8.1 System Context

每轮模型请求必须包含可信的平台权限上下文：

```text
Permission mode: REVIEW_REQUIRED
Formal writes allowed: false
Auto-apply command allowlist: []
```

该内容由平台生成，不能来自用户消息或 Test Knowledge。

### 8.2 Scenario Authoring Skill

更新 `scenario-authoring/SKILL.md`：

- `READ_ONLY`：只分析，不创建 Draft，不声称已修改
- `REVIEW_REQUIRED`：完成 Draft、Validation 和 Diff，等待用户应用
- `AUTO_APPLY`：仅在 Guard Tool 明确允许时调用 `applyScenarioDraft`
- 自动应用被拒绝时保留 Draft，并说明进入人工审查
- 不重复调用相同的正式写入 Tool

### 8.3 Skill 与 Tool 依赖

当前 Skill 的 `requiredTools` 不能导致 `READ_ONLY` 因缺少 Draft Tool 而加载失败。

实现者需要选择并明确一种方案：

1. 将只读分析和 Scenario 写作拆成两个 Skill；推荐
2. 或让 Skill Tool 依赖支持按权限模式声明

推荐拆分：

```text
scenario-analysis
scenario-authoring
```

`READ_ONLY` 只加载 `scenario-analysis`；另外两种模式可以同时加载两者。

## 9. 前端交互

### 9.1 新建 Chat

在 Agent Panel 新建 Chat 状态中增加 Mode 下拉框：

```text
Read only
Review required
Auto apply
```

推荐默认：

- Project：`Read only`
- Scenario：`Review required`
- Suite：`Read only`，直到 Suite Draft 能力实现

`Auto apply` 仅在服务端 capability 表示已启用，且当前 target 支持时显示。

### 9.2 Task 创建后

- Header 持续显示当前模式，并允许用户切换当前 Chat 的模式
- 运行中的 Task 禁用切换控件
- 切换成功后立即按新模式重新获取 Task、Tool 可用范围和 Draft 卡片
- 切回 `READ_ONLY` 后隐藏 Draft 和 Apply；已有 Draft 保留在审计记录中，不被删除
- Tooltip 使用用户语言解释副作用
- Chat History 中可显示当前模式图标或简短标签

### 9.3 不同模式的结果

`READ_ONLY`：

- 不显示 Draft 卡片
- 不显示 Apply 按钮

`REVIEW_REQUIRED`：

- 显示最新 Draft、Plan、Validation 和 Diff
- 显示 `Apply to editor`
- 用户仍需在 Editor 保存正式 Scenario

`AUTO_APPLY`：

- 自动应用成功后显示紧凑 `Applied automatically`
- 通知 Workbench 重新获取 Scenario
- 如果当前 Editor 有未保存修改，不得静默覆盖；应提示刷新冲突
- 自动降级后显示标准 Draft 卡片和 `Apply to editor`

### 9.4 选择 Auto Apply 的确认

用户第一次选择 `AUTO_APPLY` 时显示一次确认，内容应说明：

- 只会自动应用允许的低风险修改
- 所有修改仍经过 Draft、Validation 和 Diff
- 高风险修改会要求人工审查
- 切换会创建可审计的权限阶段，且不会改变历史 Tool 调用的权限解释

不要在每条消息前重复确认。

## 10. API 与 Capability

建议 Runtime/Agent Capability 增加：

```json
{
  "permissionModes": ["READ_ONLY", "REVIEW_REQUIRED"],
  "autoApplyEnabled": false,
  "autoApplyTargetTypes": ["SCENARIO"]
}
```

前端不能根据本地常量猜测是否支持 `AUTO_APPLY`。

Task 创建 API 示例：

```json
{
  "title": "Order lifecycle",
  "targetType": "SCENARIO",
  "targetId": "scenario-id",
  "runtimeType": "DEEPSEEK",
  "permissionMode": "REVIEW_REQUIRED",
  "context": {
    "included": [],
    "excluded": []
  }
}
```

服务端对非法组合返回明确错误，例如：

```text
AUTO_APPLY_NOT_ENABLED
PERMISSION_MODE_NOT_SUPPORTED_FOR_TARGET
```

## 11. 审计与可恢复性

至少记录：

```text
task.created                 permissionMode
task.permission_mode.changed fromPermissionMode, permissionMode, permissionModeRevision
draft.created                sourceRevision
command.plan.applied         commandTypes, risks
auto_apply.requested         draftId
auto_apply.downgraded        reason
auto_apply.completed         beforeRevision, afterRevision
decision.draft_applied_to_editor
scenario.saved               actor, source
```

正式自动应用必须保存修改前的 Scenario content 或可逆 revision snapshot。没有可靠恢复信息时，不应启用 `AUTO_APPLY`。

审计内容必须经过现有 redactor，不记录 Profile secret、PGP private key、Authorization header 或解密后的敏感值。

## 12. 实施阶段

### 阶段 A：权限类型与 Read Only

- 新增 `AgentPermissionMode`
- 删除 `DRAFT_ONLY`
- Task API 接收并返回强类型模式
- 实现 Tool 权限矩阵和 Tool 过滤
- 新增前端模式选择和固定状态展示
- 完成 `READ_ONLY` 与 `REVIEW_REQUIRED`

完成门槛：`READ_ONLY` 的模型请求中不存在任何 Draft 写 Tool。

### 阶段 B：风险模型与 Guard

- 新增 `AgentCommandRisk`
- 建立 Command 类型到风险的完整映射
- 新增 Auto Apply allowlist 配置
- 未知 Command 默认拒绝自动应用
- 实现自动降级结果

完成门槛：每个现有 Command 都有测试覆盖的风险分类。

### 阶段 C：正式发布链路

- 新增 `ScenarioDraftApplyService`
- 新增 `applyScenarioDraft` Tool
- 实现事务、revision、所有权、Validation 和风险检查
- 实现 Draft `APPLIED` 状态
- 增加 snapshot 和审计

完成门槛：并发修改正式 Scenario 时，Auto Apply 不覆盖任何用户修改。

### 阶段 D：Auto Apply UI 闭环

- 服务端 capability 暴露支持模式
- 前端 Auto Apply 确认与状态展示
- 自动成功后刷新 Scenario
- Editor dirty conflict 提示
- 降级后显示 Review Draft

完成门槛：用户能区分“已自动保存”“已应用到编辑器”“等待审查”三种结果。

## 13. 测试要求

### 13.1 后端单元测试

- 三种模式对四类 Tool 的授权矩阵
- Tool Registry 不暴露无权限 Tool
- 所有 Command 的风险分类
- 未知 Command 拒绝 Auto Apply
- `AUTO_APPLY` disabled 时拒绝创建对应 Task

### 13.2 后端集成测试

- `READ_ONLY` 可以读取但不能创建 Draft
- `REVIEW_REQUIRED` 可以创建 Draft但不能正式写入
- `AUTO_APPLY` 低风险计划成功写入并增加 revision
- 中高风险计划自动降级，不修改正式 Scenario
- Validation 失败时不写入
- source revision 冲突时不写入
- Draft 不属于 Task、用户或 Project 时拒绝
- 重复 Apply 不产生重复修改
- 事务失败时 Scenario、Draft 和审计状态一致

### 13.3 前端测试

- 不同 target type 的默认模式
- capability 未启用时不显示 Auto Apply
- Task 创建请求包含 permissionMode
- Task 切换模式会增加 permissionModeRevision 并写入审计事件
- 运行中的 Task 不允许切换模式
- Read Only 不显示 Draft 和 Apply
- Review Required 显示 Apply to editor
- Auto Apply 成功显示 Applied automatically
- Auto Apply 降级显示 Review Draft
- 当前 Editor dirty 时不静默刷新

### 13.4 端到端验收

验收场景一：

```text
READ_ONLY 分析当前 Scenario 的断言覆盖
```

预期：只有分析结果，没有 Draft 和写操作。

验收场景二：

```text
REVIEW_REQUIRED 为响应增加 orderId Extraction
```

预期：生成有效 Draft，用户应用到 Editor，正式 Scenario 不会被 Agent 自动保存。

验收场景三：

```text
AUTO_APPLY 增加允许的普通 Assertion
```

预期：通过 Validation 和 Guard 后自动保存，Scenario revision 增加，UI 刷新并显示审计结果。

验收场景四：

```text
AUTO_APPLY 更换 Operation 或删除 Step
```

预期：不自动保存，降级为可审查 Draft。

## 14. 禁止事项

后续实现模型不得：

- 为旧 `DRAFT_ONLY` 数据增加兼容逻辑
- 让模型直接提交正式 Scenario YAML
- 让前端承担正式写入的唯一权限检查
- 仅用 Prompt 约束 Auto Apply
- 在 Validation 之前写入正式 Scenario
- 自动 rebase 或覆盖 revision 冲突
- 将降级包装成自动应用成功
- 因实现 Auto Apply 而开放生产执行、Profile secret 或环境配置写入
- 将所有 Command 暂时标记为低风险以通过测试

## 15. 完成定义

只有同时满足以下条件，三模式改造才算完成：

- 代码和数据定义中不存在 `DRAFT_ONLY`
- 三种模式使用强类型枚举并持久化到 Agent Task
- Tool 暴露和 Tool 执行都经过权限检查
- `READ_ONLY` 不具备任何写能力
- `REVIEW_REQUIRED` 不能由 Agent 正式保存 Scenario
- `AUTO_APPLY` 只允许白名单低风险 Command
- 正式应用经过原子事务、Validation、revision 和所有权检查
- 风险操作可靠降级为人工审查
- UI 明确区分分析、等待审查和自动应用结果
- 所有正式写入具备审计和恢复信息
- 单元、集成、前端和端到端验收全部通过
