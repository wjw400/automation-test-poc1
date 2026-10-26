# Test Agent 作用域、能力与 UI 布局设计

状态：`Design Baseline`

最后更新：`2026-08-29`

## 1. 文档目的

本文是 Test Agent 前端交互与作用域设计的实现依据，供开发人员和代码生成模型使用。

它定义：

- Project、Scenario 和 Suite Chat 的职责边界
- Test Agent 在 UI 中的入口和承载方式
- 对话、上下文、权限和 Artifact 如何隔离
- 跨作用域请求如何升级和交接
- Project Agent 如何展示跨资源 Change Set
- 前端实现顺序与验收标准

如本文与以下文档中的旧 UI 描述冲突，以本文为准：

- `test-agent-architecture.md` 第 7 节
- `test-agent-implementation/phase-4-agent-panel.md` 中关于“全局统一右侧 Panel”的布局描述

本文只覆盖产品交互和 UI 信息架构。Tool 权限、审批、安全和执行限制仍以 `test-agent-runtime-governance.md`、`test-agent-permission-modes.md` 和 `ai-context-and-security.md` 为准。

## 2. 核心结论

Test Agent 使用统一品牌和统一 Chat 组件，但不使用一个全局 Chat 承载所有作用域。

最终布局为：

- Scenario Agent：当前 Scenario 工作 Tab 内的右侧 Sidecar
- Suite Agent：当前 Suite 工作 Tab 内的右侧 Sidecar
- Project Agent：独立的 Project Agent 工作 Tab

核心原则：

> 入口跟随上下文，解释能力允许重叠，修改能力归属于资源，Project Agent 负责跨资源编排。

## 3. 为什么需要三个作用域

三个 Chat 代表不同的工作范围，而不是不同的 Agent Persona。

### 3.1 Scenario Chat

目标是解释、检查和修改一个确定的 Scenario。

它必须始终知道当前 Scenario，不要求用户再次选择目标。其修改结果只能进入当前 Scenario 的 Draft、Diff 和 Validation 流程。

### 3.2 Suite Chat

目标是解释、检查和修改一个确定的 Suite。

它可以读取 Suite 引用的 Scenario，但只拥有当前 Suite 的修改权。它不能在用户不知情的情况下修改被引用的 Scenario。

### 3.3 Project Chat

目标是理解项目级资源关系，完成跨资源分析、创建和修改编排。

Project Agent 可以定位任意项目资源，但不能建立绕过原生资源规则的“万能写入”通道。Scenario 修改仍产生 Scenario Draft，Suite 修改仍产生 Suite Draft，跨资源结果由 Change Set 统一组织。

## 4. 能力归属

解释和查询能力可以重叠，因为用户可能从不同上下文提出相同问题。产生副作用的能力必须有明确归属。

| 能力 | Scenario Chat | Suite Chat | Project Chat |
|---|---|---|---|
| 解释当前资源 | 支持 | 支持 | 支持 |
| 查询关联资源 | 按需读取 | 按需读取 | 全项目按需搜索和读取 |
| 修改 Scenario | 仅当前 Scenario | 不直接修改 | 编排对应 Scenario Draft |
| 修改 Suite | 不支持 | 仅当前 Suite | 编排对应 Suite Draft |
| 创建 Scenario 或 Suite | 默认不提供 | 可建议，不直接创建 | 支持，生成 Draft |
| 删除、移动和批量重命名 | 不支持 | 不支持 | 仅在相应审批规则允许时支持 |
| 跨资源覆盖分析 | 很有限 | Suite 范围 | 支持 |
| 跨资源批量修改 | 不支持 | 不支持 | 通过 Change Set 支持 |
| 运行测试 | 当前 Scenario，受执行政策限制 | 当前 Suite，受执行政策限制 | 选择明确目标后，受执行政策限制 |
| 查看执行证据 | 当前 Scenario | 当前 Suite | 按需检索项目执行记录 |

实现时不要仅通过前端隐藏按钮控制边界。后端 Tool Policy 必须根据 Agent Task 的 Scope 再次校验目标资源和副作用。

