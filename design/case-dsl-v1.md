# Case DSL v1 Design

## 1. Purpose

Case DSL v1 is the only supported YAML structure for API automation cases in the platform.

The scenario remains centered on business-level API requests. Protocol processing around a request is modeled as an ordered Action pipeline so that encryption, encoding, request-body construction, decryption, assertions, and extraction remain visible and debuggable without turning each technical operation into a Scenario Step.

The application is still under development. Legacy case structures and legacy Action names are intentionally not supported or migrated.

## 2. Core Model

- `steps` contains business-level HTTP interactions.
- `request.path` supplies values for placeholders declared by the selected Operation path.
- `actions.beforeSend` transforms request-side data before transmission.
- `actions.afterReceive` transforms response-side data before assertions and extraction.
- `response.assertions` decides whether the Step passed.
- `response.extract` promotes response values into Scenario Variables for later Steps.
- `artifacts.*` carries temporary values between Actions in the same Step.
- `decrypted.*` contains processed response payloads while the raw response remains unchanged.

Actions execute sequentially in YAML list order. The output of one Action is immediately available to the next Action.

### Path Parameters

Imported Operation paths may contain placeholders such as `/orders/{orderId}`. Values are declared separately from query parameters:

```yaml
request:
  path:
    orderId: ${orderId}
  query:
    expand: lines
```

The Runner resolves variables and URL-encodes each path value before replacing the matching placeholder. `request.path` keys that do not exist in the Operation path are validation warnings; missing required path values are validation errors.

### Variable References

Case DSL v1 uses `${variableName}` as its canonical variable syntax. `${variables.variableName}` is also accepted when an explicit Scenario Variable prefix improves readability. Postman-style `{{variableName}}` placeholders are invalid and are never sent as literal request data.

Variables created by `response.extract` are flat Scenario Variables. The extraction key is the variable name, independent of the producing Step name:

```yaml
response:
  extract:
    productId:
      source: response.body
      path: $[0].id

# A later Step
request:
  body:
    productId: ${productId}
```

Do not use `{{productId}}`, `{{list-products.productId}}`, or `${list-products.productId}` for this extraction.

### HTTP Status Rules

Every HTTP Step must declare `response.status`. It is the first assertion evaluated for the response and supports three explicit modes:

```yaml
# One exact status
status:
  operator: equals
  expected: 201

# Any listed status
status:
  operator: oneOf
  expected: [200, 202, 204]

# Inclusive status range
status:
  operator: range
  min: 200
  max: 299
```

Status values must be integers from 100 to 599. `oneOf` requires at least one unique value, and `range.min` must not exceed `range.max`. The legacy scalar form `status: 200` is invalid and is not migrated automatically.

## 3. Standard Example

The following case starts with an XML business payload, signs and encrypts it with PGP, Base64-encodes the armored encrypted text, and constructs the final JSON Wire Request Body.

```yaml
dslVersion: v1
name: Submit secure XML payment
description: Validate plaintext response metadata and the decrypted secure payload.
tags:
  - payment
  - pgp

runtime:
  profile: paymentSecureProfile

variables:
  paymentId: P1001

steps:
  - name: Submit encrypted payment
    type: http

    operation:
      id: imported-operation-id
      apiSpecId: payment-api
      operationId: submitPayment
      service: paymentService
      method: POST
      path: /payments

    runtime: {}

    request:
      path: {}
      query: {}
      headers:
        Content-Type: application/json
      body: |
        <Payment>
          <Id>${paymentId}</Id>
          <Amount>100</Amount>
        </Payment>

    actions:
      beforeSend:
        - type: crypto.pgp.signAndEncrypt
          name: Protect XML payload
          input:
            source: request.body
            format: xml
          output:
            target: artifacts.encryptedPayload
            format: text

        - type: data.encodeBase64
          name: Encode protected payload
          input:
            source: artifacts.encryptedPayload
          output:
            target: artifacts.encryptedPayloadBase64

        - type: body.buildJson
          name: Build wire request body
          template:
            meta:
              channel: platform-poc
            encryptBase64: ${artifacts.encryptedPayloadBase64}
          output:
            target: request.body

      afterReceive:
        - type: crypto.pgp.decryptAndVerify
          name: Read secure response payload
          input:
            source: response.body
            path: $.payload
          output:
            target: decrypted.securePayload
            format: json

    response:
      status:
        operator: oneOf
        expected: [200]
      assertions:
        - source: response.body
          path: $.code
          operator: equals
          expected: SUCCESS
        - source: decrypted.securePayload
          path: $.paymentId
          operator: equals
          expected: ${paymentId}
      extract:
        traceId:
          source: response.body
          path: $.traceId
```

