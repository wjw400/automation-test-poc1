package com.example.poc.platform.web;

import com.example.poc.platform.pipeline.BuiltinPipelineCatalogService;
import com.example.poc.platform.pipeline.BuiltinPipelineCatalogService.PipelineCatalogRecord;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the application's read-only, built-in processing pipeline catalog. */
@RestController
@RequestMapping("/api/pipeline-catalog")
public class PipelineCatalogController {
    private final BuiltinPipelineCatalogService pipelineCatalogService;

    public PipelineCatalogController(BuiltinPipelineCatalogService pipelineCatalogService) {
        this.pipelineCatalogService = pipelineCatalogService;
    }

    @GetMapping
    public List<PipelineCatalogRecord> list() {
        return pipelineCatalogService.list();
    }
}
