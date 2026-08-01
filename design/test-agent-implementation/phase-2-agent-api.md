# Agent Backend API

本文档描述阶段二 Agent Backend 的可用接口。阶段二提供可恢复的会话与模型运行时，但不会读取完整业务 Context，也不会修改正式测试资产。

## 1. Runtime 配置

默认使用不产生费用的 Fake Runtime：

```text
AGENT_DEFAULT_RUNTIME=FAKE
```

启用 DeepSeek：

```text
DEEPSEEK_API_KEY=<secret>
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_TEMPERATURE=0.2
```

API Key 只通过环境变量进入运行时，不写入 Task、Message、Event、Artifact 或应用日志。

可查询 Runtime 状态：

```http
GET /api/agent/runtimes
```

## 2. 创建 Task

```http
POST /api/projects/{projectId}/agent/tasks
Content-Type: application/json

{
  "title": "Add order extraction",
  "targetType": "SCENARIO",
  "targetId": "scenario-id",
  "runtimeType": "FAKE",
  "modelId": "fake-agent-v1",
  "tokenBudget": 4096
}
```

`targetType` 当前支持 `SCENARIO` 和 `SUITE`，也可以同时省略 `targetType` 和 `targetId` 创建 Project 级对话。

Task 状态：

```text
READY -> RUNNING -> COMPLETED
                 -> FAILED
                 -> CANCELLED
```

一个已完成、失败或取消的 Task 可以继续提交下一轮消息；同一 Task 同时只允许一个运行中的 Turn。

## 3. 对话

```http
POST /api/agent/tasks/{taskId}/messages
Content-Type: application/json

{
  "content": "Add a correlation header to this scenario"
}
```

接口返回 HTTP `202 Accepted`。模型输出通过事件流返回，最终 Assistant Message 会在 Turn 完成后写入消息历史。

```http
GET /api/agent/tasks/{taskId}
GET /api/agent/tasks/{taskId}/messages
POST /api/agent/tasks/{taskId}/cancel
GET /api/agent/tasks/{taskId}/artifacts
DELETE /api/agent/tasks/{taskId}
```

运行中的 Task 不能删除；结束后删除 Task 会级联清理它的消息、事件和 Artifact。

## 4. 事件与 SSE

浏览器订阅：

```http
GET /api/agent/tasks/{taskId}/events
Accept: text/event-stream
Last-Event-ID: 12
```

也可以通过 JSON 补读：

```http
GET /api/agent/tasks/{taskId}/events?afterSequence=12
Accept: application/json
```

每个 Task 的 Event `sequence` 单调递增，SSE `id` 等于该序号。连接空闲时服务每 15 秒发送一次 keepalive comment。当前事件包括：

```text
task.started
message.delta
message.completed
artifact.created
task.completed
task.failed
task.cancelled
```

阶段三启用 Skill 与 Tool 后，将在同一个事件协议上增加 `skill.loaded`、`tool.started`、`tool.completed`、`tool.failed`、`validation.completed` 和 `approval.required`。

## 5. Fake Runtime

Fake Runtime 用于前端开发和自动化测试：

- 返回可预测的流式文本
- 每轮生成一个 `RUNTIME_TRACE` Artifact
- 输入包含 `[fail]` 时产生受控失败
- 支持取消、超时和 Token 预算验证

## 6. DeepSeek Runtime

DeepSeek Runtime 通过 Spring AI `2.0.0` 的 `ChatClient` 访问 OpenAI-compatible Chat Completions API。默认配置不会调用 DeepSeek，只有明确选择 `DEEPSEEK` Runtime 且 API Key 已配置时才会发送请求。

供应商错误会被归类为稳定的平台错误，例如：

```text
MODEL_AUTHENTICATION_FAILED
MODEL_BALANCE_EXHAUSTED
MODEL_RATE_LIMITED
MODEL_UNAVAILABLE
MODEL_PROVIDER_ERROR
```

错误响应不会包含 API Key 或完整 Prompt。

## 7. 阶段边界

当前 Agent 可以对话、流式输出并保存审计记录，但不能声称已经修改或执行测试资产。Scenario Context、Test Knowledge、Skill、Command Tool 和 Scenario Draft 修改将在阶段三接入。
