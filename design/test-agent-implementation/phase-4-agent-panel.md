# 阶段四：Codex 风格 Agent Panel

状态：`Not Started`

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

- [ ] 顶部工作区栏增加 Test Agent 图标按钮
- [ ] 点击后打开右侧可调整宽度的 Panel
- [ ] Panel 默认不遮挡当前 Scenario 主工作区
- [ ] 支持固定、展开和关闭
- [ ] 切换 Workspace 前沿用未保存状态检查
- [ ] Agent Task 与 Workspace 隔离

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

- [ ] 新建 Chat
- [ ] 当前 Workspace 的 Chat History
- [ ] 打开历史 Task
- [ ] 自动生成并允许修改标题
- [ ] 显示 Running、Waiting、Completed、Failed、Cancelled
- [ ] 运行中的 Task 可以停止
- [ ] 不同 Task 的 Context 和事件不混合

## 5. Context 展示

- [ ] 自动绑定当前打开的 Scenario 或其他资产
- [ ] Context 以紧凑条目展示
- [ ] 用户可以移除自动上下文
- [ ] 用户可以从当前 Workspace 添加 Scenario、Suite、Operation 或 Execution
- [ ] 显示匹配的 Knowledge 文档数量
- [ ] 切换 Tab 时不静默改变正在运行 Task 的 Context

## 6. Conversation 与 Activity

- [ ] 流式展示 Agent 文本
- [ ] 支持消息发送中、完成、失败和重试状态
- [ ] Plan 使用简洁步骤列表
- [ ] Skill 加载显示 Skill 名称和用途
- [ ] Tool Activity 显示工具名、状态和摘要
- [ ] 敏感输入和原始大对象不在 Chat 中展开
- [ ] Activity 默认折叠，可按需查看
- [ ] 不展示隐藏 Chain of Thought

## 7. Artifact UI

支持：

- Command Plan
- Scenario Draft
- Structured Diff
- Validation Result
- Knowledge Citations

- [ ] Artifact 以紧凑行或面板显示
- [ ] Diff 区分新增、修改、删除和移动
- [ ] Validation 显示 Error、Warning 和 Information
- [ ] Citation 可打开对应 Test Knowledge Tab
- [ ] `Open Draft` 打开平台原生 Scenario Editor
- [ ] `Apply to editor` 将 Draft 放入编辑器工作副本
- [ ] 应用后 Scenario Tab 标记为未保存
- [ ] 用户仍通过原有 Save 按钮正式保存

## 8. 前端状态与事件

- [ ] 使用统一 `AgentEvent` 客户端类型
- [ ] SSE 断线后使用最后 `sequence` 恢复
- [ ] 重复事件不重复渲染
- [ ] 页面刷新后可恢复 Task、消息和 Artifact
- [ ] Task 结束后关闭 SSE
- [ ] Workspace 切换时关闭旧连接

## 9. 错误体验

- [ ] 模型未配置时显示明确状态，不展示堆栈
- [ ] 模型超时允许 Retry
- [ ] Tool 被拒绝时说明权限原因
- [ ] Revision 冲突时要求刷新或重新生成 Draft
- [ ] Validation 失败时保留 Draft 和错误详情
- [ ] 用户取消后保留已完成的事件和 Artifact

## 10. 测试要求

- [ ] Agent Panel 打开、关闭和固定测试
- [ ] 新建、恢复和切换 Task 测试
- [ ] SSE 流式消息测试
- [ ] 断线重连和事件去重测试
- [ ] Context 固定和切换测试
- [ ] Stop、Retry 和失败状态测试
- [ ] Apply to editor 与未保存保护测试
- [ ] 桌面和常用窄屏视口截图检查
- [ ] 文本、按钮和活动记录无重叠检查

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

- [ ] 用户无需离开 Scenario 页面即可完成 V1 闭环
- [ ] Chat、Activity 和 Artifact 层次清楚
- [ ] 不暴露模型、MCP 和 Tool Schema 等底层复杂度
- [ ] Agent 不直接保存正式资产
- [ ] 刷新和断线后 Task 可恢复
- [ ] 前端构建、Lint 和浏览器验收通过
