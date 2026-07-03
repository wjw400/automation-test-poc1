package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiSpecRepository extends JpaRepository<ApiSpecEntity, String> {
    List<ApiSpecEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    Optional<ApiSpecEntity> findByProjectIdAndName(String projectId, String name);
}
