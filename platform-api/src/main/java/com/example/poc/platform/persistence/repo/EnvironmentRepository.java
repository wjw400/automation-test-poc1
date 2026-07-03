package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.EnvironmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, String> {
    List<EnvironmentEntity> findByProjectIdOrderByNameAsc(String projectId);
}
