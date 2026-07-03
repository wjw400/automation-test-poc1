package com.example.poc.steps;

import com.example.poc.engine.ApiCaseRunner;
import io.cucumber.java.en.Given;

public class ApiCaseSteps {
    private final ApiCaseRunner runner = new ApiCaseRunner();

    @Given("execute api test case {string}")
    public void executeApiTestCase(String caseFile) {
        runner.run(caseFile);
    }
}
