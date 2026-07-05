# Case DSL v1 Design

## 1. Purpose

Case DSL v1 defines the standard YAML structure for API automation cases in this platform.

The main design goal is to keep the test scenario centered on meaningful API interactions, while still allowing protocol-specific processing such as encryption, signing, decryption, token generation, body wrapping, and response normalization to evolve through an action pipeline.

This document is the target design for new cases created by the platform UI and AI generation. Older YAML structures remain compatibility inputs, but should not be the default shape for newly authored cases.

## 2. Core Decision

The top-level `steps` array represents business-level API interactions.

Each `step` should answer:

- Which API operation is being tested?
- What request data is supplied?
- What response behavior is expected?
- Which values are extracted for later steps?

Technical processing inside a step is modeled through `actions`, not through extra top-level steps.

This avoids returning to a keyword-heavy style where one business API call becomes many small steps such as `prepare request`, `encrypt`, `send`, `decrypt`, and `validate`. That style is flexible, but it can make scenarios noisy and hard to review.

## 3. Industry Reference

This design follows a similar separation used by mature API testing tools:

- Postman keeps the request as the primary unit, while pre-request and post-response scripts add processing around it.
  Reference: https://learning.postman.com/docs/tests-and-scripts/write-scripts/pre-request-scripts/

- Tavern models YAML tests as multiple stages, where each stage has a request and response.
  Reference: https://github.com/taverntesting/tavern/blob/master/docs/source/basics.md

- Karate keeps scenarios readable around request execution and response matching, while allowing reusable setup and logic.
  Reference: https://docs.karatelabs.io/

- Robot Framework shows the power and risk of keyword-driven testing: everything can become a keyword, but over-fragmentation can produce many low-level repeated steps.
  Reference: https://robotframework.org/robotframework/latest/RobotFrameworkUserGuide.html

The platform should borrow the extensibility of action/script hooks, but keep API interaction as the visible scenario unit.

## 4. Standard Structure

```yaml
dslVersion: v1

name: Secure mock service mixed response
description: Validate plaintext response fields and decrypted secure payload fields.
tags:
  - mock-service
  - secure-response

runtime:
  environment: local
  profile: defaultSecureProfile

variables:
  customerId: C1
  amount: 88

steps:
  - name: Submit encrypted request and validate mixed response
    type: http

    operation:
      apiSpecId: mock-service-api
      operationId: submitEncryptedJsonField
      service: mockService
      method: POST
      path: /secure/json-field

    runtime:
      profile: clientToServerProfile

    request:
      query:
        field: bizData
      headers:
        Content-Type: application/json
      body:
        customerId: ${customerId}
        amount: ${amount}

    actions:
      beforeSend:
        - type: crypto.encrypt
          name: Encrypt request body into bizData
          profile: clientToServerProfile
          input:
            source: request.body
            format: json
          output:
            target: request.body
            mode: jsonField
            field: bizData

      afterReceive:
        - type: crypto.decrypt
          name: Decrypt secure payload
          profile: serverToClientProfile
          input:
            source: response.body
            path: $.payload
          output:
            target: decrypted.securePayload
            format: json

    response:
      status: 200
      assertions:
        - source: response.body
          path: $.code
          operator: equals
          expected: SUCCESS
        - source: response.body
          path: $.fieldUsed
          operator: equals
          expected: bizData
        - source: decrypted.securePayload
          path: $.result
          operator: equals
          expected: ACCEPTED
        - source: decrypted.securePayload
          path: $.customerId
          operator: equals
          expected: ${customerId}

      extract:
        acceptedCustomerId:
          source: decrypted.securePayload
          path: $.customerId
```

## 5. Execution Order

The runner should execute an HTTP step in this order:

1. Resolve case variables and runtime context.
2. Resolve step operation from the imported OpenAPI catalog.
3. Build the request from operation metadata, environment service base URL, and `request`.
4. Run `actions.beforeSend`.
5. Send the HTTP request.
6. Capture raw response evidence.
7. Run `actions.afterReceive`.
8. Evaluate `response.status`.
9. Evaluate `response.assertions`.
10. Run `response.extract`.
11. Write step evidence.

The report should preserve evidence for every major stage: request before actions, request after actions, raw response, processed response artifacts, assertions, extracted values, and errors.

## 6. Semantics Boundary

### 6.1 actions.beforeSend

`actions.beforeSend` is for request-side data processing before the request is sent.

Examples:

- `crypto.encrypt`
- `crypto.sign`
- `auth.oauth2.clientCredentials`
- `body.wrapJsonField`
- `body.encodeBase64`
- `headers.set`

It may mutate `request.body`, `request.headers`, `request.query`, or create intermediate artifacts.

### 6.2 actions.afterReceive

`actions.afterReceive` is for response-side data processing after the response is received.

Examples:

- `crypto.decrypt`
- `body.parseJson`
- `body.parseXml`
- `body.decodeBase64`
- `body.decompress`
- `response.normalize`

It should prepare data for assertions and extraction, but it should not decide whether the test passed.

### 6.3 response.assertions

`response.assertions` is the test verdict layer.

It answers: did the system behave as expected?

Assertions should remain separate from `actions.afterReceive`, even though they run after the response is received. This keeps the DSL readable and keeps reports focused on test intent.

