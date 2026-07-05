# Repository Working Rules

This repository contains an automation testing platform POC. For UI work under `platform-ui/`, use `design/platform-ui-guidelines.md` as the high-level design reference.

## Product Mindset

- Treat the product as an internal engineering workbench.
- Optimize for clarity, trust, repeat use, and debugging flow.
- Avoid marketing-page patterns unless the user explicitly asks for them.

## Design Judgment

- Start from the user's task, not from the backend data model.
- Make the primary workflow obvious before exposing secondary configuration.
- Keep technical power available, but avoid making raw YAML, raw JSON, ids, and metadata the first thing users must deal with.
- Prefer structured, guided interactions when the data model is known.

## Platform Character

- The UI should feel calm, utilitarian, and precise.
- Visual design should support scanning and decision-making.
- Decoration should be minimal and purposeful.
- Dense information is acceptable when it is organized and readable.

## Consistency

- Reuse existing page patterns and components where they fit.
- Let new patterns emerge only when the current task genuinely needs them.
- When improving a page, reduce unnecessary surface area before adding more controls.

## Review Standard

Before finalizing UI work, sanity-check that:

- the main user task is easy to find
- secondary metadata is not competing with primary work
- the page feels like part of the same platform
- the design supports future API-driven and AI-assisted test generation
