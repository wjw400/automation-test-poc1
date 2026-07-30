# Native OpenAPI Operation Inspector Design

> **Superseded processing model:** The original `x-platform-payload` and
> `Protocol` sections in this document describe the first implementation draft.
> The active processing design is [builtin-processing-pipeline-catalog.md](builtin-processing-pipeline-catalog.md):
> `x-platform-processing`, immutable built-in pipeline references, and the
> read-only Pipeline Catalog are the current baseline.

## 1. Status

- Status: Proposed
- Date: 2026-07-29
- Scope: `platform-api`, `platform-ui`, imported OpenAPI assets, Case Designer, execution evidence
- Replaces: Stoplight-based API reference after phased migration
- Related documents:
  - `design/platform-ui-guidelines.md`
  - `design/case-dsl-v1.md`
  - `design/system-architecture.md`
  - `design/ai-assistance-boundaries.md`
  - `design/ai-context-and-security.md`

## 2. Background

The platform currently embeds Stoplight Elements to render an imported OpenAPI document. This works for standard wire-level API documentation, but it does not expose platform-specific concepts as first-class product concepts.

Some partner APIs accept a business payload that must be signed, encrypted, encoded, or wrapped before it is sent. For example, the business request:

```json
{
  "customerName": "Automation Partner",
  "items": [
    {
      "productId": "P100",
      "quantity": 2
    }
  ]
}
```

is transformed into the HTTP wire request:

```json
{
  "payload": "-----BEGIN PGP MESSAGE-----\n...\n-----END PGP MESSAGE-----"
}
```

The standard OpenAPI `requestBody` must continue to describe the wire request because that is the actual HTTP contract. If the standard `requestBody` is replaced with the plaintext business schema, OpenAPI validation, generated clients, examples, and other consumers receive an inaccurate contract.

The mock service OpenAPI already separates these two layers with:

```yaml
x-plaintext-request-schema:
  $ref: '#/components/schemas/CreateOrderPayload'

x-plaintext-response-schema:
  $ref: '#/components/schemas/Order'

requestBody:
  $ref: '#/components/requestBodies/EncryptedRequest'
```

The remaining problem is presentation and reuse. A generic OpenAPI renderer does not treat these extensions as primary content, and the platform cannot use them consistently for test generation, validation, evidence, or AI context.

## 3. Decision Summary

This design makes the following decisions:

1. Replace Stoplight Elements with a platform-native Operation Inspector through a phased migration.
2. Keep the imported OpenAPI YAML as the canonical source of truth.
3. Introduce a normalized `OperationContract` model shared by API reference, Case Designer, validation, evidence, and AI assistance.
4. Show the business request by default and retain an explicit wire request view.
5. Model business payloads and protocol transforms with a versioned `x-platform-payload` extension.
6. Continue supporting the existing `x-plaintext-request-schema` and `x-plaintext-response-schema` extensions.
7. Resolve local `$ref` values only. External references are not fetched automatically.
8. Do not attempt to implement the entire OpenAPI ecosystem. Support the subset required by the platform and imported specifications.
9. Preserve access to the raw OpenAPI source after Stoplight is removed.

## 4. Goals

The native Operation Inspector must help a user:

- find an imported operation quickly
- understand the business request they need to construct
- inspect the actual wire request sent over HTTP
- understand request and response transform pipelines
- inspect business and wire response schemas separately
- view parameters, examples, constraints, and descriptions
- create a Scenario from an Operation
- generate recommended request and response Actions
- understand import and extension diagnostics
- access the original OpenAPI source when needed

The normalized contract must also support:

- business request validation before request Actions
- wire request validation after request Actions
- wire response validation before response Actions
- business response validation after response Actions
- structured execution evidence
- future AI-assisted test generation

## 5. Non-Goals

The first versions will not:

- reproduce every Stoplight or Swagger UI feature
- generate SDKs or client libraries
- provide a generic OAuth authorization console
- execute a generic OpenAPI `Try It` request
- support arbitrary scripting in OpenAPI extensions
- fetch arbitrary external `$ref` URLs
- support every OpenAPI callback, link, webhook, or discriminator edge case
- replace Case DSL Actions with an implicit, non-debuggable transform

The platform is an automation testing workbench, not a general-purpose API developer portal.

## 6. Terminology

### 6.1 Business Request

The structured payload a user understands, edits, generates, and validates before protocol processing.

### 6.2 Wire Request

The final HTTP request body after all request Actions have completed.

### 6.3 Business Response

The response payload after extraction, decoding, decryption, and verification.

### 6.4 Wire Response

The raw HTTP response received from the service.

### 6.5 Payload Transform

A declarative description of the operations between a business payload and a wire payload. Examples include:

- PGP sign and encrypt
- Base64 encode
- embed in a JSON field
- extract from a JSON field
- PGP decrypt and verify

### 6.6 Operation Contract

The normalized platform representation of one OpenAPI Operation, including parameters, business payloads, wire payloads, transform metadata, capabilities, and diagnostics.

