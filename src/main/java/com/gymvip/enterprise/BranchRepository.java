package com.gymvip.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<BranchEntity, UUID> {
    List<BranchEntity> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    List<BranchEntity> findByStatusIgnoreCaseAndIsPublishedTrueOrderByNameAsc(String status);
}
