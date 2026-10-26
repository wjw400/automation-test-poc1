# 自动化测试平台 POC 设计文档

本目录用于沉淀“将当前项目演进为自动化测试平台”的设计方案。

## 阅读顺序

1. [平台 POC 总览](C:/Users/blaine/Documents/code/automation-test-poc1/design/platform-poc-overview.md)
2. [领域模型](C:/Users/blaine/Documents/code/automation-test-poc1/design/domain-model.md)
3. [系统架构](C:/Users/blaine/Documents/code/automation-test-poc1/design/system-architecture.md)
4. [MVP 范围与路线](C:/Users/blaine/Documents/code/automation-test-poc1/design/mvp-scope.md)
5. [Test Agent 产品愿景](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-vision.md)
6. [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)
7. [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)
8. [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)
9. [Test Agent Runtime 技术选型决策](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-technology-decision.md)
10. [Test Agent 分阶段实施计划](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-implementation/README.md)
11. [AI 上下文与安全规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-context-and-security.md)
12. [AI 产品交付规范](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-product-delivery-guidelines.md)
13. [Test Agent 作用域、能力与 UI 布局设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-ui-scope-and-layout.md)
14. [Test Suite Agent 开发指南](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-suite-agent-development-guide.md)
15. [Test Suite Agent 跨资源发现与创建开发指南](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-suite-agent-cross-resource-development-guide.md)
16. [Test Agent 暂停与继续执行开发指南](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-pause-and-continuation-development-guide.md)
17. [Test Agent 用例生成与续跑修复方案](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-authoring-and-continuation-repair-plan.md)
18. [接口 PGP 处理实施说明](operation-processing-implementation.md)

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
- `test-agent-vision.md`
  Test Agent 的目标用户、核心体验、产品原则、能力演进、成功标准和非目标。
- `test-agent-architecture.md`
  Test Agent 产品定位、Agent Task、统一交互入口、总体架构和分阶段路线。
- `test-agent-runtime-governance.md`
  语义 Command、工具副作用分级、授权审批、受控执行循环和停止条件。
- `test-agent-runtime-technology-decision.md`
  Agent Runtime 技术选型，Spring AI 与 OpenCode 的取舍，以及 Skill、MCP 和可替换运行时边界。
- `test-agent-implementation/`
  Test Agent 五阶段实施计划、各阶段任务清单、验收标准和完成门槛。
- `ai-context-and-security.md`
  AI Context Builder、数据分级、脱敏、工具白名单和审计要求。
- `ai-product-delivery-guidelines.md`
  AI 如何进入现有工作流，以及 Draft、Diff、失败分析和分阶段交付要求。
- `test-agent-ui-scope-and-layout.md`
  Project、Scenario 和 Suite Agent 的能力归属、UI 入口、Sidecar/工作 Tab 布局、会话隔离、跨 Scope 交接和验收标准。
- `test-suite-agent-development-guide.md`
  Suite Agent 的能力边界、Suite Draft/Command/Tool/Skill、权限策略、前后端实施顺序、测试和验收标准。
- `test-suite-agent-cross-resource-development-guide.md`
  Suite Agent 按需发现项目 Scenario/API、创建新 Scenario Draft、跨资源依赖、Tool 权限、UI 流程和验收标准。
- `test-agent-pause-and-continuation-development-guide.md`
  Agent 达到内部预算或可恢复运行保护时的暂停状态、统一用户确认、continuation Turn、自动重试、审计和验收标准。
- `test-agent-authoring-and-continuation-repair-plan.md`
  用例生成失败的根因、结构化工具协议、草稿恢复与幂等修复方案，以及本次落地和验证记录。

## 当前设计定位

这组文档面向 POC 阶段，目标是先把平台“核心骨架”定义清楚：

- 以结构化 `test case` 为中心
- 以 `step evidence` 为报告核心
- 支持安全协议场景
- 支持后续 Test Agent 生成、验证和维护测试资产

这组文档不是最终产品规格，而是后续实施、拆任务、评估技术路线的基础。
