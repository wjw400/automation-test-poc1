package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.TestKnowledgeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestKnowledgeRepository extends JpaRepository<TestKnowledgeEntity, String> {
    List<TestKnowledgeEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    List<TestKnowledgeEntity> findByProjectIdAndStatusOrderByUpdatedAtDesc(String projectId, String status);
}
