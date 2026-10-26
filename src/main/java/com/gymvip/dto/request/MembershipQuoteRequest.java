package com.gymvip.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipQuoteRequest(@NotNull UUID clientId, @NotNull UUID planId,
                                     @NotNull Operation operation) {
    public enum Operation { ENROLL, RENEW }
}
