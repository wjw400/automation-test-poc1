package com.example.poc.mockservice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
    List<ProductEntity> findAllByOrderByNameAsc();
}
