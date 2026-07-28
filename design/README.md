# 自动化测试平台 POC 设计文档

本目录用于沉淀“将当前项目演进为自动化测试平台”的设计方案。

## 阅读顺序

1. [平台 POC 总览](C:/Users/blaine/Documents/code/automation-test-poc1/design/platform-poc-overview.md)
2. [领域模型](C:/Users/blaine/Documents/code/automation-test-poc1/design/domain-model.md)
3. [系统架构](C:/Users/blaine/Documents/code/automation-test-poc1/design/system-architecture.md)
4. [MVP 范围与路线](C:/Users/blaine/Documents/code/automation-test-poc1/design/mvp-scope.md)
5. [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)
6. [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)
7. [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)
8. [AI 上下文与安全规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-context-and-security.md)
9. [AI 产品交付规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-product-delivery-guidelines.md)

## 文档拆分原则

- `platform-poc-overview.md`
  平台的目标、边界、设计原则，以及当前仓库代码如何映射到平台能力。
- `domain-model.md`
  平台的核心实体、关系、关键字段建议。
- `system-architecture.md`
  平台的服务划分、执行链路、存储与安全边界。
- `mvp-scope.md`
  第一版做什么、不做什么、交付顺序和主要风险。
- `ai-assistance-boundaries.md`
  Test Agent 可以读取、规划、生成和建议什么，以及不可跨越的数据、安全、权限和执行边界。
- `test-agent-architecture.md`
  Test Agent 产品定位、Agent Task、统一交互入口、总体架构和分阶段路线。
- `test-agent-runtime-governance.md`
  语义 Command、工具副作用分级、授权审批、受控执行循环和停止条件。
- `ai-context-and-security.md`
  AI Context Builder、数据分级、脱敏、工具白名单和审计要求。
- `ai-product-delivery-guidelines.md`
  AI 如何进入现有工作流，以及 Draft、Diff、失败分析和分阶段交付要求。

## 当前设计定位

这组文档面向 POC 阶段，目标是先把平台“核心骨架”定义清楚：

- 以结构化 `test case` 为中心
- 以 `step evidence` 为报告核心
- 支持安全协议场景
- 支持后续 Test Agent 生成、验证和维护测试资产

这组文档不是最终产品规格，而是后续实施、拆任务、评估技术路线的基础。
