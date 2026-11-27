package com.gymvip.dto.response;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID id,
        String email,
        Set<String> roles,
        boolean mfaRequired,
        String challengeToken,
        String message,
        boolean mfaEnrollmentRequired,
        boolean firstAccessPasswordRequired
) {
    public AuthResponse(String token, UUID id, String email, Set<String> roles, boolean mfaRequired,
                        String challengeToken, String message) {
        this(token, id, email, roles, mfaRequired, challengeToken, message, false, false);
    }

    public AuthResponse(String token, UUID id, String email, Set<String> roles) {
        this(token, id, email, roles, false, null, null, false, false);
    }
}
