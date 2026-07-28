# Test Knowledge

## Purpose

Test Knowledge is a Markdown asset maintained inside a Workspace. It captures business and protocol rules that OpenAPI cannot express, such as PGP payload envelopes, encrypted response fields, state transitions, required test data, and cleanup constraints.

It is human-readable source material for test design and a controlled context source for the future Test Agent.

## Scope

Each document has exactly one scope:

- `PROJECT`: applies to the whole workspace.
- `API_SPEC`: supplements an imported OpenAPI document.
- `OPERATION`: applies to one imported operation.
- `SCENARIO`: explains constraints specific to one test scenario.
- `SUITE`: explains a multi-scenario business flow.

For every scope other than `PROJECT`, the document must reference an asset in the same Workspace.

## Lifecycle

- `DRAFT`: visible to editors but excluded from default Agent context.
- `PUBLISHED`: eligible for Agent context assembly.

Documents are versioned on every successful save. V1 uses direct structured filtering by scope, target, title, and tags; it deliberately does not use embeddings or a vector database.

## Agent Context Order

For a Scenario or Suite task, the context builder prefers published documents in this order:

1. Scenario or Suite scope
2. Operation scope
3. API Spec scope
4. Project scope

The Agent must cite the document used for a conclusion. When a lower-level document conflicts with a higher-level document, it must not guess; it should surface the conflict for user review.

## Security

Test Knowledge must not contain private keys, passphrases, access tokens, cookies, passwords, or database credentials. It may describe how a configured Profile is used, but may not expose secret material from that Profile.
