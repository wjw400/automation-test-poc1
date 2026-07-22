package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.CaseEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<CaseEntity, String> {
    List<CaseEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    List<CaseEntity> findByProjectIdAndStatusNotOrderByUpdatedAtDesc(String projectId, String status);
    Optional<CaseEntity> findByProjectIdAndCode(String projectId, String code);
    boolean existsByFolderIdAndStatusNot(String folderId, String status);
}
