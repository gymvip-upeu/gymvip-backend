package com.gymvip.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClientChangeResponse(UUID changeId, UUID clientId, String actor,
        String field, String before, String after, LocalDateTime changedAtUtc) {}
