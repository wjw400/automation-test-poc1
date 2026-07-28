# Test Agent 能力边界

## 1. 文档目的

本文定义自动化测试平台中 Test Agent 能做什么、不能做什么，以及 Agent 与用户、平台校验器和 Runner 之间的职责边界。

本文是后续 AI 功能设计、技术选型、接口设计、UI 设计和验收的上位约束。具体功能文档可以收紧这些规则，但不能绕过这些规则。

当前状态：`Design Baseline`

配套规范：

- [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)
- [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)
- [AI 上下文与安全规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-context-and-security.md)
- [AI 产品交付规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-product-delivery-guidelines.md)

本文暂不规定具体模型供应商、模型版本或部署方式。

## 2. 产品定位

平台中的 AI 长期定位为 Test Agent：

> Test Agent 是运行在 Test Workspace 内的领域 Agent。它将自然语言测试目标转化为结构化测试资产，并在经过授权的非生产环境中使用执行证据持续验证和改进这些资产。

V1 交付的是受限 Test Agent，只开放读取、Draft 和可审阅变更能力，不开放自主运行。后续是否开放受控 Sandbox 执行，必须按照独立权限、环境白名单、预算、审批和停止条件逐阶段评审。

Test Agent 的主要价值是：

- 降低结构化 Test Scenario 的编写成本
- 帮助用户理解 API 和业务流程
- 提高测试设计质量和覆盖率
- 帮助用户理解失败原因和变更影响

Test Agent 不是平台管理员，不能成为测试结果、业务规则、安全配置或生产操作的最终决策者。

## 3. 核心原则

### 3.1 结构化优先

AI 应输出平台领域对象或 DSL AST，不应把任意自然语言、代码或 YAML 文本直接当作可执行资产。

推荐链路：

```text
用户目标
  -> Agent 生成结构化 Plan
  -> Agent 生成 CommandPlan 或结构化 Draft
  -> Schema 校验
  -> DSL 语义校验
  -> 安全策略检查
  -> 用户审阅
  -> 用户保存
  -> 用户运行
```

### 3.2 Draft 优先

AI 生成或修改的内容默认都是未保存 Draft。

AI 可以提出修改，但不能静默覆盖现有 Scenario、Suite、Environment、Profile 或 API 资产。

### 3.3 确定性校验优先于模型判断

以下判断必须由平台代码完成，不能交给模型：

- DSL Schema 是否合法
- Operation 是否存在
- Action 是否受支持
- 变量是否先定义后使用
- Profile 和 Environment 引用是否存在
- Step 顺序和 Extract 依赖是否合法
- 用户是否有权保存或运行

模型只能生成候选内容和解释校验结果。

### 3.4 最小上下文

AI 每次只能获得完成当前任务所需的最小上下文。

不能因为模型可能“用得上”，就把整个 Project、所有 Execution 或全部配置发送给模型。

### 3.5 用户拥有最终控制权

V1 中，保存、覆盖、删除和运行必须由平台中的明确用户操作触发。

自然语言中的“帮我直接保存并运行”在 V1 中不视为保存或执行授权。

后续阶段即使允许 Sandbox 执行，也必须使用平台 Approval Engine 授予范围明确、可过期、可取消的执行权限。自然语言本身不能替代权限检查。

### 3.6 不确定性必须可见

AI 必须区分：

- 来自 OpenAPI 或平台资产的事实
- 根据已有 Scenario 推导的模式
- AI 自己的假设
- 无法确认的信息

假设和警告必须随 Draft 一起展示。

## 4. 允许的能力

### 4.1 API 理解

AI 可以：

- 解释 Operation 的用途
- 总结 Request 和 Response Schema
- 说明字段约束、状态码和错误响应
- 根据 OpenAPI Tags 和引用关系发现相关 Operations
- 将技术字段转化为测试设计建议

AI 输出是说明和建议，不改变 API Spec。

### 4.2 Test Scenario 设计

AI 可以：

- 根据业务目标推荐 Operations
- 设计多 Step 调用顺序
- 推荐变量提取和后续引用
- 推荐 Before Send 和 After Receive Actions
- 推荐 Assertions 和边界条件
- 生成新的 Scenario Draft
- 使用受支持的语义 Command 生成可审阅修改

生成结果必须引用当前 Project 中真实存在的 Operation。

### 4.3 测试数据生成

AI 可以：

- 根据 Schema 生成虚构数据
- 生成边界值和非法值
- 生成不同业务分支需要的数据组合
- 解释数据设计目的

生成的数据不得默认包含真实个人信息、真实账户、真实 Token 或生产数据。

### 4.4 Scenario 审查

AI 可以检查：

- Step 是否存在明显冗余
- 变量引用是否清晰
- Assertions 是否过弱或过度具体
- 是否遗漏关键状态码和业务分支
- 是否存在难以维护的固定值
- 是否有可以复用的既有 Scenario

