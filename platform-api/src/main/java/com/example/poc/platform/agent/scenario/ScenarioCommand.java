package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.CaseActionSpec;
import com.example.poc.engine.ExtractValueSpec;
import com.example.poc.engine.ValidationSpec;

public sealed interface ScenarioCommand permits
        ScenarioCommand.SetRequestHeader,
        ScenarioCommand.RemoveRequestHeader,
        ScenarioCommand.SetQueryParameter,
        ScenarioCommand.SetPathParameter,
        ScenarioCommand.SetRequestBody,
        ScenarioCommand.SetRequestBodyValue,
        ScenarioCommand.AddBeforeAction,
        ScenarioCommand.AddAfterAction,
        ScenarioCommand.AddAssertion,
        ScenarioCommand.RemoveAssertion,
        ScenarioCommand.AddExtraction,
        ScenarioCommand.RemoveExtraction,
        ScenarioCommand.AddStep,
        ScenarioCommand.RemoveStep,
        ScenarioCommand.MoveStep,
        ScenarioCommand.RenameStep,
        ScenarioCommand.SelectOperation {

    record SetRequestHeader(String stepId, String name, String value) implements ScenarioCommand {}
    record RemoveRequestHeader(String stepId, String name) implements ScenarioCommand {}
    record SetQueryParameter(String stepId, String name, Object value) implements ScenarioCommand {}
    record SetPathParameter(String stepId, String name, Object value) implements ScenarioCommand {}
    record SetRequestBody(String stepId, Object body) implements ScenarioCommand {}
    record SetRequestBodyValue(String stepId, String path, Object value) implements ScenarioCommand {}
    record AddBeforeAction(String stepId, CaseActionSpec action) implements ScenarioCommand {}
    record AddAfterAction(String stepId, CaseActionSpec action) implements ScenarioCommand {}
    record AddAssertion(String stepId, ValidationSpec assertion) implements ScenarioCommand {}
    record RemoveAssertion(String stepId, int index) implements ScenarioCommand {}
    record AddExtraction(String stepId, String name, ExtractValueSpec extraction) implements ScenarioCommand {}
    record RemoveExtraction(String stepId, String name) implements ScenarioCommand {}
    record AddStep(String stepId, String afterStepId, Integer targetIndex, ApiStep step) implements ScenarioCommand {}
    record RemoveStep(String stepId) implements ScenarioCommand {}
    record MoveStep(String stepId, int targetIndex) implements ScenarioCommand {}
    record RenameStep(String stepId, String name) implements ScenarioCommand {}
    record SelectOperation(String stepId, String operationId) implements ScenarioCommand {}
}