## 7. Product Experience

### 7.1 Primary User Flow

```text
Select an API Spec
    -> Find an Operation
    -> Read the business request
    -> Inspect the protocol transform when needed
    -> Inspect the wire request when needed
    -> Create a Scenario
    -> Edit business-level test data
    -> Execute and inspect business and wire evidence
```

### 7.2 Information Hierarchy

The primary information is:

- selected Operation
- business request schema
- business response schema
- required parameters
- protocol requirements
- create Scenario action

Secondary information is:

- wire envelope metadata
- raw OpenAPI source
- internal Operation identifiers
- parser diagnostics without user impact

This follows the platform UI principles of task-first design, progressive disclosure, and structured editing.

### 7.3 Page Layout

```text
+------------------------------------------------------------------+
| OrderFlow Secure Fulfillment API        OpenAPI 3.0.3 · 5 ops    |
| [Search operations]                         [View source]         |
+----------------------+-------------------------------------------+
| Order Fulfillment    | POST /secure/fulfillment/orders/create    |
|                      | Create partner order          Encrypted   |
| POST  Create order   |                                           |
| POST  Query order    | Parameters | Request | Responses | Protocol|
| POST  Reserve order  |                                           |
| POST  Fulfill order  | Request                                   |
| POST  Cancel order   | [Business Request] [Wire Request]         |
|                      |                                           |
| Fulfillment Console  | Field            Type       Required      |
| GET   Summary        | customerName     string     yes           |
| GET   Products       | items            array      yes           |
|                      |                                           |
|                      | [Copy example] [Create Scenario]          |
+----------------------+-------------------------------------------+
```

### 7.4 Operation Navigation

The left navigation provides:

- text search across path, summary, operation ID, and tags
- tag grouping
- HTTP method badges
- encrypted payload indicator
- diagnostic warning indicator
- selected state
- empty search state

The list must remain compact and scannable. It must not use oversized cards.

### 7.5 Operation Detail Tabs

#### Parameters

Parameters are grouped by:

- path
- query
- header
- cookie

The table columns are:

```text
Name | Location | Type | Required | Default | Description
```

#### Request

The Request tab uses a segmented view:

```text
[Business Request] [Wire Request]
```

The Business Request is selected by default when a business schema exists.

If the Operation has no separate business schema, the UI displays:

```text
This operation does not declare a separate business request.
The request shown below is the HTTP wire request.
```

#### Responses

The Responses tab first selects a response status:

```text
[200] [400] [500]
```

For each status, the UI displays:

```text
[Business Response] [Wire Response]
```

The Business Response option only appears when the selected status declares one.

#### Protocol

The Protocol tab displays a visual sequence rather than raw extension JSON:

```text
Business JSON
    -> PGP sign and encrypt
    -> ASCII armor
    -> embed at JSON Pointer /payload
    -> HTTP wire request
```

It also shows:

- input and output media types
- whether a runtime Profile is required
- partner selector parameters
- encoding steps
- JSON source or target pointer
- generated Case DSL Actions

#### Source

The Source view provides:

- selected Operation YAML
- full OpenAPI YAML
- copy
- download
- search
- line numbers when the selected source component supports them

The first version may use a read-only preformatted block. A large code editor dependency is not required for the initial release.

## 8. OpenAPI Extension Contract

### 8.1 Extension Name

The preferred extension is:

```yaml
x-platform-payload:
```

The extension is versioned because it is a platform contract rather than an informal display hint.

### 8.2 Complete Request and Response Example

```yaml
paths:
  /secure/fulfillment/orders/create:
    post:
      tags:
        - Order Fulfillment
      summary: Create partner order
      operationId: createFulfillmentOrder

      parameters:
        - $ref: '#/components/parameters/ProfileParam'

      x-platform-payload:
        version: 1

        request:
          business:
            mediaType: application/json
            schema:
              $ref: '#/components/schemas/CreateOrderPayload'

          profile:
            runtimeProfileRequired: true
            partnerSelector:
              in: query
              name: profile

          transform:
            steps:
              - type: pgp-sign-encrypt
                armored: true

              - type: json-embed
                pointer: /payload

        responses:
          '200':
            business:
              mediaType: application/json
              schema:
                $ref: '#/components/schemas/Order'

            transform:
              steps:
                - type: json-extract
                  pointer: /payload

                - type: pgp-decrypt-verify
                  outputMediaType: application/json

      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/EncryptedPayloadRequest'

      responses:
        '200':
          $ref: '#/components/responses/EncryptedSuccess'
        '400':
          $ref: '#/components/responses/BadRequest'
        '500':
          $ref: '#/components/responses/InternalError'
```

### 8.3 Business Payload

```yaml
business:
  mediaType: application/json
  schema:
    $ref: '#/components/schemas/CreateOrderPayload'
  example:
    customerName: Automation Partner
    items:
      - productId: P100
        quantity: 2
```

Fields:

