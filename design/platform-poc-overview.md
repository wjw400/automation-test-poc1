# 平台 POC 总览

## 1. 背景

当前项目已经具备三个重要基础能力：

- `test-framework`
  已经初步具备结构化 YAML 场景定义、执行引擎、step 级报告能力。
- `pgp-support`
  已经开始抽出安全协议相关能力，用于请求加密签名和响应解密验签。
- `mock-service`
  已经作为本地联调沙盒存在，用于验证协议链路和测试框架行为。

现阶段的价值不再只是“写几个自动化 case”，而是具备继续演进为一个自动化测试平台的可能性。

## 2. 平台目标

本 POC 平台的目标不是做一个“大而全”的测试管理系统，而是先验证以下能力可以在一个统一平台里闭环：

- 统一管理结构化测试场景
- 统一管理环境、变量、profile、密钥引用
- 统一触发执行并跟踪 run
- 统一查看 step 级执行证据
- 支持安全协议测试，包括：
  - request transform
  - response decrypt
  - raw 与 decrypted mixed validation
- 为后续 AI 生成与维护 case 提供稳定的数据模型

## 3. 平台边界

POC 阶段的平台边界应当收紧，避免过早扩张。

本阶段要解决的是：

- 如何定义测试场景
- 如何执行测试场景
- 如何记录执行证据
- 如何管理环境和 profile

本阶段不优先解决的是：

- 复杂权限体系
- 审批流
- 多租户
- 高并发分布式调度
- 全量测试资产迁移
- 大规模 AI 自动生成和审核工作流

## 4. 核心设计判断

平台的中心对象必须是结构化 `test case`，不是 Cucumber feature。

原因：

- YAML/JSON DSL 更适合机器生成和校验
- 复杂安全协议测试不适合继续膨胀为自然语言 step
- 平台需要对 request、response、decrypt、validation、extract 做结构化理解
- 未来平台中的 UI、执行引擎、AI 生成器都应共享同一份 case schema

Cucumber 在平台内可以保留为兼容入口，但不应该是平台的主模型。

## 5. 平台设计原则

### 5.1 以场景定义为中心

场景是第一公民，执行器、报告、UI 都围绕场景定义展开。

### 5.2 以执行证据为中心

平台报告不应停留在通过/失败，而要以 step 级证据为核心。

至少包括：

- request plaintext
- request wire body
- response raw body
- decrypted payloads
- validations
- extracted values
- profile used
- error details

### 5.3 以安全协议能力为内建能力

平台不是只支持普通 REST JSON。
对于当前业务背景，平台需要原生支持：

- request sign/encrypt
- response decrypt/verify
- encrypted payload 在 raw text 或 JSON field 中传输
- mixed plaintext + encrypted response validation

### 5.4 兼容演进，不推倒现有资产

平台应该建立在当前仓库基础上演进，而不是另起一套完全无关的系统。

当前代码的建议映射关系：

- `test-framework` -> 未来的 `runner-core`
- `pgp-support` -> 未来的共享协议能力模块
- `mock-service` -> 平台联调与演示沙盒

## 6. 平台最小价值闭环

如果平台 POC 成功，应至少能完成如下闭环：

1. 在平台中维护一个结构化 case
2. 选择 environment 和 profile
3. 手动触发 run
4. 执行器按 step 执行
5. 记录每个 step 的 request/response/decrypt/validation
6. 在平台中查看 run detail 和失败原因

只要这条链路跑通，平台就具备继续扩展的价值。

## 7. 当前仓库在平台化中的位置

当前仓库已经可以被看作平台雏形的技术原型：

- 它已经验证了 YAML case 的可行性
- 它已经验证了 step 级报告模型的方向
- 它已经开始验证 request transform / response decrypt 的复杂协议路径

因此平台建设的关键不是“另写一套”，而是把现有能力重新组织为：

- 领域模型
- 服务边界
- 可视化入口
- 运行与证据闭环

## 8. POC 成功标准

平台 POC 的成功标准建议定义为：

- 可以通过 UI 或 API 管理 case
- 可以执行至少一条真实 secure protocol case
- 可以查看 step 级执行证据
- 可以支持 environment/profile 选择
- 可以证明当前 DSL 能作为平台统一模型继续演进

如果这些都成立，就说明平台方向正确。
