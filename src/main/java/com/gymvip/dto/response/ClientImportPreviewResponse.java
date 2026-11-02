package com.gymvip.dto.response;

import java.util.List;

public record ClientImportPreviewResponse(
        String fileName,
        int totalRows,
        int validCount,
        int duplicateCount,
        int invalidCount,
        List<ClientImportRowPreviewDto> rows
) {}

