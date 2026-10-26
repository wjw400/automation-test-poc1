# Test Suite Agent 开发指南

状态：`Approved for Implementation`

最后更新：`2026-08-29`

## 1. 文档目的

本文定义 Test Suite Agent 的产品边界、领域模型、Tool、Skill、权限、前后端改造和验收标准，用于指导开发人员及代码生成模型实现 Suite Agent。

本文是 Suite Agent 实现的直接依据。若本文与旧文档中的 Suite Chat 描述冲突，以本文为准；通用的 Agent 安全、运行时、上下文和 UI 规则仍遵从：

- `test-agent-runtime-governance.md`
- `test-agent-permission-modes.md`
- `ai-context-and-security.md`
- `test-agent-ui-scope-and-layout.md`
- `test-suite-agent-cross-resource-development-guide.md`

关于搜索全项目 Scenario、按需读取 API Spec、创建新 Scenario Draft 并加入 Suite 的扩展能力，以 `test-suite-agent-cross-resource-development-guide.md` 为准。

## 2. 核心结论

Test Suite Agent 的职责是：

> 理解、检查和编排当前 Test Suite 对已有 Scenario 的使用方式，但不直接修改 Scenario 内部定义。

第一阶段必须完成以下闭环：

```text
用户描述 Suite 修改目标
  -> Agent 按需读取 Suite 和关联资源
  -> 创建 Suite Draft
  -> 应用受控 Suite Command
  -> 确定性 Validation 和 Diff
  -> 用户将 Draft 应用到 Suite Editor 工作副本
  -> 用户使用原生 Save 正式保存
```

Suite Agent 默认使用 `REVIEW_REQUIRED`。它不能直接更新正式 Suite，不能修改 Scenario，不能读取 Environment/Profile 密钥，也不能在第一阶段运行测试。

## 3. 当前代码基线与缺口

实现前必须认识当前状态，不能把 Scenario Agent 已有能力误认为 Suite Agent 已经拥有。

当前基线：

- Agent Task 已支持 `targetType = SUITE`。
- Suite Agent 已在当前 Suite Tab 内显示，并与 Project、Scenario 会话隔离。
- `AgentContextBuilder` 能把目标 Suite 的元数据及 Suite Entry 摘要放入上下文。
- Suite Task 当前默认并且只能使用 `READ_ONLY`。
- `AgentToolRegistry.toolsFor(...)` 当前只为 `SCENARIO` Task 注册 Tool，Suite Task 实际没有 Tool。
- `AgentSkillRegistry` 当前没有默认的 `suite-analysis` 或 `suite-authoring` Skill。
- 平台只有 `ScenarioDraft`、Scenario Command、Scenario Validation 和 Scenario Diff，没有对应的 Suite 领域内核。
- Suite 正式实体当前没有用于并发检查的 revision。
- 前端 Agent Artifact 和 Draft 应用流程主要围绕 `ScenarioDraftRecord` 构建。

因此，当前 Suite Chat 只能基于启动时已有上下文做普通只读回答，尚不能主动读取资源或产生 Suite 修改。

## 4. 能力边界

### 4.1 必须支持的能力

#### 解释当前 Suite

- 解释 Suite 的名称、描述、状态和测试目标。
- 总结 Suite Entry 的顺序、启用状态、Display Name 和运行上下文。
- 解释某个 Scenario 在当前 Suite 中的作用。
- 识别空 Suite、全部禁用、失效引用和明显重复覆盖。

#### 管理 Suite Draft

- 修改 Suite 名称、描述和状态。
- 添加已有 Scenario 作为新的 Suite Entry。
- 删除 Suite Entry。
- 调整 Suite Entry 顺序。
- 启用或禁用 Suite Entry。
- 修改 Display Name。
- 修改 Entry 的 Environment、Profile 和 Variables Override。
- 将 Variables Override 恢复为空对象。

#### 检查运行配置

- 检查 Environment/Profile 是否存在、启用且彼此兼容。
- 检查 Variables Override 是否为 JSON Object。
- 对比 Scenario 默认变量与 Variables Override。
- 提醒没有任何已启用 Entry 的 Suite 不能运行。

#### 分析 Suite Execution

第一阶段只需支持读取和解释，不支持由 Agent 发起执行：

