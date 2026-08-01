# Test Agent Runtime 技术选型决策

## 1. 决策状态

- 状态：`Accepted`
- 决策日期：`2026-07-31`
- 技术基线：Spring Boot `4.1.0`、Java `17`、计划接入 Spring AI `2.0.x`
- 适用阶段：Test Agent POC 与第一版受限 Agent
- 相关文档：
  - [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)
  - [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)
  - [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)
  - [Test Knowledge](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-knowledge.md)

## 2. 决策结论

Test Agent 第一版采用以下技术路线：

> 使用 Spring AI 作为模型调用、流式响应、Tool Calling 和 MCP 集成基础；由测试平台自行实现领域编排、Agent Task、Skill Registry、Draft、Diff、确定性校验、权限和审计。

同时保留 `AgentRuntime` 抽象，使 OpenCode 或其他 Agent Runtime 可以在未来作为替代实现接入。

本阶段不 Fork OpenCode，不将 OpenCode Session 作为平台业务 Session，也不直接使用裸 HTTP 调用把业务代码绑定到某一家模型供应商。

## 3. 决策背景

平台希望提供类似 Codex 的 Agent Chat：用户可以通过多轮自然语言对话生成或修改 Test Scenario、Test Suite，并看到计划、Skill 加载、Tool Activity、Draft、Diff、Validation 和审批过程。

Agent 需要支持：

- 多轮 Session 和流式事件
- 按需加载测试领域 Skill
- 调用平台原生 Tool 或外部 MCP Tool
- 读取 OpenAPI、Scenario、Suite、Test Knowledge 和脱敏 Evidence
- 使用语义 Command 修改 Draft
- 通过确定性校验后将结果应用到现有编辑器

平台是一个由 Spring Boot、PostgreSQL 和 React 构成的多 Workspace Web 应用。核心资产存储在数据库中，不是以本地文件、Git 仓库或 Shell 为中心。

## 4. 评估标准

候选方案按以下标准评估：

1. 是否适合多用户、Workspace 隔离和平台审计
2. 是否能安全操作 Scenario、Suite 等领域对象
3. 是否支持 Codex 式流式交互和 Tool Activity
4. 是否支持 Skill 和 MCP 扩展
5. 是否容易与现有 Java/Spring 技术栈集成
6. 是否能避免模型供应商锁定
7. POC 开发成本和长期维护成本是否合理

## 5. 候选方案与评价

### 5.1 Spring AI 与平台自有编排

优点：

- 与现有 Spring Boot、JPA、事务、配置和安全体系直接集成
- 支持多模型、结构化输出、流式响应、Tool Calling、Memory 和 MCP
- Java Tool 可以直接调用现有领域 Service
- 平台可以完整控制每次 Tool 调用、权限判断和审计
- 不需要增加独立的 TypeScript Agent 服务

代价：

- 平台需要自行实现 Agent Task、完整消息历史、Artifact 和事件协议
- Spring AI 没有 OpenCode 风格的一等 Skill 生命周期，需要实现轻量 `SkillRegistry`
- Spring AI 版本升级仍需跟随其正式兼容矩阵进行回归验证

结论：最符合当前平台架构，作为首选方案。

### 5.2 OpenCode Headless Runtime

优点：

- 已具备 Session、Agent Loop、Skill、MCP、上下文管理和流式事件
- 适合快速构建接近 Codex 的 Agent 交互原型
- 可以通过 `opencode serve` 以独立 Runtime 方式接入

代价：

- OpenCode 的主要模型是代码、文件、项目目录和终端，与平台数据库领域对象存在差异
- 需要额外部署 TypeScript Sidecar，并转换认证、Session 和事件协议
- 平台仍需实现 Workspace 权限、Draft、语义 Command、DSL 校验、Diff 和审计
- OpenCode Session 不能替代平台的 `AgentTask`
- 内置文件、Shell、网络等能力需要默认禁用

结论：适合作为未来可选 `AgentRuntime`，不作为第一版核心。

### 5.3 Fork OpenCode 二次开发

优点：

- 可以深度修改 OpenCode 的 Agent Runtime 和交互行为

代价：

- 需要长期同步快速变化的上游代码
- 测试平台领域改动会与通用 Coding Agent 内核耦合
- 容易形成难以升级和维护的私有分支

结论：不采用。

### 5.4 裸模型 API

优点：

- 初始依赖少，调用链最直接

代价：

- Tool Loop、流式处理、重试、模型适配、MCP 和观测都需要自行实现
- 业务代码容易与模型供应商 API 耦合

结论：只适合验证模型连通性，不作为正式架构。

### 5.5 其他 Agent Framework

LangChain4j、OpenAI Agents SDK 或其他运行时具备部分相同能力，但没有在当前 Java/Spring、多 Workspace 测试平台场景中形成明显优势。它们可以作为未来 `AgentRuntime` 的候选实现，不进入第一版主路径。

## 6. 目标架构

```text
Test Agent Panel
        |
Agent API + SSE
        |
TestAgentOrchestrator
  |-- AgentTask / Message / Event / Artifact
  |-- ContextBuilder
  |-- SkillRegistry
  |-- ToolRegistry + ToolPolicy
  |-- ScenarioCommandService
  |-- Draft / Diff / DSL Validator
  `-- AgentRuntime
        |-- SpringAiAgentRuntime    第一版
        `-- OpenCodeAgentRuntime    可选
                |
        Model Provider / MCP Server
```

