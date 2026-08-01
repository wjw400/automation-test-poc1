package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioDraftRepository extends JpaRepository<ScenarioDraftEntity, String> {
    List<ScenarioDraftEntity> findByProjectIdAndStatusOrderByUpdatedAtDesc(String projectId, String status);
    List<ScenarioDraftEntity> findByProjectIdAndCreatedByAndStatusOrderByUpdatedAtDesc(
            String projectId, String createdBy, String status);
    Optional<ScenarioDraftEntity> findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(
            String agentTaskId, String status);
}
