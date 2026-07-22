package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.FolderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {
    List<FolderEntity> findByProjectIdOrderByParentFolderIdAscSortOrderAscNameAsc(String projectId);
    List<FolderEntity> findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(String projectId, String parentFolderId);
}
