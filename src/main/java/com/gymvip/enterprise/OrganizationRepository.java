package com.gymvip.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
    List<OrganizationEntity> findByStatusIgnoreCaseAndIsPublishedTrueOrderByNameAsc(String status);
}