| Field | Required | Description |
|---|---:|---|
| `mediaType` | yes | Media type before request transformation or after response transformation |
| `schema` | yes | Inline Schema Object or local `$ref` |
| `example` | no | Explicit business example |

If `example` is absent, the platform generates one from the schema.

### 8.4 Profile Metadata

```yaml
profile:
  runtimeProfileRequired: true
  partnerSelector:
    in: query
    name: profile
```

`runtimeProfileRequired` means Case execution needs a platform runtime Profile containing the local and remote key material.

`partnerSelector` describes a wire-level parameter used by the remote service to select a trusted partner identity.

These are related but distinct concepts:

- the runtime Profile supplies client-side cryptographic material
- the partner selector is sent to the service

The OpenAPI extension never contains private keys, passphrases, or other secret material.

### 8.5 Supported Request Transform Steps

#### PGP Sign and Encrypt

```yaml
- type: pgp-sign-encrypt
  armored: true
```

Maps to:

```yaml
type: crypto.pgp.signAndEncrypt
```

#### Base64 Encode

```yaml
- type: base64-encode
  charset: UTF-8
```

Maps to:

```yaml
type: data.encodeBase64
```

#### JSON Embed

```yaml
- type: json-embed
  pointer: /payload
```

Maps to a generated `body.buildJson` Action.

A nested pointer is allowed:

```yaml
- type: json-embed
  pointer: /secure/data
```

The platform generates:

```json
{
  "secure": {
    "data": "${artifacts.encryptedPayload}"
  }
}
```

### 8.6 Supported Response Transform Steps

#### JSON Extract

```yaml
- type: json-extract
  pointer: /payload
```

The platform converts the JSON Pointer to the corresponding supported runtime path representation.

#### PGP Decrypt and Verify

```yaml
- type: pgp-decrypt-verify
  outputMediaType: application/json
```

Maps to:

```yaml
type: crypto.pgp.decryptAndVerify
```

### 8.7 Transform Validation Rules

Import diagnostics are generated when:

- `version` is missing or unsupported
- a transform step has no `type`
- a transform step type is unsupported
- a JSON Pointer is invalid
- a referenced schema does not exist
- request steps are in an invalid order
- response steps are in an invalid order
- a cryptographic transform requires a Profile but `runtimeProfileRequired` is false or absent
- a response transform is declared for an undefined response status

An invalid platform extension must not silently generate incorrect test Actions.

### 8.8 Legacy Compatibility

When `x-platform-payload` is absent, the parser supports:

```yaml
x-plaintext-request-schema:
  $ref: '#/components/schemas/CreateOrderPayload'

x-plaintext-response-schema:
  $ref: '#/components/schemas/Order'
```

The compatibility adapter produces:

- a business request contract from `x-plaintext-request-schema`
- a business response contract for the primary successful response
- no automatic transform unless sufficient metadata can be inferred safely

The adapter must not infer encryption steps from a field description or field name alone.

## 9. Normalized Contract Model

### 9.1 API Spec Contract

```java
public record ApiSpecContractRecord(
        String apiSpecId,
        String title,
        String version,
        List<OperationContractRecord> operations,
        Map<String, Object> schemaRegistry,
        List<ContractDiagnosticRecord> diagnostics) {
}
```

The schema registry is keyed by canonical local references:

```json
{
  "#/components/schemas/CreateOrderPayload": {
    "type": "object",
    "properties": {}
  }
}
```

Schemas are not fully inlined into every Operation because:

- it duplicates large definitions
- it expands cyclic graphs indefinitely
- it increases API payload size
- it makes schema origin difficult to explain

### 9.2 Operation Contract

```java
public record OperationContractRecord(
        String id,
        String method,
        String path,
        String operationId,
        String summary,
        String description,
        List<String> tags,
        List<ParameterContractRecord> parameters,
        PayloadContractRecord request,
        List<ResponseContractRecord> responses,
        OperationCapabilitiesRecord capabilities,
        Object rawOperation,
        List<ContractDiagnosticRecord> diagnostics) {
}
```

The database Operation ID is attached by matching the parsed OpenAPI Operation with the stored operation using:

```text
apiSpecId + HTTP method + path
```

The platform must not rely only on `operationId` because it can be missing or duplicated in invalid third-party documents.

### 9.3 Payload Contract

```java
public record PayloadContractRecord(
        ContentContractRecord business,
        ContentContractRecord wire,
        TransformContractRecord transform,
        ProfileRequirementRecord profile) {
}
```

### 9.4 Content Contract

```java
public record ContentContractRecord(
        String mediaType,
        Object schema,
        String schemaRef,
        Object example) {
}
```

When a schema is a `$ref`, `schemaRef` contains the canonical reference. `schema` may retain the original reference object rather than an expanded schema.

### 9.5 Response Contract

```java
public record ResponseContractRecord(
        String status,
        String description,
        ContentContractRecord business,
        ContentContractRecord wire,
        TransformContractRecord transform) {
}
```

Business response schemas are status-specific. A successful encrypted response does not imply that a `400` or `500` response is also encrypted.

### 9.6 Capabilities

