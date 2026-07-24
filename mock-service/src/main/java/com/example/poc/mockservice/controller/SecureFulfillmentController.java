package com.example.poc.mockservice.controller;

import com.example.poc.mockservice.service.SecureFulfillmentService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/secure/fulfillment")
public class SecureFulfillmentController {
    private final SecureFulfillmentService secureFulfillmentService;

    public SecureFulfillmentController(SecureFulfillmentService secureFulfillmentService) {
        this.secureFulfillmentService = secureFulfillmentService;
    }

    @PostMapping(path = "/orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createOrder(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureFulfillmentService.createFulfillmentOrder(profile, request);
    }

    @PostMapping(path = "/orders/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> queryOrder(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureFulfillmentService.queryFulfillmentOrder(profile, request);
    }

    @PostMapping(path = "/orders/reserve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> reserveOrder(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureFulfillmentService.reserveFulfillmentOrder(profile, request);
    }

    @PostMapping(path = "/orders/fulfill", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> fulfillOrder(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureFulfillmentService.fulfillFulfillmentOrder(profile, request);
    }

    @PostMapping(path = "/orders/cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> cancelOrder(
            @RequestParam(name = "profile", required = false) String profile,
            @RequestBody Map<String, Object> request) {
        return secureFulfillmentService.cancelFulfillmentOrder(profile, request);
    }
}
