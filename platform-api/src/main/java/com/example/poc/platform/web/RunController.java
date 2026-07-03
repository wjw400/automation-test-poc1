package com.example.poc.platform.web;

import com.example.poc.platform.run.RunExecutionService;
import com.example.poc.platform.run.RunExecutionService.RunRecord;
import com.example.poc.platform.run.RunExecutionService.StartRunRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final RunExecutionService runExecutionService;

    public RunController(RunExecutionService runExecutionService) {
        this.runExecutionService = runExecutionService;
    }

    @GetMapping
    public List<RunRecord> listRuns() {
        return runExecutionService.listRuns();
    }

    @GetMapping("/{runId}")
    public RunRecord getRun(@PathVariable("runId") String runId) {
        return runExecutionService.getRun(runId);
    }

    @PostMapping
    public RunRecord startRun(@Valid @RequestBody StartRunRequest request) {
        return runExecutionService.startRun(request);
    }
}
