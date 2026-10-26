# Test Agent 暂停与继续执行开发指南

状态：`Approved for Implementation`

最后更新：`2026-08-29`

## 1. 文档目的

本文指导 Test Agent 在达到内部预算、运行保护或可恢复故障时，从“直接失败”改造为“暂停并由用户决定是否继续”。

本文的核心产品原则是：

> 平台负责理解 Tool、Token、超时和循环检测；用户只需要决定当前请求是否值得继续。

本文是以下文档的补充：

- `test-agent-runtime-governance.md`
- `test-agent-architecture.md`
- `test-agent-permission-modes.md`
- `test-agent-ui-scope-and-layout.md`

若本文与旧实现中的失败行为冲突，以本文为准。

## 2. 核心设计结论

### 2.1 用户只看到一个概念

所有可以恢复、但需要用户授权继续消耗资源的情况，统一显示为：

> Agent 暂停了当前任务，以避免持续占用资源。是否继续完成这个请求？

用户操作只有：

- `继续`
- `停止`

UI 不直接展示以下内部术语：

- Tool 调用次数
- Token Budget
- No Progress Counter
- Repeated Tool Call
- Turn Budget
- Runtime Timeout

这些信息只进入日志、审计和可选的开发者诊断详情。

### 2.2 内部原因仍需精确区分

统一 UI 不等于统一后台策略。后台必须保留精确的 `pauseReason`，据此决定：

- 追加多少执行额度。
- 是否清理循环检测状态。
- 是否先自动重试。
- 是否允许再次继续。
- 何时达到绝对安全上限。

### 2.3 继续不是重复发送原始消息

达到限制后，当前模型请求通常已经结束，无法恢复同一个 HTTP 调用。因此“继续”必须实现为：

1. 保留原始用户消息、Task、Draft、Artifact 和审计事件。
2. 保存当前 Turn 的执行断点摘要。
3. 创建一个内部 continuation Turn。
4. 向模型注入可信的继续执行上下文。
5. 从已有 Draft 和正式资源状态继续，而不是重新执行整个原始请求。

禁止前端通过再次发送最后一条用户消息来模拟继续。这会导致重复 Draft、重复命令和副作用竞态。

## 3. 范围与非目标

### 3.1 本次必须覆盖

- Tool 总调用额度达到上限。
- 重复 Tool 调用保护触发。
- Tool 调用无进展保护触发。
- Task Turn 总数达到上限。
- 输出 Token 额度达到上限。
- 整轮运行超时。
- 无活动超时。
- 模型请求超时、限流、服务不可用在自动重试耗尽后的处理。
- 服务重启导致正在运行的 Task 中断。
- Chat 中统一的暂停确认交互。
- 继续和停止操作的审计、幂等与权限校验。

### 3.2 本次不做

- 恢复同一个已经断开的模型 HTTP Stream。
- 向用户开放 Tool/Token/Timeout 参数配置。
- 允许无限次继续。
- 把认证失败、权限拒绝或余额不足伪装成可继续问题。
- 修改 Scenario、Suite 和 Agent 的权限模式语义。
- 在继续时绕过 Draft 校验、revision 校验或正式资产审批。

## 4. 当前实现问题

当前代码中，下列路径会把 Task 直接置为 `FAILED`：

| 内部原因 | 当前代码 | 当前结果 |
| --- | --- | --- |
| Tool 调用总数 | `AgentToolCallBudget.begin` | 抛出不可重试异常 |
| 重复 Tool 调用 | `AgentToolCallBudget.begin` | 抛出不可重试异常 |
| Tool 无进展 | `AgentToolCallBudget.recordOutcome` | 抛出不可重试异常 |
| Turn 总数 | `AgentTurnCoordinator.submit` / `AgentTaskService.exhaustTurnBudget` | Task 直接失败 |
| 输出 Token | `AgentTurnCoordinator.finish` | 丢弃结果并失败 |
| Run Timeout | `AgentTurnCoordinator.timeout` | 取消 Runtime 并失败 |
| Inactivity Timeout | `AgentTurnCoordinator.timeout` | 取消 Runtime 并失败 |
| Provider 可恢复错误 | `DeepSeekAgentRuntime.classify` | Task 失败，只允许普通重试 |
| 服务重启 | `AgentTaskService.recoverInterruptedTasks` | Task 直接失败 |

