package com.gymvip.dto.response;

import java.time.Instant;
import java.util.List;

public record ClientImportResultResponse(
        String fileName,
        int totalRows,
        int createdCount,
        int skippedCount,
        int rejectedCount,
        Instant timestampUtc,
        String actor,
        List<ClientImportRowResultDto> results
) {}

