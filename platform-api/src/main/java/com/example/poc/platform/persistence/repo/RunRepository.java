package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.RunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<RunEntity, String> {
    List<RunEntity> findAllByOrderByStartedAtDesc();
    List<RunEntity> findBySuiteRunIdOrderBySuiteCaseOrderAsc(String suiteRunId);
}
