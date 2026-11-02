package com.gymvip.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClientDetailResponse(
        UUID id,
        String memberCode,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        int age,
        boolean isAdult,
        String phone,
        boolean phoneVerified,
        String email,
        boolean emailVerified,
        String status,
        List<ClientResponse.DocumentDto> documents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long version, String notes
) {}
