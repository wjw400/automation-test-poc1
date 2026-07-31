# Test Agent 产品愿景

## 1. 愿景

我们希望构建一个运行在自动化测试 Workspace 内的领域 Agent。

它让用户可以像与一名熟悉系统、接口和测试规范的测试工程师协作一样，通过自然语言完成测试设计、场景维护、覆盖分析和失败诊断。

> Test Agent 将用户的测试目标转化为结构化、可验证、可审查、可执行的测试资产，并使用真实执行证据帮助用户持续改进这些资产。

Test Agent 的目标不是替代测试工程师，而是减少重复配置、降低复杂协议的使用成本，并让用户把注意力放在业务风险和验证目标上。

## 2. 目标用户

Test Agent 主要服务于：

- 需要维护大量 API 自动化场景的测试工程师
- 需要快速验证接口行为的后端工程师
- 了解业务但不熟悉测试 DSL 的项目成员
- 需要审查覆盖范围和失败证据的测试负责人

用户不需要掌握 Prompt Engineering、MCP、模型 Tool Schema 或 Agent 内部实现。

## 3. 核心体验

用户在 Scenario、Suite、API Operation 或 Execution 附近打开统一的 Test Agent Panel，并用自然语言描述目标，例如：

- 为创建订单接口补充正常和异常场景
- 给当前请求增加签名和加密处理
- 从响应中提取订单号，并在下一步请求中使用
- 检查当前 Scenario 的断言是否足够
- 根据失败的请求、响应和校验过程分析原因

Agent 应当：

1. 明确显示当前正在处理的 Workspace 和测试资产
2. 读取相关 OpenAPI、Test Knowledge、Scenario 和脱敏执行证据
3. 按需加载可信 Skill 并调用受控 Tool
4. 生成结构化 Scenario 或 Suite Draft
5. 使用平台确定性规则完成校验
6. 展示依据、假设、Tool Activity、Diff 和 Validation
7. 由用户决定是否应用、保存或执行

Chat 是表达意图的入口，不是测试资产的最终存储方式。最终结果必须回到平台原生的 Scenario、Suite、Diff、Validation 和 Execution 界面。

## 4. 产品定位

Test Agent 是测试平台中的领域 Agent，不是：

- 通用聊天机器人
- 通用 Coding Agent
- 平台管理员
- 可以绕过权限的自动化脚本
- 独立于 Workspace 和测试资产的聊天中心

它理解并操作以下领域对象：

- OpenAPI Spec 和 Operation
- Test Knowledge
- Test Scenario、Step 和 Suite
- Environment 和 Profile 的非敏感引用
- Draft、Command Plan、Diff 和 Validation
- Scenario Execution、Suite Execution 和脱敏 Evidence

## 5. 核心原则

### 5.1 Artifact-centered

对话只是过程，结构化测试资产才是结果。Agent 的重要输出必须形成 Draft、Diff、Validation 或 Failure Analysis。

### 5.2 Context-aware

Agent 必须知道当前 Workspace 和目标资产，并使用范围匹配的 OpenAPI、Test Knowledge 和执行证据。任何关键结论都应能追溯到上下文来源。

### 5.3 Deterministic validation

模型负责理解目标和提出方案；DSL 合法性、Operation 引用、变量依赖、Action 能力和权限由平台代码确定性验证。

### 5.4 Human-controlled

用户始终掌握正式保存、删除、配置修改和执行权限。Agent 第一版只读取上下文、修改 Draft 和执行校验。

### 5.5 Evidence-oriented

Agent 不以“生成内容”为完成标准，而以结构化资产是否通过校验、执行证据是否支持结论为标准。

### 5.6 Open capability model

平台能力通过 Skill、Java Tool 和 MCP 扩展，但任何能力都必须经过白名单、权限、上下文和副作用检查。

### 5.7 Native workflow

Agent 与现有 Scenario Editor、Suite Editor 和 Execution 页面协作，不创建第二套编辑器，也不要求用户理解底层 YAML、模型或工具协议。

## 6. 能力演进

### 阶段一：受限协作 Agent

- 理解当前 Scenario、Suite 和 Operation
- 读取已发布 Test Knowledge
- 生成和修改 Draft
- 展示 Diff 与 Validation
- 审查测试设计和分析脱敏失败证据
- 不自动保存，不自动执行

### 阶段二：批准后执行

- 用户明确批准单次非生产环境执行
- Agent 读取脱敏 Evidence 并分析结果
- 基于证据提出下一版 Draft
- 每次修改和执行都可追溯

### 阶段三：有边界的 Sandbox Agent

- 在明确 Environment Allowlist 和预算内运行
- 可以执行有限次数的验证与改进循环
- 达到目标、预算或停止条件后结束
- 风险操作仍需用户批准

生产环境自主执行不属于当前愿景范围。

## 7. 第一版标志性场景

用户打开一个订单 Scenario，对 Agent 输入：

> 这个接口发送前需要加密和签名，请根据项目协议修改请求，并验证响应中加密字段解密后的订单状态。

Agent 应当：

- 识别当前 Operation 和 Scenario
- 找到关联的加密协议 Test Knowledge
- 加载安全请求处理 Skill
- 生成 Before/After Action、Assertion 和 Extraction 的语义命令
- 创建 Scenario Draft
- 通过平台校验并展示 Diff
- 说明使用了哪些协议文档和假设
- 等待用户将 Draft 应用到编辑器并保存

这个场景能够同时验证 Agent 的上下文理解、Skill、Tool、复杂协议、Draft、校验和用户控制，是第一版最有代表性的端到端体验。

## 8. 成功标准

Test Agent 的成功不以消息数量或生成速度衡量，而以以下结果衡量：

- 用户能通过自然语言完成原本需要理解 DSL 的场景修改
- Agent 生成的变更可以被结构化 Diff 清楚解释
- 生成的 Draft 能通过确定性校验
- 用户能知道 Agent 使用了哪些知识、工具和假设
- Agent 不会读取秘密或绕过权限
- 用户可以随时停止、拒绝或修改 Agent 的建议
- Agent 生成的测试资产能够独立运行和长期维护，而不依赖聊天记录
- 失败分析能够引用请求、响应、解密过程和断言证据，而不是只给出猜测

## 9. 非目标

当前不追求：

- 一个可以回答所有问题的企业 Chatbot
- 让 Agent 自主修改生产配置或执行生产测试
- 让模型直接编辑数据库、DOM 或原始 YAML 文本
- 通过增加大量 Agent Persona 制造表面上的智能
- 用向量数据库替代清晰的知识范围和资产关系
- 用 AI 自动把失败测试改成通过

## 10. 与其他设计文档的关系

- 本文定义“为什么做、最终做成什么样”
- [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md) 定义 Agent Task、UI 形态和总体架构
- [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md) 定义 Command、Tool、权限和执行规则
- [Test Agent Runtime 技术选型决策](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-technology-decision.md) 定义 Spring AI、OpenCode、Skill 和 MCP 的技术路线
- [Test Agent 分阶段实施计划](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-implementation/README.md) 定义五个开发阶段及其验收门槛
- [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md) 定义允许和禁止的 AI 行为
- [Test Knowledge](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-knowledge.md) 定义业务知识的维护和 Agent 上下文来源