```java
public record OperationCapabilitiesRecord(
        boolean hasBusinessRequest,
        boolean hasBusinessResponse,
        boolean hasRequestTransform,
        boolean hasResponseTransform,
        boolean encrypted,
        boolean canGenerateScenario) {
}
```

Capabilities allow the UI to display Operation state without inspecting arbitrary extension objects.

### 9.7 Diagnostics

```java
public record ContractDiagnosticRecord(
        DiagnosticSeverity severity,
        String code,
        String message,
        String location) {
}
```

Severity:

```java
public enum DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
```

Example:

```json
{
  "severity": "WARNING",
  "code": "UNRESOLVED_SCHEMA_REF",
  "message": "Schema reference could not be resolved",
  "location": "POST /orders/create x-platform-payload.request.business.schema"
}
```

## 10. Backend Design

### 10.1 Packages

```text
platform-api/src/main/java/com/example/poc/platform/openapi/
├── OpenApiContractService.java
├── OpenApiContractParser.java
├── OpenApiExtensionParser.java
├── OpenApiReferenceResolver.java
├── OpenApiExampleGenerator.java
├── OpenApiContractValidator.java
└── model/
    ├── ApiSpecContractRecord.java
    ├── OperationContractRecord.java
    ├── PayloadContractRecord.java
    ├── ContentContractRecord.java
    ├── TransformContractRecord.java
    └── ContractDiagnosticRecord.java
```

### 10.2 Service Responsibilities

#### `OpenApiContractService`

- verify project and API Spec ownership
- read the canonical YAML from `ApiSpecEntity`
- parse and cache the normalized contract
- attach stored Operation IDs
- return an entire Spec contract
- return one Operation contract
- invalidate cached contracts when a Spec is re-imported or deleted

#### `OpenApiContractParser`

- parse paths and HTTP methods
- normalize Parameters
- normalize Request Body
- normalize Responses
- build the Schema Registry
- call `OpenApiExtensionParser`
- produce Operation capabilities
- collect diagnostics

#### `OpenApiExtensionParser`

- parse `x-platform-payload`
- adapt legacy `x-plaintext-*` extensions
- validate extension versions and shapes
- validate transform step order
- convert extension data into typed records

#### `OpenApiReferenceResolver`

- resolve local references only
- decode JSON Pointer segments
- detect reference cycles
- enforce depth and node limits
- distinguish missing, cyclic, and unsupported external references

#### `OpenApiExampleGenerator`

- generate business or wire examples on demand
- retain explicit examples when present
- avoid following cycles indefinitely
- cap generated collection size

#### `OpenApiContractValidator`

- validate platform extension structure
- verify referenced local schemas exist
- verify response status mappings exist
- determine whether an Operation can safely generate a Scenario

### 10.3 Parse Behavior

The existing OpenAPI parser uses:

```java
options.setResolve(false);
options.setResolveFully(false);
```

This behavior should remain. Automatic full resolution can:

- duplicate schemas
- lose reference identity
- expand cycles
- fetch external resources depending on parser configuration

The platform resolver handles only the local references needed for display, generation, and validation.

### 10.4 REST Endpoints

#### Get API Spec Contract

```http
GET /api/projects/{projectId}/api-specs/{apiSpecId}/contract
```

Response:

```json
{
  "apiSpecId": "spec-id",
  "title": "OrderFlow Secure Fulfillment API",
  "version": "2.0.0",
  "operations": [],
  "schemaRegistry": {},
  "diagnostics": []
}
```

This endpoint is used by the native API Reference.

#### Get Operation Contract

```http
GET /api/projects/{projectId}/operations/{operationId}/contract
```

This endpoint is used by:

- Case Designer
- AI context assembly
- future contract-aware validation editors

#### Existing Source Endpoint

The existing endpoint remains:

```http
GET /api/projects/{projectId}/api-specs/{apiSpecId}/content
```

It remains the source for viewing and downloading the original YAML.

### 10.5 Lightweight Operation Lists

The existing Operation list must remain lightweight. It should add only normalized capability flags:

```json
{
  "id": "operation-id",
  "method": "POST",
  "path": "/secure/fulfillment/orders/create",
  "summary": "Create partner order",
  "capabilities": {
    "hasBusinessRequest": true,
    "hasBusinessResponse": true,
    "hasRequestTransform": true,
    "hasResponseTransform": true,
    "encrypted": true,
    "canGenerateScenario": true
  }
}
```

It must not include the full Schema Registry for every list request.

### 10.6 Storage

The first implementation does not require a database migration.

Existing stored data is sufficient:

- `ApiSpecEntity.content` contains the canonical YAML
- `ApiOperationEntity.rawOperationJson` contains the raw parsed Operation
- `ApiOperationEntity.requestBodyJson` contains the wire request
- `ApiOperationEntity.responsesJson` contains wire responses

The normalized contract is derived data.

If parsing becomes expensive, a later migration may add:

```sql
alter table platform_api_operation
    add column contract_json text;
```

