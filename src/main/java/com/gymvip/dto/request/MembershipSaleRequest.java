package com.gymvip.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record MembershipSaleRequest(
        @NotNull UUID clientId, @NotNull UUID planId,
        @NotNull MembershipQuoteRequest.Operation operation,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String quoteFingerprint,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amountPen,
        @NotBlank @Size(max = 50) String paymentMethod,
        @Size(max = 100) String reference, @Size(max=500) String notes, java.time.LocalDate startDate) {
    public MembershipSaleRequest(UUID clientId,UUID planId,MembershipQuoteRequest.Operation operation,String quoteFingerprint,BigDecimal amountPen,String paymentMethod,String reference) {
        this(clientId,planId,operation,quoteFingerprint,amountPen,paymentMethod,reference,null,null);
    }
}
