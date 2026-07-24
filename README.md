# API Automation Platform POC

This repository is a proof of concept for an API automation testing platform. Test Scenarios are authored as structured Case DSL v1 YAML, executed by a Java runner, and managed through a React UI and Spring Boot API.

## Modules

```text
pgp-support     Shared OpenPGP sign/encrypt and decrypt/verify implementation
mock-service    Real Spring Boot backend used to exercise secure API protocols
test-framework  Case DSL parser, Action pipeline, HTTP runner, assertions, and reports
platform-api    Platform catalog, execution service, PostgreSQL persistence, and Flyway migrations
platform-ui     React, TypeScript, and Ant Design engineering workbench
design          Product, architecture, UI, and Case DSL design documents
```

## Case DSL

Each Scenario contains one or more business-level HTTP Steps. Technical request and response processing stays inside ordered Action pipelines.

DSL v1 supports four Actions:

- `crypto.pgp.signAndEncrypt` in Before Send
- `data.encodeBase64` in Before Send
- `body.buildJson` in Before Send
- `crypto.pgp.decryptAndVerify` in After Receive

Actions use the Step Profile, falling back to the Scenario Profile. Action-level Profiles are not supported.

```yaml
dslVersion: v1
name: Create secure partner order
runtime:
  profile: secureProfile
variables:
  baseUrl: http://localhost:8081
steps:
  - name: Create partner order
    type: http
    operation:
      operationId: createFulfillmentOrder
      method: POST
      path: /secure/fulfillment/orders/create
    runtime: {}
    request:
      headers:
        Content-Type: application/json
      query: {}
      body:
        customerName: Automation Partner
        customerReference: AUTO-ORDER-1001
        items:
          - productId: P100
            quantity: 2
    actions:
      beforeSend:
        - type: crypto.pgp.signAndEncrypt
          input:
            source: request.body
            format: json
          output:
            target: artifacts.encryptedPayload
            format: text
        - type: body.buildJson
          template:
            payload: ${artifacts.encryptedPayload}
          output:
            target: request.body
      afterReceive:
        - type: crypto.pgp.decryptAndVerify
          input:
            source: response.body
            path: $.payload
          output:
            target: decrypted.order
            format: json
    response:
      status: 200
      assertions:
        - source: response.body
          path: $.code
          operator: equals
          expected: SUCCESS
        - source: decrypted.order
          path: $.status
          operator: equals
          expected: CREATED
      extract: {}
```

See [Case DSL v1](design/case-dsl-v1.md) for the complete execution contract.

## Start The Platform

Prerequisites: JDK 17, Maven, Node.js, npm, and Docker.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Start the secure mock backend:

```bash
mvn -pl mock-service spring-boot:run
```

The mock service listens on `http://localhost:8081`. Open that URL to use the embedded OrderFlow order fulfillment UI. Its OpenAPI document is stored at `mock-service/src/main/resources/openapi/mock-service-openapi.yaml` and contains the encrypted partner fulfillment operations.

OrderFlow uses an H2 in-memory database with seeded Products, Orders, Shipments, and a default `secureEcho` Partner Profile. The UI can restore business data, inspect recent API activity, download the service public key, and manage trusted partner public keys from Partner Access.

Start the platform API:

```bash
mvn -pl platform-api spring-boot:run
```

The API listens on `http://localhost:8090`. Flyway applies PostgreSQL schema migrations during startup.

Start the platform UI:

```bash
cd platform-ui
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite development server proxies `/api` requests to the platform API.

## Verification

Compile all backend modules:

```bash
mvn clean package -DskipTests
```

Run the self-contained real-PGP Action pipeline test:

```bash
mvn -pl test-framework -am test -Dtest=ApiCaseRunnerActionTest -Dsurefire.failIfNoSpecifiedTests=false
```

Build the UI:

```bash
cd platform-ui
npm run build
```

The Cucumber feature under `test-framework/src/test/resources/features` is an integration entry point. Its Postman Echo scenarios require internet access, and its mock-service scenarios require the local mock backend.

## Reports

The runner writes machine-readable JSON and human-readable HTML under:

```text
target/api-case-reports/
```

Each Step report includes:

- resolved Profile
- final Wire Request
- raw HTTP response
- ordered Action inputs, outputs, statuses, durations, and errors
- decrypted payloads
- assertion results
- extracted values

Platform runs persist the same Action evidence in PostgreSQL and expose it in the Run Detail UI.