## 5. 统一品牌与分布式入口

所有入口统一使用 `Test Agent` 品牌、图标、消息样式、Composer、Activity 和 Artifact 组件。

不在首次打开时弹出 `Project / Scenario / Suite` 类型选择器。用户所在位置已经表达了主要意图。

### 5.1 Project 入口

项目顶部或项目导航中的 `Test Agent` 按钮代表 Project Agent。

点击行为：

1. 如果当前工作区尚未打开 Project Agent Tab，则创建并激活一个 Project Agent 工作 Tab。
2. 如果已经打开，则激活已有 Tab。
3. 不重复创建相同的 Project Agent Tab。
4. Project Agent Tab 可以关闭；关闭不会删除 Chat History。

### 5.2 Scenario 入口

Scenario Tab 工具栏中的 `Ask Agent` 按钮打开或收起当前 Tab 的 Scenario Agent Sidecar。

入口自动绑定当前 Scenario。用户不需要先选择 Scenario、模型或审批模式。

### 5.3 Suite 入口

Suite Tab 工具栏中的 `Ask Agent` 按钮打开或收起当前 Tab 的 Suite Agent Sidecar。

入口自动绑定当前 Suite。Suite 中选中某个 Scenario 行不会改变 Chat Scope。

## 6. 工作区布局

### 6.1 Scenario Agent Sidecar

Scenario Agent 是 Scenario 编辑器的一部分：

```text
┌──────────────────────────────────────────────────────────┐
│ Scenario: Create order                       Ask Agent    │
├──────────────────────────────────┬───────────────────────┤
│ Scenario Editor                  │ Test Agent            │
│                                  │ Scenario · Create     │
│ Steps                            ├───────────────────────┤
│ Variables                        │ Conversation          │
│ Tags                             │ Activity / Artifacts  │
│ Description                      │                       │
│                                  ├───────────────────────┤
│                                  │ Message...       Send │
└──────────────────────────────────┴───────────────────────┘
```

要求：

- Sidecar 在当前 Scenario Tab 内，不是应用级全局浮层。
- 默认宽度建议为 `400px`，允许在 `340px` 到当前工作区宽度 `45%` 之间调整。
- 展开 Sidecar 时 Scenario Editor 保持可见。
- 收起 Sidecar 不取消运行中的 Agent Task。
- Draft 应用后直接更新左侧原生 Scenario Editor 工作副本。
- 正式保存仍由 Scenario Editor 的保存流程完成。

### 6.2 Suite Agent Sidecar

Suite Agent 与 Scenario Agent 使用相同 Sidecar 骨架：

```text
┌──────────────────────────────────┬───────────────────────┐
│ Suite Editor / Executions        │ Test Agent            │
│                                  │ Suite · Regression    │
│ Scenario order                   │ Conversation          │
│ Variable overrides               │ Activity / Artifacts  │
│ Execution history                │                       │
└──────────────────────────────────┴───────────────────────┘
```

要求：

- Chat Scope 始终是当前 Suite。
- Suite 中引用的 Scenario 只作为可读关联资源。
- 请求修改某个 Scenario 时显示 `Open Scenario Agent` 或 `Continue in Project Agent`。
- Suite Draft 应用到原生 Suite Editor，不在 Chat 内创建第二套 Suite 编辑器。

### 6.3 Project Agent 工作 Tab

Project Agent 需要展示跨资源上下文和 Change Set，因此使用独立工作 Tab：

```text
[Create order] [Order regression] [Project Agent]

┌────────────────────────────────┬─────────────────────────┐
│ Conversation                   │ Working Context         │
│                                │ Referenced Resources    │
│ User / Agent / Activity        │ Plans                   │
│                                │ Drafts and Validation   │
│ Composer                       │ Change Set              │
└────────────────────────────────┴─────────────────────────┘
```

要求：

