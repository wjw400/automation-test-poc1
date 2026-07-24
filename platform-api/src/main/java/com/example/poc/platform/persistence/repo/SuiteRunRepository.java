package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.SuiteRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuiteRunRepository extends JpaRepository<SuiteRunEntity, String> {
    List<SuiteRunEntity> findAllByOrderByStartedAtDesc();
}
