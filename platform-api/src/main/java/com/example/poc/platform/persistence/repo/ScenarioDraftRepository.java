package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioDraftRepository extends JpaRepository<ScenarioDraftEntity, String> {
    org.springframework.data.domain.Slice<ScenarioDraftEntity> findByAgentTaskIdAndProjectIdAndCreatedByAndStatusInOrderByUpdatedAtDesc(
            String agentTaskId, String projectId, String createdBy, List<String> statuses,
            org.springframework.data.domain.Pageable pageable);
    List<ScenarioDraftEntity> findByProjectIdAndStatusOrderByUpdatedAtDesc(String projectId, String status);
    List<ScenarioDraftEntity> findByProjectIdAndCreatedByAndStatusOrderByUpdatedAtDesc(
            String projectId, String createdBy, String status);
    Optional<ScenarioDraftEntity> findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(
            String agentTaskId, String status);
    List<ScenarioDraftEntity> findByAgentTaskIdAndStatusOrderByUpdatedAtDesc(
            String agentTaskId, String status);
}