- 查询 Suite 最近的执行记录。
- 读取一条 Suite Execution 及其 Scenario Runs。
- 汇总失败 Scenario、失败 Step、错误和验证证据。
- 判断问题更可能属于 Suite 编排、运行上下文还是 Scenario 定义。

### 4.2 后续能力

- API Operation 覆盖分析。
- 正常、异常、安全流程覆盖分类。
- 两次 Suite Execution 对比。
- 运行当前 Suite 或选定 Entry。
- Project Agent 编排多个 Suite Draft 和 Scenario Draft 的 Change Set。

这些能力不得阻塞第一阶段 Draft 闭环。

### 4.3 明确不允许

- Suite Agent 不得直接修改 Scenario DSL。
- Suite Agent 不得创建、删除或重命名正式 Scenario。
- Suite Agent 不得修改 API Spec、Environment 或 Profile。
- Suite Agent 不得读取任何私钥、口令、Token 或 Secret 值。
- Suite Agent 不得直接写入 `platform_suite` 或 `platform_suite_case`。
- Suite Agent 不得绕过 Suite Draft、Validation、Diff 和原生 Save 流程。
- Suite Agent 不得因为 Suite 引用了 Scenario，就获得该 Scenario 的写权限。

当用户要求修改 Scenario 时，Agent 应解释边界，并返回可执行的 UI 建议：

- `Open Scenario Agent`
- `Continue in Project Agent`

## 5. Suite Entry 语义

Suite 中被编排的对象是 Suite Entry，而不是 Scenario 本身。

一个 Suite Entry 当前包含：

```text
id
caseId
displayName
order
enabled
variableOverrides
environmentId
profileId
```

必须遵守：

- 同一个 Scenario 可以在同一 Suite 中出现多次。
- 不同 Entry 可以使用不同的 Display Name、变量和运行上下文。
- 所有修改命令必须通过稳定的 `entryId` 定位已有条目，不能通过 `caseId` 定位。
- Entry 顺序由 Draft 中的列表位置决定；保存前统一规范化为从 0 或 1 开始的连续顺序，具体起点沿用现有平台约定。
- `AddSuiteEntry` 必须一次携带新 Entry 所需的全部配置，避免同一 Command Plan 依赖服务端刚生成的 Entry ID。

## 6. 默认上下文与按需读取

### 6.1 默认上下文

每轮 Suite Agent 对话自动包含：

- 当前 Suite 的 id、name、description、status 和 revision。
- Suite Entry 列表及每项的结构化配置。
- 平台固定规则和 Suite Draft Schema。
- 匹配当前 Suite 的已发布 Test Knowledge。
- 权限模式和 Tool Policy 摘要。

默认上下文不应包含：

- 所有关联 Scenario 的完整 DSL。
- 整个项目的 API Spec。
- 全部历史 Execution。
- Environment/Profile Secret。

### 6.2 按需读取

Agent 只有在回答需要时才调用 Tool：

- 查看 Entry 对应 Scenario 的摘要或完整定义。
- 查看 Scenario 引用的 API Operation。
- 查看指定 Suite Execution。
- 搜索与当前 Suite、Scenario、Operation 相关的已发布知识。

Tool 返回必须经过 `AgentContextRedactor`，并受长度和条数预算限制。

## 7. 权限模式与应用流程

### 7.1 READ_ONLY

允许读取和分析，不允许创建 Draft 或调用写入类 Tool。

适用于：

- 解释 Suite。
- 分析覆盖范围。
- 分析 Suite Execution。
- 给出自然语言修改建议。

### 7.2 REVIEW_REQUIRED

Suite Agent 的默认模式。允许创建和修改 Suite Draft，但不能直接修改正式 Suite。

完整流程：

```text
Suite Draft
  -> Command Plan
  -> Validation
  -> Diff
  -> Apply to Editor
  -> User Save
```

`Apply to Editor` 只更新前端 Suite Editor 的本地工作副本，并将页面标记为 unsaved。正式保存仍由用户点击 Suite 页面原生 Save 完成。

### 7.3 AUTO_APPLY

本阶段不实现。未来即使启用，也只能自动应用到 Suite Editor 工作副本，不能绕过正式保存和审计规则直接更新数据库。

### 7.4 现有权限代码改造

