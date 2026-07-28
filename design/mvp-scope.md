# MVP 范围与路线

## 1. MVP 目标

第一版平台 POC 的目标不是覆盖所有自动化测试管理能力，而是证明：

- 结构化 case 可以作为平台主模型
- 安全协议场景可以被平台统一管理和执行
- step 级报告足够支撑问题定位

## 2. MVP 必做范围

### 2.1 项目与基础配置

- 创建一个 `Project`
- 管理 `Environment`
- 管理 `Profile`

### 2.2 Case 管理

- 列表查看 case
- 查看 case 详情
- 文本方式编辑 YAML/JSON DSL
- 基于 schema 做格式校验

### 2.3 手动执行

- 支持选择 case 执行
- 支持选择 environment
- 支持选择或隐式绑定 profile

### 2.4 报告查看

- 查看 run 列表
- 查看 run 详情
- 查看 step 级 evidence

### 2.5 协议能力

- request transform
- response decrypt
- raw + decrypted mixed validation
- extract chaining

### 2.6 演示链路

- 使用 `mock-service` 作为联调后端
- 跑通至少一条真实 secure protocol case

## 3. MVP 暂不做

POC 阶段不建议做：

- 复杂权限控制
- 审批与发布流程
- 任务编排中心
- 定时调度
- 多租户
- 大规模并发执行
- 可视化拖拽式 case 编排
- 全自动 AI 生成 + 自动入库

这些能力都可能有价值，但会让 POC 失去聚焦。

## 4. 建议页面清单

### 4.1 必要页面

- `Projects`
- `Cases`
- `Case Detail`
- `Runs`
- `Run Detail`
- `Environments`
- `Profiles`

### 4.2 页面重点

`Case Detail` 重点解决：

- case 内容可读
- schema 错误可见
- environment/profile 关系可理解

`Run Detail` 重点解决：

- step 粒度展示
- request/response 明确
- decrypted payload 清楚
- validation 失败原因可定位

## 5. 分阶段实施建议

### Phase 1: 资产与执行闭环

- case 存储
- environment/profile 存储
- 手动执行
- step 级 run 报告

### Phase 2: 平台视图完善

- case 编辑页
- run 详情页
- 失败筛查

### Phase 3: 批量与协作

- suite 执行
- 历史对比
- 基础团队协作能力

### Phase 4: AI 增强

- 提供统一 Test Agent Panel 和页面内上下文入口
- Test Agent 生成 Scenario Draft 和 Suite Draft
- Test Agent 使用语义 Command 补全 Request、Extract 和 Assertions
- Test Agent 生成可审阅 Diff
- Test Agent 总结脱敏后的失败 Evidence

所有 Phase 4 功能必须遵守 [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)、[Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md) 和 [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)。V1 Agent 最高权限为读取上下文、修改未保存 Draft、执行确定性校验和提出可审阅 Diff，不包含自动保存、配置或执行。

受审批的 Sandbox 运行和 Agent 自动迭代属于后续阶段，不纳入当前 MVP。

## 6. 主要风险

### 6.1 DSL 设计过度自由

风险：

- case 可写性变强，但执行器和 UI 很难稳定支持

建议：

- 优先强约束 schema
- 让 80% 场景走标准结构

### 6.2 报告信息过少或过多

风险：

- 信息过少：无法定位问题
- 信息过多：报告失控、泄露敏感信息

建议：

- 设计 mask 策略
- 区分 plaintext / wire body / decrypted payload 展示规则

### 6.3 执行器与平台层耦合过重

风险：

- 以后 runner 难以独立演进

建议：

- 保持 `runner-core` 独立边界
- 平台只编排和可视化，不把执行细节塞进平台服务

### 6.4 安全配置散落

风险：

- profile、key、passphrase 分散在多个地方，难维护

建议：

- 统一 profile 模型
- 用 secret 引用替代明文

## 7. POC 完成定义

建议将 POC 完成定义为：

- 平台可以管理至少一个项目
- 平台可以维护至少一个 secure protocol case
- 平台可以触发执行并查看 step 级 run 详情
- 平台可以展示 raw 与 decrypted mixed validation 的证据
- 平台可以证明现有 YAML DSL 足以作为后续平台主模型

## 8. 下一步设计建议

在正式实现平台前，建议继续补充两类设计：

1. API 设计
   包括 case、run、environment、profile 的平台接口。

2. UI 信息架构
   包括 case detail 和 run detail 页面的信息分块与交互流程。

当这两部分也清楚后，平台 POC 就可以开始实现。
