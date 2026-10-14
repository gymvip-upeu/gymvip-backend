package com.gymvip.clients.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String memberCode,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        int age,
        String phone,
        String email,
        String status,
        List<DocumentDto> documents,
        LocalDateTime createdAt
) {
    public record DocumentDto(
            UUID id,
            String countryCode,
            String documentType,
            String documentNumber,
            boolean isPrimary
    ) {}
}
