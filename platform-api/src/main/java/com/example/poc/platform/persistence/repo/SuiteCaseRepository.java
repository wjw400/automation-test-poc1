package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.SuiteCaseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuiteCaseRepository extends JpaRepository<SuiteCaseEntity, String> {
    List<SuiteCaseEntity> findBySuiteIdOrderByCaseOrderAsc(String suiteId);
    void deleteBySuiteId(String suiteId);
}
