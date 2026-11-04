package com.gymvip.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReminderConfigResponse(
        UUID id,
        boolean enabled,
        List<Integer> anticipationDays,
        int postExpiryDays,
        List<String> channels,
        String updatedBy,
        LocalDateTime updatedAt
) {}
