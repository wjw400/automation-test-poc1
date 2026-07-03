# API Automation Test POC

This POC keeps Cucumber as the test entry point while moving repetitive API scenario details into structured YAML files.
The repository is now split into two Maven modules so the test framework and the mock secure backend can evolve independently.

## Why This Shape

Existing Cucumber scenarios can continue to run as-is. New or migrated scenarios can use one generic step:

```gherkin
Given execute api test case "echo/create-user.yaml"
```

The YAML file becomes the source of truth for request data, assertions, and extracted values. This structure is easier for humans to review and easier for AI tools to generate because the allowed fields are explicit.

## Project Layout

```text
pgp-support/src/main/java/com/example/poc/pgp                   Shared OpenPGP utilities for both modules
mock-service/src/main/java/com/example/poc/mockservice      Spring Boot mock secure backend
mock-service/src/main/resources                             Backend config and test PGP key files
test-framework/src/test/java/com/example/poc/engine         YAML loader, HTTP executor, assertion engine
test-framework/src/test/java/com/example/poc/steps          Generic Cucumber step
test-framework/src/test/java/com/example/poc/runner         Cucumber JUnit Platform suite
test-framework/src/test/resources/features                  Cucumber feature files
test-framework/src/test/resources/api-cases                 Structured API test cases
docs/api-case-schema.json               Draft schema for AI generation and validation
```

## YAML Case Shape

Single-request cases can use the shortcut shape:

```yaml
name: Create user
description: Optional human-readable context.
tags:
  - smoke

variables:
  baseUrl: https://postman-echo.com
  userName: Alice
  email: poc_${random.uuid}@example.com

profiles:
  default:
    senderPrivateKey: classpath:keys/client-private.asc
    recipientPublicKey: classpath:keys/server-public.asc

request:
  method: POST
  baseUrl: ${baseUrl}
  path: /post
  headers:
    Content-Type: application/json
  query:
    source: cucumber
  body:
    name: ${userName}
    email: ${email}

expect:
  status: 200
  jsonPath:
    $.json.name: ${userName}

extract:
  echoedName: $.json.name
```

Multi-request cases use `steps`. Each step has its own request, validations, and extracted values. Extracted values are stored in the shared case context and can be referenced by later steps.

```yaml
name: Create user and lookup by extracted value

variables:
  baseUrl: https://postman-echo.com
  userName: Alice
  email: poc_${random.uuid}@example.com

profiles:
  clientToServer:
    senderPrivateKey: classpath:keys/client-private.asc
    recipientPublicKey: classpath:keys/server-public.asc

steps:
  - name: Create user through echo API
    profile: clientToServer
    request:
      method: POST
      baseUrl: ${baseUrl}
      path: /post
      headers:
        Content-Type: application/json
      body:
        name: ${userName}
        email: ${email}
    expect:
      status: 200
      jsonPath:
        $.json.email: ${email}
    extract:
      createdEmail: $.json.email

  - name: Lookup user by extracted email
    profile: clientToServer
    request:
      method: GET
      baseUrl: ${baseUrl}
      path: /get
      query:
        email: ${createdEmail}
    expect:
      status: 200
      jsonPath:
        $.args.email: ${createdEmail}
```

By default, a failed step stops the remaining steps. Use `continueOnFailure: true` on a step only when later steps are still meaningful after that failure.

Each step must declare a `profile`. Profiles are defined once at case level and hold the key material or key references used for encryption and decryption.
The single-request shortcut shape implicitly uses the `default` profile.

```yaml
profiles:
  secureEcho:
    senderPublicKey: classpath:keys/server-public.asc
    senderPrivateKey: classpath:keys/client-private.asc
    senderPrivateKeyPassphrase: demo-client-passphrase
    recipientPublicKey: classpath:keys/server-public.asc
    recipientPrivateKey: classpath:keys/client-private.asc
    recipientPrivateKeyPassphrase: demo-client-passphrase

steps:
  - name: Validate plaintext and decrypted payload
    profile: secureEcho
```

Responses can now be validated from multiple sources:

- `raw`: the original HTTP response body
- `decrypted.<name>`: a named payload decrypted from one response field

Example:

```yaml
expect:
  status: 200
  decrypt:
    - name: securePayload
      input:
        path: $.json.payload
      algorithm: fake
      plaintextFormat: json
  validations:
    - source: raw
      path: $.json.code
      operator: equals
      expected: SUCCESS
    - source: decrypted.securePayload
      path: $.amount
      operator: equals
      expected: 100

extract:
  orderId:
    source: decrypted.securePayload
    path: $.orderId
```

For legacy `echo/*` examples, the framework still supports a fake decryptor with the format:

```text
ENCRYPTED({"amount":100})
```

The mock secure backend path is now designed for real PGP sign/encrypt and decrypt/verify.

Request-side PGP transform example:

```yaml
request:
  method: POST
  baseUrl: http://localhost:8080
  path: /secure/json-field
  body:
    customerId: C1
    amount: 88
  transform:
    type: pgp
    algorithm: pgp
    plaintextFormat: json
    output:
      mode: jsonField
      field: bizData
```

## Supported Variables

- `${random.uuid}`
- `${random.email}`
- `${time.now}`
- `${env.NAME}`
- `${customVariable}` from the case `variables` section

## Run

Install JDK 17 and Maven, then run:

```bash
mvn -pl test-framework test
```

To start the local mock backend:

```bash
mvn -pl mock-service spring-boot:run
```

The service listens on `http://localhost:8080` by default.

Available secure mock endpoints:

- `POST /secure/raw`
- `POST /secure/json-field?field=bizData`
- `POST /secure/multi-step/create`
- `POST /secure/multi-step/query`
- `POST /secure/error-case?mode=bad-payload`

The mock secure backend uses real armored PGP messages and shared test key material from each module's resources.
The old fake `ENCRYPTED(...)` format is still supported only by the legacy `echo/*` examples.

```text
ENCRYPTED({"customerId":"C1","amount":88})
```

The local service is meant to exercise:

- raw encrypted request bodies
- encrypted payloads nested in arbitrary JSON fields
- mixed plaintext and encrypted response fields
- multi-step resource creation and lookup
- deterministic error injection for report debugging

## Reports

Cucumber is only the migration-friendly shell in this design. The useful execution evidence is generated by the API case runner itself.

Each YAML case writes two files under:

```text
target/api-case-reports/
```

Report files include:

- Case name, case file, status, start time, and duration
- Step-by-step execution details
- Resolved request method, URL, headers, and body for each step
- Response status, headers, and body for each step
- Decrypted payloads for each step
- Every validation with type, target, expected value, actual value, and pass/fail status
- Extracted values for each step
- Error message when execution or validation fails

The runner currently writes both human-readable HTML and machine-readable JSON:

```text
target/api-case-reports/20260626-120000-000-create-user-through-echo-api-passed.html
target/api-case-reports/20260626-120000-000-create-user-through-echo-api-passed.json
```

## Migration Strategy

Start with existing feature files and replace only the most repetitive scenarios with the generic YAML execution step. Old Cucumber steps and new YAML-backed scenarios can live in the same suite during migration.

Suggested first migration target:

- One happy path create/update API
- One validation failure case
- One authorization or permission case

That is enough to compare line count, readability, and maintenance cost against the current step-heavy approach.
