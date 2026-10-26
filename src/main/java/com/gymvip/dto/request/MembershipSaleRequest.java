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
        @Size(max = 100) String reference) {}
