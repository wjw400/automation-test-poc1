# Copilot Responses REST runtime

The `COPILOT` runtime calls `POST https://api.githubcopilot.com/responses`
directly using the OpenAI Responses wire format. It does not use the Copilot Java
SDK, launch a CLI process, or read CLI login credentials. Existing platform
tools, permission modes, draft validation and artifact events remain in use.

The Copilot-specific protocol is based on Microsoft's public client implementation.
OpenAI's Responses documentation describes the common wire format; it does not
establish that every OpenAI endpoint, parameter or hosted tool is available on Copilot.

## Setup

Start with the Bearer token, model ID and additional headers from a successful
Copilot `/responses` request. Configure these only in the backend process:

```powershell
$env:COPILOT_ENABLED = 'true'
$env:AGENT_DEFAULT_RUNTIME = 'COPILOT'
$env:COPILOT_BASE_URL = 'https://api.githubcopilot.com'
$env:COPILOT_API_TOKEN = '<token used after Bearer in your working REST request>'
$env:COPILOT_MODEL = '<model ID whose supported_endpoints includes /responses>'
mvn -pl platform-api spring-boot:run
```

Set `COPILOT_API_TOKEN` to the token value only, without the `Bearer ` prefix.
Use the same credential that works for your REST request; the adapter performs
no GitHub-to-Copilot token exchange and does not assume a GitHub PAT or OAuth
token is interchangeable with a Copilot session token. Provision credentials
through your deployment's secret configuration, never committed YAML or frontend
code. If a token expires, replace it in that configuration and restart the API.

Create a **new Agent chat** after restarting the API. Existing chats retain their
runtime and model. The model menu switches between configured models within a
chat's runtime. Existing chats created with the SDK model `auto` must switch to
an explicit enabled model before sending another message.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `COPILOT_ENABLED` | `false` | Enable the REST adapter. |
| `AGENT_DEFAULT_RUNTIME` | `FAKE` | Set to `COPILOT` for new chats. |
| `COPILOT_BASE_URL` | `https://api.githubcopilot.com` | Base URL; the adapter appends `/responses`. A full URL ending in `/responses` also works. |
| `COPILOT_API_TOKEN` | empty | Bearer credential from a successful REST request. |
| `COPILOT_MODEL` | empty | Explicit model ID supporting `/responses`; CLI model selection via `auto` is not used. |
| `COPILOT_MODELS` | empty | Comma-separated model allowlist; the default model is always included. |
| `COPILOT_API_VERSION` | empty | Optional `X-GitHub-Api-Version`, matching your successful request. |
| `COPILOT_INTEGRATION_ID` | empty | Optional `Copilot-Integration-Id`, matching your successful request. |
| `COPILOT_MAX_OUTPUT_TOKENS` | `8192` | Per-request output cap, further reduced to the turn's remaining output allowance. |
| `AGENT_MODEL_REQUEST_TIMEOUT` | `3m` | Deadline for each HTTP request, including its response stream. |

For other headers required by your existing request, use external Spring
configuration. For example, in a local override file:

```yaml
agent:
  copilot:
    headers:
      "[Editor-Version]": "${COPILOT_EDITOR_VERSION}"
      "[Editor-Plugin-Version]": "${COPILOT_EDITOR_PLUGIN_VERSION}"
```

Bracket notation preserves header names during Spring map binding. Copy actual
values from your working integration. No client identity or API version is
invented by default. The adapter controls `Authorization`, `Content-Type`,
`Accept` and the per-request `X-Request-Id` header. A custom `Authorization`
header cannot override `COPILOT_API_TOKEN`.

`available=true` in `GET /api/agent/runtimes` means Copilot is enabled and has a
nonempty token, base URL, explicit default model, and positive output cap. It is
not an authentication or model-entitlement health check. Runtime discovery and
task creation do not call the remote service. Configure model IDs using the
`supported_endpoints` reported by your account's `GET /models`; the platform
does not automatically discover them.

The task-creation API continues to accept `runtimeType: "COPILOT"` and `modelId`.

## Runtime behavior

- Each turn sends platform conversation history chronologically as Responses
  `input` messages, preceded by the trusted system prompt, context and loaded
  platform skills. Credentials are sent only as HTTP headers.
