# Test Agent 分阶段实施计划

## 1. 目的

本目录将 Test Agent 愿景和架构决策拆分为五个可独立验收的实施阶段，用于后续逐步开发、评审和跟踪。

计划基于以下设计基线：

- [Test Agent 产品愿景](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-vision.md)
- [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)
- [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)
- [Test Agent Runtime 技术选型决策](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-technology-decision.md)
- [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)

## 2. V1 目标

V1 只完成一条端到端闭环：

> 用户在当前 Scenario 中通过 Chat 描述修改目标，Agent 读取受控上下文并创建 Scenario Draft，平台完成确定性校验和 Diff，用户将结果应用到现有 Scenario Editor，最后由用户手动保存。

首个验收用例：

> 给当前请求增加 `X-Correlation-Id`，并从响应中提取订单号。

第二个标志性验收用例：

> 根据项目协议为当前请求增加加密和签名处理，并验证响应密文字段解密后的订单状态。

## 3. 阶段划分

1. [阶段一：Scenario Command 与 Draft 领域内核](phase-1-domain-kernel.md)
2. [阶段二：Agent Backend 与模型运行时](phase-2-agent-backend.md)
3. [阶段三：Context、Skill 与 Tool](phase-3-context-and-capabilities.md)
4. [阶段四：Codex 风格 Agent Panel](phase-4-agent-panel.md)
5. [阶段五：安全治理与端到端验收](phase-5-security-and-e2e.md)

阶段必须按顺序推进。后续阶段可以提前做技术验证，但不能绕过前一阶段的完成门槛进入正式实现。

## 4. 开发前置条件

- [ ] 当前愿景、架构、Runtime 选型和实施计划已经提交
- [ ] 确认 V1 不包含 MCP、多 Agent、向量数据库和自动执行
- [ ] 确认首个模型供应商和 OpenAI-compatible 配置方式
- [ ] 模型密钥只通过服务端环境变量或 Secret Manager 提供
- [ ] 记录现有构建和测试基线，包括已知的历史失败
- [ ] 确认 Spring Boot 与 Spring AI 的兼容版本组合

推荐配置名：

```text
TEST_AGENT_ENABLED
TEST_AGENT_BASE_URL
TEST_AGENT_API_KEY
TEST_AGENT_MODEL
```

没有真实模型配置时，平台应使用 `FakeAgentRuntime` 支持后端和 UI 开发。

## 5. 总体完成定义

五个阶段全部完成时，系统必须满足：

- Agent Chat 与 Workspace、用户和目标 Scenario 明确关联
- Agent 可以按需读取 OpenAPI、Scenario 和已发布 Test Knowledge
- Agent 只能使用白名单语义 Command 修改 Draft
- 所有 Draft 都经过确定性校验并生成结构化 Diff
- UI 能流式展示消息、Skill、Tool、Draft 和 Validation 状态
- 用户可以停止、拒绝或应用 Agent 结果
- Agent 不能直接保存正式资产、读取秘密或运行测试
- 完整消息、上下文来源、Tool Activity 和用户决策可审计
- Agent 生成的 Scenario 在脱离聊天记录后仍可独立维护和执行

## 6. 计划维护规则

- 每个阶段开始时，将状态从 `Not Started` 更新为 `In Progress`
- 只在该阶段完成定义全部满足后标记为 `Completed`
- 新需求先判断属于当前阶段、后续阶段还是 V1 非目标
- 影响权限、数据边界或正式资产写入的变更必须先更新设计文档
- 实施中发现架构决策不再成立时，先更新 Runtime 技术选型决策，再修改代码
