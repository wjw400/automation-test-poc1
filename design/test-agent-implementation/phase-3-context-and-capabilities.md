# 阶段三：Context、Skill 与 Tool

状态：`Completed`

## 1. 目标

让 Agent 能够在明确的 Workspace 和目标资产范围内，按需读取最小化上下文、加载可信 Skill，并调用白名单 Java Tool 创建和校验 Scenario Draft。

## 2. 范围

### 包含

- Context Builder
- Context Snapshot
- Skill Registry
- Tool Registry 与 Tool Policy
- 首批 Java Tool
- Test Knowledge 检索和引用
- 结构化 Command Plan
- Draft、Diff 和 Validation Artifact

### 不包含

- 外部 MCP Server
- 向量数据库
- 多 Agent 和 Handoff
- 自动执行测试
- Suite Draft

## 3. Context Builder

Agent 上下文按以下优先级构建：

1. 当前 Scenario
2. 当前 Step 和关联 Operation
3. Scenario 级已发布 Test Knowledge
4. Operation 级已发布 Test Knowledge
5. API Spec 级已发布 Test Knowledge
6. Project 级已发布 Test Knowledge
7. DSL、Action 和安全固定规则

- [x] 每次 Turn 生成不可变 `ContextSnapshot`
- [x] 记录每个上下文片段的来源、范围和 Revision
- [x] 只包含当前任务需要的字段
- [x] Profile 只暴露能力描述和 ID，不暴露秘密
- [x] 所有文本上下文标记可信级别
- [x] Test Knowledge 视为非可信业务内容

## 4. Skill Registry

首批 Skill：

```text
agent-skills/
  scenario-authoring/SKILL.md
  secure-payload/SKILL.md
  failure-analysis/SKILL.md
```

V1 实际启用 `scenario-authoring` 和 `secure-payload`。

- [x] 定义 `SKILL.md` Front Matter
- [x] 支持名称、描述、版本、风险等级和所需 Tool
- [x] Skill 只允许从代码库受信任目录加载
- [x] Skill 内容进入 Context Snapshot 和审计
- [x] 未知、禁用或不匹配权限的 Skill 加载失败
- [x] 尽量保持与 OpenCode Skill 目录和基本格式兼容

Skill 不能授予权限，只能在 `ToolPolicy` 允许范围内请求能力。

## 5. 首批 Java Tool

### READ

- `getScenario`
- `getOperationSchema`
- `searchPublishedKnowledge`
- `getSupportedActions`

### DRAFT_WRITE

- `createScenarioDraft`
- `applyScenarioCommands`

### VALIDATE

- `validateScenarioDraft`
- `getScenarioDraftDiff`

每个 Tool 必须定义：

- 名称和面向模型的精确描述
- JSON 输入 Schema
- 输出 Schema
- 副作用等级
- 所需权限
- Workspace 和目标资产检查
- 超时和最大输出大小
- 脱敏规则

## 6. Tool Policy

- [x] V1 只允许 `READ`、`DRAFT_WRITE` 和 `VALIDATE`
- [x] 每次调用校验 Task、Workspace 和目标资产
- [x] 禁止跨 Workspace 读取
- [x] 禁止正式资产写入
- [x] 禁止读取 Environment/Profile 秘密
- [x] 禁止通用 Shell、文件写入和任意 HTTP
- [x] Tool 输出写入 Agent Artifact，Event 只保存脱敏后的活动元数据

当前 POC 仍采用单用户身份模型。`createdBy` 已进入 Task 审计，但真实登录主体与 Tool 调用者的一致性校验，要在平台接入认证后补齐。

## 7. Command Plan 输出

Agent 必须输出结构化 Command Plan：

```json
{
  "targetType": "SCENARIO_DRAFT",
  "targetId": "draft-id",
  "baseRevision": 12,
  "commands": [],
  "assumptions": [],
  "warnings": [],
  "citations": []
}
```

- [x] 使用 JSON Schema 或类型安全结构化输出
- [x] 引用使用 Knowledge ID 和来源范围
- [x] Command Plan 不允许携带任意脚本
- [x] 平台重新校验模型输出，不能信任模型自报结果

## 8. Prompt Injection 防护基础

- [x] 系统规则与 Test Knowledge 分层传递
- [x] 明确告诉模型 Knowledge 是数据而不是系统指令
- [x] Knowledge 中的 Tool、权限和秘密请求不生效
- [x] ToolPolicy 不依赖模型是否遵守 Prompt
- [x] 记录被拒绝的越权 Tool 请求

## 9. 测试要求

- [x] Context 优先级和范围测试
- [x] Draft Knowledge 不进入默认 Agent Context
- [x] 跨 Workspace Knowledge 不可见
- [x] Skill 加载和禁用测试
- [x] Tool 权限和目标检查测试
- [x] Prompt Injection 样例测试
- [x] Command Plan Schema 测试
- [x] Knowledge citation 测试

## 10. 验收场景

通过 Agent Backend 完成：

1. 用户要求增加 Header 和订单号 Extraction
2. Agent 加载 `scenario-authoring` Skill
3. Agent 读取当前 Scenario、Operation 和相关 Knowledge
4. Agent 调用 Draft Tool 生成语义 Command
5. 平台校验 Draft 并生成 Diff
6. Artifact 保存 Command Plan、引用、Diff 和 Validation
7. 正式 Scenario 保持不变

## 11. 完成定义

- [x] Agent 能按需加载 Skill
- [x] Agent 只能看到匹配范围和权限的上下文
- [x] Agent 能通过 Java Tool 完成首个验收用例
- [x] 所有输出都有来源、权限和审计记录
- [x] 不依赖 MCP 和向量数据库
- [x] 阶段三自动化测试通过

## 12. 实现记录

- `ContextSnapshot` 作为 `CONTEXT_SNAPSHOT` Artifact 在模型调用前持久化；即使 Turn 失败也保留。
- Context 中的 Scenario、Operation 和固定规则标记为可信平台内容，Published Knowledge 标记为 `UNTRUSTED_BUSINESS_CONTENT`。
- 常见密码、Token、Authorization、私钥和凭据字段在进入模型上下文前替换为 `[REDACTED]`。
- `scenario-authoring` 默认用于 Scenario Task；`secure-payload` 只在用户意图或 Scenario 涉及加密、解密、签名时加载；`failure-analysis` 保留但禁用。
- DeepSeek Runtime 通过 Spring AI 2.0 Tool Calling 调用平台 Tool；Tool 超时、输出大小和错误码由平台适配层控制。
- Tool Artifact 在单次 Tool 完成后立即持久化，不依赖整轮模型回复成功。
- `GET /api/agent/skills` 和 `GET /api/agent/tools` 可用于 UI 展示能力清单。
- Fake Runtime 的 `[draft-demo]` 路径覆盖 Scenario、Operation、Knowledge、Draft、Command、Validation 和 Diff 的完整闭环。
