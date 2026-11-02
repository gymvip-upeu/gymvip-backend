package com.gymvip.dto.response;

public record ClientImportRowResultDto(
        int rowNumber,
        String fullName,
        String countryCode,
        String documentType,
        String documentNumber,
        String memberCode,
        String status, // CREATED, SKIPPED_DUPLICATE, REJECTED
        String message
) {}

