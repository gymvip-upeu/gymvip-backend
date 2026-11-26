package com.gymvip.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountBranchAssignmentRepository extends JpaRepository<AccountBranchAssignmentEntity, UUID> {
    List<AccountBranchAssignmentEntity> findByAccountIdAndStatusIgnoreCase(UUID accountId, String status);
    void deleteByAccountId(UUID accountId);
}
