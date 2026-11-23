package com.gymvip.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record StaffAccountResponse(
        UUID id,
        String email,
        Set<String> roles,
        String status,
        boolean mfaEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String firstName,
        String lastName,
        String phone
) {
    public StaffAccountResponse(UUID id, String email, Set<String> roles, String status,
                                boolean mfaEnabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, email, roles, status, mfaEnabled, createdAt, updatedAt, null, null, null);
    }
}
