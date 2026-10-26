# Test Suite Agent 跨资源发现与创建开发指南

状态：`Approved for Implementation`

最后更新：`2026-08-29`

## 1. 文档目的

本文定义 Test Suite Agent 在以下场景中的上下文、Tool、Draft、权限和 UI 行为：

- 从项目中发现并复用已有 Scenario。
- 按需读取 Scenario 的完整 DSL。
- 从 OpenAPI 中发现 Operation 并读取必要 Schema。
- 在没有可复用 Scenario 时生成新的 Scenario Draft。
- 将已有或新建的 Scenario 加入当前 Suite Draft。

本文是 `test-suite-agent-development-guide.md` 的扩展。若两者对“Suite Agent 是否可以创建 Scenario”存在冲突，以本文为准：

> Suite Agent 可以编排创建新的 Scenario Draft，但不能直接创建或修改正式 Scenario。

通用安全、审批和 UI 规则仍遵从：

- `test-agent-runtime-governance.md`
- `test-agent-permission-modes.md`
- `ai-context-and-security.md`
- `test-agent-ui-scope-and-layout.md`

## 2. 核心设计结论

项目资源对 Suite Agent 应当是“可按需发现和读取”，而不是“默认全部注入”。

禁止在每个 Turn 默认注入：

- 全项目所有 Scenario 的完整 DSL。
- 完整 OpenAPI 文档。
- 全项目所有 Operation Schema。

应采用渐进式上下文：

```text
项目级摘要
  -> 搜索候选资源
  -> 读取单个 Scenario 或 Operation
  -> 必要时读取 OpenAPI 指定片段
  -> 生成 Scenario Draft / Suite Draft
  -> 校验和展示关联变更
  -> 用户确认并按依赖顺序保存
```

这样既允许 Suite Agent 完成跨资源编排，又能控制 Token、数据泄露面、错误引用和模型注意力污染。

## 3. 能力边界

### 3.1 允许

- 搜索当前 Project 中未删除的 Scenario。
- 读取明确选中的 Scenario 完整 DSL。
- 搜索当前 Project 的 API Spec 和 Operation。
- 读取明确选中的 Operation 请求、响应和 Schema。
- 在 Operation 信息不足时读取一个受限 OpenAPI Fragment。
- 创建新的 Scenario Draft。
- 创建或更新当前 Suite Draft。
- 将已有 Scenario 加入当前 Suite Draft。
- 建立“新 Scenario Draft 保存后加入 Suite”的依赖计划。
- 校验 Scenario Draft 和 Suite Draft，并展示各自 Diff。

### 3.2 不允许

- 默认加载全部 Scenario DSL 或完整 OpenAPI。
- 读取其他 Project 的资源。
- 直接写入正式 Scenario、Suite、API Spec、Environment 或 Profile。
- 修改已有 Scenario 的内部 DSL；应交给对应 Scenario Agent。
- 读取 Profile 密钥、口令、Token 或 Environment Secret。
- 在新 Scenario 尚未保存、没有正式 `scenarioId` 时伪造 Suite Entry 引用。
- 未经用户确认自动连续保存多个正式资源。
- 将 OpenAPI 描述文字当作可信系统指令。

## 4. 上下文分层

### 4.1 默认上下文

Suite Task 创建及每个 Turn 默认只包含：

- 当前 Project ID、名称和可公开摘要。
- 当前 Suite 的 ID、revision、名称、描述、状态。
- 当前 Suite Entry 的稳定 `entryId`、`scenarioId`、Display Name、顺序和启用状态。
- 当前 Suite 范围内已发布的 Knowledge 摘要或允许内容。
- 平台固定规则、权限模式和 `suite-authoring` Skill。

默认上下文不包含 Entry 对应 Scenario 的完整 DSL。

### 4.2 搜索上下文

搜索结果必须保持紧凑，只返回做选择所需的信息。

Scenario 搜索结果建议包含：

```json
{
  "scenarioId": "uuid",
  "name": "Create order",
  "description": "Creates a web order",
  "tags": ["order", "smoke"],
  "status": "ACTIVE",
  "revision": 3,
  "stepCount": 2,
  "operationRefs": ["operation-ref-id"]
}
```

