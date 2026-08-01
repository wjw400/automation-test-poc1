package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.AgentArtifactEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentArtifactRepository extends JpaRepository<AgentArtifactEntity, String> {
    List<AgentArtifactEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
    void deleteByTaskId(String taskId);
}
