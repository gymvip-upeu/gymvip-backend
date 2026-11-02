package com.gymvip.dto.response;

import java.time.LocalDate;

public record ClientImportRowPreviewDto(
        int rowNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryCode,
        String documentType,
        String documentNumber,
        String phone,
        String email,
        String status, // VALID, DUPLICATE_IN_FILE, DUPLICATE_IN_DATABASE, INVALID
        String message
) {}

