package com.example.poc.mockservice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerKeyRepository extends JpaRepository<PartnerKeyEntity, String> {
    List<PartnerKeyEntity> findAllByOrderByProfileNameAsc();
}
