package com.gymvip.payments.infrastructure;

import com.gymvip.payments.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    List<PaymentRecord> findByMembershipIdOrderByPaymentTimestampUtcDesc(UUID membershipId);
}