### 6.4 response.extract

`response.extract` is the context propagation layer.

It answers: which values should later steps reuse?

Extraction should also remain separate from `actions.afterReceive`. It is not just data processing; it changes scenario context.

## 7. Action Model

Each action should follow a common shape:

```yaml
- type: crypto.encrypt
  name: Encrypt request body
  profile: clientToServerProfile
  input:
    source: request.body
    format: json
  output:
    target: request.body
    mode: jsonField
    field: bizData
```

Common fields:

- `type`: Required. Selects the action handler.
- `name`: Optional. Human-readable report label.
- `profile`: Optional. Security or protocol profile used by the action.
- `input`: Optional. Declares where the action reads data from.
- `output`: Optional. Declares where the action writes data to.

The runner should reject unknown action types unless a plugin mechanism is explicitly enabled.

## 8. Source And Target Paths

Use consistent source names across actions, assertions, and extraction:

- `request.body`
- `request.headers`
- `request.query`
- `response.body`
- `response.headers`
- `decrypted.<name>`
- `variables.<name>`

JSONPath should be used for structured JSON payload access:

```yaml
source: response.body
path: $.code
```

For decrypted payloads:

```yaml
source: decrypted.securePayload
path: $.customerId
```

## 9. Operation, Service, Environment, And Profile

### 9.1 Operation

When a step binds an OpenAPI operation, the operation is the primary source for method and path.

```yaml
operation:
  apiSpecId: mock-service-api
  operationId: submitEncryptedJsonField
  service: mockService
  method: POST
  path: /secure/json-field
```

`method` and `path` may be kept as a snapshot for traceability, but UI should treat them as read-only when `operationId` is present.

Resolution priority:

1. OpenAPI operation matched by `apiSpecId` and `operationId`.
2. Operation snapshot `method` and `path`.
3. Legacy `request.method` and `request.path`.

### 9.2 Service And Environment

`service` identifies the target system. `environment` identifies a set of runtime bindings.

A single environment can provide multiple service base URLs:

```yaml
environment: local
services:
  mockService:
    baseUrl: http://localhost:8081
  paymentService:
    baseUrl: http://localhost:8090
```

New DSL cases should prefer `operation.service` or `request.service` instead of hard-coded `request.baseUrl`.

### 9.3 Profile

Real keys and passphrases should not live in case YAML.

Case YAML should reference logical profile names:

```yaml
runtime:
  profile: defaultSecureProfile

actions:
  beforeSend:
    - type: crypto.encrypt
      profile: clientToServerProfile
```

Resolution priority:

1. Action-level `profile`.
2. Step-level `runtime.profile`.
3. Case-level `runtime.profile`.
4. Run-selected profile.

## 10. Compatibility Mapping

The runner and migration tools should continue to support current POC YAML structures.

Legacy mapping:

- Top-level `request`, `expect`, and `extract` become a single `steps[0]`.
- `request.baseUrl` is treated as a legacy direct URL source.
- `request.transform` maps to `actions.beforeSend` with `type: crypto.encrypt` or a compatible transform action.
- `expect.decrypt` maps to `actions.afterReceive` with `type: crypto.decrypt`.
- `expect.status` maps to `response.status`.
- `expect.validations` maps to `response.assertions`.
- `expect.jsonPath` maps to `response.assertions` with `source: response.body`, `operator: equals`.
- Existing `extract` map moves to `response.extract`.
- Existing source `raw` maps to `response.body`.
- Existing source `decrypted.<name>` remains valid.

The UI should generate only the v1 standard structure for new cases, while allowing old cases to be opened and migrated.

## 11. UI Mapping

The case editor should expose the v1 model like this:

- Scenario toolbar: case name, environment, profile, save, run.
- Step rail: one card per API interaction.
- Request tab: operation binding, query, headers, body.
- Actions or Security tab: `beforeSend` and `afterReceive` processors, especially crypto actions.
- Assertions tab: `response.assertions`.
- Extract tab: `response.extract`.
- Advanced tab: raw YAML and low-frequency fields.

The UI should not force users to think in low-level action order for common cases. Common actions should be edited through structured controls.

## 12. Report Mapping

Step evidence should show:

- Operation summary.
- Runtime environment and profile resolution.
- Request before `beforeSend`.
- Action evidence for each `beforeSend` action.
- Wire request.
- Raw response.
- Action evidence for each `afterReceive` action.
- Assertion results.
- Extracted values.
- Error details.

This keeps protocol debugging visible without turning the test scenario itself into a low-level workflow.

## 13. Non-Goals

Case DSL v1 is not intended to be a general workflow engine.

It should not model every technical action as a top-level step by default.

It should not store plaintext private keys or passphrases inside case YAML.

It should not require users to manually maintain HTTP method and path after selecting an OpenAPI operation.

It should not require all users to edit raw YAML for common request, action, assertion, and extraction patterns.

## 14. Design Conclusion

Use `steps` for business-level API interactions.

Use `actions.beforeSend` and `actions.afterReceive` for extensible request/response data processing.

Keep `response.assertions` and `response.extract` as first-class test concepts, not generic actions.

This gives the platform a stable center: API requests remain the scenario backbone, while action pipelines provide the extension points needed for security protocols and future integrations.
