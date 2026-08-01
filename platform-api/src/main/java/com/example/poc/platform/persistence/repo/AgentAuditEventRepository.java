package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.AgentAuditEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAuditEventRepository extends JpaRepository<AgentAuditEventEntity, String> {
    List<AgentAuditEventEntity> findByTaskIdAndActorIdOrderByCreatedAtAsc(String taskId, String actorId);
    List<AgentAuditEventEntity> findByProjectIdAndActorIdOrderByCreatedAtAsc(String projectId, String actorId);
}