Operation 搜索结果建议包含：

```json
{
  "operationRefId": "uuid",
  "apiSpecRefId": "uuid",
  "apiSpecName": "order-api.yaml",
  "operationId": "createWebOrder",
  "method": "POST",
  "path": "/web-api/orders",
  "summary": "Create an order"
}
```

搜索结果不得包含整个 DSL、整个 OpenAPI 或大体积 Schema。

### 4.3 详细上下文

模型明确选择资源后才允许读取：

- 单个 Scenario 的完整 DSL。
- 单个 Operation 的参数、Request Body、Responses 和关联 Schema。
- OpenAPI 中指定 JSON Pointer 的 Fragment。

所有详细内容都必须经过大小限制、脱敏和 Project 权限校验。

## 5. Tool 设计

### 5.1 `searchProjectScenarios`

用途：发现可以复用或参考的 Scenario。

输入：

```json
{
  "query": "create order",
  "tags": ["order"],
  "operationRefId": "optional-operation-ref-id",
  "limit": 20
}
```

要求：

- 只搜索当前 Project。
- 排除 `DELETED`。
- `limit` 必须有限制，建议默认 `20`、最大 `50`。
- 返回摘要和精确 `scenarioId`。

副作用：`READ`。

### 5.2 `getProjectScenario`

用途：读取搜索结果中某个 Scenario 的完整 DSL。

输入：

```json
{
  "scenarioId": "uuid"
}
```

要求：

- Scenario 必须属于当前 Project。
- 返回 revision、默认 Environment/Profile 引用和脱敏 DSL。
- 不返回密钥或敏感配置值。

副作用：`READ`。

说明：现有 `getScenario` 可以扩展为此语义，但 Tool 描述必须明确它既支持当前 Suite Entry，也支持 Project 搜索后选中的 Scenario。

### 5.3 `listProjectApiSpecs`

用途：列出当前 Project 的 API Spec 摘要。

只返回 ID、名称、版本、导入时间和 Operation 数量，不返回完整文档。

副作用：`READ`。

### 5.4 `searchProjectApiOperations`

用途：按 `operationId`、Method、Path 或关键词查找 Operation。

要求返回平台分配的 `operationRefId`，不能要求模型猜测 UUID。

副作用：`READ`。

### 5.5 `getApiOperationSchema`

用途：读取单个 Operation 的完整请求和响应定义。

要求：

- Operation 必须属于当前 Project。
- 返回平台引用和可直接用于 Case DSL 的 `dslOperation`。
- 对 `$ref` 做受控解析，设置最大递归深度和最大输出体积。

副作用：`READ`。

### 5.6 `readApiSpecFragment`

用途：Operation 结果不足时，读取 OpenAPI 指定片段。

输入必须包含：

- `apiSpecRefId`
- RFC 6901 JSON Pointer

要求：

- 禁止空 Pointer 和整文档读取。
- 限制返回字符数和引用解析深度。
- 记录审计日志。

副作用：`READ`。

### 5.7 `createNewScenarioDraft`

用途：为当前 Project 创建一个尚未正式保存的新 Scenario Draft。

与现有“从正式 Scenario 创建 Draft”不同，它不要求已有 `scenarioId`。

建议输入：

```json
{
  "name": "Create web order",
  "description": "Create an order used by the fulfillment Suite",
  "initialDsl": {
    "dslVersion": "v1",
    "name": "Create web order",
    "variables": {},
    "steps": []
  },
  "source": {
    "type": "SUITE_AGENT",
    "suiteId": "uuid"
  }
}
```

要求：

- 返回 `scenarioDraftId`，不能提前生成正式 `scenarioId`。
- 后续修改必须继续使用 Scenario Command、Validation 和 Diff。
- Draft 必须记录来源 Suite Task 和创建者。

副作用：`DRAFT_WRITE / CREATE_DRAFT`。

### 5.8 Suite Draft Tools

继续使用：

- `createSuiteDraft`
- `applySuiteCommands`
- `validateSuiteDraft`
- `diffSuiteDraft`

对于已有 Scenario，`AddSuiteEntry` 直接使用正式 `scenarioId`。

