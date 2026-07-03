package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.StepRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRunRepository extends JpaRepository<StepRunEntity, String> {
    List<StepRunEntity> findByRunIdOrderByStepIndexAsc(String runId);
}
