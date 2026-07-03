package com.example.poc.mockservice.controller;

import com.example.poc.mockservice.service.SecureMockService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/secure")
public class SecureMockController {
    private final SecureMockService secureMockService;

    public SecureMockController(SecureMockService secureMockService) {
        this.secureMockService = secureMockService;
    }

    @PostMapping(path = "/raw", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> raw(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody String body) {
        return secureMockService.handleRaw(profile, body);
    }

    @PostMapping(path = "/json-field", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jsonField(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestParam(name = "field", defaultValue = "payload") String field,
            @RequestBody Map<String, Object> request) {
        return secureMockService.handleJsonField(profile, field, request);
    }

    @PostMapping(path = "/multi-step/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> create(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureMockService.createEntity(profile, request);
    }

    @PostMapping(path = "/multi-step/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> query(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureMockService.queryEntity(profile, request);
    }

    @PostMapping(path = "/error-case", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> errorCase(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestParam(name = "mode", defaultValue = "bad-payload") String mode) {
        return secureMockService.errorCase(profile, mode);
    }
}