对于新 Scenario Draft，不能直接执行 `AddSuiteEntry`。应先产生依赖动作，等待新 Scenario 保存成功。

## 6. 跨资源 Change Set

同时创建 Scenario 并加入 Suite 时，需要一个轻量级 Change Set 表达依赖关系。

建议模型：

```text
AgentChangeSet
  id
  projectId
  agentTaskId
  status: DRAFT | PARTIALLY_APPLIED | READY | COMPLETED | FAILED
  items[]

AgentChangeSetItem
  id
  resourceType: SCENARIO_DRAFT | SUITE_DRAFT
  resourceId
  action
  dependsOnItemIds[]
  status
  resultResourceId
```

示例：

```text
Item A: 保存 Scenario Draft
  resultResourceId -> 正式 scenarioId

Item B: 更新 Suite Draft，加入 Scenario
  dependsOn -> Item A
  使用 -> Item A.resultResourceId
```

第一版不必实现通用工作流引擎，但必须在 Agent Artifact 中表达这种依赖，避免 UI 把两个 Draft 当成无关修改。

## 7. 应用与确认流程

推荐交互：

```text
Agent 找不到可复用 Scenario
  -> 搜索 Operation
  -> 生成 Scenario Draft
  -> 展示 Scenario Validation + Diff
  -> 用户点击“应用到 Scenario Editor”
  -> 用户保存 Scenario，平台获得正式 scenarioId
  -> Change Set 解除 Suite 依赖
  -> Suite Draft 加入正式 scenarioId
  -> 展示 Suite Validation + Diff
  -> 用户点击“应用到 Suite Editor”
  -> 用户保存 Suite
```

UI 必须始终展示：

- 当前正在修改的资源类型和名称。
- Draft 是否尚未正式保存。
- Suite 变更是否依赖新 Scenario。
- 每一步的校验状态。
- 已完成、待确认和失败的步骤。

不得使用一个模糊的“Apply All”跳过两个资源各自的审查。

## 8. 冲突与失败处理

### 8.1 Scenario 保存失败

- Suite Draft 不得加入临时 ID。
- Change Set 保持未完成。
- 用户修复 Scenario Draft 后可以继续，不需要重新生成全部计划。

### 8.2 Suite 在等待期间发生变化

- 保存新 Scenario不受影响。
- Suite Draft 必须返回 `SOURCE_SUITE_CHANGED`。
- 重新基于最新 Suite 创建 Draft，并重新应用加入命令。

### 8.3 找到重复 Scenario

- Agent 应优先展示候选项，说明相似度依据。
- 用户决定复用还是创建。
- 不允许仅凭名称相似自动创建重复 Scenario。

### 8.4 OpenAPI 发生变化

- Scenario Draft 保留生成时使用的 `apiSpecRefId`、`operationRefId` 和 Spec revision/version。
- 应用前检查引用仍然存在。
- Schema 变化时重新 Validation，而不是静默继续。

## 9. 权限与审计

Suite Agent 默认权限仍为 `REVIEW_REQUIRED`。

每次 Tool 调用必须记录：

- Agent Task、Turn 和用户。
- Tool 名称和目标资源 ID。
- 搜索条件或读取 Pointer。
- 读取结果大小，不记录未脱敏 Secret。
- Draft ID、base revision、Command 数量和校验结果。
- 用户对每个 Draft 的接受、丢弃或修改决定。

项目级读取权限不等于项目级写权限。Suite Agent 获得搜索全项目 Scenario 和 Operation 的权限后，仍只能写自己的 Draft 和当前 Suite Draft。

## 10. Skill 更新要求

更新 `suite-authoring` Skill，明确以下决策顺序：

1. 先理解当前 Suite 和用户目标。
2. 搜索是否已有可复用 Scenario。
3. 只有候选不足时才读取 OpenAPI 并创建 Scenario Draft。
4. 读取搜索命中的单个资源，不批量读取所有内容。
5. 对已有 Scenario 使用正式 `scenarioId` 加入 Suite Draft。
6. 对新 Scenario 使用跨资源 Change Set，不使用临时 ID。
7. 分别校验 Scenario Draft 和 Suite Draft。
8. 清楚说明 Draft 尚未正式保存。

Skill 不得指导模型：