前端目前只在 `task.failed` 且 `retryable=true` 时显示普通 `Retry`。该机制会重新发送上一条用户消息，不可用于 continuation。

另有一个现存命名不一致：前端识别 `TURN_TIMEOUT`，后台实际产生 `RUN_TIMEOUT` 和 `INACTIVITY_TIMEOUT`。改造时应删除这类由 UI 直接解释内部错误码的分支。

## 5. 错误与恢复分类

### 5.1 用户确认后继续

以下原因进入统一暂停状态：

| `pauseReason` | 内部含义 | 继续策略 |
| --- | --- | --- |
| `EXECUTION_ALLOWANCE_REACHED` | Tool 或 Turn 执行额度耗尽 | 创建 continuation Turn，并授予标准追加额度 |
| `POSSIBLE_LOOP` | 重复调用或无进展 | 清理本次循环计数，授予较小额度 |
| `OUTPUT_ALLOWANCE_REACHED` | 输出长度或 Token 额度不足 | 提高 continuation Turn 的输出额度 |
| `RUN_WINDOW_REACHED` | 整轮运行时间达到上限 | 使用执行断点启动 continuation Turn |
| `INACTIVITY_DETECTED` | 长时间无事件 | 结束旧 Runtime 后，从断点继续 |
| `RECOVERY_EXHAUSTED` | 自动重试已耗尽 | 用户确认后重新建立模型请求 |
| `SERVICE_INTERRUPTED` | 服务重启中断运行 | 服务恢复后允许继续 |

### 5.2 平台自动恢复，不打扰用户

以下问题应先在后台有限重试：

- `MODEL_RATE_LIMITED`
- `MODEL_TIMEOUT`
- `MODEL_UNAVAILABLE`
- 临时网络连接失败

建议默认策略：

- 最多自动重试 `2` 次。
- 使用指数退避并加入随机抖动。
- 自动重试期间保持 Task 为 `RUNNING`。
- Activity 只显示中性的“正在恢复连接”，不显示供应商错误码。
- 自动重试耗尽后进入 `WAITING_FOR_USER`，内部原因记录为 `RECOVERY_EXHAUSTED`。

### 5.3 Tool 内部自行恢复

以下情况不应暂停整个 Task：

- Tool 单次执行超时。
- Tool 输出过大。
- 搜索结果超过单页限制。
- Tool 参数校验失败。
- Draft 命令校验失败。

应把结构化错误返回给模型，让模型分页、缩小查询、修正参数或调整命令。只有连续失败触发 `POSSIBLE_LOOP` 时才暂停。

### 5.4 必须直接失败

以下问题无法通过增加执行额度解决：

- 模型认证失败。
- 模型访问被拒绝。
- 模型账户余额不足。
- Runtime 未配置。
- Tool 权限或跨 Project 安全检查失败。
- 服务端代码缺陷或不可识别的 Runtime Failure。

这些 Task 保持 `FAILED`，并给用户可操作但非技术化的提示，例如检查模型配置或联系管理员。

## 6. 状态机

Agent Task 增加状态：

```text
WAITING_FOR_USER
```

目标状态机：

```text
READY
  -> RUNNING
      -> COMPLETED
      -> WAITING_FOR_USER
          -> RUNNING       用户选择继续
          -> CANCELLED     用户选择停止
      -> FAILED            不可恢复错误
      -> CANCELLED         用户主动停止运行
```

规则：

- 达到可继续限制时禁止先写入 `FAILED`。
- `WAITING_FOR_USER` 状态下禁止发送普通用户消息，必须先处理当前暂停决定。
- `继续`必须以原子操作把 Task 从 `WAITING_FOR_USER` 改为 `RUNNING`。
- `停止`建议写入 `CANCELLED`，并记录 `USER_DECLINED_CONTINUATION`。
- 重复提交同一个决定必须幂等。
- 多个浏览器窗口同时操作时，只有第一个有效决定生效。

## 7. 持久化设计

建议为 `platform_agent_task` 增加：

```text
pause_reason             varchar(64) nullable
pause_context_json       text nullable
continuation_count       integer not null default 0
```

`pause_context_json` 只保存恢复所需的受控内部信息，例如：

