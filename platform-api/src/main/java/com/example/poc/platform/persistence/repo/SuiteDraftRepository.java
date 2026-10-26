package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.SuiteDraftEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuiteDraftRepository extends JpaRepository<SuiteDraftEntity, String> {
    Optional<SuiteDraftEntity> findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(String agentTaskId, String status);
}
