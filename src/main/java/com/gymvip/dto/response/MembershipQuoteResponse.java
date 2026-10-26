package com.gymvip.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipQuoteResponse(UUID clientId, UUID planId, UUID membershipId,
        UUID planVersionId, String planName, String benefits, BigDecimal pricePen,
        LocalDate currentEndExclusive, LocalDate startDate, LocalDate lastIncludedDate,
        LocalDate endExclusive, String timezone, String policy, String fingerprint) {}
