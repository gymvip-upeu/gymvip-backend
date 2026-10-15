package com.gymvip.dto.response;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID id,
        String email,
        Set<String> roles
) {}