```json
{
  "sourceTurnId": "uuid",
  "lastCompletedToolSequence": 38,
  "draftIds": ["uuid"],
  "artifactIds": ["uuid"],
  "completedToolSummary": [
    {"toolName": "searchOperations", "result": "completed"},
    {"toolName": "createNewScenarioDraft", "result": "completed"}
  ],
  "suggestedAllowance": {
    "toolCalls": 20,
    "outputTokens": 4096
  },
  "automaticRecoveryAttempts": 2
}
```

要求：

- 内容必须经过 `AgentContextRedactor`。
- 禁止保存 Tool 原始密钥、Profile Secret 或未脱敏响应。
- 只保存恢复所需摘要，不复制完整上下文。
- 用户不能通过 API 修改 `pauseReason` 或 `pauseContextJson`。
- continuation 成功启动后清空当前 pause 字段，但保留事件和审计记录。

数据库迁移使用新的 Flyway migration，不修改已有 migration。

## 8. 后端领域模型

### 8.1 专用暂停异常

不要继续使用只有 `retryable` 布尔值的 `AgentRuntimeException` 表达暂停。

新增专用类型，例如：

```java
public final class AgentPauseException extends RuntimeException {
    private final AgentPauseReason reason;
    private final AgentContinuationCheckpoint checkpoint;
}
```

`AgentPauseReason` 只能使用第 5.1 节定义的稳定枚举。Tool 和 Provider 原始错误码只放入内部 checkpoint 或日志。

### 8.2 每个 Turn 的执行额度

当前 `AgentToolCallBudget` 直接读取全局 `AgentProperties`。改造为从 `AgentTurnRequest` 中读取本 Turn 的 allowance：

```java
public record AgentExecutionAllowance(
        int maxToolCalls,
        int maxRepeatedToolCalls,
        int maxNoProgressToolCalls,
        long maxOutputTokens) {}
```

初始 Turn 使用平台默认额度；continuation Turn 根据 `pauseReason` 使用追加额度。

建议默认值：

```yaml
agent:
  max-tool-calls: 40
  continuation:
    standard-tool-call-grant: 20
    loop-tool-call-grant: 5
    output-token-grant: 4096
    provider-auto-retries: 2
```

这些参数仅供服务端配置，不暴露在普通 UI。

### 8.3 用户确认边界

平台不限制用户主动确认 continuation 的总次数。每个 continuation 都开启一个新的受限执行段，并重新应用 Tool 调用总数、重复调用、无进展、输出 token、运行时间和空闲时间等保护。

Agent 不能自行绕过暂停；只有 Task 所有者明确选择 `CONTINUE` 才能开始下一段执行。后台持续记录 continuation 次数、暂停原因和每次授予的额度，供审计和后续成本分析。

## 9. 执行断点与继续上下文

### 9.1 暂停时必须保存

- 原始 Task 和当前 Turn ID。
- 当前已生成的 Assistant partial text；允许不展示，但不能无声丢失。
- 已完成 Tool 的紧凑摘要。
- 已创建或更新的 Draft ID、revision 和 validation 状态。
- 已创建 Artifact ID。
- 最后一个失败或被拒绝的 Tool 名称。
- 当前权限模式 revision。

### 9.2 continuation 系统上下文

继续时向模型注入一个平台可信的内部上下文，不新增可见用户消息：

```text
Continue the user's interrupted request.
Do not repeat completed side effects.
Inspect and reuse the listed Drafts and current resource revisions.
Revalidate mutable resources before applying new commands.
If the original goal is already satisfied, summarize the result and stop.
```

同时附带结构化 checkpoint 摘要。

禁止把这一段作为普通 `USER` message 保存，否则用户会在 Chat 历史中看到平台内部控制语句。

### 9.3 幂等要求

continuation 必须优先复用当前 Task 已有的活动 Draft。所有有副作用 Tool 必须继续执行现有的：

- Task 所有权校验。
- Draft revision 校验。
- Source revision 校验。
- command fingerprint 幂等校验。
- permission mode 和 revision 校验。

不得为了“继续顺利”跳过 `SOURCE_SCENARIO_CHANGED`、`SOURCE_SUITE_CHANGED` 或 `DRAFT_REVISION_CHANGED`。

## 10. API 设计

新增统一决定接口：

