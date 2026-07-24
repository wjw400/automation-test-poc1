package com.example.poc.platform.web;

import com.example.poc.platform.run.RunExecutionService;
import com.example.poc.platform.run.RunExecutionService.StartSuiteRunRequest;
import com.example.poc.platform.run.RunExecutionService.SuiteRunRecord;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suite-runs")
public class SuiteRunController {
    private final RunExecutionService runExecutionService;

    public SuiteRunController(RunExecutionService runExecutionService) {
        this.runExecutionService = runExecutionService;
    }

    @GetMapping
    public List<SuiteRunRecord> listSuiteRuns() {
        return runExecutionService.listSuiteRuns();
    }

    @GetMapping("/{suiteRunId}")
    public SuiteRunRecord getSuiteRun(@PathVariable("suiteRunId") String suiteRunId) {
        return runExecutionService.getSuiteRun(suiteRunId);
    }

    @PostMapping
    public SuiteRunRecord startSuiteRun(@Valid @RequestBody StartSuiteRunRequest request) {
        return runExecutionService.startSuiteRun(request);
    }
}
