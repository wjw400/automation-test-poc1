package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ProjectEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    Optional<ProjectEntity> findByCode(String code);
}
