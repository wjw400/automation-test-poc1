# Test Agent 用例生成与续跑修复方案

状态：`P0 Implemented / Live Verification Pending`

日期：`2026-10-19`

## 1. 目标与问题证据

本方案针对 2026-10-19 日志中 Suite Agent 持续生成失败的问题。目标是让模型拿到可执行的工具协议，并在暂停后从已持久化的草稿继续，而不是反复重做原始需求。

已确认的链路：

```text
查询 Scenario / Operation 成功
  → 创建 Scenario 时猜测 YAML 结构
  → 修改 Scenario 时猜测 Command 名称和字段
  → 连续校验失败或用尽 40 次工具调用
  → 暂停，用户选择继续
  → 新 Turn 只有原用户消息，重新查询并重复相似错误
```

具体证据：

| 现象 | 实现缺口 | 影响 |
| --- | --- | --- |
| `id`、`operationRefId`、`method` 在错误层级出现 | 创建工具只声明 `content: string`，要求模型自行编写完整 YAML | 模型用解析器错误逐字段试探 DSL |
| `UpdateStep`、`SetStep`、`UpdateScenarioMetadata` 不支持 | 新 Scenario 修改工具将 `commandPlan` 声明为无结构 object | 命令名称、字段和 revision 均缺少明确约束 |
| `Expected a non-blank string` | 工具返回丢弃 `ScenarioCommandException.path/commandIndex` | 模型无法定位需要修正的字段 |
| `continuation=true`，每轮 `messages=1` | 续跑只重建对话消息；Context 不载入已有草稿和失败进度 | 已保存副作用与模型认知脱节 |
| 同一 Turn 多次创建成功仍继续创建 | 生成、修订、待确认缺少明确停止规则；无草稿读取工具 | 草稿重复或被替换，用户难以判断成果 |

`NO_PROGRESS` 和调用上限是保护触发结果，不能通过单纯增大额度解决。SSE 断开后后台仍然继续调用工具，本次不将 SSE 重构作为前置工作。

## 2. 设计决策

### 2.1 用结构化工具输入替代模型编写 YAML

模型调用 `createNewScenarioDraft` 时提供 `name`、可选 `description/tags/variables` 和结构化 `steps`。后端固定 DSL 版本，转换成已有领域对象，再由 `ScenarioDslCodec` 序列化为 YAML。

- 复用现有 Scenario Command 中的 HTTP Step 定义、状态码规则、请求结构、断言和提取规则，避免两套协议漂移。
- `operation` 复制 `getApiOperationSchema` 的 `dslOperation`；平台引用放在 `operation.id`，不放在 Step 或 Request 根层级。
- 稳定 Step ID 由平台维护，不混入 Case DSL。
- 工具返回草稿 ID、revision、步骤摘要、Validation 和 Diff；创建成功仅表示 Draft 已存在，不等同于可发布。
- 现有 REST / 编辑器 YAML 输入可以保留。兼容期内旧内部调用可继续接受 `content`，但不再向模型推荐该路径；同时传新旧格式应拒绝，避免歧义。
- 校验失败的草稿必须能读取和修订，不要求为了修正内容再次创建。

示例（Operation 值来自平台工具，示例 ID 仅用于说明）：

```json
{
  "name": "创建并查询订单",
  "variables": {"customerId": "test-customer"},
  "steps": [{
    "name": "创建订单",
    "type": "http",
    "operation": {
      "id": "operation-ref-from-tool",
      "apiSpecId": "api-spec-ref-from-tool",
      "operationId": "createOrder",
      "method": "POST",
      "path": "/orders"
    },
    "request": {"body": {"customerId": "${customerId}"}},
    "response": {
      "status": {"operator": "equals", "expected": 201},
      "extract": {"orderId": {"source": "response.body", "path": "$.id"}}
    }
  }]
}
```

### 2.2 共享 Scenario 命令协议与编写规则

`applyNewScenarioCommands` 和 `applyScenarioCommands` 使用同一份输入 Schema，包括 `targetType/targetId/baseRevision/commands`、允许的命令名称及各字段含义。

Suite Agent 增加自有新 Scenario Draft 的读取、校验、Diff 和数据源查询能力。授权范围必须区分：

- 现有项目 Scenario：允许发现、读取，不能修改其内容。
- 当前 Task 创建、来源为当前 Suite 的新 Scenario Draft：允许读取和修订。
- 其他 Task、Project 或 Suite 的草稿：拒绝访问。

将 DSL 编写规则提取为可共享说明，覆盖状态码、Operation 位置、变量引用、断言与提取。不能简单给 Suite Task 加载完整 `scenario-authoring` 并要求其调用不存在的 `getScenario/createScenarioDraft`。

### 2.3 返回可修复的错误

统一工具错误保留：

