package com.gymvip.service.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Proveedor de pasarela de verificación manual en recepción.
 * La evidencia o número de operación registrado por el socio permanece en estado PENDIENTE
 * y exige verificación manual por el personal antes de ser aprobada.
 */
@Component
public class ManualVerificationGatewayProvider implements PaymentGatewayProvider {

    @Override
    public String getProviderName() {
        return "MANUAL_RECEPTION_VERIFICATION";
    }

    @Override
    public boolean isAutomated() {
        return false;
    }

    @Override
    public PaymentVerificationResult verifyPayment(String transactionReference, BigDecimal expectedAmountPen) {
        if (transactionReference == null || transactionReference.isBlank()) {
            return new PaymentVerificationResult(false, false, null, BigDecimal.ZERO, "Referencia de pago no provista");
        }
        // Permanece pendiente de revisión en caja
        return new PaymentVerificationResult(false, true, transactionReference.trim(), expectedAmountPen, "Pendiente de verificación física o bancaria por recepción");
    }
}
