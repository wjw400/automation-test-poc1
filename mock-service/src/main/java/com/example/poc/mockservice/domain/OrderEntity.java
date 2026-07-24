package com.example.poc.mockservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demo_order")
public class OrderEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<OrderLineEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(String id, String customerName, String customerReference) {
        this.id = id;
        this.customerName = customerName;
        this.customerReference = customerReference;
        this.totalAmount = BigDecimal.ZERO;
        this.status = "CREATED";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void addItem(ProductEntity product, int quantity) {
        items.add(new OrderLineEntity(this, product.getId(), product.getName(), quantity, product.getUnitPrice()));
        totalAmount = totalAmount.add(product.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        updatedAt = Instant.now();
    }

    public void transitionTo(String nextStatus) {
        status = nextStatus;
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderLineEntity> getItems() {
        return items;
    }
}
