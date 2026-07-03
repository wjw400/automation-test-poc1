# 自动化测试平台 POC 设计文档

本目录用于沉淀“将当前项目演进为自动化测试平台”的设计方案。

## 阅读顺序

1. [平台 POC 总览](C:/Users/blaine/Documents/code/automation-test-poc1/design/platform-poc-overview.md)
2. [领域模型](C:/Users/blaine/Documents/code/automation-test-poc1/design/domain-model.md)
3. [系统架构](C:/Users/blaine/Documents/code/automation-test-poc1/design/system-architecture.md)
4. [MVP 范围与路线](C:/Users/blaine/Documents/code/automation-test-poc1/design/mvp-scope.md)

## 文档拆分原则

- `platform-poc-overview.md`
  平台的目标、边界、设计原则，以及当前仓库代码如何映射到平台能力。
- `domain-model.md`
  平台的核心实体、关系、关键字段建议。
- `system-architecture.md`
  平台的服务划分、执行链路、存储与安全边界。
- `mvp-scope.md`
  第一版做什么、不做什么、交付顺序和主要风险。

## 当前设计定位

这组文档面向 POC 阶段，目标是先把平台“核心骨架”定义清楚：

- 以结构化 `test case` 为中心
- 以 `step evidence` 为报告核心
- 支持安全协议场景
- 支持后续 AI 生成和维护测试场景

这组文档不是最终产品规格，而是后续实施、拆任务、评估技术路线的基础。