```json
{
  "ok": false,
  "error": {
    "code": "INVALID_COMMAND_FIELD",
    "message": "Expected a non-blank string",
    "path": "plan.commands[0].stepId",
    "commandIndex": 0
  }
}
```

- 路径、命令序号和消息同时进入工具结果及审计事件，经过脱敏。
- 未知命令引导模型使用已声明的命令；revision 冲突引导先重新读取草稿。
- 相同错误的连续修正仍不成功时停止盲试，保留草稿并说明阻塞点。
- 运行限制异常应继续传播给协调器，不能误包装为可无限重试的普通输入错误。

### 2.4 续跑恢复真实资源状态

每轮准备时加载当前 Task 的草稿摘要；续跑时额外提供上一轮暂停原因、最近工具失败和用户已确认的保存结果。采用平台确定性摘要，不依赖模型另写一段总结成功后才能恢复。

恢复上下文包含：

- 原始目标和当前权限边界。
- 草稿 ID、类型、状态、来源、当前 revision、步骤摘要及校验结果。
- 新草稿发布后获得的正式 `scenarioId`，以及是否已经加入 Suite。
- 最近失败的工具名、错误码、字段路径和错误消息。
- 下一步规则：先读取需要修改的草稿；已通过校验且满足需求时结束生成、等待确认；已发布资源不得重复创建。

实现约束：

1. 不清除暂停信息后才尝试构建恢复上下文；先读取必要状态，或者通过持久化暂停事件恢复。
2. 资源数据库是当前 revision 和状态的事实来源，Artifact 是审计与发现线索。
3. 摘要限条数、限字符数，详细内容按需读，不重放所有历史工具输出和完整 OpenAPI。
4. 草稿、业务描述和工具数据仍属于数据，不能因放入续跑上下文而获得系统指令权限。
5. 用户再次发送新请求也能看见本 Task 已有草稿，避免仅修复 continuation 分支。
6. 服务重启后能从持久化资源和事件恢复，不依赖内存中尚未结束的模型会话。

### 2.5 创建和修改幂等

模型成功创建草稿后应使用其 ID 修订。后端也要防止重新创建导致已做修改被覆盖：

- 重复请求可返回已有草稿，并明确说明已复用。
- 同 Task 内的同一逻辑草稿使用稳定标识；名称只能作为兼容期辅助匹配，不能作为跨项目全局主键。
- 新创建请求与已有草稿内容不同时，返回冲突和已有草稿引用，要求走修改工具，避免静默丢弃旧草稿。
- 继续使用现有 Command Plan fingerprint 与 revision 冲突检查，不重复应用已生效的命令。

稳定创建键及并发唯一约束可作为后续增强；本次核心修复不能声称仅凭提示词即可保证幂等。

## 3. 用户交互

```text
理解当前 Suite
  → 搜索可复用 Scenario
  → 存在：准备 Suite Draft
  → 不存在：读取必要 Operation，结构化创建 Scenario Draft
      → 校验失败：修订同一草稿
      → 校验成功：展示名称、步骤、校验和确认操作，结束本轮生成
  → 用户确认后才保存正式资源
```

保留当前已实现的“创建 Scenario”和“创建并加入当前 Suite”显式确认入口。后者已经是用户选择的组合操作，应继续复用 `publishNew` 的事务及 Suite revision 校验；本次不额外引入通用 Change Set 引擎，也不要求为解决生成失败增加确认层级。

暂停与待确认草稿分别表达：

- 有可审查草稿：优先引导查看和确认现有成果。
- 确实还需模型工作：提供继续与停止，继续恢复现有状态。
- 用户不需要输入 UUID、YAML 或工具调用参数来帮助 agent 脱困。
- 多个有效草稿按资源分别呈现，不能只因 Artifact 排序只显示最后一个。该展示优化与后端可靠性修复分开验收。

## 4. 实施顺序与代码位置

| 顺序 | 变更 | 主要位置 | 验收结果 |
| --- | --- | --- | --- |
| P0-1 | 结构化创建；共享命令 Schema | `AgentToolRegistry`、`ScenarioDslCodec` | 模型能够通过已声明结构生成可校验用例 |
| P0-2 | 草稿读取、校验与数据源授权 | `AgentToolRegistry`、`AgentToolPolicy` | Suite Agent 能修订自有新草稿，越界访问被拒绝 |
| P0-3 | 精确错误返回 | `DeepSeekAgentRuntime` | 模型收到错误路径与命令序号 |
| P0-4 | 恢复摘要与当前草稿状态注入 | `AgentWorkContextBuilder`、`AgentContextBuilder`、`AgentTaskService` | 暂停、重建恢复组件或再次请求后不丢失已完成工作 |
| P0-5 | 编写与停止规则 | `suite-authoring`、共享编写规则 | 草稿有效后进入确认流程，不继续试探字段 |
| P1 | 后端重复创建冲突与稳定创建键 | `ScenarioDraftService`、必要的 migration | 重试不能覆盖已有修订；并发不能创建同一逻辑草稿两次 |
| P1 | 按资源显示多草稿、恢复进度说明 | `AgentPanel`、i18n | 用户能找到每个成果和下一步 |