AI 只能输出问题清单或修改建议。

### 4.5 Test Suite 设计

AI 可以：

- 推荐已有 Scenarios 的组合
- 推荐执行顺序
- 推荐 Suite Variable Overrides
- 识别重复或缺失场景
- 生成 Suite Draft

AI 不得自动把 Scenario 加入正式 Suite。

### 4.6 覆盖率分析

AI 可以分析：

- 未覆盖的 Operations
- 未覆盖的响应状态码
- 缺少正常、异常或边界测试的 API
- OpenAPI 变更后可能失效的 Scenario

基础覆盖率计算应由平台代码完成，AI 负责解释和提出建议。

### 4.7 Execution 失败分析

AI 可以基于脱敏后的执行证据：

- 总结失败发生在哪个 Step 和阶段
- 区分请求构造、加密、网络、解密、Assertion 和 Extract 错误
- 比较预期和实际结果
- 给出调查路径
- 提出 Scenario 修改建议

AI 不得以“让测试变绿”为目标自动修改 Assertions。

### 4.8 API 变更影响分析

AI 可以：

- 解释 OpenAPI 版本间差异
- 识别受影响的 Scenario 和 Suite
- 生成迁移 Draft
- 标注可能的破坏性变更

AI 不得自动覆盖已有测试资产。

## 5. 禁止的能力

以下是硬边界。第一版及后续版本都不能默认绕过。

### 5.1 Secret 和密钥

AI 不得读取、接收、记录或输出：

- 私钥
- Private Key Passphrase
- Password
- Access Token
- Refresh Token
- Authorization Header
- Cookie 和 Session
- 数据库连接凭证
- Secret Provider 的原始值

AI 可以看到：

- Profile 名称
- Key ID
- Fingerprint
- 算法
- 是否配置完成
- 脱敏后的 Secret Reference

### 5.2 直接执行

AI V1 不得：

- 直接发送 HTTP 请求
- 触发 Scenario 或 Suite 执行
- 选择生产 Environment
- 重试失败执行
- 取消正在运行的任务
- 调用 Mock Service 或其他业务系统

AI 可以调用纯校验、静态分析和 Draft 生成能力。

后续只允许在 [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md) 规定的 `Run In Sandbox` 或 `Autonomous Sandbox` 模式下增加受控执行。生产环境执行始终禁止。

### 5.3 平台配置

AI 不得创建、修改或删除：

- Environment
- Profile
- Partner Key
- Secret Reference
- Project 权限
- 用户和团队配置

AI 可以指出配置缺失，但不能代替用户补充敏感配置。

### 5.4 正式资产变更

AI 不得：

- 静默保存 Scenario 或 Suite
- 覆盖已有版本
- 删除测试资产
- 批量修改多个 Scenario
- 修改导入的 OpenAPI Spec
- 绕过 Validation 保存非法 DSL

### 5.5 虚构平台事实

AI 不得把以下内容作为事实写入可执行 Draft：

- 不存在的 Operation ID
- 不存在的 Request 或 Response 字段
- 不存在的 Environment 或 Profile
- 平台不支持的 Action Type
- 未在上下文中提供的业务规则

确有需要时，只能作为明确标注的假设展示给用户。

### 5.6 自动迎合测试结果

AI 不得为了让测试通过而：

- 自动删除失败 Assertion
- 把 Expected Value 改成 Actual Value
- 扩大允许状态码范围
- 忽略解密或签名失败
- 隐藏失败证据
- 将产品缺陷直接判断为测试错误

## 6. 权限层级

AI 能力分为四级：

| Level | 名称 | 能力 | V1 |
|---|---|---|---|
| L1 | Read | 读取允许的结构化、脱敏上下文 | 允许 |
| L2 | Draft | 生成新的未保存资产 | 允许 |
| L3 | Propose Change | 对现有资产生成可审阅 Diff | 允许 |
| L4 | Act | 保存、删除、配置或运行等有外部副作用的操作 | 不允许 |

V1 的最高权限是 L3。

用户接受 AI 建议后，由普通平台命令执行保存；该保存动作属于用户操作，不属于 AI 权限。

## 7. 职责摘要

V1 的能力边界可以概括为：

> Test Agent 可以理解、规划、生成、审查、解释和提出修改。
>
> Test Agent V1 不可以配置、保存、删除、执行或做最终判断。

平台校验器负责合法性，用户负责最终决策，Runner 负责确定性执行。Agent 负责协调这些能力，但不能替代它们的职责。

Agent Task 和产品交互见 [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)。

语义 Command、工具副作用分级、授权模式和停止条件见 [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)。

数据、工具和审计要求见 [AI 上下文与安全规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-context-and-security.md)。

交互、失败分析和分阶段验收要求见 [AI 产品交付规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-product-delivery-guidelines.md)。