- `AgentPermissionMode.defaultForTarget("SUITE")` 应返回 `REVIEW_REQUIRED`。
- `AgentTaskService.resolvePermissionMode(...)` 应允许 Suite Task 使用 `REVIEW_REQUIRED`。
- 前端 `permissionOptions(...)` 应允许 Scenario 和 Suite 选择 `READ_ONLY`、`REVIEW_REQUIRED`。
- 运行中的 Task 仍禁止切换模型和权限模式。

## 8. Suite Draft 领域模型

建议新增独立的 `suite` Agent 子包，不复用或泛化 Scenario Command 内核到难以理解的抽象层。

建议类：

```text
agent/suite/SuiteDraftService
agent/suite/SuiteCommand
agent/suite/SuiteCommandDecoder
agent/suite/SuiteCommandEngine
agent/suite/SuiteDraftValidator
agent/suite/SuiteDiffCalculator
```

### 8.1 SuiteDraftRecord

建议字段：

```text
id
projectId
suiteId
sourceRevision
revision
status                ACTIVE | APPLIED | REJECTED | STALE
content               规范化后的 Suite Draft JSON
validation
diff
createdBy
agentTaskId
createdAt
updatedAt
```

Draft content 对应：

```json
{
  "name": "Order regression",
  "description": "Order fulfillment regression suite",
  "status": "ACTIVE",
  "entries": [
    {
      "id": "stable-entry-id",
      "scenarioId": "scenario-id",
      "displayName": "Create order as operator",
      "enabled": true,
      "variableOverrides": {},
      "environmentId": "environment-id",
      "profileId": "profile-id"
    }
  ]
}
```

### 8.2 正式 Suite Revision

应为 `SuiteEntity` 增加单调递增的 `revision`，并在 `SuiteRecord` 中返回。

用途：

- 创建 Draft 时记录 `sourceRevision`。
- 应用 Draft 到编辑器前检测正式 Suite 是否已变化。
- 保存 Suite 时支持乐观并发检查。

不要仅用 `updatedAt` 代替 revision。

## 9. Suite Command

所有模型产生的写操作必须解码为白名单 Command，不允许模型直接提交完整数据库实体。

建议 Command：

| Command | 作用 | 关键字段 |
|---|---|---|
| `UpdateSuiteMetadata` | 修改名称、描述或状态 | `name?`, `description?`, `status?` |
| `AddSuiteEntry` | 添加一个已有 Scenario | `scenarioId`, `position?`, `displayName?`, `enabled?`, `variableOverrides?`, `environmentId?`, `profileId?` |
| `RemoveSuiteEntry` | 删除一个 Entry | `entryId` |
| `MoveSuiteEntry` | 调整顺序 | `entryId`, `targetIndex` |
| `SetSuiteEntryEnabled` | 启用或禁用 | `entryId`, `enabled` |
| `SetSuiteEntryDisplayName` | 修改显示名称 | `entryId`, `displayName` |
| `SetSuiteEntryRunContext` | 修改运行上下文 | `entryId`, `environmentId?`, `profileId?` |
| `SetSuiteEntryVariableOverrides` | 替换变量覆盖对象 | `entryId`, `variableOverrides` |
| `ResetSuiteEntryVariableOverrides` | 恢复默认变量 | `entryId` |

Command Plan 沿用 Scenario Agent 的治理形式：

```json
{
  "targetType": "SUITE_DRAFT",
  "targetId": "suite-draft-id",
  "baseRevision": 2,
  "commands": [],
  "assumptions": [],
  "warnings": [],
  "citations": []
}
```

要求：

- Decoder 拒绝未知字段。
- `targetType` 必须精确为 `SUITE_DRAFT`。
- `baseRevision` 必须等于当前 Draft revision。
- 一个 Command Plan 事务性执行；任一命令失败时不能部分应用。
- 模型不能设置 Draft revision、projectId、suiteId、createdBy 或 agentTaskId。

## 10. 确定性 Validation

Validation 必须由平台代码完成，不能依赖模型自我检查。

### 10.1 Error

- Suite 名称为空或超过平台长度限制。
- Suite status 不在平台允许值内。
- Entry ID 缺失或重复。
- Entry 引用的 Scenario 不存在或属于其他 Project。
- Environment/Profile 不存在或属于其他 Project。
- Profile 未启用。
- Profile 不支持 Entry 选择的 Environment。
- Variables Override 不是 JSON Object。
- Command 引用了不存在的 Entry。
- Draft 与正式 Suite revision 冲突。

