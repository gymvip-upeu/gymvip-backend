package com.gymvip.dto.response;

public record PasswordResetConfirmResponse(
        boolean success,
        String message
) {}
