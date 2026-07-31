# 阶段三：Context、Skill 与 Tool

状态：`Not Started`

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

- [ ] 每次 Turn 生成不可变 `ContextSnapshot`
- [ ] 记录每个上下文片段的来源、范围和 Revision
- [ ] 只包含当前任务需要的字段
- [ ] Profile 只暴露能力描述和 ID，不暴露秘密
- [ ] 所有文本上下文标记可信级别
- [ ] Test Knowledge 视为非可信业务内容

## 4. Skill Registry

首批 Skill：

```text
agent-skills/
  scenario-authoring/SKILL.md
  secure-payload/SKILL.md
  failure-analysis/SKILL.md
```

V1 实际启用 `scenario-authoring` 和 `secure-payload`。

- [ ] 定义 `SKILL.md` Front Matter
- [ ] 支持名称、描述、版本、风险等级和所需 Tool
- [ ] Skill 只允许从代码库受信任目录加载
- [ ] Skill 内容进入 Context Snapshot 和审计
- [ ] 未知、禁用或不匹配权限的 Skill 加载失败
- [ ] 尽量保持与 OpenCode Skill 目录和基本格式兼容

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

- [ ] V1 只允许 `READ`、`DRAFT_WRITE` 和 `VALIDATE`
- [ ] 每次调用校验 Task、用户、Workspace 和目标资产
- [ ] 禁止跨 Workspace 读取
- [ ] 禁止正式资产写入
- [ ] 禁止读取 Environment/Profile 秘密
- [ ] 禁止通用 Shell、文件写入和任意 HTTP
- [ ] Tool 输出写入 Agent Event 并按策略脱敏

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

- [ ] 使用 JSON Schema 或类型安全结构化输出
- [ ] 引用使用 Knowledge ID 和来源范围
- [ ] Command Plan 不允许携带任意脚本
- [ ] 平台重新校验模型输出，不能信任模型自报结果

## 8. Prompt Injection 防护基础

- [ ] 系统规则与 Test Knowledge 分层传递
- [ ] 明确告诉模型 Knowledge 是数据而不是系统指令
- [ ] Knowledge 中的 Tool、权限和秘密请求不生效
- [ ] ToolPolicy 不依赖模型是否遵守 Prompt
- [ ] 记录被拒绝的越权 Tool 请求

## 9. 测试要求

- [ ] Context 优先级和范围测试
- [ ] Draft Knowledge 不进入默认 Agent Context
- [ ] 跨 Workspace Knowledge 不可见
- [ ] Skill 加载和禁用测试
- [ ] Tool 权限和目标检查测试
- [ ] Prompt Injection 样例测试
- [ ] Command Plan Schema 测试
- [ ] Knowledge citation 测试

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

- [ ] Agent 能按需加载 Skill
- [ ] Agent 只能看到匹配范围和权限的上下文
- [ ] Agent 能通过 Java Tool 完成首个验收用例
- [ ] 所有输出都有来源、权限和审计记录
- [ ] 不依赖 MCP 和向量数据库
- [ ] 阶段三自动化测试通过
