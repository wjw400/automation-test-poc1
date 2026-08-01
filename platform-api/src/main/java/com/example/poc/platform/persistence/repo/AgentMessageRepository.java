package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.AgentMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, String> {
    List<AgentMessageEntity> findByTaskIdOrderBySequenceAsc(String taskId);
    long countByTaskId(String taskId);
    void deleteByTaskId(String taskId);
}