Persisting derived contracts is deferred until measurements justify it.

### 10.7 Cache

Cache key:

```text
apiSpecId + apiSpec.updatedAt
```

Cache behavior:

- parse lazily when the contract is first requested
- populate or refresh after import
- invalidate after re-import
- invalidate after delete
- allow reconstruction after application restart

The cache stores normalized immutable records, not parser objects.

## 11. Reference Resolution

### 11.1 Supported References

The first version supports:

```yaml
$ref: '#/components/schemas/CreateOrderPayload'
```

It may also resolve local references to:

- parameters
- request bodies
- responses
- headers
- examples

### 11.2 External References

External references are not fetched automatically:

```yaml
$ref: 'https://example.com/common.yaml#/schemas/Address'
```

The Operation remains visible, but a diagnostic is returned:

```text
EXTERNAL_REF_NOT_RESOLVED
```

This avoids SSRF, unbounded network dependency, authentication ambiguity, and non-repeatable imports.

A future controlled external-reference feature would require:

- explicit project allowlists
- size and timeout limits
- authentication policy
- content caching
- import evidence
- deterministic versioning

### 11.3 Cycle Detection

The resolver tracks the current reference stack:

```text
Order -> Customer -> Orders -> Order
```

When the next reference already exists in the stack, it returns a cycle marker rather than resolving further.

The UI displays:

```text
Circular reference to Order
```

### 11.4 Limits

Recommended defaults:

- maximum resolution depth: 40
- maximum schema nodes per response: 10,000
- maximum generated example depth: 12
- maximum generated array items: 1
- maximum displayed string example length: configurable

Limits must produce diagnostics rather than a blank page or stack overflow.

## 12. Frontend Design

### 12.1 Component Structure

```text
platform-ui/src/components/openapi/
├── ApiReference.tsx
├── ApiReferenceHeader.tsx
├── OperationNavigator.tsx
├── OperationListItem.tsx
├── OperationDetail.tsx
├── ParameterTable.tsx
├── PayloadViewer.tsx
├── ResponseViewer.tsx
├── SchemaTree.tsx
├── SchemaNode.tsx
├── SchemaExample.tsx
├── TransformPipeline.tsx
├── ContractDiagnostics.tsx
├── OpenApiSourceViewer.tsx
└── schema/
    ├── schemaResolver.ts
    ├── exampleGenerator.ts
    ├── schemaTypes.ts
    └── schemaFormatters.ts
```

### 12.2 API Client

Add:

```ts
async getApiSpecContract(projectId: string, apiSpecId: string) {
  const { data } = await apiClient.get<ApiSpecContractRecord>(
    `/projects/${projectId}/api-specs/${apiSpecId}/contract`,
  )
  return data
}

async getOperationContract(projectId: string, operationId: string) {
  const { data } = await apiClient.get<OperationContractRecord>(
    `/projects/${projectId}/operations/${operationId}/contract`,
  )
  return data
}
```

### 12.3 Page Data Loading

```tsx
function ApiSpecTabPane({ projectId, spec }: Props) {
  const contractQuery = useQuery({
    queryKey: ['api-spec-contract', projectId, spec.id],
    queryFn: () => platformApi.getApiSpecContract(projectId, spec.id),
  })

  return (
    <ApiReference
      spec={spec}
      contract={contractQuery.data}
      loading={contractQuery.isPending}
      error={contractQuery.error}
    />
  )
}
```

The API Reference keeps selected Operation and selected detail tab as local workbench state.

### 12.4 Loading State

The loading state uses stable skeleton regions:

- header skeleton
- Operation navigator skeleton
- detail skeleton

It must not replace the entire workbench with a centered spinner.

### 12.5 Empty States

No Operations:

```text
No operations were found in this OpenAPI document.
View the source or import a corrected document.
```

No business schema:

```text
This operation does not declare a separate business request.
The wire request is shown instead.
```

Search with no results:

```text
No operations match the current search.
```

### 12.6 Error States

Contract load failure:

```text
The API contract could not be loaded.

[Retry] [View source]
```

Unresolved business schema:

```text
Business schema could not be resolved:
#/components/schemas/CreateOrderPayload

The wire request and source remain available.
```

Unsupported transform:

```text
This operation declares an unsupported transform: jwe-encrypt.
Documentation is available, but automatic Action generation is disabled.
```

## 13. Schema Tree

### 13.1 Purpose

`SchemaTree` is a shared platform component, not an API Reference-only widget.

It will later support:

- Case Designer field guidance
- Assertion path selection
- Extraction path selection
- generated example review
- AI context previews

### 13.2 Columns

```text
Field | Type | Required | Constraints / Default | Description
```

Example:

```text
customerName        string          yes     minLength: 1
customerReference   string          no
items               array<object>   yes     minItems: 1
  productId         string          yes
  quantity          integer         yes     minimum: 1
```

### 13.3 Primitive Types

Display:

- type
- format
- enum values
- default
- example
- minimum and maximum
- length constraints
- pattern
- nullable state