```http
POST /api/agent/tasks/{taskId}/continuation-decision
```

请求：

```json
{
  "decision": "CONTINUE"
}
```

或：

```json
{
  "decision": "STOP"
}
```

响应：

- `CONTINUE`：返回已进入 `RUNNING` 的 Task，HTTP `202 Accepted`。
- `STOP`：返回 `CANCELLED` 的 Task，HTTP `200 OK`。
- Task 不在 `WAITING_FOR_USER`：返回 `409 AGENT_TASK_CONFLICT`。
- 非 Task 所有者：沿用现有访问控制。

前端不提交追加额度、内部原因或 checkpoint。

服务端事件建议：

```text
task.paused
continuation.accepted
continuation.started
continuation.declined
continuation.completed
```

`task.paused` 的 SSE 公共 payload 只包含：

```json
{
  "turnId": "uuid"
}
```

精确原因和计数进入 Audit，不进入普通 SSE payload。

## 11. 前端交互

### 11.1 Chat 内确认卡片

在对话末尾显示一个轻量内联卡片：

```text
Agent 暂停了当前任务，以避免持续占用资源。
是否继续完成这个请求？

[停止] [继续]
```

设计要求：

- 不使用技术错误样式，不显示红色失败图标。
- 不出现 Tool、Token、Budget、Timeout 等词。
- `继续`为主操作，`停止`为次操作。
- 点击后立即禁用两个按钮并显示提交状态。
- 继续成功后卡片收起为“已继续执行”。
- 停止后显示“已停止”。
- 历史暂停卡片不得在后续 Turn 中继续显示可点击按钮。

### 11.2 Composer 行为

- `WAITING_FOR_USER` 时禁用普通消息输入。
- Placeholder 显示“请先选择继续或停止”。
- continuation 启动后恢复正常运行状态。
- 不使用现有 `retry()`，也不重新发送最后一条用户消息。

### 11.3 错误展示

只有不可恢复错误使用现有错误行。

自动重试阶段可以显示中性 Activity：

```text
正在恢复连接...
```

不要为后台的每一次重试添加永久聊天消息。

## 12. 日志与审计

普通 UI 屏蔽复杂性，但后台必须完整记录：

- 原始内部错误码。
- 当前计数和上限。
- pauseReason。
- 自动重试次数和退避时间。
- continuation 次数。
- 每次追加的 allowance。
- 用户决定、actorId 和时间。
- continuation 使用的 source Turn 和 checkpoint。
- 是否复用了既有 Draft。

日志不得包含模型 API Key、Profile Secret、PGP 私钥或未脱敏业务数据。

建议审计事件：

```text
agent.paused
agent.continuation.accepted
agent.continuation.declined
agent.continuation.limit_reached
agent.recovery.attempted
agent.recovery.exhausted
```

## 13. 实施顺序

### 阶段一：状态与持久化

1. 增加 `WAITING_FOR_USER` Task 状态。
2. 增加 pause 字段和 Flyway migration。
3. 增加 `AgentPauseReason`、checkpoint 和 allowance 类型。
4. 在 `AgentTaskService` 实现 pause、continue、decline 原子状态转换。

完成标准：可以通过服务测试让 Task 在 `RUNNING -> WAITING_FOR_USER -> RUNNING/CANCELLED` 间正确转换。

### 阶段二：Runtime 和 Coordinator

1. 将可恢复限制映射为 `AgentPauseException`。
2. `AgentTurnCoordinator.finish` 对暂停调用 `pauseTurn`，不再调用 `failTurn`。
3. 保存 partial output 和 continuation checkpoint。
4. 实现 continuation Turn，禁止重复追加原始用户消息。
5. 增加 Provider 有限自动重试。
6. 服务重启恢复改为 `WAITING_FOR_USER`。

完成标准：触发每一种可恢复限制后都不会产生 `task.failed`。

### 阶段三：API 与 UI

1. 增加 continuation decision API。
2. 更新 TypeScript Task status。
3. 增加 Chat 内确认卡片。
4. 移除预算错误的普通 Retry 行为。
5. 修复 Timeout 错误码映射问题。

完成标准：用户只看到统一文案和“继续/停止”，看不到内部限制名称。

### 阶段四：测试与治理

