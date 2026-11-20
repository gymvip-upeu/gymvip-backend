package com.gymvip.service.payment;

import java.math.BigDecimal;

/**
 * Interfaz formal para pasarelas de pago de comercio electrónico en Perú.
 * Desacopla la lógica de negocio de los proveedores externos (Izipay, Niubiz, Culqi, Mercado Pago).
 * En la fase inicial opera con verificación manual auditada en recepción.
 */
public interface PaymentGatewayProvider {

    String getProviderName();

    boolean isAutomated();

    PaymentVerificationResult verifyPayment(String transactionReference, BigDecimal expectedAmountPen);

    record PaymentVerificationResult(
            boolean isSuccessful,
            boolean isPending,
            String gatewayTransactionId,
            BigDecimal confirmedAmountPen,
            String message
    ) {}
}