P0 作为最小完整修复单元：工具输入、可修复反馈与恢复状态必须一起验证。P1 独立提交，不能用其未完成来阻塞 P0。

## 5. 测试与验收

### 5.1 自动化回归

- 使用工具声明的结构化输入创建“创建订单 → 查询订单”两步用例，序列化、重新解析、Validation 均成功，且没有写正式 Scenario。
- Suite 新草稿的修改 Schema 与 Scenario 修改工具一致；状态码、断言和提取不再是无结构对象。
- 修改缺少 `stepId` 时，结果包含 `path` 与 `commandIndex`；未知命令保留准确错误码。
- 当前 Task 可以读写自有新草稿；其他 Task、Project、Suite 的草稿及已有 Scenario 草稿均不能借此修改。
- 创建草稿并修改后暂停，续跑拿到相同 Draft ID、最新 revision、最后错误；不增加一条伪造用户消息。
- 发布后的正式 Scenario ID 和已加入 Suite 的事实可被后续 Turn 识别。
- 服务重建后恢复状态仍可用；历史记录过长时恢复上下文大小受限。
- 草稿无效时确认按钮保持不可用，发布接口仍执行最终校验；Suite 并发变化时不静默覆盖。

### 5.2 模型联调

用日志对应需求和相同可用模型做一次人工发起的联调，观察：

1. 工具参数使用已声明的结构，不再连续猜 YAML 字段和命令名称。
2. 首轮产出可验证的草稿；如果暂停，继续读取原草稿并修复。
3. 记录读取/写入/失败次数和最终校验结果，不能仅以助手文字宣称“完成”为通过。
4. 无需为了让测试通过而增加 40 次调用上限。

单元和集成测试验证确定性协议及状态恢复，不能替代真实模型联调。没有执行模型联调时必须明确标记未验证。

## 6. 与已有文档的关系

- 补齐《Test Suite Agent 跨资源发现与创建开发指南》的结构化创建和依赖恢复要求。
- 落实《Test Agent 暂停与继续执行开发指南》中“继续不是重复发送原始消息”的规定。
- 对当前实现已支持的显式“创建并加入 Suite”确认操作，本次以现有事务边界为基础，不要求退回旧文档的分步编辑器流程。
- 本文描述目标及实施边界，完成状态应以实际代码和验收记录为准。

## 7. 本次落地与验证记录

2026-10-19 已完成 P0 核心修复：

- 创建工具向模型声明结构化输入，后端检查关键容器类型、解析并序列化 DSL；不再要求模型编写 YAML。
- 创建与修改共享 HTTP Step、状态码、断言、提取规则定义；修改命令按类型声明必填字段和允许字段。
- Suite Agent 可以读取、校验和修订本 Task 创建的新 Scenario Draft，并按需查看自己的 Suite Draft；不能借此修改已有 Scenario。
- 错误结果、事件和日志保留字段路径及命令序号。
- 每轮加载数据库中的当前工作状态和最近暂停、失败、保存事件；恢复组件不依赖上一轮内存会话。
- 工作摘要最多 12,000 字符，每页 8 个 Scenario Draft；超限压缩详情但保留本页草稿引用。`getTaskWorkState` 支持继续分页发现草稿。
- 原创建请求重试可复用已经修改过的草稿；同名不同内容请求返回已有草稿冲突，不再静默丢弃之前的修改。并发唯一创建键仍属于 P1。
- 更新共享编写规则和 Suite Skill，明确校验成功后进入用户确认、结束生成。

验证结果：63 项针对性自动化回归通过，0 失败、0 错误。包括新增 9 项 Suite Agent 集成测试，以及错误位置返回、既有 Agent API、安全与 Scenario Command 回归。

新增集成测试覆盖：结构化两步用例生成；恢复最新 revision 与最近错误；重复创建保留修订；跨 Task/User/Project/Suite 隔离；正式发布状态恢复；分页；大体积摘要保留草稿引用；Suite Draft 恢复；错误提取格式在创建前拒绝。

真实模型联调尚未执行：当前执行环境未配置 `DEEPSEEK_API_KEY`，且未启用 live test。没有重启用户正在运行的后端。需重启后端后，用原始业务需求检查真实模型生成及续跑效果；自动化测试通过不等于已验证模型行为。

本次没有更改 UI。多草稿同时展示、稳定创建键与并发唯一约束仍为后续 P1 项目。