### 13.4 Objects

Display:

- properties
- required fields
- additional properties policy
- read-only and write-only state
- property descriptions

When `additionalProperties` is a schema, display:

```text
Map<string, ValueType>
```

When it is `false`, display:

```text
Additional properties are not allowed
```

### 13.5 Arrays

Display:

- `array<ItemType>`
- minimum and maximum items
- uniqueness
- item schema

The tree uses one conceptual item node and does not render generated array indexes.

### 13.6 `$ref`

The UI preserves the referenced schema name:

```text
shipment  Shipment
```

The user may expand the node or navigate to the Schema definition.

Reference cycles display a link-like cycle marker and do not expand automatically.

### 13.7 `allOf`

Default behavior:

- present a merged field view for readability
- preserve origin metadata for each field
- provide an advanced view of the original composition

Conflicting properties produce a warning rather than being silently overwritten.

### 13.8 `oneOf` and `anyOf`

The UI does not merge alternatives.

It provides a variant selector:

```text
Variant: [Card Payment v]
```

If a discriminator is available, use its mapping as the variant label.

### 13.9 Nullable

Normalize:

```yaml
nullable: true
```

and:

```yaml
type:
  - string
  - 'null'
```

to:

```text
string | null
```

The raw schema remains available for exact dialect details.

## 14. Example Generation

### 14.1 Priority

The generator uses:

1. explicit media type example
2. explicit schema example
3. property example
4. default
5. first enum value
6. format-aware placeholder
7. type-aware placeholder

### 14.2 Type Defaults

Suggested placeholders:

| Schema | Generated value |
|---|---|
| `string` | `"string"` |
| `string`, `date` | `"2026-07-29"` |
| `string`, `date-time` | `"2026-07-29T00:00:00Z"` |
| `string`, `uuid` | deterministic placeholder UUID |
| `integer` | minimum when valid, otherwise `0` |
| `number` | minimum when valid, otherwise `0` |
| `boolean` | `true` |
| `array` | one generated item |
| `object` | generated required properties, then example-bearing optional properties |

Generated examples must be deterministic. Reopening the same schema must not produce random data.

### 14.3 Required and Optional Properties

Default example generation includes:

- every required property
- optional properties with explicit examples or defaults

The UI may offer:

```text
[Required fields only] [Include optional fields]
```

## 15. Case Designer Integration

### 15.1 Operation Selection

When a user selects an Operation:

1. update the Operation snapshot
2. fetch its `OperationContract`
3. determine whether a business request is available
4. determine whether protocol Actions can be generated
5. offer the recommended configuration

### 15.2 Recommendation Prompt

For an empty Step:

```text
This operation defines a business request and an encrypted wire protocol.

Business JSON
-> PGP sign and encrypt
-> embed at /payload

[Apply recommended setup] [Select operation only]
```

For a Step with existing request data or Actions:

```text
Applying the recommended setup will replace the current Request Body and
protocol Actions.

[Review changes] [Replace] [Cancel]
```

Existing user content must never be silently overwritten.

### 15.3 Generated Request

The Case continues to store the business request:

```yaml
request:
  body:
    customerName: Automation Partner
    customerReference: AUTO-ORDER-1001
    items:
      - productId: P100
        quantity: 2
```

The user does not edit ciphertext.

### 15.4 Generated Request Actions

For:

```yaml
steps:
  - type: pgp-sign-encrypt
    armored: true
  - type: json-embed
    pointer: /payload
```

generate:

```yaml
actions:
  beforeSend:
    - type: crypto.pgp.signAndEncrypt
      name: Protect request payload
      input:
        source: request.body
        format: json
      output:
        target: artifacts.encryptedPayload
        format: text

    - type: body.buildJson
      name: Build wire request body
      template:
        payload: ${artifacts.encryptedPayload}
      output:
        target: request.body
```

### 15.5 Generated Response Actions

For:

```yaml
steps:
  - type: json-extract
    pointer: /payload
  - type: pgp-decrypt-verify
    outputMediaType: application/json
```

generate:

```yaml
actions:
  afterReceive:
    - type: crypto.pgp.decryptAndVerify
      name: Read secure response payload
      input:
        source: response.body
        path: $.payload
      output:
        target: decrypted.securePayload
        format: json
```

The Action generator owns deterministic artifact and decrypted output names. Name collisions are resolved before modifying the Step.

### 15.6 Assertion Assistance

When a business response schema exists, the Assertion editor may offer fields under:

```text
decrypted.securePayload
```

The wire response remains available under:

```text
response.body
```

This allows assertions against both envelope metadata and decrypted business data.

## 16. Execution and Validation

### 16.1 Execution Order

```text
Resolve variables and runtime context
    -> validate business request
    -> execute beforeSend Actions
    -> validate wire request
    -> send HTTP request
    -> validate wire response
    -> execute afterReceive Actions
    -> validate business response
    -> evaluate assertions
    -> perform extraction
```

This extends the existing Case DSL execution model without hiding the Action pipeline.

