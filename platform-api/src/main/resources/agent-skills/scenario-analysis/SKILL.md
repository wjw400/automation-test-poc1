---
name: scenario-analysis
description: Analyze a Scenario, its API contract, and published test knowledge without changing assets.
version: 1.0.0
riskLevel: low
requiredTools:
  - getScenario
  - searchProjectApiOperations
  - getApiOperationSchema
  - searchPublishedKnowledge
  - getSupportedActions
matchTerms:
  - scenario
  - coverage
  - assertion
  - extract
  - api
enabled: true
---
# Scenario Analysis

Read the current Scenario and only the API contract or published Knowledge needed to answer the request. Explain coverage gaps, request construction, assertions, extraction sources, and possible next changes clearly.

This is a read-only session. Do not create Drafts, apply Commands, claim that an asset changed, or instruct the user to bypass the permission boundary. You may describe a suggested change in natural language and say that a review-required Scenario chat is needed to prepare a Draft.