- Project Agent 是普通可切换、可关闭的工作 Tab，而不是永久占用页面的全局右侧 Panel。
- 左侧以 Conversation 为主，右侧展示 Working Context、Plan 和 Artifacts。
- 右侧区域没有内容时保持安静，不显示大量空状态卡片。
- Project Agent 不复制 Scenario Editor 或 Suite Editor。
- 点击具体 Draft 或资源，在现有工作区打开对应原生 Tab。

## 7. Chat Header

三个作用域共用紧凑 Header，但必须清楚显示当前作用域和目标。

推荐显示：

```text
Test Agent
Scenario · Create order
```

```text
Test Agent
Suite · Order regression
```

```text
Test Agent
Project · Order fulfillment
```

Header 可以包含：

- Scope 和目标名称
- 当前模型
- 当前审批模式
- New Chat
- History
- Stop，仅运行中显示
- More 菜单，包括重命名和删除 Chat
- Sidecar 特有的关闭按钮

不要在 Header 中显示内部 ID、Runtime 实现名、Skill 列表或 Tool 数量。

创建 Chat 时不要求用户预先选择模型或审批模式。后端使用默认值创建 Task；Task 创建后，模型和审批模式可以在非运行状态下切换。

## 8. Scope 与会话规则

### 8.1 Scope 固定

Chat 创建后 Scope 固定：

- Scenario Chat 永远指向创建时的 Scenario
- Suite Chat 永远指向创建时的 Suite
- Project Chat 永远属于当前 Project

模型和审批模式可以改变，Scope 不允许通过普通设置直接改变。

### 8.2 History 隔离

History 按 Scope 隔离：

- Scenario History 只显示该 Scenario 的 Chat
- Suite History 只显示该 Suite 的 Chat
- Project History 只显示该 Project 的 Project Chat

禁止将三种 History 混成一个默认列表。可以在高级搜索中跨 Scope 查找，但结果必须显示 Scope 和目标名称。

### 8.3 Tab 状态

每个打开的 Scenario 或 Suite Tab 独立保存：

- Sidecar 是否展开
- Sidecar 宽度
- 当前选中的 Chat Task
- Composer 草稿
- 未读 Agent 结果
- 运行状态
- 待审核 Artifact

切换工作 Tab 时，当前 Agent Task 不被取消。返回原 Tab 后恢复原状态。

关闭工作 Tab 不等于删除 Chat。只有显式执行 `Delete Chat` 才删除会话和对应 Agent Artifact。

## 9. Tab 状态提示

工作 Tab 可以使用克制的状态点提示 Agent 状态：

- 蓝点：Agent 正在运行
- 橙点：存在待审核 Draft 或 Change Set
- 普通中性点：存在未读结果

不要在多个工作 Tab 标题中显示持续旋转的 Loading 图标，也不要把完整状态文本塞入 Tab 标题。

状态点必须提供 Tooltip 和无障碍名称，不能只依赖颜色表达含义。

## 10. 越界请求与交接

Agent 不得静默扩大 Scope。

### 10.1 Scenario 或 Suite Chat 收到越界请求

例如用户在 Scenario Chat 中要求同时修改多个 Suite，当前 Agent 应：

1. 说明该请求超出当前 Scenario Scope。
2. 提供 `Continue in Project Agent` 命令。
3. 不在当前 Task 中调用跨资源写 Tool。

### 10.2 Continue in Project Agent

用户确认后：

1. 打开或激活 Project Agent Tab。
2. 创建新的 Project Chat，而不是改变原 Chat Scope。
3. 携带原用户请求、来源 Task ID、当前资源引用和最小必要摘要。
4. 不复制隐藏推理、无关历史或未经授权的上下文。
5. 在新 Chat 中显示来源，例如 `Continued from Scenario · Create order`。

反向进入更窄 Scope 时使用 `Open Scenario Agent` 或 `Open Suite Agent`，不复用 Project Chat 的 Task ID。

## 11. Project Change Set

Project Agent 的跨资源修改必须形成结构化 Change Set。

示例：

