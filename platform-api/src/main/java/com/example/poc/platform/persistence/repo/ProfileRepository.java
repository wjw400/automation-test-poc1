package com.example.poc.platform.persistence.repo;

import com.example.poc.platform.persistence.entity.ProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<ProfileEntity, String> {
    List<ProfileEntity> findByProjectIdOrderByNameAsc(String projectId);
}