### 10.2 Warning

- Suite 没有 Entry。
- Suite 没有任何已启用 Entry。
- 同一个 Scenario 被重复引用且配置完全相同。
- Entry 没有 Display Name。
- Entry 没有明确 Environment/Profile，将回退到 Scenario 默认配置。
- Override 中存在 Scenario 默认变量未声明的字段。
- 关联 Scenario 当前不可执行或 DSL 存在验证错误。

重复引用同一个 Scenario 不是 Error，因为不同 Entry 可以承担不同数据和身份组合。

## 11. Agent Tool 契约

### 11.1 只读 Tool

| Tool | 用途 | Scope 约束 |
|---|---|---|
| `getSuite` | 读取目标 Suite | 只能读取 Task 目标 Suite |
| `getSuiteScenario` | 读取 Entry 引用的 Scenario | 必须被目标 Suite 引用 |
| `getSuiteCoverage` | 汇总关联 Operation | 只基于目标 Suite Entry |
| `listSuiteExecutions` | 查询 Suite 执行摘要 | 只能查询目标 Suite |
| `getSuiteExecution` | 读取执行证据 | Execution 必须属于目标 Suite |
| `getApiOperationSchema` | 按需读取 Operation | 必须被可读 Scenario 引用 |
| `searchPublishedKnowledge` | 搜索知识 | 仅当前 Suite 相关和 Project Scope 的已发布知识 |

### 11.2 Draft Tool

| Tool | Category | 用途 |
|---|---|---|
| `getSupportedSuiteCommands` | `READ` | 返回可用 Command Schema |
| `createSuiteDraft` | `DRAFT_WRITE` | 从目标 Suite 创建或重新建立 Draft |
| `applySuiteCommands` | `DRAFT_WRITE` | 事务性应用 Command Plan |
| `validateSuiteDraft` | `VALIDATE` | 执行确定性校验 |
| `diffSuiteDraft` | `READ` | 生成结构化 Diff |

### 11.3 暂不提供

- `runSuite`
- `saveSuite`
- `deleteSuite`
- `createScenario`
- 任意 SQL、Shell、文件或网络访问 Tool

## 12. Tool Policy

后端必须在每次 Tool 调用时重新授权，不能依赖前端按钮或模型提示词。

建议新增或扩展以下策略方法：

```text
requireTargetSuite(task, suiteId)
requireReadableSuiteScenario(task, scenarioId)
requireTargetSuiteDraft(task, draft)
requireSuiteExecution(task, executionId)
requireSuiteEnvironment(task, environmentId)
requireSuiteProfile(task, profileId, environmentId)
```

策略要求：

- Task `targetType` 必须为 `SUITE`。
- 所有资源必须属于 Task projectId。
- Draft 必须属于当前 Task 和当前用户。
- Draft 的 source Suite 必须是 Task targetId。
- `READ_ONLY` 只允许 `READ`。
- `REVIEW_REQUIRED` 允许 `READ`、`DRAFT_WRITE`、`VALIDATE`。
- Secret 字段永不进入 Tool output、Context、Artifact 或日志。

## 13. Skill 设计

建议新增三个 Skill。

### 13.1 suite-analysis

默认用于 `SUITE + READ_ONLY`。

职责：

- 解释 Suite 结构和运行配置。
- 按需读取 Scenario 和 Execution。
- 分析覆盖、重复和失败原因。
- 不创建 Draft，不声称已修改资源。

### 13.2 suite-authoring

默认用于 `SUITE + REVIEW_REQUIRED`。

职责：

- 先读取目标 Suite。
- 修改前创建 Suite Draft。
- 使用稳定 Entry ID。
- 应用 Command 后必须调用 Validation 和 Diff。
- 明确区分 Suite 修改和 Scenario 修改。

### 13.3 suite-execution-analysis

仅在用户明确询问运行、失败、错误、请求、响应或验证证据时加载。

职责：

- 读取最少必要的 Execution。
- 优先使用结构化 evidence。
- 不把失败自动归因于 Scenario。
- 不发起新执行。

