package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiOperationRepository extends JpaRepository<ApiOperationEntity, String> {
    List<ApiOperationEntity> findByProjectIdOrderByTagNameAscMethodAscPathAsc(String projectId);
    List<ApiOperationEntity> findByApiSpecIdOrderByMethodAscPathAsc(String apiSpecId);
    void deleteByApiSpecId(String apiSpecId);
}
