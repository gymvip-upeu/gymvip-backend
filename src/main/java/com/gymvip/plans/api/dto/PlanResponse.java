package com.gymvip.plans.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String description,
        int durationDays,
        BigDecimal pricePen,
        String benefits,
        String status,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
