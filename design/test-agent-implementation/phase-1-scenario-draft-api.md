# Scenario Draft API

## 1. 定位

Scenario Draft API 是 Test Agent 与正式 Scenario 之间的受控修改边界。它不调用模型，也不保存正式 Scenario。

Draft 创建时保存：

- 正式 Scenario 的 `sourceRevision`
- 创建时的基础 YAML
- 当前 Draft YAML
- 与 YAML 分离的稳定 Step ID
- Draft 自身的 `revision`

Command Plan 必须同时匹配 URL 中的 Draft ID 和当前 Draft Revision。正式 Scenario 或 Draft 已变化时，服务返回 `409 Conflict`。

## 2. 生命周期 API

```text
POST   /api/projects/{projectId}/scenario-drafts
GET    /api/projects/{projectId}/scenario-drafts
GET    /api/scenario-drafts/{draftId}
POST   /api/scenario-drafts/{draftId}/commands
POST   /api/scenario-drafts/{draftId}/validate
GET    /api/scenario-drafts/{draftId}/diff
DELETE /api/scenario-drafts/{draftId}
```

创建 Draft：

```json
{
  "scenarioId": "scenario-id"
}
```

返回的 `steps` 包含 Draft 内稳定引用：

```json
{
  "id": "draft-id",
  "sourceRevision": 4,
  "revision": 0,
  "steps": [
    {
      "id": "draft-step-id",
      "index": 0,
      "name": "Create order"
    }
  ]
}
```

Step ID 不写入 Case DSL YAML。新增、移动和删除 Step 时，Draft Service 会同步维护 ID 与 YAML Step 的对应关系。

## 3. Command Plan

```json
{
  "targetType": "SCENARIO_DRAFT",
  "targetId": "draft-id",
  "baseRevision": 0,
  "commands": [
    {
      "type": "SetRequestHeader",
      "stepId": "draft-step-id",
      "name": "X-Correlation-Id",
      "value": "${requestId}"
    },
    {
      "type": "AddExtraction",
      "stepId": "draft-step-id",
      "name": "orderId",
      "extraction": {
        "source": "response.body",
        "path": "$.data.id"
      }
    }
  ],
  "assumptions": [],
  "warnings": []
}
```

所有 Command 在内存工作副本中按顺序执行。任一 Command 失败时，Draft 内容和 Revision 都不变化。

## 4. Command 白名单

| Command | 必需字段 |
|---|---|
| `SetRequestHeader` | `stepId`, `name`, `value` |
| `RemoveRequestHeader` | `stepId`, `name` |
| `SetQueryParameter` | `stepId`, `name`, `value` |
| `SetPathParameter` | `stepId`, `name`, `value` |
| `SetRequestBody` | `stepId`, `body` |
| `SetRequestBodyValue` | `stepId`, `path`, `value` |
| `AddBeforeAction` | `stepId`, `action` |
| `AddAfterAction` | `stepId`, `action` |
| `AddAssertion` | `stepId`, `assertion` |
| `RemoveAssertion` | `stepId`, `index` |
| `AddExtraction` | `stepId`, `name`, `extraction` |
| `RemoveExtraction` | `stepId`, `name` |
| `AddStep` | `step`; optional `stepId`; optional mutually exclusive placement using `afterStepId` or zero-based `targetIndex` |
| `RemoveStep` | `stepId` |
| `MoveStep` | `stepId`, `targetIndex` |
| `RenameStep` | `stepId`, `name` |
| `SelectOperation` | `stepId`, `operationId` |

`SetRequestBodyValue.path` 使用 JSON Pointer，例如 `/customer/address/city` 或 `/items/0/sku`。

未知 Command、未知字段、嵌入 Profile 或任何 Environment、Secret 修改字段都会被拒绝。

## 5. Validation 和 Diff

Validation 是确定性代码，不依赖模型。当前检查：

- Case DSL v1 结构
- Operation 存在且属于当前 Project
- Operation Snapshot 是否过期
- Path Parameter 是否完整
- Action 类型、Stage、Source、Target 和 Format
- Assertion 和 Extraction 必填字段
- 变量是否在提取前使用
- Draft 是否嵌入 Profile Key Material

Diff 使用稳定 Step ID 和 JSON Pointer：

```json
{
  "type": "ADD",
  "path": "/steps/draft-step-id/request/headers/X-Correlation-Id",
  "stepId": "draft-step-id",
  "before": null,
  "after": "${requestId}"
}
```

## 6. 冲突响应

Draft Revision 过期：

```json
{
  "error": "SCENARIO_DRAFT_CONFLICT",
  "reason": "DRAFT_REVISION_CHANGED",
  "expectedRevision": 0,
  "actualRevision": 1
}
```

正式 Scenario 在 Draft 创建后发生变化时，`reason` 为 `SOURCE_SCENARIO_CHANGED`。调用方必须重新读取 Scenario 并创建新 Draft，不能覆盖正式资产。
