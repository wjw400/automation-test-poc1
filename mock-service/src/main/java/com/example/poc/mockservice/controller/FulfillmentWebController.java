package com.example.poc.mockservice.controller;

import com.example.poc.mockservice.activity.ApiActivityRecord;
import com.example.poc.mockservice.activity.ApiActivityService;
import com.example.poc.mockservice.service.FulfillmentService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web-api")
public class FulfillmentWebController {
    private final FulfillmentService fulfillmentService;
    private final ApiActivityService activityService;

    public FulfillmentWebController(
            FulfillmentService fulfillmentService,
            ApiActivityService activityService) {
        this.fulfillmentService = fulfillmentService;
        this.activityService = activityService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return fulfillmentService.summary();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return fulfillmentService.listProducts();
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return fulfillmentService.listOrders();
    }

    @GetMapping("/orders/{orderId}")
    public Map<String, Object> order(@PathVariable("orderId") String orderId) {
        return fulfillmentService.queryOrder(Map.of("orderId", orderId));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request) {
        return observe("Create Order", "POST", "/web-api/orders", request,
                () -> fulfillmentService.createOrder(request));
    }

    @PostMapping("/orders/{orderId}/reserve")
    public Map<String, Object> reserveOrder(@PathVariable("orderId") String orderId) {
        Map<String, Object> request = Map.of("orderId", orderId);
        return observe("Reserve Inventory", "POST", "/web-api/orders/{orderId}/reserve", request,
                () -> fulfillmentService.reserveOrder(request));
    }

    @PostMapping("/orders/{orderId}/fulfill")
    public Map<String, Object> fulfillOrder(
            @PathVariable("orderId") String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        if (body != null) {
            request.putAll(body);
        }
        request.put("orderId", orderId);
        return observe("Dispatch Shipment", "POST", "/web-api/orders/{orderId}/fulfill", request,
                () -> fulfillmentService.fulfillOrder(request));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable("orderId") String orderId) {
        Map<String, Object> request = Map.of("orderId", orderId);
        return observe("Cancel Order", "POST", "/web-api/orders/{orderId}/cancel", request,
                () -> fulfillmentService.cancelOrder(request));
    }

    @GetMapping("/shipments")
    public List<Map<String, Object>> shipments() {
        return fulfillmentService.listShipments();
    }

    @GetMapping("/activity")
    public List<Map<String, Object>> activity() {
        return activityService.listSummaries();
    }

    @GetMapping("/activity/{activityId}")
    public ApiActivityRecord activity(@PathVariable("activityId") String activityId) {
        return activityService.find(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
    }

    @DeleteMapping("/activity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearActivity() {
        activityService.clear();
    }

    @PostMapping("/admin/reset")
    public Map<String, Object> reset() {
        fulfillmentService.resetDemoData();
        activityService.clear();
        return Map.of("status", "RESET", "message", "Demo data restored");
    }

    private Map<String, Object> observe(
            String operation,
            String method,
            String path,
            Object request,
            Supplier<Map<String, Object>> action) {
        Instant startedAt = Instant.now();
        try {
            Map<String, Object> result = action.get();
            activityService.record(
                    startedAt,
                    "WEB",
                    operation,
                    method,
                    path,
                    "web-ui",
                    200,
                    request,
                    request,
                    result,
                    result,
                    null);
            return result;
        } catch (RuntimeException error) {
            activityService.record(
                    startedAt,
                    "WEB",
                    operation,
                    method,
                    path,
                    "web-ui",
                    400,
                    request,
                    request,
                    null,
                    null,
                    error.getMessage());
            throw error;
        }
    }
}
