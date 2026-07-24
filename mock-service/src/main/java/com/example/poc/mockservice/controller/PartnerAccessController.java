package com.example.poc.mockservice.controller;

import com.example.poc.mockservice.service.PartnerKeyService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web-api/security")
public class PartnerAccessController {
    private final PartnerKeyService partnerKeyService;

    public PartnerAccessController(PartnerKeyService partnerKeyService) {
        this.partnerKeyService = partnerKeyService;
    }

    @GetMapping("/service-key")
    public Map<String, Object> serviceKey() {
        return partnerKeyService.servicePublicKey();
    }

    @GetMapping(path = "/service-key/download", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> downloadServiceKey() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orderflow-public.asc")
                .body(partnerKeyService.servicePublicKeyArmored());
    }

    @GetMapping("/partners")
    public List<Map<String, Object>> partners() {
        return partnerKeyService.listPartners();
    }

    @PostMapping("/partners")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPartner(@RequestBody Map<String, Object> request) {
        return partnerKeyService.createPartner(
                text(request, "profileName"),
                text(request, "displayName"),
                text(request, "publicKey"));
    }

    @PutMapping("/partners/{profileName}")
    public Map<String, Object> replacePartner(
            @PathVariable("profileName") String profileName,
            @RequestBody Map<String, Object> request) {
        return partnerKeyService.replacePartner(
                profileName,
                text(request, "displayName"),
                text(request, "publicKey"));
    }

    @DeleteMapping("/partners/{profileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePartner(@PathVariable("profileName") String profileName) {
        partnerKeyService.deletePartner(profileName);
    }

    private String text(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }
}
