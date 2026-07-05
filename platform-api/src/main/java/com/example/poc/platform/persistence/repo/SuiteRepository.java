package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.SuiteEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuiteRepository extends JpaRepository<SuiteEntity, String> {
    List<SuiteEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    Optional<SuiteEntity> findByProjectIdAndName(String projectId, String name);
}
