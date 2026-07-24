package com.example.poc.mockservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "demo_product")
public class ProductEntity {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String imageKey;

    protected ProductEntity() {
    }

    public ProductEntity(
            String id,
            String sku,
            String name,
            String category,
            BigDecimal unitPrice,
            int availableQuantity,
            String status,
            String imageKey) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.availableQuantity = availableQuantity;
        this.status = status;
        this.imageKey = imageKey;
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public String getStatus() {
        return status;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void adjustAvailableQuantity(int delta) {
        int nextQuantity = availableQuantity + delta;
        if (nextQuantity < 0) {
            throw new IllegalArgumentException("Insufficient inventory for " + name);
        }
        availableQuantity = nextQuantity;
    }
}