`AgentSkillRegistry.select(...)` 必须根据 Task targetType 和 permissionMode 选择默认 Skill，不能让 Suite Task 加载依赖 Scenario Draft Tool 的 Skill。

## 14. Artifact 与审计

新增 Artifact Type：

```text
SUITE_DRAFT
SUITE_COMMAND_PLAN
SUITE_DIFF
SUITE_VALIDATION
SUITE_EXECUTION_ANALYSIS
```

Artifact 必须记录：

- target Suite/Draft。
- sourceRevision 和 Draft revision。
- Command Plan。
- Validation 结果。
- Diff。
- Knowledge citation。
- 创建 Artifact 的 Agent Task 和 Turn。

继续沿用现有事件流：

```text
tool.started
tool.completed
tool.failed
artifact.created
task.completed
task.failed
```

应用到编辑器和正式保存分别记录决策：

```text
SUITE_DRAFT_APPLIED_TO_EDITOR
SUITE_SAVED
SUITE_DRAFT_REJECTED
```

## 15. 前端实现

### 15.1 Agent Panel

- Suite Agent 保持在当前 Suite Tab 的内部分栏中。
- Header 显示当前模型、权限模式、新对话、历史和关闭。
- Context 明确显示 `Suite · <suite name>`。
- `READ_ONLY` 会话不显示可应用 Draft 的操作。

### 15.2 Draft 展示

Agent 消息区域应支持：

- Suite Draft 摘要。
- Command Plan。
- Validation Error/Warning。
- 结构化 Diff。
- `Apply to Editor` 和 `Reject`。

Diff 至少能表达：

- 元数据修改。
- Entry 添加和删除。
- Entry 移动。
- enabled、Display Name、Environment、Profile 和 Variables Override 修改。

### 15.3 应用到 Suite Editor

- 在 Workbench 中维护目标 Suite 的 pending Agent Draft。
- `SuitesPage` 接收并解析 `SuiteDraftRecord`。
- 应用 Draft 后更新现有 Suite Editor draft，不创建第二套编辑器。
- 页面立即标记为 unsaved。
- Agent Draft 的 sourceRevision 与当前 Suite 不一致时阻止应用并显示冲突。
- 用户可以继续手工修改，再使用原生 Save 保存。
- 保存成功后记录 `SUITE_SAVED`，并清理 pending Draft 状态。

## 16. 后端实施顺序

### 阶段 A：Suite Draft 内核

1. 为 Suite 增加 revision。
2. 新增 Suite Draft persistence 和 repository。
3. 实现 Suite Draft codec、Command、Decoder、Engine、Validator 和 Diff。
4. 添加领域单元测试，不接入模型。

完成门槛：所有 Command 可以通过纯 Java 测试确定性执行，错误路径无部分写入。

### 阶段 B：Tool、Policy 与 Skill

1. 为 Suite Task 注册只读和 Draft Tool。
2. 扩展 `AgentToolPolicy`。
3. 新增 Suite Skills。
4. 修正 Skill 与 Tool 的 Scope 选择。
5. 增加 Tool 集成测试和越权测试。

完成门槛：Suite Task 能端到端产生合法 Suite Draft；Scenario/Project Task 不能调用 Suite Draft Tool。

### 阶段 C：前端闭环

1. 增加 Suite Draft/Validation/Diff Type。
2. 扩展 Agent Artifact 展示。
3. 将 Draft 应用到现有 Suite Editor。
4. 处理 dirty、save、reject 和 source revision conflict。
5. 增加组件测试和浏览器验收。

完成门槛：用户可以通过 Chat 调整 Suite Entry 顺序和变量，检查 Diff，应用到编辑器并手工保存。

### 阶段 D：执行分析

1. 增加 Suite Execution 只读 Tool。
2. 增加 execution-analysis Skill。
3. 在 UI 中打开关联 Execution Evidence。

完成门槛：Agent 能准确定位失败 Entry 和 Step，并引用对应结构化证据。

## 17. 测试要求

### 17.1 领域测试

- 每个 Command 的成功和失败测试。
- 同一 Scenario 多次引用测试。
- Entry ID 重复和不存在测试。
- 顺序移动边界测试。
- Command Plan 事务回滚测试。
- sourceRevision 和 Draft revision 冲突测试。
- Environment/Profile 兼容性测试。
- Variables Override Object 校验测试。

