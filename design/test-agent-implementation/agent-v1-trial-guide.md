# Test Agent V1 试用指南

## 1. 试用目标

V1 验证一条受控闭环：用户在 Scenario 中描述修改目标，Agent 读取受限上下文并生成 Scenario Draft，平台执行确定性校验和 Diff，用户确认后应用到编辑器，最后仍由用户手动保存和运行。

Agent 当前不能直接保存正式 Scenario、运行 Scenario 或 Suite、读取 Profile 密钥，也不能访问其他 Workspace 的资产。

## 2. Runtime 配置

未配置模型时使用 Fake Runtime：

```text
AGENT_DEFAULT_RUNTIME=FAKE
```

接入 DeepSeek 时在 `platform-api` 的服务端启动环境中配置：

```text
AGENT_DEFAULT_RUNTIME=DEEPSEEK
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_API_KEY=<server-secret>
DEEPSEEK_MODEL=deepseek-chat
```

可选治理参数：

```text
AGENT_MAX_TURNS=20
AGENT_MAX_TOOL_CALLS=20
AGENT_MAX_REPEATED_TOOL_CALLS=3
AGENT_MAX_NO_PROGRESS_TOOL_CALLS=3
AGENT_RUN_TIMEOUT=2h
AGENT_INACTIVITY_TIMEOUT=5m
AGENT_MODEL_REQUEST_TIMEOUT=3m
AGENT_TOOL_DEFAULT_TIMEOUT=30s
```

`DEEPSEEK_API_KEY` 只能出现在服务端环境变量或 Secret Manager 中，不得写入 Scenario、Knowledge、浏览器存储或前端构建变量。

## 3. 准备数据

试用前需要在同一 Workspace 中准备：

- 已导入的 OpenAPI Operation
- 至少包含一个 HTTP Step 的 Scenario
- 安全场景所需的 Environment 和 Profile
- 与 Scenario、Operation、API Spec 或 Workspace 关联的已发布 Test Knowledge

普通示例 Knowledge：

```markdown
# Order response contract

Create order returns the order identifier at `$.orderId`.
Every request should carry `X-Correlation-Id` from `${correlationId}`.
```

安全示例 Knowledge：

```markdown
# Secure order protocol

Before sending, sign and encrypt `request.body`, Base64 encode the encrypted text,
then build a JSON body containing `meta` and `encryptedPayload`.
After receiving, decrypt and verify `$.payload` into `decrypted.securePayload`.
Assert plaintext `$.code` and decrypted `$.status` separately.
Actions inherit the Scenario or Step Profile.
```

Knowledge 只能描述协议和业务规则，不能存放私钥、Passphrase、Token 或密码。

## 4. 两个验收场景

### 基础修改

在目标 Scenario 中打开 Agent Panel，发送：

> 给当前请求增加 `X-Correlation-Id`，并从响应中提取订单号。

Fake Runtime 的确定性验收输入为：

```text
[draft-demo] Add X-Correlation-Id and extract orderId
```

预期看到 Scenario、Operation 和 Knowledge 的读取活动，随后生成 Command Plan、Validation 和 Diff。点击 Apply 后只更新编辑器，Save 前正式 Scenario 不变。

### 安全协议

发送：

> 这个接口发送前需要加密和签名，请根据项目协议修改请求，并验证响应中加密字段解密后的订单状态。

Fake Runtime 的确定性验收输入为：

```text
[secure-demo] Add secure request and response processing
```

预期 Draft 包含 `crypto.pgp.signAndEncrypt`、`data.encodeBase64`、`body.buildJson` 和 `crypto.pgp.decryptAndVerify`，同时包含明文字段与解密结果断言，并显示 Knowledge Citation。Agent 输出和审计中不应出现任何密钥或 Passphrase。

## 5. 人工决策与反馈

- Apply：仅把有效 Draft 应用到当前 Scenario 编辑器。
- Save：仍使用 Scenario 原有保存动作，Agent 无权代替用户保存。
- Stop：终止当前模型 Turn，Task 进入明确终态。
- Retry：重新发起最后一条用户消息；相同 Command Plan 由 Draft 服务幂等处理。
- Useful / Not useful：记录试用反馈，不改变 Scenario。

平台审计会记录 Draft 是否应用、是否最终保存，以及保存前是否又发生人工修改。

## 6. 故障判断

UI 会区分限流、超时、认证或授权失败、余额不足、模型不可用、预算耗尽和重复无进展。失败时已完成的消息、Tool Activity 和 Artifact 会保留，正式 Scenario 不会被修改。

未配置 DeepSeek 时可切回 `FAKE`，手工 Scenario 编辑、保存和 Runner 执行不依赖 Agent Runtime。

## 7. 审计与指标

单个 Task 审计：

```text
GET /api/agent/tasks/{taskId}/audit
```

Workspace 当前用户汇总：

```text
GET /api/projects/{projectId}/agent/audit/summary
```

汇总提供任务成功率、Draft 校验失败率、保存采纳率、人工修改次数和正负反馈。审计同时记录模型、Prompt、Skill 和 Toolset 版本。

POC 使用 `X-Platform-User` 作为本地身份适配头，默认值为 `local-user`。这只用于验证所有权隔离；接入真实用户系统后必须由认证网关生成并覆盖该身份，不能信任浏览器任意填写的 Header。

## 8. 试用数据保留

- Agent Task、消息、Draft 和 Artifact 在试用期间保留，测试者可手动删除 Task。
- 删除 Task 时普通消息和 Artifact 会删除，追加式审计继续保留。
- POC 暂无自动清理作业；试用审计约定保留 30 天，由数据库管理员在试用结束后统一清理。
- 试用数据不得包含真实生产凭据或客户敏感数据。

## 9. 验证命令

```powershell
mvn test
```

```powershell
cd platform-ui
npm.cmd test -- --run
npm.cmd run lint
npm.cmd run build
```

Agent 的核心安全与验收用例位于 `AgentCapabilityIntegrationTest`、`AgentSecurityIntegrationTest`、`AgentToolCallBudgetTest` 和 `AgentContextRedactorTest`；真实安全 Action 与 Runner 闭环由 `ApiCaseRunnerActionTest` 和订单履约 Cucumber Scenario 覆盖。