`AgentRuntime` 只负责模型交互和 Tool Calling 过程，不拥有平台业务事实。

平台始终拥有：

- 用户、Workspace 和权限
- 完整 Chat History 和审计记录
- Agent Task 生命周期
- Context Snapshot 和知识引用
- Scenario/Suite Draft
- Command Plan、Diff 和 Validation
- Apply、Save 和执行审批

## 7. Skill、Knowledge 与 Tool 的边界

### Skill

Skill 是经过平台团队审查的可信工作流程。第一版使用版本化 `SKILL.md` 文件：

```text
agent-skills/
  scenario-authoring/SKILL.md
  secure-payload/SKILL.md
  failure-analysis/SKILL.md
```

目录和基础格式尽量兼容 OpenCode Skill，便于未来复用。Spring AI 不负责 Skill 生命周期，由平台的 `SkillRegistry` 按需发现和加载。

### Test Knowledge

Test Knowledge 是用户维护的项目业务事实，不是可信系统指令。它必须按 Workspace 和关联资产筛选，并视为可能包含提示注入的非可信上下文。

Test Knowledge 不能扩大 Agent 权限，不能新增 Tool，也不能覆盖系统安全规则。

### Tool 与 MCP

第一版平台内部能力优先实现为 Java Tool：

- `getScenario`
- `getApiOperationSchema`
- `listProjectApiSpecs`
- `searchProjectApiOperations`
- `readApiSpecFragment`
- `searchPublishedKnowledge`
- `createScenarioDraft`
- `applyScenarioCommands`
- `validateScenarioDraft`
- `getScenarioDraftDiff`

当能力需要被 OpenCode、Codex 或其他 Agent 复用时，再将同一领域 Service 暴露为 MCP Server。

外部系统能力可以通过 MCP Client 接入，但必须经过服务器白名单、用户权限和副作用等级检查。

## 8. 第一版能力边界

V1 只允许：

- `READ`
- `DRAFT_WRITE`
- `VALIDATE`

V1 禁止：

- 自动保存正式 Scenario 或 Suite
- 删除资产
- 修改 Environment、Profile 或密钥
- 读取密码、Token、私钥和认证 Header
- 自动运行测试
- 访问生产环境
- 任意文件、Shell 或网络操作

Agent 生成的 Draft 只能应用到现有编辑器，并将对应 Tab 标记为未保存。用户仍需要使用平台原有 Save 流程完成正式保存。

## 9. 实施顺序

1. 将 Spring Boot 升级到与选定 Spring AI 版本兼容的版本（已完成：`4.1.0`）
2. 实现与模型供应商无关的 `AgentRuntime`
3. 实现后端 `ScenarioCommandService`、Draft、Diff 和确定性校验
4. 建立 `AgentTask`、`AgentMessage`、`AgentEvent` 和 `AgentArtifact`
5. 实现 `SkillRegistry` 和首批测试领域 Skill
6. 实现受控 Java Tool 和 `ToolPolicy`
7. 接入 `SpringAiAgentRuntime`
8. 提供 Agent REST API、SSE 和取消能力
9. 实现 Codex 风格的 Test Agent Panel
10. 完成“自然语言修改当前 Scenario Draft”的端到端闭环
11. 稳定后评估 Platform MCP 和 `OpenCodeAgentRuntime`

## 10. 第一版验收场景

用户打开一个 Scenario，对 Agent 输入：

> 给当前请求增加 `X-Correlation-Id`，并从响应中提取订单号。

系统应完成：

1. 识别当前 Scenario 和 Operation
2. 按需加载 `scenario-authoring` Skill
3. 读取匹配的已发布 Test Knowledge
4. 生成白名单语义 Command
5. 应用到 Scenario Draft
6. 完成确定性校验
7. 展示知识引用、Tool Activity、Diff 和 Validation
8. 用户将 Draft 应用到现有 Scenario Editor
9. 未经用户 Save 前，不修改正式 Scenario

## 11. 重新评估条件

满足以下任一条件时，应重新比较 Spring AI 与 OpenCode：

- Agent 需要长时间自主运行、复杂上下文压缩或多 Agent 协作
- Skill 和 MCP 生命周期的维护成本明显超过预期
- OpenCode Headless API 能稳定满足多用户隔离和平台事件需求
- Agent 从领域工作流转向以文件、代码、Git 和终端为中心
- Spring AI 的 Tool Loop、流式事件或模型兼容性不能满足需求

重新评估时优先新增 `OpenCodeAgentRuntime` 适配器，不修改平台领域对象、Tool、Skill、Artifact 和 UI 协议。

## 12. 决策后果

正面影响：

- Test Agent 与现有平台技术栈保持一致
- 领域约束、权限和审计由平台完整掌握
- Skill 和 MCP 仍保留开放性
- 将来切换模型或 Agent Runtime 时，业务层和 UI 不需要重写

需要承担的成本：

- 平台需要维护自己的 Agent Task 和 Artifact 模型
- 需要实现轻量 Skill Registry 和 Agent Event 协议
- Spring Boot 4.1 升级已完成；正式接入 Spring AI 时仍需执行模型、Tool Calling、SSE 和 MCP 的专项回归