### 17.2 安全测试

- Suite Task 不能修改非目标 Suite。
- Suite Task 不能修改 Scenario Draft。
- Scenario Task 不能调用 Suite Draft Tool。
- 跨 Project Suite、Scenario、Execution、Environment/Profile 全部拒绝。
- Tool output、Artifact、Event 和日志不包含 Secret。
- READ_ONLY 调用 Draft Tool 被拒绝。

### 17.3 集成测试

- 创建 Suite Task -> 创建 Draft -> 应用命令 -> Validation -> Diff。
- Agent Runtime 能发现和调用 Suite Tool。
- Skill requiredTools 与实际 Tool 集合一致。
- Draft Artifact 能通过 API 查询和 SSE 事件展示。
- apply-to-editor 不直接更新数据库。

### 17.4 前端测试

- Suite Agent 权限模式切换。
- Suite Draft Artifact 展示。
- Apply 后 Suite Editor 正确更新并标记 dirty。
- Reject 后编辑器不变化。
- revision conflict 显示明确错误。
- 关闭 Agent 不丢失未保存的 Suite Editor 工作副本。

## 18. 首批验收场景

### 场景一：调整执行顺序

用户：

> 把“查询订单”移动到“创建订单”之后。

期望：

- Agent 读取 Suite。
- 创建 Suite Draft。
- 使用 Entry ID 执行 `MoveSuiteEntry`。
- Validation 通过。
- Diff 明确显示顺序变化。
- Apply 后编辑器顺序变化但尚未正式保存。

### 场景二：为同一 Scenario 增加不同身份组合

用户：

> 再添加一次“查询订单”，使用只读用户身份，并显示为“只读用户查询订单”。

期望：

- 允许同一 Scenario 重复出现。
- 新 Entry 使用指定 Display Name 和 Profile。
- Profile 与 Environment 不兼容时 Validation 报错。

### 场景三：覆盖变量

用户：

> 将第二个场景的 orderType 覆盖为 EXPRESS。

期望：

- Agent 通过 Entry ID 定位第二个条目。
- `SetSuiteEntryVariableOverrides` 保留其他已有 Override。
- Diff 只显示相关变量变化。

### 场景四：错误的职责请求

用户：

> 给“创建订单”场景增加响应断言。

期望：

- Suite Agent 不创建 Scenario Draft。
- 明确说明该修改属于 Scenario。
- UI 提供 `Open Scenario Agent` 或 `Continue in Project Agent`。

### 场景五：分析失败执行

用户：

> 为什么这个套件上一次执行失败？

期望：

- Agent 查询最近的 Suite Execution。
- 明确失败 Entry、Scenario、Step 和 Validation。
- 不读取无关历史记录。
- 不自动修改 Suite 或 Scenario。

## 19. 完成定义

Suite Agent 第一阶段完成必须同时满足：

- Suite Task 默认支持 `REVIEW_REQUIRED`。
- Suite Task 有独立的 Analysis 和 Authoring Skill。
- Suite Tool 不再为空，并严格受 Scope Policy 限制。
- Suite Draft、Command、Validation 和 Diff 是独立的确定性领域能力。
- Suite Agent 只能修改当前 Suite Draft，不能修改 Scenario。
- Draft 可以应用到现有 Suite Editor，并由用户手动正式保存。
- 同一 Scenario 多次引用得到正确支持。
- 所有跨 Project、跨 Scope 和 Secret 访问测试通过。
- DeepSeek 和 Fake Runtime 都能完成至少一个端到端验收场景。
- 前后端自动化测试和浏览器验收通过。

## 20. 禁止的实现捷径

- 不要让模型直接生成完整 `SaveSuiteInput` 并提交正式保存 API。
- 不要复用 `caseId` 作为 Suite Entry 唯一标识。
- 不要通过 Prompt 声明权限而省略后端 Tool Policy。
- 不要把所有 Scenario DSL 和 Execution 默认塞入 Prompt。
- 不要把 Suite 修改伪装成 Scenario Command。
- 不要在 Agent Panel 中实现第二套 Suite 编辑器。
- 不要为了覆盖分析提前引入向量数据库、多 Agent 或 MCP。
- 不要在第一阶段加入自动运行或生产环境执行。
