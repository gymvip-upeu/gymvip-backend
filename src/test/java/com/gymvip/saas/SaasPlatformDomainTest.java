package com.gymvip.saas;

import com.gymvip.saas.domain.SaasCalculations;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SaasPlatformDomainTest {

    @Test
    void commissionCalculationRejectsPendingOrFailedPayments() {
        assertThrows(IllegalArgumentException.class, () ->
                SaasCalculations.calculateCommission(
                        new BigDecimal("150.00"), "PENDING", "PERCENTAGE",
                        new BigDecimal("0.0300"), BigDecimal.ZERO, new BigDecimal("0.0344")
                ));
        assertThrows(IllegalArgumentException.class, () ->
                SaasCalculations.calculateCommission(
                        new BigDecimal("150.00"), "CANCELLED", "PERCENTAGE",
                        new BigDecimal("0.0300"), BigDecimal.ZERO, new BigDecimal("0.0344")
                ));
    }

    @Test
    void commissionBreakdownDifferentiatesGrossGatewayPlatformAndGymNet() {
        SaasCalculations.CommissionBreakdown b = SaasCalculations.calculateCommission(
                new BigDecimal("200.00"),
                "PAID",
                "HYBRID",
                new BigDecimal("0.0300"),
                new BigDecimal("1.50"),
                new BigDecimal("0.0344")
        );
        assertEquals(new BigDecimal("200.00"), b.grossAmountPen());
        assertEquals(new BigDecimal("6.88"), b.gatewayFeePen());
        assertEquals(new BigDecimal("7.50"), b.platformCommissionPen());
        assertEquals(new BigDecimal("185.62"), b.gymNetAmountPen());
    }

    @Test
    void simulatorComputesMrrArrOperatingMarginAndBreakEvenAccurately() {
        SaasCalculations.SimulatorProjection sim = SaasCalculations.simulateB2B2C(
                10,
                new BigDecimal("349.00"),
                150,
                new BigDecimal("120.00"),
                new BigDecimal("0.40"),
                new BigDecimal("0.0300"),
                new BigDecimal("0.0344"),
                new BigDecimal("1200.00"),
                new BigDecimal("1800.00"),
                new BigDecimal("45.00")
        );
        assertEquals(new BigDecimal("3490.00"), sim.mrrPen());
        assertEquals(new BigDecimal("41880.00"), sim.arrPen());
        assertEquals(new BigDecimal("72000.00"), sim.gymsGrossDigitalVolumePen());
        assertEquals(new BigDecimal("2160.00"), sim.grossCommissionRevenuePen());
        assertEquals(new BigDecimal("5650.00"), sim.totalPlatformRevenuePen());
        assertEquals(new BigDecimal("3450.00"), sim.totalDirectAndFixedCostsPen());
        assertEquals(new BigDecimal("2200.00"), sim.operatingMarginPen());
        assertTrue(sim.breakEvenGyms().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(sim.alerts().isEmpty());
    }

    @Test
    void groupDiscountRespectsUpperLimitAndComputesExactPerPersonPrice() {
        assertEquals(new BigDecimal("102.00"),
                SaasCalculations.calculateGroupDiscountedPrice(new BigDecimal("120.00"), new BigDecimal("15.00")));
        assertThrows(IllegalArgumentException.class, () ->
                SaasCalculations.calculateGroupDiscountedPrice(new BigDecimal("120.00"), new BigDecimal("75.00")));
    }

    @Test
    void educationalBmiProvidesOmsReferenceAndExplicitNonDiagnosticDisclaimer() {
        SaasCalculations.EducationalBmiResult res = SaasCalculations.calculateEducationalBmi(
                new BigDecimal("175"), new BigDecimal("70")
        );
        assertEquals(new BigDecimal("22.86"), res.bmiValue());
        assertTrue(res.classification().contains("Peso saludable"));
        assertTrue(res.disclaimer().contains("no constituye diagnóstico médico"));
        assertFalse(res.classification().toLowerCase().contains("endomorfo"));
        assertFalse(res.classification().toLowerCase().contains("mesomorfo"));
    }
}