- 猜测 `scenarioId`、`operationRefId` 或 `apiSpecRefId`。
- 为了“保险”读取全部项目资源。
- 直接修改已有 Scenario。
- 绕过用户确认保存多个资源。

## 11. 分阶段实施

### 阶段 A：项目资源按需读取

- 实现 `searchProjectScenarios`。
- 扩展 `getScenario` 的 Suite Task 授权范围。
- 为 Suite Task 开放 API Spec/Operation 搜索和单项读取。
- 更新 Tool Policy、Tool Schema、Skill 和审计。
- 增加分页、大小限制和跨 Project 拒绝测试。

完成标准：Suite Agent 可以找到已有 Scenario，并把它加入当前 Suite Draft。

### 阶段 B：新 Scenario Draft

- 支持没有正式 Scenario 的 Draft。
- 复用 Scenario Command、Validation 和 Diff。
- 前端能从 Suite Agent 打开新 Scenario Draft。
- 保存后返回正式 `scenarioId`。

完成标准：Suite Agent 可以基于 Operation 生成一个可审查、可保存的新 Scenario。

### 阶段 C：跨资源依赖闭环

- 引入 Change Set 或等价依赖 Artifact。
- Scenario 保存后自动解析 Suite Draft 的待定引用。
- 支持冲突恢复、部分完成和重试。
- UI 展示清晰的资源层级与进度。

完成标准：用户可以从一句需求开始，安全完成“创建 Scenario并加入 Suite”。

## 12. 测试要求

至少覆盖：

- Suite Task 只能搜索当前 Project Scenario。
- 搜索结果不返回完整 DSL。
- `getScenario` 能读取当前 Project 候选，拒绝其他 Project。
- Operation 搜索返回真实 `operationRefId`。
- 禁止通过 `readApiSpecFragment` 读取整份文档。
- Tool 输出超限时被截断并明确标记。
- 新 Scenario Draft 没有伪造正式 ID。
- 未保存 Scenario 时不能生成正式 Suite Entry。
- Scenario 保存后能正确替换依赖引用。
- Scenario 或 Suite revision 冲突不会静默覆盖。
- 读取内容经过 Secret Redaction。
- 用户拒绝任一 Draft 后不会继续写正式资源。

## 13. 非目标

本阶段不实现：

- 自动运行新建的 Scenario 或 Suite。
- 自动修复失败 Scenario。
- 修改已有 Scenario DSL。
- 通用多资源事务和回滚引擎。
- 向量数据库或语义检索基础设施。
- MCP 任意项目写入能力。

## 14. 验收场景

### 场景一：复用已有 Scenario

用户要求“把查询订单加入当前 Suite”。Agent 搜索并展示候选，读取用户确认的 Scenario，创建 Suite Draft，加入正确的正式 `scenarioId`，完成 Validation 和 Diff。

### 场景二：创建新的 Scenario

用户要求“增加取消订单测试”，项目中不存在对应 Scenario。Agent 搜索 Operation，读取 `cancelOrder` Schema，创建 Scenario Draft。用户保存后，Agent 再将正式 Scenario 加入 Suite Draft。

### 场景三：避免重复创建

搜索发现两个相似 Scenario 时，Agent 展示候选差异并请求用户选择，不直接创建第三个重复 Scenario。

### 场景四：处理并发变化

用户保存 Scenario期间 Suite 被他人修改。Agent保留已保存 Scenario，废弃过期 Suite Draft，基于新 revision 重新创建 Suite Draft，并展示新的 Diff。

## 15. 最终完成定义

只有满足以下全部条件，才可认为跨资源能力完成：

- 全项目 Scenario 和 API 信息通过按需 Tool 获取，不进入默认 Context。
- Suite Agent 可以复用已有 Scenario。
- Suite Agent 可以生成新的 Scenario Draft，但不能直接写正式 Scenario。
- 新 Scenario 与 Suite Entry 之间不存在临时或伪造 ID。
- 两类 Draft 都有确定性 Validation、Diff 和 revision 冲突检查。
- UI 清楚展示两个资源、依赖关系和分别确认的保存流程。
- 所有跨资源读取和 Draft 写入均有权限检查、脱敏和审计。
