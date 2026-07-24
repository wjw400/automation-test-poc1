package com.example.poc.mockservice.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<ShipmentEntity, String> {
    List<ShipmentEntity> findAllByOrderByCreatedAtDesc();

    Optional<ShipmentEntity> findByOrderId(String orderId);
}
