# Platform UI Guidelines

## 1. Product Positioning

The platform UI should be designed as an internal engineering workbench for API automation testing.

It should help users:

- understand API test assets
- compose reliable test scenarios
- configure execution context
- inspect execution evidence
- evolve cases toward AI-assisted generation

The UI should not feel like a marketing site, a generic dashboard, or a simple YAML file editor.

## 2. Design Principles

### 2.1 Task First

Design around the user's current task before exposing the underlying data model.

For example, when editing a test case, the primary task is building and validating a scenario. Metadata, ids, and raw DSL details should support that task rather than dominate it.

### 2.2 Progressive Disclosure

Show essential information first and reveal advanced configuration when needed.

Users should be able to start with a guided path, then drop into lower-level configuration when the case becomes complex.

### 2.3 Evidence-Oriented

Testing workflows should make evidence easy to inspect.

Request, response, decrypted payloads, validations, extracted values, and failure reasons should be treated as product concepts, not incidental logs.

### 2.4 API-Aware

Imported OpenAPI operations are first-class assets.

Case authoring should preserve the relationship between scenarios, steps, operations, request schemas, response schemas, and validations.

### 2.5 Structured Over Raw

Prefer structured editing for known concepts such as request parts, validations, extraction rules, profiles, and runtime context.

Raw YAML or JSON remains important for power users, but should usually be an advanced path rather than the default experience.

## 3. Visual Direction

The visual tone should be:

- calm
- technical
- focused
- trustworthy
- dense but readable

Use visual hierarchy to clarify work, not to decorate the page.

Avoid visual patterns that make the product feel like a landing page, such as oversized hero areas, decorative gradients, ornamental backgrounds, and large presentation cards with little operational value.

## 4. Layout Philosophy

Prefer stable workbench layouts where different regions have clear responsibilities.

Typical responsibilities include:

- navigation or asset selection
- primary editing or inspection
- contextual reference or execution evidence

The exact layout may vary by page, but the user should always understand where to select, where to edit, and where to inspect results.

## 5. Information Hierarchy

Prioritize information by how directly it supports the user's current action.

High-priority concepts include:

- selected API operation
- scenario steps
- request composition
- validations
- extraction rules
- runtime profile or environment
- execution evidence

Lower-priority concepts include:

- raw identifiers
- generated codes
- status metadata
- DSL versions
- storage-oriented fields

Lower-priority information should be available without taking over the primary workflow.

## 6. Interaction Philosophy

The platform can borrow familiar API-client patterns when they reduce learning cost, especially around request editing.

However, the product should remain a testing platform, not just an API sender.

This means validation, extraction, encryption/profile handling, and evidence review deserve first-class treatment.

## 7. Industry References

Use mature design systems as references, not as rigid templates.

The platform should primarily draw from:

- Ant Design for implementation foundations, component behavior, form patterns, tables, modals, menus, and theming tokens.
- Atlassian Design System for workbench navigation, information hierarchy, product structure, and collaboration-oriented interaction patterns.
- IBM Carbon and PatternFly for enterprise density, configuration-heavy screens, operational clarity, and evidence-oriented technical interfaces.
- GitLab Pajamas and GitHub Primer for developer-tool patterns, code-adjacent workflows, status-heavy lists, logs, traces, and repository-like asset navigation.
- Postman and Stoplight for API authoring mental models, request editing, operation reference, and API documentation browsing.

Do not copy one system wholesale. Select patterns based on the current workflow:

- Use Ant Design as the practical component base because the project already uses it.
- Use Atlassian-like thinking for navigation and cross-page product structure.
- Use Carbon or PatternFly-like restraint for dense enterprise screens.
- Use GitLab or GitHub-like behavior for developer-facing details, technical artifacts, and execution traces.
- Use Postman or Stoplight-like patterns where users are composing or inspecting API requests.

Material Design and Fluent can be useful secondary references, but they should not define the main product character unless the project intentionally shifts toward those ecosystems.

## 8. Consistency

Favor consistency through reusable patterns rather than rigid page templates.

When adding or changing UI:

- reuse existing components and layout patterns where they still fit
- adjust shared patterns when multiple pages would benefit
- avoid one-off visual exceptions unless the workflow truly needs them

## 9. Design Review Questions

Before finalizing a UI change, ask:

- What is the main user task on this screen?
- Is that task visually and interactively primary?
- Which information is advanced or secondary?
- Can a user understand the state of the test scenario without reading raw YAML?
- Does the page help users connect APIs, steps, validations, and evidence?
- Does the result still feel like one coherent platform?
- Which industry reference is most relevant to this workflow, and why?