1. 增加绝对 continuation 上限。
2. 补充审计事件和敏感信息测试。
3. 增加并发点击和幂等测试。
4. 增加 Draft 不重复创建的端到端测试。
5. 使用 Fake Runtime 构造稳定的暂停场景。

完成标准：连续恢复、浏览器刷新和服务重启均不会丢失决定状态或重复副作用。

## 14. 测试要求

### 14.1 后端单元测试

- Tool 总额度触发 `EXECUTION_ALLOWANCE_REACHED`。
- Repeated 和 No Progress 都映射为 `POSSIBLE_LOOP`。
- 可恢复异常不再调用 `failTurn`。
- continuation 使用专用 allowance。
- 达到绝对 continuation 上限后拒绝继续。
- continuation checkpoint 已脱敏。

### 14.2 后端集成测试

- `RUNNING -> WAITING_FOR_USER -> RUNNING -> COMPLETED`。
- `RUNNING -> WAITING_FOR_USER -> CANCELLED`。
- 重复提交 `CONTINUE` 只启动一个 Turn。
- 非所有者不能处理 continuation。
- continuation 不新增重复的可见 USER message。
- 已有 Scenario/Suite Draft 被复用而不是重复创建。
- Provider 自动重试耗尽后才进入暂停。
- 服务重启后原 RUNNING Task 进入可继续状态。

### 14.3 前端测试

- 暂停卡片不显示内部错误码。
- `WAITING_FOR_USER` 时 Composer 禁用。
- 点击继续后按钮不可重复点击。
- 刷新页面后仍显示当前有效暂停卡片。
- continuation 启动后旧卡片不可操作。
- 停止后显示稳定的结束状态。
- 普通不可恢复错误仍使用错误行。

### 14.4 端到端验收

使用 Fake Runtime 构造一个需要超过初始 Tool 额度的 Suite Agent 请求：

1. Agent 先创建或更新 Draft。
2. 达到额度后进入暂停，不显示失败。
3. 用户点击继续。
4. Agent 复用已有 Draft 完成剩余工作。
5. Chat 中只出现一次原始用户请求。
6. 最终 Diff 和 Validation 正确。
7. 审计中可以还原暂停原因、额度和用户决定。

## 15. 验收标准

全部满足后才算完成：

- 用户界面不暴露 Tool、Token、Budget 和循环计数。
- 所有可恢复限制都进入 `WAITING_FOR_USER`，不直接失败。
- 用户可以只通过“继续/停止”完成决策。
- 继续不会重复发送原始消息或重复创建 Draft。
- 后台仍能精确区分原因并应用不同额度策略。
- 短暂 Provider 故障优先自动恢复，不频繁打扰用户。
- 不可恢复错误仍明确失败，不提供无意义的继续操作。
- continuation 次数有绝对上限并完整审计。
- 刷新、并发操作和服务重启下状态保持一致。
- 后端测试、前端测试和生产构建全部通过。

## 16. 主要涉及文件

后端：

- `platform-api/src/main/java/com/example/poc/platform/agent/AgentProperties.java`
- `platform-api/src/main/java/com/example/poc/platform/agent/AgentTaskService.java`
- `platform-api/src/main/java/com/example/poc/platform/agent/AgentTurnCoordinator.java`
- `platform-api/src/main/java/com/example/poc/platform/agent/runtime/AgentRuntimeException.java`
- `platform-api/src/main/java/com/example/poc/platform/agent/runtime/AgentToolCallBudget.java`
- `platform-api/src/main/java/com/example/poc/platform/agent/runtime/DeepSeekAgentRuntime.java`
- `platform-api/src/main/java/com/example/poc/platform/web/AgentController.java`
- `platform-api/src/main/java/com/example/poc/platform/persistence/entity/AgentTaskEntity.java`
- `platform-api/src/main/resources/application.yml`
- `platform-api/src/main/resources/db/migration/`

前端：

- `platform-ui/src/components/AgentPanel.tsx`
- `platform-ui/src/components/AgentPanel.test.tsx`
- `platform-ui/src/api/platformApi.ts`
- `platform-ui/src/types/platform.ts`
- `platform-ui/src/i18n/resources.ts`
- `platform-ui/src/index.css`

Terra 实施时应先运行并记录现有测试基线，保留工作区中与本任务无关的修改，不得通过重置或回退覆盖已有开发成果。