```text
Change Set
3 resources affected

+ Scenario Draft: Cancel paid order
~ Suite Draft: Order regression
~ Test Knowledge Draft: Cancellation policy
```

Change Set 至少包含：

- 受影响资源及资源类型
- 每个资源的动作类型：Create、Update、Move 或 Delete
- 每个 Draft 的 Validation 状态
- 每个资源的 Diff 摘要
- 资源之间的依赖关系
- 需要额外审批的动作
- Apply、Reject 和 Open in Editor 操作

应用规则：

- Project Agent 不直接写正式资产。
- 每个资源继续使用自己的 Command、Draft、Validation 和保存机制。
- 用户可以逐项应用，也可以在权限允许时批准整个 Change Set。
- 部分应用后必须显示每项结果，不能把 Change Set 简单标记为全部成功。

## 12. Context 策略

Scope 决定默认上下文，不等于把整个 Scope 的全部数据发送给模型。

- Scenario Chat 默认包含当前 Scenario，关联 Operation、Knowledge 和 Execution 按需读取。
- Suite Chat 默认包含当前 Suite，引用 Scenario 的摘要或完整内容按需读取。
- Project Chat 默认只包含 Project 标识、能力说明和用户明确选择的引用；其他资源通过搜索和读取 Tool 渐进加载。

UI 应显示 Agent 已引用的资源，但不要默认把所有 Project 资源平铺成巨大的 Context 列表。

Chat 创建后，自动目标 Scope 锁定；用户附加的可读 Context 是否允许继续编辑，必须遵守 Agent Context 和权限设计，不得改变写入目标。

## 13. Shared Components

前端应复用以下共享组件，不为三个 Scope 各写一套 Chat：

- `AgentConversation`
- `AgentComposer`
- `AgentHeader`
- `AgentHistory`
- `AgentActivity`
- `AgentArtifacts`
- `AgentModelSelector`
- `AgentPermissionSelector`
- `AgentContextSummary`

容器组件负责布局和 Scope：

- `ScenarioAgentSidecar`
- `SuiteAgentSidecar`
- `ProjectAgentWorkspace`

共享 Chat 组件不得自行推断当前路由作为写入 Scope。明确的 `AgentScope` 必须从容器传入，并且与后端 Task 返回的 Scope 一致。

建议的前端类型：

```ts
type AgentScope =
  | { type: 'PROJECT'; projectId: string }
  | { type: 'SCENARIO'; projectId: string; scenarioId: string }
  | { type: 'SUITE'; projectId: string; suiteId: string }
```

## 14. 响应式行为

桌面端优先实现分栏布局。

- 宽屏：Scenario/Suite 使用可调整宽度 Sidecar；Project Agent 使用双栏工作区。
- 中等宽度：Sidecar 可以覆盖部分编辑器，但必须保留明确关闭入口。
- 窄屏：对象级 Agent 使用当前 Tab 内的全宽临时层；关闭后回到原编辑器位置。
- Project Agent 在窄屏下将右侧 Working Context 折叠为抽屉或次级面板。

无论尺寸如何，不允许 Conversation、Composer 和 Artifact 操作互相遮挡。

## 15. 不采用的方案

### 15.1 一个全局右侧 Panel 承载全部 Chat

不采用。它会模糊当前 Scope，且 Project Change Set 无法在狭窄空间中清楚展示。

### 15.2 同时提供 Project Agent Panel 和 Project Agent Tab

不采用。两个完整入口会造成 History、Context 和 Artifact 归属混乱。

### 15.3 在 Scenario/Suite 内使用 `Editor / Agent` 互斥子 Tab

不采用。用户无法边查看原生编辑器边与 Agent 协作。

### 15.4 Chat 自动跟随当前选中资源改变 Scope

不采用。切换选中项可能使 Agent 修改错误资产。Scope 在 Task 创建后必须固定。

## 16. 实施顺序

后续模型应按以下顺序实施，不要一次重写全部 Agent 前后端：

