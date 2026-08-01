# 阶段一：Scenario Command 与 Draft 领域内核

状态：`Completed`

完成日期：`2026-08-01`

实现验证：13 个 Agent 领域测试、3 个 Runner Action/Path 测试和 5 个 mock-service 回归测试通过；PostgreSQL 16.14 已应用 Flyway V18，并通过真实 Draft API 验证原子 Command、结构化 Diff、Revision 冲突和正式 Scenario 隔离。

## 1. 目标

建立一套完全不依赖模型的 Scenario 修改内核，使人工 UI、Spring AI、未来 MCP 和其他 Agent Runtime 可以通过相同的语义 Command 创建和校验 Draft。

本阶段是后续所有 Agent 能力的基础。没有通过本阶段验收，不开始真实模型接入。

## 2. 范围

### 包含

- Scenario DSL 的后端结构化解析与序列化
- `ScenarioCommand` 白名单模型
- `CommandPlan` 和 `baseRevision`
- Scenario Draft 创建和更新
- 原子执行 Command Plan
- DSL、Operation、Action 和变量依赖校验
- 结构化 Diff
- Revision 冲突和错误模型
- 单元测试

### 不包含

- 大模型调用
- Chat Session
- Skill 和 MCP
- Agent UI
- 自动保存和执行测试

## 3. 领域对象

建议新增：

```text
ScenarioCommand
CommandPlan
ScenarioDraft
ScenarioRevision
DraftValidationResult
DraftDiff
CommandExecutionResult
```

首批 Command：

- `SetRequestHeader`
- `RemoveRequestHeader`
- `SetQueryParameter`
- `SetPathParameter`
- `SetRequestBody`
- `SetRequestBodyValue`
- `AddBeforeAction`
- `AddAfterAction`
- `AddAssertion`
- `RemoveAssertion`
- `AddExtraction`
- `RemoveExtraction`
- `AddStep`
- `RemoveStep`
- `MoveStep`
- `RenameStep`
- `SelectOperation`

## 4. 实施任务

### 4.1 DSL 结构化模型

- [x] 确认后端 Scenario AST 与现有 YAML v1 Schema 一致
- [x] 使用结构化 YAML 解析，不使用字符串替换
- [x] 保证解析和序列化后的语义稳定
- [x] 为无效 YAML 返回明确的字段路径错误

### 4.2 Command 模型

- [x] 为每种 Command 定义类型安全的输入结构
- [x] 定义 Command JSON 多态解码规则
- [x] 拒绝未知 Command 类型和未知字段
- [x] 定义 Command 的目标 Step 和字段定位规则

### 4.3 Draft Service

- [x] 实现 `ScenarioDraftService`
- [x] 从正式 Scenario 和 Revision 创建 Draft
- [x] Draft 与正式 Scenario 分离存储
- [x] Command Plan 全部成功后才提交 Draft 更新
- [x] 任一 Command 失败时不保留部分修改

### 4.4 Command Service

- [x] 实现 Scenario Command Engine，并由 `ScenarioDraftService` 提供事务边界
- [x] 检查 `targetId` 和 `baseRevision`
- [x] 按顺序执行 Command
- [x] 保留 Command 执行结果和错误位置
- [x] 禁止 Command 修改 Environment、Profile 和秘密

### 4.5 确定性校验

- [x] 校验 DSL Schema
- [x] 校验 Operation 属于当前 Workspace
- [x] 校验 Step 引用的 Operation 存在
- [x] 校验 Action 类型和配置参数
- [x] 校验变量表达式和提取变量依赖
- [x] 校验 Profile 只作为非敏感引用
- [x] 区分 Error、Warning 和 Information

### 4.6 Diff

- [x] 生成结构化字段 Diff，不只生成 YAML 文本 Diff
- [x] 支持 Step 新增、删除、移动和修改
- [x] 支持 Header、Body、Action、Assertion 和 Extraction Diff
- [x] Diff 项使用稳定 Step ID 和 JSON Pointer 定位 Scenario Editor 字段

## 5. API 草案

本阶段 API 只用于开发验证，最终可由 Agent Artifact API 包装：

```text
POST /api/projects/{projectId}/scenario-drafts
GET  /api/scenario-drafts/{draftId}
POST /api/scenario-drafts/{draftId}/commands
POST /api/scenario-drafts/{draftId}/validate
GET  /api/scenario-drafts/{draftId}/diff
DELETE /api/scenario-drafts/{draftId}
```

## 6. 测试要求

- [x] 每种 Command 至少包含成功、目标不存在和输入非法测试
- [x] Command Plan 原子性测试
- [x] Revision 冲突 API 验证
- [x] YAML round-trip 测试
- [x] Operation 跨 Workspace 引用测试
- [x] 变量先使用后提取测试
- [x] 加密 Before Action 和解密 After Action 测试
- [x] Diff 顺序和字段定位测试

## 7. 验收场景

不接入 AI，通过固定 JSON Command Plan 完成：

1. 为指定 Step 增加 `X-Correlation-Id` Header
2. 增加从响应提取 `orderId` 的规则
3. Draft 校验通过
4. Diff 准确展示两项修改
5. 正式 Scenario 内容保持不变

## 8. 完成定义

- [x] 所有首批 Command 已实现并测试
- [x] Draft 与正式资产完全隔离
- [x] 校验和 Diff 不依赖大模型
- [x] Command Plan 具备 Revision 和原子性保护
- [x] 阶段一测试全部通过
- [x] API 和领域模型有简明开发文档

当前 API 和 Command 格式见 [Scenario Draft API](phase-1-scenario-draft-api.md)。
