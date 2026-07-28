# AI 产品交付规范

## 1. 文档目的

本文规定 AI 能力如何进入现有产品工作流、失败分析应遵守什么规则，以及 V1 和后续阶段的交付范围。

本规范从属于 [Test Agent 能力边界](C:/Users/blaine/Documents/code/automation-test-poc1/design/ai-assistance-boundaries.md)。任务模型见 [Test Agent 产品与架构设计](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-architecture.md)，语义 Command 和执行治理见 [Test Agent 工具与执行治理](C:/Users/blaine/Documents/code/automation-test-poc1/design/test-agent-runtime-governance.md)。

## 2. 统一入口，原生资产承载结果

平台可以提供统一的 Test Agent Panel，让用户通过自然语言表达跨资产测试目标。Panel 应在页面切换时保持 Agent Task、Conversation、Plan 和 Tool Activity。

同时，AI 应提供嵌入当前工作流的上下文入口：

- API 页面中的解释与覆盖分析
- Add Test Scenario 中的 Generate with AI
- Scenario Editor 中的 Review 和 Propose Change
- Suite 页面中的组合建议
- Execution 页面中的 Failure Analysis

统一 Panel 是 Agent 的入口和过程界面，不是最终资产编辑器。Agent 产出的 Scenario Draft、Suite Draft、Diff 和 Execution Analysis 必须在现有原生页面或 Tab 中打开。

产品交互遵循：

> Chat-first，Artifact-centered。

不得让用户只能从聊天历史中寻找、复制或维护测试资产。

## 3. Draft 必须可辨认

AI 生成内容必须显示：

- `AI Draft` 状态
- 尚未保存
- 生成时间
- 来源和假设
- Validation 状态

AI Draft 应使用现有 Scenario Editor 和 Suite Editor，不建立第二套编辑体验。

通过自然语言修改当前 Scenario 时，Agent 应生成语义 `CommandPlan`，由平台应用到 Draft 并展示 Diff，不操作 UI 控件或直接改写 YAML 文本。

## 4. 修改必须显示 Diff

对已有 Scenario 或 Suite 的建议必须以 Diff 或逐项变更形式展示。

用户应能：

- 接受全部
- 接受部分
- 拒绝全部
- 返回手工编辑

接受建议后仍需要使用普通保存流程。

## 5. 不使用误导性表达

在未经实际执行时，AI 不得使用：

- “测试已经通过”
- “接口没有问题”
- “这是产品缺陷”

应使用：

- “该 Draft 已通过结构校验”
- “根据当前证据，可能原因是……”
- “需要运行或人工确认”

## 6. 失败分析专项规则

失败分析必须按阶段分类：

1. Runtime Context Resolution
2. Request Preparation
3. Before Send Actions
4. HTTP Transport
5. After Receive Actions
6. Assertions
7. Extract

AI 必须优先解释已有证据，不得补造未记录的 Request 或 Response。

AI 给出的建议应区分：

- 产品行为可能错误
- 测试数据可能错误
- Environment/Profile 可能错误
- Scenario 设计可能错误
- 当前证据不足

修改 Expected Value 只能作为最后一种候选建议，并必须说明业务依据不足。

## 7. 测试数据专项规则

AI 生成测试数据时：

- 默认使用明显虚构的名称、邮箱、地址和标识符
- 遵守 OpenAPI Schema 和字段约束
- 标明正常值、边界值或非法值的设计目的
- 不复用 Execution 中的真实敏感值
- 不生成可被误认为真实凭证的 Token 或密钥

生产数据样本必须先由平台完成脱敏和最小化。

## 8. 破坏性 Operation

AI 可以在 Draft 中推荐 `DELETE`、取消、退款等 Operation，但必须：

- 标记为潜在破坏性 Step
- 说明业务影响
- 不选择生产 Environment
- 不自动执行

平台应使用 API 元数据和项目策略进行最终风险分类，不能只依赖 HTTP Method。

## 9. V1 交付范围

允许：

- 通过统一 Test Agent Panel 描述测试目标
- 展示 Agent Plan、Tool Activity 和 Artifacts
- 自然语言生成 Scenario Draft
- Scenario Review
- OpenAPI 覆盖建议
- 脱敏 Execution 失败解释
- 对已有资产生成 Diff

不允许：

- AI 自动保存
- AI 自动执行
- AI 自动修复并重跑
- AI 批量修改
- AI 修改配置和 Secret

## 10. 后续可评估能力

以下能力不属于当前承诺，必须单独设计和评审：

- 在隔离 Sandbox 中自动执行 Draft
- 根据校验错误自动迭代 Draft
- 批量生成候选 Scenario
- 自动维护受 OpenAPI 变更影响的资产
- 有预算和停止条件的 Agent 式测试探索

即使未来开放，也必须满足：

- 明确的非生产 Environment
- 独立权限
- 资源和次数限制
- 可取消
- 完整审计
- 不可访问 Secret 原始值

## 11. AI 功能设计清单

任何 AI 功能进入开发前必须回答：

- 用户当前要完成的具体任务是什么
- AI 为什么比确定性代码更适合这一环节
- AI 能读取哪些数据
- 数据如何脱敏
- AI 输出是说明、Draft 还是 Diff
- 输出使用什么 Schema
- 哪些校验由平台完成
- 用户在哪一步确认
- 是否可能触发保存、删除、配置或执行
- 如何记录来源、假设和审计信息
- 模型失败时用户能否继续手工完成任务

如果以上问题不能明确回答，该功能不应进入实现。

## 12. V1 验收标准

第一项 AI Scenario Draft 能力至少满足：

- 不引用不存在的 Operation
- 输出能够通过 JSON Schema 校验
- 输出能够通过 DSL 语义校验
- 变量提取和引用顺序合法
- 不包含 Secret
- 明确展示来源、假设和警告
- 生成结果作为未保存 Draft 打开
- 用户可以继续使用现有编辑器修改
- 用户拒绝 Draft 后不产生正式资产
- 模型不可用时仍可手工创建 Scenario
