Feature: Structured API test case runner

  Scenario: Execute a YAML API case from Cucumber
    Given execute api test case "echo/create-user.yaml"

  Scenario: Execute a multi-step YAML API case from Cucumber
    Given execute api test case "echo/create-user-and-lookup.yaml"

  Scenario: Execute a mixed raw and encrypted response case from Cucumber
    Given execute api test case "echo/mixed-raw-and-encrypted-response.yaml"

  Scenario: Execute a mixed raw and encrypted response case from Cucumber
    Given execute api test case "mock/secure-mock-mixed-response.yaml"