## 4. Execution Order

An HTTP Step executes in this order:

1. Resolve Scenario Variables and runtime context.
2. Resolve the operation snapshot and environment Base URL.
3. Resolve the Step Profile, falling back to the Scenario Profile.
4. Build the initial request from `request`.
5. Execute `actions.beforeSend` in list order.
6. Send the final Wire Request.
7. Capture the raw HTTP response.
8. Execute `actions.afterReceive` in list order.
9. Evaluate `response.status` and `response.assertions`.
10. Evaluate `response.extract`.
11. Write request, response, Action, assertion, extraction, and error evidence.

An Action failure immediately fails the Step. V1 does not provide `continueOnError` or conditional Action execution.

## 5. Supported Actions

DSL v1 supports exactly four Actions.

### 5.1 `crypto.pgp.signAndEncrypt`

Stage: `beforeSend`

Signs the input with our `signingPrivateKey` and encrypts it with the counterparty's `encryptionPublicKey`. It uses the resolved Step or Scenario Profile and produces ASCII-armored PGP text.

Supported input formats:

- `json`: serialize structured input as JSON before protection.
- `text`: protect the input string unchanged.
- `xml`: protect the XML string unchanged.

Typical output targets:

- `request.body` for an encrypted raw-text Wire Request.
- `artifacts.<name>` when later Actions must encode or embed the result.

### 5.2 `data.encodeBase64`

Stage: `beforeSend`

Base64-encodes its input. String input is encoded as UTF-8 bytes. Structured input is serialized as JSON before encoding.

This Action is intentionally separate from encryption. A protocol that accepts ASCII-armored PGP directly does not need it. A protocol that requires Base64 around the armored PGP text uses it explicitly.

### 5.3 `body.buildJson`

Stage: `beforeSend`

Builds the final JSON Wire Request Body from a YAML template. The template may contain fixed values, Scenario Variables, and `artifacts.*` references.

The template must resolve to a JSON object or array, and the output target must be `request.body`.

```yaml
- type: body.buildJson
  template:
    meta:
      requestId: ${requestId}
    payload: ${artifacts.encryptedPayload}
  output:
    target: request.body
```

`body.wrapJsonField` is not a DSL Action. A one-field wrapper is simply a small `body.buildJson` template and may be offered by the UI as a preset.

### 5.4 `crypto.pgp.decryptAndVerify`

Stage: `afterReceive`

Reads an encrypted value from the raw response, decrypts it with our `decryptionPrivateKey`, and verifies the embedded signature with the counterparty's `verificationPublicKey`.

Supported output formats:

- `json`: parse plaintext into a structured JSON value.
- `text`: keep plaintext as a string.
- `xml`: keep XML plaintext as a string.

The output target must use `decrypted.<name>`. Signature or integrity verification failure fails the Action and Step.

## 6. Action Structure

```yaml
- type: crypto.pgp.signAndEncrypt
  name: Protect request payload
  enabled: true
  input:
    source: request.body
    path:
    format: json
  output:
    target: artifacts.encryptedPayload
    path:
    format: text
```

Common fields:

- `type`: required Action handler name.
- `name`: optional human-readable report label.
- `enabled`: optional; defaults to `true`.
- `input`: source, optional JSONPath, and input format.
- `output`: target, optional JSONPath, and output format.
- `template`: Action-specific input used by `body.buildJson`.

Unknown Action types and Actions placed in the wrong stage are validation errors.

## 7. Sources, Targets, And Artifacts

Supported Action sources:

- `request.body`
- `response.body`
- `variables.<name>`
- `artifacts.<name>`
- `decrypted.<name>`

Supported Action targets:

- `request.body`
- `artifacts.<name>`
- `decrypted.<name>`

`artifacts.*` is scoped to one Step and is not automatically available to later Steps. Values that later Steps need must be declared under `response.extract` and become Scenario Variables.

