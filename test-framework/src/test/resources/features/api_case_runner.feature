Feature: Structured API test case runner

  Scenario: Execute a YAML API case from Cucumber
    Given execute api test case "echo/create-user.yaml"

  Scenario: Execute a multi-step YAML API case from Cucumber
    Given execute api test case "echo/create-user-and-lookup.yaml"

  Scenario: Execute an encrypted order fulfillment lifecycle
    Given execute api test case "mock/secure-order-fulfillment.yaml"