1. 从现有 `AgentPanel` 提取与布局无关的 Conversation、Composer、Header、History、Activity 和 Artifact 组件。
2. 将 Scenario Agent 挂载到 Scenario 工作 Tab 的 Sidecar 容器，并保持现有 Draft Apply 闭环。
3. 将 Suite Agent 挂载到 Suite 工作 Tab 的 Sidecar 容器，先保持只读分析，再接入 Suite Draft 能力。
4. 新增唯一的 Project Agent 工作 Tab，并让项目级入口只负责打开或激活它。
5. 按 Scope 隔离 History、Composer、本地 UI 状态和事件连接。
6. 实现 `Continue in Project Agent` 交接协议。
7. 实现 Project Change Set Artifact 和跨资源 Draft 编排。
8. 删除已被替代的全局 Agent Panel 状态和重复入口。

每一步都必须保持手工 Scenario、Suite 和 Execution 工作流可用。

## 17. 验收标准

### 17.1 Scenario

- 从 Scenario Tab 点击 `Ask Agent` 后只打开当前 Tab 的 Sidecar。
- Header 明确显示当前 Scenario。
- 切换到另一 Scenario Tab 不复用当前 Chat。
- Agent Draft 可以应用到当前 Scenario Editor。
- 越界修改不会在当前 Task 中执行。

### 17.2 Suite

- 从 Suite Tab 点击 `Ask Agent` 后只打开当前 Tab 的 Sidecar。
- Header 明确显示当前 Suite。
- Suite 内选中 Scenario 不改变 Chat Scope。
- Suite Agent 不直接修改引用的 Scenario。

### 17.3 Project

- 项目级 `Test Agent` 入口打开或激活唯一 Project Agent Tab。
- Project Agent 不占用全局右侧 Panel。
- 跨资源修改形成 Change Set。
- 每项资源修改都有独立 Draft、Diff 和 Validation。

### 17.4 Session 与状态

- 三种 Scope 的 History 不混合。
- Scope 创建后不可直接改变。
- 模型和审批模式可在非运行状态切换。
- 工作 Tab 切换后运行继续，返回时状态恢复。
- 同一时刻当前页面只显示一个 Agent 工作表面。

### 17.5 测试

- 覆盖入口路由和 Project Agent Tab 去重。
- 覆盖 Scenario/Suite Sidecar 独立状态。
- 覆盖 History Scope 过滤。
- 覆盖跨 Scope 交接。
- 覆盖运行、未读和待审核 Tab 状态。
- 覆盖桌面、中等宽度和窄屏布局。
- 验证 Agent UI 不遮挡 Save、Run、Draft Apply 和主要编辑控件。

## 18. 实现约束

- 使用现有 Ant Design 组件和平台设计 Token。
- 保持安静、紧凑、工作台化，不使用营销式卡片和装饰背景。
- 不创建第二套 Scenario 或 Suite 编辑器。
- 不让前端显示状态代替后端权限校验。
- 不把原始 Prompt、隐藏推理、秘密或完整敏感 Evidence 展示在 UI。
- 不因 Agent 不可用而阻塞手工测试工作流。

## 19. 实现状态

### 2026-08-29：UI 作用域承载第一阶段完成

已完成：

- 移除应用级全局 Agent Panel 承载方式。
- Scenario 和 Suite Agent 改为各自工作 Tab 内的可调整 Sidecar。
- Sidecar 打开状态保存在工作 Tab 状态中；切换 Tab 后保持原会话和面板状态。
- 项目顶部 `Test Agent` 入口改为打开或激活唯一的 Project Agent 工作 Tab。
- Project Agent Tab 与 Scenario/Suite Tab 共用工作区 Tab Strip，但使用独立会话 Scope。
- 保留现有 Agent Task、History、Draft、Validation、模型与审批模式逻辑。

后续阶段：

- Suite Draft 和 Suite Agent 的写入能力。
- `Continue in Project Agent` 的受控交接协议。
- Project Change Set 的跨资源 Draft 编排与逐项应用界面。
- Agent 运行、待审核 Draft 和未读结果的工作 Tab 状态点。