- Requests use `stream: true`, `store: false`, and include encrypted reasoning
  items for stateless continuation. Text deltas become existing `message.delta`
  events. Final response text is recovered without duplicating streamed text.
- Only prepared platform tools are advertised as Responses function tools.
  Calls are executed through the shared platform executor, retaining permissions,
  validation, timeouts, budgets, recoverable errors and artifact events. No native
  CLI tools or shell access are introduced.
- On each tool round, the next request replays response output items, including
  reasoning and function calls, followed by `function_call_output` items with
  matching `call_id` values. Tools run only after a complete response has arrived.
- Input/output usage accumulates across rounds. Each request's output cap is the
  smaller of `COPILOT_MAX_OUTPUT_TOKENS` and the turn's remaining allowance.
  Reaching the turn allowance reports `TOKEN_BUDGET_EXCEEDED` so the coordinator
  can pause the task for continuation. Repeated/no-progress checks and tool call
  limits remain enforced.
- Cancellation, service shutdown and platform run/inactivity deadlines stop the
  HTTP stream. Each model request also has its own complete-stream deadline.
  The adapter stops reading at `response.completed`, without waiting for the
  remote server to close its connection.
- HTTP or stream errors are classified without exposing provider response
  bodies or credentials in task events. Requests are not automatically retried,
  avoiding accidental repetition of tool side effects. Truncated or incomplete
  responses fail rather than executing partial tool calls.
- Structured output remains unadvertised. Validated draft authoring continues
  through typed platform tools.

## Migration from SDK configuration

`COPILOT_CLI_PATH`, `COPILOT_HOME`, `COPILOT_USE_LOGGED_IN_USER`, and
`COPILOT_GITHUB_TOKEN` are no longer used by this adapter. Set
`COPILOT_API_TOKEN` from your working HTTP request and choose an explicit
`COPILOT_MODEL` instead of `auto`. The backend has no Copilot SDK dependency.

This POC uses one configured backend identity. It does not provide per-user OAuth,
automatic token refresh, or a credential-exchange service.

## Troubleshooting

| Error | Action |
| --- | --- |
| `COPILOT_NOT_CONFIGURED` / `RUNTIME_NOT_CONFIGURED` | Check enabled state, token, explicit default model, base URL and output cap. |
| `MODEL_AUTHENTICATION_FAILED` | Check or renew the Bearer token used by your REST request. |
| `MODEL_ACCESS_DENIED` | Compare the working request's account, headers and model access. |
| `MODEL_BALANCE_EXHAUSTED` | Check the account/organization quota and budget. |
| `MODEL_RATE_LIMITED` | Wait for the provider limit to clear, then retry or continue the task. |
| `MODEL_TIMEOUT` | Check connectivity and `AGENT_MODEL_REQUEST_TIMEOUT`. |
| `MODEL_RESPONSE_INCOMPLETE` | The stream ended early or the provider stopped before completion; check output limits before continuing. |
| `MODEL_PROVIDER_ERROR` | Check `/responses` model support, required headers and request compatibility. |

## Verification

The adapter tests use an actual local HTTP server with scripted SSE/JSON responses.
They cover headers, history/context, text streaming, tool continuation, encrypted
reasoning, malformed arguments, denied tools, budgets, token accounting, HTTP and
stream errors, cancellation and request deadlines without a Copilot subscription:

```powershell
mvn -pl platform-api -am test '-Dtest=CopilotAgentRuntimeTest,DeepSeekAgentRuntimeTest,AgentApiIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

For live verification, configure your working REST credentials, create a Copilot
chat, ask it to inspect a Scenario, verify streamed text and tool events, then
request a draft change and review the validation and diff.

## References

- [OpenAI Responses API](https://developers.openai.com/api/reference/typescript/resources/responses/methods/create)
- [Responses streaming](https://developers.openai.com/api/docs/guides/streaming-responses)
- [Microsoft Responses adapter](https://github.com/microsoft/vscode-copilot-chat/blob/main/src/platform/endpoint/node/responsesApi.ts)
- [Microsoft request headers](https://github.com/microsoft/vscode-copilot-chat/blob/main/src/platform/networking/common/networking.ts)
- [Microsoft model capabilities](https://github.com/microsoft/vscode-copilot-chat/blob/main/src/platform/endpoint/common/endpointProvider.ts)