JSONPath is used when an Action reads a nested value:

```yaml
input:
  source: response.body
  path: $.payload
```

### Response Assertion And Extraction Sources

`response.assertions[].source` and `response.extract.<variableName>.source` use a smaller,
stage-specific source vocabulary. Case DSL v1 supports exactly these forms:

| Source | Meaning | Availability |
| --- | --- | --- |
| `response.body` | Raw HTTP response body captured before `afterReceive` processing | Always |
| `decrypted.<name>` | Decrypted payload written by an enabled `afterReceive` Action | Only when that Action produces the exact target in the same Step |

`raw` is not a Case DSL v1 response source. Use `response.body` for the received HTTP body.

The `source` selects a logical response document. The required `path` is a JSONPath evaluated
against that document:

```yaml
extract:
  orderId:
    source: response.body
    path: $.orderId
```

For a processed payload, the source name must match an earlier `afterReceive` output target:

```yaml
actions:
  afterReceive:
    - type: crypto.pgp.decryptAndVerify
      input:
        source: response.body
        path: $.payload
      output:
        target: decrypted.securePayload
        format: json
response:
  status:
    operator: oneOf
    expected: [200]
  assertions: []
  extract:
    orderId:
      source: decrypted.securePayload
      path: $.orderId
```

Response sources are Step-scoped. A `decrypted.<name>` produced by one Step cannot be referenced
directly by another Step. Promote values needed by later Steps through `response.extract`.

## 8. Profile Resolution

Actions never define their own Profile.

Profile resolution order:

1. `step.runtime.profile`
2. `scenario.runtime.profile`

```yaml
runtime:
  profile: defaultSecureProfile

steps:
  - name: Use a different communication identity
    runtime:
      profile: paymentSecureProfile
```

If a Step contains a crypto Action and neither level resolves a Profile, validation fails before the HTTP request is sent.

A Profile contains both directions of a communication relationship:

- Outbound protection uses our `signingPrivateKey` and the counterparty's `encryptionPublicKey`.
- Inbound processing uses our `decryptionPrivateKey` and the counterparty's `verificationPublicKey`.

Private keys and passphrases are provided by the platform Profile catalog at execution time and should not be stored in managed Case YAML.

## 9. Operation And Environment

An imported OpenAPI operation is the source of method and path. The YAML keeps method and path snapshots for traceability and execution stability.

The runtime environment supplies `baseUrl`. The current POC also supports a service-specific variable named `<service>.baseUrl`, falling back to `baseUrl`.

The UI treats operation method and path as read-only after the user selects an imported operation.

## 10. Assertions And Extraction

`actions.afterReceive` prepares response data but does not define the test verdict.

Assertions remain under `response.assertions`, allowing the same Step to validate both raw plaintext fields and decrypted fields:

```yaml
assertions:
  - source: response.body
    path: $.code
    operator: equals
    expected: SUCCESS
  - source: decrypted.securePayload
    path: $.result
    operator: equals
    expected: ACCEPTED
```

Extraction remains under `response.extract` because it changes Scenario context for later Steps.

Both assertions and extraction must use one of the response sources defined in Section 7. A
syntactically valid `decrypted.<name>` is still invalid when no enabled `afterReceive` Action in
the same Step produces that exact target.

## 11. Reporting

Each Step report records:

- resolved Profile name
- final Wire Request
- raw HTTP response
- every Action stage, index, type, name, status, duration, input, output, and error
- decrypted payloads
- assertion results
- extracted values

The raw response is never overwritten by an After Action. This allows reports to show both received data and processed artifacts.

## 12. UI Mapping

- Before tab offers PGP Sign & Encrypt, Encode Base64, and Build JSON Body.
- After tab offers PGP Decrypt & Verify.
- Action order is visible and reorderable.
- Action editors do not contain a Profile field.
- Scenario and Step runtime settings own Profile selection.
- Tests and Extract remain separate tabs.
- Unsupported or legacy YAML is shown as a validation error rather than migrated.

## 13. Non-Goals

DSL v1 is not a general workflow or scripting engine.

It does not support arbitrary scripts, conditional Actions, `continueOnError`, Action-level Profiles, fake encryption, legacy transforms, or automatic migration from earlier POC YAML structures.