### 16.2 Validation Errors

Use distinct error codes:

- `BUSINESS_REQUEST_SCHEMA_INVALID`
- `REQUEST_TRANSFORM_FAILED`
- `WIRE_REQUEST_SCHEMA_INVALID`
- `WIRE_RESPONSE_SCHEMA_INVALID`
- `RESPONSE_TRANSFORM_FAILED`
- `BUSINESS_RESPONSE_SCHEMA_INVALID`

The user-facing error identifies:

- validation layer
- schema path
- instance path
- expected constraint
- observed value, subject to redaction

### 16.3 Schema Dialects

OpenAPI 3.0 Schema Objects and OpenAPI 3.1 JSON Schema objects are not identical.

The validator must:

- detect the imported OpenAPI version
- apply a compatible schema dialect
- normalize only presentation concepts such as nullable display
- avoid rewriting the canonical source

## 17. Execution Evidence

Each secure Step should expose:

```text
Request
├── Business Request
├── Request Actions
└── Wire Request

Response
├── Wire Response
├── Response Actions
└── Business Response

Validation
├── Business Request Schema
├── Wire Request Schema
├── Wire Response Schema
└── Business Response Schema
```

The raw wire response is never overwritten by a decrypted response.

Evidence labels must use business language rather than internal storage paths as the primary display. Advanced details may show:

- `request.body`
- `artifacts.encryptedPayload`
- `response.body`
- `decrypted.securePayload`

## 18. Security

### 18.1 External References

Do not fetch external `$ref` targets by default. This prevents:

- SSRF
- access to internal metadata services
- unexpected credential use
- non-deterministic imports
- unbounded downloads

### 18.2 Secrets

OpenAPI documents and platform extensions must never contain:

- private keys
- passphrases
- access tokens
- runtime Profile secret values

The extension may state that a runtime Profile is required, but key material remains in the Profile catalog.

### 18.3 Markdown and HTML

OpenAPI descriptions may contain Markdown. Rendered descriptions must sanitize unsafe HTML and URLs.

### 18.4 Limits

Protect parsing and rendering with:

- upload size limits
- YAML alias limits
- schema depth limits
- node count limits
- string display limits
- example generation limits

### 18.5 Evidence Redaction

Business request and response data may be more sensitive than ciphertext.

The existing evidence redaction policy applies independently to:

- business payloads
- wire envelopes
- Action inputs
- Action outputs
- validation errors

## 19. Performance

### 19.1 Loading Strategy

- API Spec lists return summaries only.
- The full Spec contract loads when its workbench tab opens.
- A single Operation contract loads when Case Designer selects it.
- Examples generate when their view is opened.
- Schema nodes resolve when expanded.

### 19.2 Rendering

For large documents:

- virtualize long Operation lists
- collapse Schema nodes by default after a reasonable depth
- memoize Schema node formatting
- avoid cloning the full Schema Registry per Operation
- keep search indexes compact

### 19.3 Cache

Cache immutable normalized records by Spec version. Do not cache decrypted runtime payloads as part of the documentation cache.

## 20. Accessibility and Internationalization

The native viewer must:

- support keyboard navigation in the Operation list
- expose selected and expanded state with ARIA attributes
- avoid communicating HTTP method or diagnostic severity by color alone
- provide accessible labels for icon-only actions
- maintain visible focus indicators
- support English and Chinese through the existing i18n resources
- keep schema tables usable at common desktop zoom levels

Protocol diagrams must have a text equivalent.

## 21. Testing Strategy

### 21.1 Backend Unit Tests

Cover:

- plain OpenAPI Operation
- Operation with business request schema
- Operation with business response schema
- request and response transforms
- legacy `x-plaintext-*`
- missing schema references
- cyclic schema references
- `allOf`
- `oneOf`
- arrays
- maps
- OpenAPI 3.0 nullable
- OpenAPI 3.1 null types
- unsupported extension version
- unsupported transform step
- invalid JSON Pointer
- external reference diagnostics
- cache invalidation

### 21.2 Controller Tests

Verify:

- project ownership checks
- Spec ownership checks
- whole contract response
- single Operation contract response
- source endpoint remains unchanged
- missing resources return the platform-standard error

### 21.3 Frontend Unit and Component Tests

Verify:

- tag grouping
- Operation search
- business request selected by default
- wire request switching
- required field display
- enum and constraints
- cyclic `$ref` rendering
- `allOf` merged view
- `oneOf` variant selection
- response status selection
- protocol pipeline order
- diagnostics
- loading state
- empty states
- contract error with source fallback

### 21.4 Case Designer Tests

Verify:

- selecting a normal Operation changes only the Operation snapshot
- selecting a secure Operation offers recommended setup
- accepting generates the business body
- accepting generates request Actions in the correct order
- accepting generates response Actions in the correct order
- existing user content is not silently overwritten
- unsupported transforms disable automatic generation
- generated artifact names do not collide

### 21.5 Integration Tests

Use the mock service OpenAPI to verify:

