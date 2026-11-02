package com.gymvip.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipQuoteRequest(@NotNull UUID clientId, @NotNull UUID planId,
                                     @NotNull Operation operation, java.time.LocalDate startDate) {
    public MembershipQuoteRequest(UUID clientId,UUID planId,Operation operation) {this(clientId,planId,operation,null);}
    public enum Operation { ENROLL, RENEW }
}
