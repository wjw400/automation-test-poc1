package com.example.poc.mockservice.service;

import com.example.poc.mockservice.domain.OrderEntity;
import com.example.poc.mockservice.domain.OrderLineEntity;
import com.example.poc.mockservice.domain.OrderRepository;
import com.example.poc.mockservice.domain.ProductEntity;
import com.example.poc.mockservice.domain.ProductRepository;
import com.example.poc.mockservice.domain.ShipmentEntity;
import com.example.poc.mockservice.domain.ShipmentRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final AtomicInteger orderSequence = new AtomicInteger(3000);
    private final AtomicInteger shipmentSequence = new AtomicInteger(7000);

    public FulfillmentService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            ShipmentRepository shipmentRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProducts() {
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(this::productView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::orderView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShipments() {
        return shipmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::shipmentView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", productRepository.count());
        result.put("orders", orderRepository.count());
        result.put("shipments", shipmentRepository.count());
        result.put("pendingOrders", orderRepository.findAll().stream()
                .filter(order -> !"DISPATCHED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus()))
                .count());
        return result;
    }

    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> request) {
        String customerName = requiredText(request, "customerName");
        String customerReference = optionalText(
                request,
                "customerReference",
                "WEB-" + System.currentTimeMillis());
        List<Map<String, Object>> itemRequests = mapList(request.get("items"));
        if (itemRequests.isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }

        OrderEntity order = new OrderEntity(nextOrderId(), customerName, customerReference);
        for (Map<String, Object> itemRequest : itemRequests) {
            String productId = requiredText(itemRequest, "productId");
            int quantity = positiveInteger(itemRequest.get("quantity"), "quantity");
            ProductEntity product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
            if (!"ACTIVE".equals(product.getStatus())) {
                throw new IllegalArgumentException("Product is not available: " + productId);
            }
            order.addItem(product, quantity);
        }
        return orderView(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queryOrder(Map<String, Object> request) {
        return orderView(requireOrder(requiredText(request, "orderId")));
    }

    @Transactional
    public Map<String, Object> reserveOrder(Map<String, Object> request) {
        return reserveOrder(requiredText(request, "orderId"));
    }

    @Transactional
    public Map<String, Object> reserveOrder(String orderId) {
        OrderEntity order = requireOrder(orderId);
        if ("RESERVED".equals(order.getStatus())) {
            return orderView(order);
        }
        requireStatus(order, "CREATED");
        for (OrderLineEntity item : order.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + item.getProductId()));
            product.adjustAvailableQuantity(-item.getQuantity());
        }
        order.transitionTo("RESERVED");
        return orderView(order);
    }

    @Transactional
    public Map<String, Object> fulfillOrder(Map<String, Object> request) {
        return fulfillOrder(
                requiredText(request, "orderId"),
                optionalText(request, "carrier", "Northstar Logistics"));
    }

    @Transactional
    public Map<String, Object> fulfillOrder(String orderId, String carrier) {
        OrderEntity order = requireOrder(orderId);
        if ("DISPATCHED".equals(order.getStatus())) {
            Map<String, Object> result = orderView(order);
            shipmentRepository.findByOrderId(orderId)
                    .ifPresent(shipment -> result.put("shipment", shipmentView(shipment)));
            return result;
        }
        requireStatus(order, "RESERVED");
        String sequence = String.valueOf(shipmentSequence.incrementAndGet());
        ShipmentEntity shipment = shipmentRepository.save(new ShipmentEntity(
                "S" + sequence,
                order.getId(),
                carrier,
                "TRK-" + sequence));
        order.transitionTo("DISPATCHED");
        Map<String, Object> result = orderView(order);
        result.put("shipment", shipmentView(shipment));
        return result;
    }

    @Transactional
    public Map<String, Object> cancelOrder(Map<String, Object> request) {
        String orderId = requiredText(request, "orderId");
        OrderEntity order = requireOrder(orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            return orderView(order);
        }
        if ("DISPATCHED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Dispatched orders cannot be cancelled");
        }
        if ("RESERVED".equals(order.getStatus())) {
            for (OrderLineEntity item : order.getItems()) {
                ProductEntity product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + item.getProductId()));
                product.adjustAvailableQuantity(item.getQuantity());
            }
        }
        order.transitionTo("CANCELLED");
        return orderView(order);
    }

    @Transactional
    public void resetDemoData() {
        shipmentRepository.deleteAllInBatch();
        orderRepository.deleteAll();
        orderRepository.flush();
        productRepository.deleteAllInBatch();
        orderSequence.set(3000);
        shipmentSequence.set(7000);

        productRepository.saveAll(List.of(
                new ProductEntity("P100", "WH-KEY-84", "Wireless Mechanical Keyboard", "Workspace",
                        new BigDecimal("129.00"), 38, "ACTIVE", "keyboard"),
                new ProductEntity("P101", "AU-HDP-20", "Studio Monitor Headphones", "Audio",
                        new BigDecimal("189.00"), 24, "ACTIVE", "headphones"),
                new ProductEntity("P102", "DS-LMP-11", "Adjustable Task Lamp", "Workspace",
                        new BigDecimal("79.00"), 42, "ACTIVE", "lamp"),
                new ProductEntity("P103", "TR-HUB-07", "USB-C Travel Hub", "Accessories",
                        new BigDecimal("59.00"), 65, "ACTIVE", "hub")));

        Map<String, Object> firstOrder = createOrder(Map.of(
                "customerName", "Northwind Design",
                "customerReference", "NW-2026-071",
                "items", List.of(
                        Map.of("productId", "P100", "quantity", 2),
                        Map.of("productId", "P103", "quantity", 3))));
        reserveOrder(String.valueOf(firstOrder.get("id")));
        fulfillOrder(String.valueOf(firstOrder.get("id")), "Northstar Logistics");

        createOrder(Map.of(
                "customerName", "Aster Labs",
                "customerReference", "AL-PO-1842",
                "items", List.of(Map.of("productId", "P102", "quantity", 6))));
    }

    private OrderEntity requireOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private void requireStatus(OrderEntity order, String expectedStatus) {
        if (!expectedStatus.equals(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Order " + order.getId() + " must be " + expectedStatus + " but is " + order.getStatus());
        }
    }

    private Map<String, Object> productView(ProductEntity product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId());
        result.put("sku", product.getSku());
        result.put("name", product.getName());
        result.put("category", product.getCategory());
        result.put("unitPrice", product.getUnitPrice());
        result.put("availableQuantity", product.getAvailableQuantity());
        result.put("status", product.getStatus());
        result.put("imageKey", product.getImageKey());
        return result;
    }

    private Map<String, Object> orderView(OrderEntity order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("customerName", order.getCustomerName());
        result.put("customerReference", order.getCustomerReference());
        result.put("totalAmount", order.getTotalAmount());
        result.put("status", order.getStatus());
        result.put("createdAt", order.getCreatedAt());
        result.put("updatedAt", order.getUpdatedAt());
        result.put("items", order.getItems().stream().map(item -> {
            Map<String, Object> itemView = new LinkedHashMap<>();
            itemView.put("productId", item.getProductId());
            itemView.put("productName", item.getProductName());
            itemView.put("quantity", item.getQuantity());
            itemView.put("unitPrice", item.getUnitPrice());
            return itemView;
        }).toList());
        return result;
    }

    private Map<String, Object> shipmentView(ShipmentEntity shipment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", shipment.getId());
        result.put("orderId", shipment.getOrderId());
        result.put("carrier", shipment.getCarrier());
        result.put("trackingNumber", shipment.getTrackingNumber());
        result.put("status", shipment.getStatus());
        result.put("createdAt", shipment.getCreatedAt());
        return result;
    }

    private String nextOrderId() {
        return "O" + orderSequence.incrementAndGet();
    }

    private String requiredText(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }

    private String optionalText(Map<String, Object> request, String field, String defaultValue) {
        Object value = request.get(field);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value).trim();
    }

    private int positiveInteger(Object value, String field) {
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(field + " must be an integer", error);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }
}
