package com.gymvip.memberships.infrastructure;

import com.gymvip.memberships.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByIdempotencyKey(String idempotencyKey);
    List<Membership> findByClientIdOrderByStartDateDesc(UUID clientId);
    Optional<Membership> findFirstByClientIdAndStatusOrderByEndDateDesc(UUID clientId, String status);
}
