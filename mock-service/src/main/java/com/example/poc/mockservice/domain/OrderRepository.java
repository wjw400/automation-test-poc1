package com.example.poc.mockservice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findAllByOrderByCreatedAtDesc();
}
