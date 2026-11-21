package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface CulqiClient {
    boolean configured();
    JsonNode createLink(UUID operationId, BigDecimal amountPen, Instant expiresAt);
    JsonNode event(String eventId);
    JsonNode charge(String chargeId);
}
