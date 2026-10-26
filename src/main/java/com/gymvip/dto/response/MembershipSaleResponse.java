package com.gymvip.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MembershipSaleResponse(UUID membershipId, UUID periodId, UUID paymentId,
        UUID clientId, UUID planVersionId, String planName, String benefits, String policy,
        LocalDate startDate, LocalDate lastIncludedDate, LocalDate endExclusive,
        BigDecimal amountPen, String currency, String paymentMethod, String reference,
        String registeredBy, LocalDateTime paidAtUtc, String receiptNumber) {}
