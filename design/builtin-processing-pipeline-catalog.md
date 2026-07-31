# Built-in Processing Pipeline Catalog

## Status

This is the current implementation baseline. It replaces the encryption-specific
`x-platform-payload` model for new OpenAPI documents. The legacy
`x-plaintext-request-schema` and `x-plaintext-response-schema` fields remain a
read-only compatibility fallback while older documents are migrated.

## Goal

An OpenAPI operation must show both of these contracts without exposing a
cryptographic envelope as the primary business contract:

- the business payload that an API user creates or receives;
- the wire payload actually sent over HTTP.

Processing is not limited to encryption. It can also include encoding, signing,
compression, envelope construction, parsing, or response verification.

## Boundary

The platform exposes a fixed, reviewed set of built-in processing pipelines.
Pipeline YAML is an application resource and is loaded only at application
startup. There is no external catalog, database catalog, UI editing, hot reload,
custom operator, scripting, or arbitrary Java class reference in v1.

```text
OpenAPI operation
  -> pipeline reference plus optional overrides
  -> built-in Pipeline Catalog YAML
  -> approved action/operator identifiers
  -> application runtime capability
```

New compositions of existing actions are introduced by adding a reviewed catalog
YAML resource and releasing the application. New primitive behavior requires an
application code change as well as a catalog entry.

## OpenAPI extension

Use `x-platform-processing` on an operation:

```yaml
x-platform-processing:
  request:
    businessSchema:
      $ref: '#/components/schemas/CreateOrderPayload'
    pipeline: system/pgp-json-envelope-request@1
    options:
      inputs:
        payload: business.body.order
      outputs:
        message: wire.body
      parameters:
        armored: true

  response:
    '200':
      businessSchema:
        $ref: '#/components/schemas/Order'
      pipeline: system/pgp-json-envelope-response@1
```

Only `businessSchema` and `pipeline` are normally required. `options` is
optional and has three reserved sections:

| Section | Purpose |
| --- | --- |
| `options.inputs` | Override a declared input port's default location. |
| `options.outputs` | Override a declared output port's default location. |
| `options.parameters` | Override a declared pipeline parameter default. |

`inputs`, `outputs`, and `parameters` are intentionally kept separate inside
`options`. This prevents a future pipeline parameter from colliding with a port
name and makes the meaning of every override deterministic.

The reference format is `namespace/name@version`. A published reference is
immutable; `@latest` is not supported in OpenAPI.

## Built-in catalog resource

Catalog resources are packaged in participating application modules:

```text
<module>/src/main/resources/META-INF/platform/pipelines/*.yaml
```

For the current application, Platform API ships the PGP pipeline descriptors while
the PGP module provides the underlying crypto capability:

```text
platform-api/
  src/main/resources/META-INF/platform/pipelines/
    pgp-json-envelope-request-v1.yaml
    pgp-json-envelope-response-v1.yaml
```

Each resource uses this restricted shape:

```yaml
apiVersion: platform.example.com/v1
kind: Pipeline
metadata:
  id: system/pgp-json-envelope-request
  version: 1
  displayName: PGP JSON request envelope

spec:
  direction: REQUEST
  requiresRuntimeProfile: true
  inputs:
    payload:
      type: object
      defaultLocation: business.body
  outputs:
    message:
      type: object
      defaultLocation: wire.body
  parameters:
    armored:
      type: boolean
      default: true
  steps:
    - id: encrypt
      uses: crypto.pgp.signAndEncrypt@1
    - id: envelope
      uses: body.buildJson@1
  flow:
    - from: inputs.payload
      to: steps.encrypt.payload
    - from: parameters.armored
      to: steps.encrypt.armored
    - from: steps.encrypt.message
      to: steps.envelope.encryptedMessage
    - from: steps.envelope.body
      to: outputs.message
```

`uses` is an allow-listed action/operator identifier. It is not a class name and
cannot contain executable script content. The catalog loader rejects unknown
identifiers. `flow` is descriptive, read-only data lineage used by the UI. Its
endpoints follow `inputs.<name>`, `parameters.<name>`, `steps.<id>.<port>`, and
`outputs.<name>`. This makes parameter and port usage explicit without putting
step bindings into an application's OpenAPI document.

## Startup validation

The application scans `classpath*:META-INF/platform/pipelines/*.yaml` during
startup. Startup fails when a catalog entry has an invalid API version or kind,
an invalid or duplicate `id@version`, no declared input/output/step, duplicate
step IDs, an unknown operator, an invalid parameter enum default, an invalid or
out-of-order flow edge, an unconsumed input/parameter, or an unproduced output.

When an imported OpenAPI document references an unavailable pipeline or an
unknown input, output, or parameter override, the operation contract exposes a
diagnostic. The API reference UI shows that diagnostic instead of silently
guessing the processing behavior.

## API and UI

The read-only catalog endpoint is:

```text
GET /api/pipeline-catalog
```

The OpenAPI contract endpoint resolves referenced catalog entries so the API UI
can display the pipeline name and version, required runtime profile, processing
steps, declared ports, default locations, and effective option values. The UI
also traces every input and parameter to the exact step port that consumes it,
and each step result to the next step or declared output. On an operation page,
the same flow displays the effective default or operation override value. The
UI calls this area **Processing**, not **Protocol**, because the model applies
to any controlled message transformation.

The UI is deliberately read-only in v1. It must not provide controls to create,
edit, publish, reload, or execute arbitrary pipeline YAML.

## Versioning and release rules

- A pipeline behavior change creates a new version.
- Existing `id@version` resources are never edited after release.
- New catalog resources and any new operator code are reviewed and tested as a
  normal application release.
- Secrets are never stored in a pipeline resource. A pipeline can require a
  runtime profile, but key material remains in the platform profile/secret path.

## Non-goals for v1

- External or Git-synced catalog directories
- Database persistence for pipelines
- Project-scoped or user-authored pipelines
- UI pipeline editor or publication workflow
- Scripted steps, dynamic code loading, or arbitrary class references
- Runtime hot reload
