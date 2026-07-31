# 阶段二：Agent Backend 与模型运行时

状态：`In Progress`

## 1. 目标

建立平台拥有的 Agent Task、完整消息历史、事件、Artifact 和可替换 Runtime，并完成 Spring AI 与一个真实模型供应商的最小接入。

## 2. 范围

### 包含

- Spring Boot 和 Spring AI 兼容版本升级
- Agent 持久化模型
- `AgentRuntime` 抽象
- `FakeAgentRuntime`
- `SpringAiAgentRuntime`
- Chat REST API
- SSE 流式事件
- Cancel、超时和轮次预算
- 模型配置和错误处理

### 不包含

- 完整 Context Builder
- 业务 Skill
- 外部 MCP
- 正式 Agent Panel
- 自动执行测试

## 3. 技术基线

- [x] 将 Spring Boot 从 `3.3.2` 升级到 `4.1.0`
- [ ] 引入固定版本的 Spring AI `2.0.x` BOM
- [x] 不使用 Snapshot 版本
- [x] 完成所有模块编译和测试基线对比
- [x] 记录升级产生的兼容性改动

若兼容性验证失败，应暂停本阶段并更新 Runtime 技术选型决策。

### 3.1 Spring Boot 4 升级记录

升级日期：`2026-07-31`

- Java 基线保持为 `17`
- Spring Boot 升级到 `4.1.0`，作为 Spring AI `2.0.x` 的运行基础
- Web 模块改用 Boot 4 的 `spring-boot-starter-webmvc` 和 `spring-boot-starter-webmvc-test`
- Flyway 改用 `spring-boot-starter-flyway`，保留 PostgreSQL 数据库扩展
- 平台 JSON/YAML 处理迁移到 Jackson 3；Swagger Parser 的传递依赖仍使用其自带 Jackson 2，两者不共享业务对象映射器
- Maven Surefire 升级到 `3.5.6`

验证结果：

- 所有 Maven 模块可完成 clean package
- `mock-service` 5 个测试通过，`ApiCaseRunnerActionTest` 2 个测试通过
- Cucumber 5 个场景中仍有 2 个历史基线错误，均为测试数据缺少 `$.payload`，升级前后结果一致
- `platform-api` 已使用 PostgreSQL `16.14` 启动，Flyway 成功验证至 V17，核心查询接口返回 HTTP 200

## 4. 持久化模型

### AgentTask

建议字段：

```text
id
projectId
title
status
targetType
targetId
permissionMode
runtimeType
modelId
createdBy
iterationCount
tokenBudget
createdAt
updatedAt
completedAt
```

### AgentMessage

```text
id
taskId
sequence
role
contentJson
createdAt
```

### AgentEvent

```text
id
taskId
sequence
eventType
payloadJson
createdAt
```

### AgentArtifact

```text
id
taskId
artifactType
targetType
targetId
baseRevision
status
contentJson
createdAt
updatedAt
```

## 5. Runtime 接口

建议定义：

```text
AgentRuntime
  startTurn(request, eventSink)
  cancel(taskId)
  capabilities()
```

- [ ] Runtime 不直接访问 JPA Repository
- [ ] Runtime 只能通过受控 Context 和 Tool 工作
- [ ] Runtime 事件统一转换为平台 `AgentEvent`
- [ ] 供应商错误不能泄露 API Key 或完整 Prompt

### FakeAgentRuntime

- [ ] 根据固定输入返回可预测消息和 Artifact
- [ ] 支持模拟流式消息、Tool Activity、失败和取消
- [ ] 用于前端开发和自动化测试

### SpringAiAgentRuntime

- [ ] 使用 Spring AI `ChatClient`
- [ ] 支持结构化输出
- [ ] 支持流式文本
- [ ] 支持 Tool Calling 事件观察
- [ ] 支持配置 Base URL、API Key 和 Model
- [ ] API Key 不写入数据库和日志

## 6. API 草案

```text
POST /api/projects/{projectId}/agent/tasks
GET  /api/projects/{projectId}/agent/tasks
GET  /api/agent/tasks/{taskId}
POST /api/agent/tasks/{taskId}/messages
GET  /api/agent/tasks/{taskId}/events
POST /api/agent/tasks/{taskId}/cancel
GET  /api/agent/tasks/{taskId}/artifacts
```

`GET /events` 使用 `text/event-stream`。

## 7. Agent Event v1

- `task.started`
- `message.delta`
- `message.completed`
- `plan.updated`
- `skill.loaded`
- `tool.started`
- `tool.completed`
- `tool.failed`
- `artifact.created`
- `validation.completed`
- `approval.required`
- `task.completed`
- `task.failed`
- `task.cancelled`

事件必须带单调递增 `sequence`，支持前端断线重连后补读。

## 8. 运行治理

- [ ] 单轮最大模型调用次数
- [ ] Tool 调用次数预算
- [ ] Token 预算
- [ ] 总执行超时
- [ ] 用户取消
- [ ] 模型限流和暂时不可用的错误分类
- [ ] 禁止无限重试

## 9. 测试要求

- [ ] Task 状态机测试
- [ ] Message 和 Event 顺序测试
- [ ] SSE 连接、重连和结束测试
- [ ] Cancel 测试
- [ ] Runtime 超时和预算测试
- [ ] Fake Runtime 端到端 API 测试
- [ ] 真实模型连接只做可选集成测试，不进入默认 CI

## 10. 完成定义

- [ ] 没有模型 Key 时可通过 Fake Runtime 完成完整对话 API
- [ ] 有模型 Key 时可流式返回真实模型消息
- [ ] Task、Message、Event 和 Artifact 可恢复和审计
- [ ] 用户可以取消正在运行的 Turn
- [ ] Runtime 可以替换且不影响领域层和 API
- [ ] Spring Boot 升级回归通过
