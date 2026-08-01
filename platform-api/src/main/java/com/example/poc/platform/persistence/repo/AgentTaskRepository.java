package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.AgentTaskEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, String> {
    List<AgentTaskEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    List<AgentTaskEntity> findByProjectIdAndCreatedByOrderByUpdatedAtDesc(String projectId, String createdBy);
    List<AgentTaskEntity> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AgentTaskEntity task where task.id = :id")
    Optional<AgentTaskEntity> findByIdForUpdate(@Param("id") String id);
}
