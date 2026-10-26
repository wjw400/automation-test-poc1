package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.AgentEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEventRepository extends JpaRepository<AgentEventEntity, String> {
    List<AgentEventEntity> findTop3ByTaskIdAndEventTypeOrderBySequenceDesc(String taskId, String eventType);
    List<AgentEventEntity> findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(String taskId, long sequence);
    long countByTaskId(String taskId);
    void deleteByTaskId(String taskId);
}
