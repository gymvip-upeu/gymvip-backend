package com.gymvip.dto.response;

import java.time.*;
import java.util.UUID;

public record CoverageResponse(UUID clientId,String memberCode,String fullName,String document,
        String clientStatus,String contractStatus,String state,boolean allowed,String reason,
        UUID planId,String planName,LocalDate startDate,LocalDate lastIncludedDate,LocalDate endExclusive,
        Long daysRemaining,Instant checkedAt,String timezone,int upcomingDays) {}
