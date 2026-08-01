# 阶段四：Codex 风格 Agent Panel

状态：`Completed`

## 1. 目标

在现有测试平台中提供统一、持续、上下文感知的 Agent Chat 入口，让用户能够理解 Agent 正在处理什么、调用了什么能力、产生了哪些 Draft，并安全地将结果应用到原生编辑器。

## 2. 设计原则

- Chat 是意图入口，Artifact 是工作结果
- 不创建第二套 Scenario Editor
- 当前 Workspace 和目标资产始终可见
- Tool Activity 清楚但默认折叠
- 重要状态通过事件呈现，不暴露模型内部推理
- 用户可以停止、拒绝、重试和应用结果
- UI 保持现有平台安静、紧凑、工作台化的风格

## 3. 入口与布局

- [x] 顶部工作区栏增加 Test Agent 图标按钮
- [x] 点击后打开右侧可调整宽度的 Panel
- [x] Panel 默认不遮挡当前 Scenario 主工作区
- [x] 支持固定、展开和关闭
- [x] 切换 Workspace 前沿用未保存状态检查
- [x] Agent Task 与 Workspace 隔离

Panel 结构：

```text
Header
  New Chat / History / Running Status / Stop
Context
  Workspace / Scenario / Step / Operation / Knowledge
Conversation
  User / Agent / Activity / Artifact
Composer
  Input / Send
```

## 4. Session 管理

- [x] 新建 Chat
- [x] 当前 Workspace 的 Chat History
- [x] 打开历史 Task
- [x] 自动生成并允许修改标题
- [x] 显示 Running、Waiting、Completed、Failed、Cancelled
- [x] 运行中的 Task 可以停止
- [x] 不同 Task 的 Context 和事件不混合

## 5. Context 展示

- [x] 自动绑定当前打开的 Scenario 或其他资产
- [x] Context 以紧凑条目展示
- [x] 用户可以移除自动上下文
- [x] 用户可以从当前 Workspace 添加 Scenario、Suite、Operation 或 Execution
- [x] 显示匹配的 Knowledge 文档数量
- [x] 切换 Tab 时不静默改变正在运行 Task 的 Context

## 6. Conversation 与 Activity

- [x] 流式展示 Agent 文本
- [x] 支持消息发送中、完成、失败和重试状态
- [x] Plan 使用简洁步骤列表
- [x] Skill 加载显示 Skill 名称和用途
- [x] Tool Activity 显示工具名和状态
- [x] 敏感输入和原始大对象不在 Chat 中展开
- [x] Activity 默认折叠，可按需查看
- [x] 不展示隐藏 Chain of Thought

## 7. Artifact UI

支持：

- Command Plan
- Scenario Draft
- Structured Diff
- Validation Result
- Knowledge Citations

- [x] Artifact 以紧凑行或面板显示
- [x] Diff 区分新增、修改、删除和移动
- [x] Validation 显示 Error、Warning 和 Information
- [x] Citation 可打开对应 Test Knowledge Tab
- [x] `Open Draft` 打开平台原生 Scenario Editor
- [x] `Apply to editor` 将 Draft 放入编辑器工作副本
- [x] 应用后 Scenario Tab 标记为未保存
- [x] 用户仍通过原有 Save 按钮正式保存

## 8. 前端状态与事件

- [x] 使用统一 `AgentEvent` 客户端类型
- [x] SSE 断线后使用最后 `sequence` 恢复
- [x] 重复事件不重复渲染
- [x] 页面刷新后可恢复 Task、消息和 Artifact
- [x] Task 结束后关闭 SSE
- [x] Workspace 切换时关闭旧连接

## 9. 错误体验

- [x] 模型未配置时显示明确状态，不展示堆栈
- [x] 模型超时允许 Retry
- [x] Tool 被拒绝时说明权限原因
- [x] Revision 冲突时要求刷新或重新生成 Draft
- [x] Validation 失败时保留 Draft 和错误详情
- [x] 用户取消后保留已完成的事件和 Artifact

## 10. 测试要求

- [x] Agent Panel 打开、关闭和固定测试
- [x] 新建、恢复和切换 Task 测试
- [x] SSE 流式消息测试
- [x] 断线重连和事件去重测试
- [x] Context 固定和切换测试
- [x] Stop、Retry 和失败状态测试
- [x] Apply to editor 与未保存保护测试
- [x] 桌面和常用窄屏视口截图检查
- [x] 文本、按钮和活动记录无重叠检查

## 11. 验收场景

用户打开 Scenario，通过 Panel 输入首个验收目标后，应能看到：

1. 当前 Scenario 和 Operation Context
2. Agent 流式响应
3. `scenario-authoring` Skill 已加载
4. 读取 Scenario、Operation 和 Knowledge 的 Tool Activity
5. Draft、Diff 和 Validation Artifact
6. 点击 `Apply to editor` 后现有 Scenario Editor 更新并标记未保存
7. 用户手动 Save 后正式 Scenario 更新

## 12. 完成定义

- [x] 用户无需离开 Scenario 页面即可完成 V1 闭环
- [x] Chat、Activity 和 Artifact 层次清楚
- [x] 不暴露模型、MCP 和 Tool Schema 等底层复杂度
- [x] Agent 不直接保存正式资产
- [x] 刷新和断线后 Task 可恢复
- [x] 前端构建、Lint 和浏览器验收通过

## 13. 实现记录

2026-08-01 已完成 V1 Agent Panel 闭环：

- Task 使用持久化 `ContextSelection` 保存用户附加和排除的上下文，目标 Scenario 始终锁定
- Context Builder 支持同 Workspace 的 Scenario、Suite、Operation 和 Execution，并继续执行后端归属校验与脱敏
- 自动关联的 Operation 和 Published Knowledge 可以在创建 Chat 前移除，Chat 创建后 Context 固定
- Command Plan 使用编号步骤展示，Knowledge Citation 打开平台原生 Test Knowledge Tab
- 服务端在 Terminal Event 提交后关闭 SSE，客户端在 `COMPLETED`、`FAILED` 或 `CANCELLED` 后停止连接
- Vitest、Testing Library 和可控 EventSource 覆盖 Panel、History、Context、SSE、Stop、Retry、Artifact 与 Draft Apply
- PostgreSQL 16.14 上已通过 Flyway V20 migration 和真实 Agent Task Context API 冒烟验证