1. import the OpenAPI document
2. open `createFulfillmentOrder`
3. see `customerName` and `items` in Business Request
4. see `payload` in Wire Request
5. see the PGP transform pipeline
6. create a Scenario
7. confirm the generated DSL uses the business body
8. execute the Scenario
9. confirm the final wire request contains armored PGP data
10. decrypt the response
11. assert against the business `Order` response

## 22. Migration Plan

### 22.1 Phase 1: Contract Foundation

- document `x-platform-payload` version 1
- implement normalized contract records
- implement local reference resolution
- implement extension compatibility adapter
- add contract endpoints
- add parser and controller tests

Stoplight remains unchanged during this phase.

### 22.2 Phase 2: Native Read-Only Reference

- implement Operation navigation
- implement Parameters
- implement Request business and wire views
- implement Responses
- implement Schema Tree
- implement Protocol view
- implement source fallback

Expose:

```text
[Native Reference] [Legacy Reference]
```

The native view should be the default for secure OpenAPI documents after it is stable.

### 22.3 Phase 3: Case Designer Integration

- fetch Operation Contract after selection
- generate business examples
- generate request Actions
- generate response Actions
- add replacement confirmation
- add contract-aware assertion source suggestions

### 22.4 Phase 4: Execution Validation and Evidence

- add business request validation
- add wire request validation
- add wire response validation
- add business response validation
- expose four validation layers in evidence
- expose business and wire payloads separately

### 22.5 Phase 5: Stoplight Removal

Before removal, verify every imported project OpenAPI uses only supported features or has an acceptable source fallback.

Then:

- remove the legacy reference tab
- replace it with the source view
- delete `platform-ui/src/components/StoplightApi.tsx`
- remove `@stoplight/elements`
- remove Stoplight styles
- remove Stoplight hash router behavior
- run the complete frontend and integration test suite

## 23. Acceptance Criteria

The feature is complete when:

- secure Operations show their business request by default
- users can switch to the exact wire request
- request transformations are understandable without reading extension YAML
- response transformations are status-specific
- plain OpenAPI Operations remain readable
- local `$ref`, objects, arrays, maps, `allOf`, and `oneOf` render correctly
- cyclic references do not crash or hang the UI
- invalid extensions produce actionable diagnostics
- users can create a business-level Scenario from an Operation
- generated Actions match Case DSL v1
- existing Step content is never overwritten without confirmation
- execution evidence separates business and wire payloads
- original OpenAPI YAML remains viewable and downloadable
- no external `$ref` is fetched implicitly
- Stoplight can be removed without losing required platform workflows

## 24. Expected File Changes

### Backend

```text
platform-api/src/main/java/com/example/poc/platform/api/ApiSpecService.java
platform-api/src/main/java/com/example/poc/platform/web/ApiSpecController.java
platform-api/src/main/java/com/example/poc/platform/openapi/**
platform-api/src/test/java/com/example/poc/platform/openapi/**
```

### Frontend

```text
platform-ui/src/api/platformApi.ts
platform-ui/src/types/platform.ts
platform-ui/src/pages/WorkbenchPage.tsx
platform-ui/src/components/CaseDesigner.tsx
platform-ui/src/components/openapi/**
platform-ui/src/i18n/resources.ts
platform-ui/src/index.css
```

### Removal Phase

```text
platform-ui/src/components/StoplightApi.tsx
platform-ui/package.json
platform-ui/package-lock.json
```

### OpenAPI Assets

```text
mock-service/src/main/resources/openapi/mock-service-openapi.yaml
```

## 25. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Reimplementing too much OpenAPI behavior | Explicitly support the platform subset and retain source fallback |
| Schema reference cycles | Registry-based resolution with cycle detection |
| Large documents cause slow rendering | Lazy loading, caching, virtualized navigation, collapsed schema nodes |
| Platform extension drifts across services | Versioned extension contract and import validation |
| Generated Actions are incorrect | Do not infer transforms from descriptions; require supported declarative steps |
| Existing test content is overwritten | Review and confirmation before applying recommendations |
| Business payload exposure increases sensitivity | Redaction, access control, and evidence policy at every layer |
| External references create security issues | No implicit external fetching |
| Stoplight removal loses an uncommon feature | Run an inventory against imported specs before final removal |

## 26. Future Extensions

The normalized contract can later support:

- XML business payload rendering
- JWS and JWE transforms
- compression
- additional envelope formats
- contract-aware assertion builders
- response field extraction suggestions
- schema coverage analysis
- automatic boundary-value test generation
- OpenAPI change impact analysis
- AI generation constrained by business and wire schemas

Future transforms must remain declarative, versioned, validated, and mappable to visible Case Actions.

## 27. Final Product Principle

The native Operation Inspector is not a documentation replacement for its own sake. It is the shared contract surface between imported APIs, test authoring, protocol processing, runtime validation, execution evidence, and AI assistance.

The user should work primarily with business payloads. The platform must preserve and expose wire payloads and transforms so that execution remains accurate, inspectable, and debuggable.
