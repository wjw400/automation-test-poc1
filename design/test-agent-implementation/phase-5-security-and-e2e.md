# 阶段五：安全治理与端到端验收

状态：`Completed`

## 1. 目标

验证 Test Agent 在真实项目上下文和复杂安全协议场景中可靠工作，并补齐权限、脱敏、Prompt Injection、防滥用、审计和恢复能力，使 V1 达到可邀请同事试用的状态。

## 2. 范围

### 包含

- 权限和 Workspace 隔离
- Context 和 Tool 脱敏
- Prompt Injection 防护验证
- Agent 预算和停止条件
- 审计和可追溯性
- 模型故障降级
- 首个与加密场景端到端测试
- POC 试用准备

### 不包含

- 生产环境自主执行
- 多 Agent 协作
- 外部 MCP 正式开放
- 向量数据库
- 自动保存和删除资产
- 自动运行 Scenario 或 Suite

## 3. 权限与隔离

- [x] 每个 Agent Task 绑定用户和 Workspace
- [x] 所有 Context、Tool 和 Artifact 检查 Workspace
- [x] 用户只能访问有权限的 Agent Task
- [x] Draft 不能应用到其他 Workspace 的资产
- [x] Tool 参数中的项目和目标 ID 不能覆盖服务器上下文
- [x] 后端不信任前端传入的权限模式

## 4. 秘密与脱敏

- [x] Agent 永远不能读取 Profile 私钥、密码和 Passphrase
- [x] Agent 不能读取认证 Header、Cookie、Token 和数据库凭据
- [x] Profile 只暴露 ID、名称、类型和能力描述
- [x] Execution Evidence 在进入 Context 前脱敏
- [x] Tool 输入输出和错误日志执行相同脱敏策略
- [x] 模型请求和响应日志默认不保存敏感正文
- [x] API Key 只来自服务端 Secret 配置

## 5. Prompt Injection 防护

测试至少包括：

- Knowledge 要求忽略系统规则
- Knowledge 要求读取私钥
- API 描述要求调用禁用 Tool
- 用户要求直接保存正式 Scenario
- 用户要求访问其他 Workspace
- Tool 输出中包含伪造系统指令

- [x] 所有越权请求被 ToolPolicy 或服务端校验拒绝
- [x] 拒绝不依赖模型是否遵循 Prompt
- [x] UI 清楚显示能力边界，不泄露内部安全规则全文

## 6. 预算与停止条件

- [x] 最大模型调用轮次
- [x] 最大 Tool 调用次数
- [x] Token 和时间预算
- [x] 连续无进展检测
- [x] 重复调用相同 Tool 检测
- [x] 用户取消
- [x] Runtime 异常和服务关闭时安全终止
- [x] 达到限制时 Task 进入明确终态

## 7. 审计

必须能够回答：

- 谁发起了 Task
- Task 操作了哪个 Workspace 和资产
- 使用了哪些 Context 和 Knowledge Revision
- 加载了哪些 Skill
- 调用了哪些 Tool 及其副作用等级
- 生成了哪些 Command 和 Artifact
- 哪些校验通过或失败
- 用户是否应用并最终保存

- [x] 审计记录不能被模型修改
- [x] 审计中秘密经过脱敏
- [x] Task 删除策略与审计保留策略分离

## 8. 模型故障与降级

- [x] 模型未配置时可以使用 Fake Runtime 演示 UI
- [x] 限流、超时、认证失败和服务不可用分别提示
- [x] 失败后保留用户消息和已完成活动
- [x] Retry 不重复应用已经成功的 Command
- [x] 模型切换不改变平台 Artifact Schema
- [x] 模型不可用不影响手工 Scenario 编辑和执行

## 9. 端到端验收一：基础修改

输入：

> 给当前请求增加 `X-Correlation-Id`，并从响应中提取订单号。

验收：

- [x] 正确读取当前 Scenario 和 Operation
- [x] 生成正确 Header 和 Extraction Command
- [x] Validation 通过
- [x] Diff 准确
- [x] Apply 后编辑器更新但未自动保存
- [x] Save 后 Scenario 可由现有 Runner 执行

## 10. 端到端验收二：安全协议

输入：

> 这个接口发送前需要加密和签名，请根据项目协议修改请求，并验证响应中加密字段解密后的订单状态。

验收：

- [x] 读取正确范围的已发布 Test Knowledge
- [x] 加载 `secure-payload` Skill
- [x] 使用 Scenario 默认 Profile 的非敏感引用
- [x] 生成正确 Before 和 After Action
- [x] 生成明文字段和解密字段 Assertion
- [x] 不读取或输出任何私钥和 Passphrase
- [x] Validation 通过并显示 Knowledge Citation
- [x] Apply 和 Save 后由现有 Runner 正确执行

## 11. POC 试用准备

- [x] 提供模型配置说明
- [x] 提供 Agent 使用说明和能力边界
- [x] 准备至少两个示例 Scenario 和对应 Test Knowledge
- [x] 提供反馈入口
- [x] 记录模型、Prompt、Skill 和 Tool 版本
- [x] 定义试用期间的数据保留策略
- [x] 收集成功率、校验失败率、采纳率和人工修改量

## 12. 完成定义

- [x] 两个端到端验收场景全部通过
- [x] 权限、秘密、注入和跨 Workspace 测试通过
- [x] Agent 失败不会损坏正式测试资产
- [x] 审计可以重建一次 Agent 修改过程
- [x] 手工测试工作流不受 Agent 可用性影响
- [x] 文档足以支持同事独立试用
- [x] V1 范围之外的能力仍保持关闭

## 13. 验收证据

- `AgentCapabilityIntegrationTest` 覆盖基础 Draft 与安全协议 Draft、Knowledge Citation、Skill、Validation、Diff 和正式 Scenario 不变。
- `AgentSecurityIntegrationTest` 覆盖 Task 所有权、跨用户拒绝、统一脱敏、审计保留、用户决策和试用指标。
- `AgentToolCallBudgetTest` 与 `AgentTurnCoordinatorTest` 覆盖 Tool 预算、重复无进展、超时、取消和终态。
- `ApiCaseRunnerActionTest` 与订单履约 Cucumber Scenario 覆盖真实 PGP Action 和现有 Runner 执行。
- PostgreSQL 16.14 已由 Flyway 从 V20 成功迁移到 V21，并通过 Hibernate schema validation。
- 独立试用步骤见 [Test Agent V1 试用指南](agent-v1-trial-guide.md)。
