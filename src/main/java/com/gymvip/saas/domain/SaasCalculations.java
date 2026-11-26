package com.gymvip.saas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cálculos determinísticos puros del dominio GYM VIP 2.0:
 * - Liquidación transparente de comisiones (sin split ficticio).
 * - Proyecciones y economía unitaria SaaS B2B2C.
 * - Precios de inscripciones grupales.
 * - Perfil físico voluntario e IMC educativo OMS (no diagnóstico clínico).
 */
public final class SaasCalculations {

    private static final Set<String> SETTLED_PAYMENT_STATES = Set.of("PAID", "SETTLED", "COMPLETADO", "COBRADO", "PAGADO");

    private SaasCalculations() {}

    public record CommissionBreakdown(
            BigDecimal grossAmountPen,
            BigDecimal gatewayFeePen,
            BigDecimal platformCommissionPen,
            BigDecimal gymNetAmountPen
    ) {}

    public static CommissionBreakdown calculateCommission(
            BigDecimal grossAmountPen,
            String paymentStatus,
            String commissionType,
            BigDecimal percentageRate,
            BigDecimal fixedFeePen,
            BigDecimal gatewayPassthroughPct
    ) {
        if (grossAmountPen == null || grossAmountPen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto bruto de la operación debe ser mayor a cero.");
        }
        String normalizedStatus = paymentStatus != null ? paymentStatus.trim().toUpperCase(Locale.ROOT) : "";
        if (!SETTLED_PAYMENT_STATES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Solo las operaciones con pago liquidado/confirmado generan comisión (estado actual: " + paymentStatus + ").");
        }

        BigDecimal gross = grossAmountPen.setScale(2, RoundingMode.HALF_UP);
        BigDecimal gwPct = gatewayPassthroughPct != null ? gatewayPassthroughPct : BigDecimal.ZERO;
        BigDecimal rate = percentageRate != null ? percentageRate : BigDecimal.ZERO;
        BigDecimal fixed = fixedFeePen != null ? fixedFeePen : BigDecimal.ZERO;
        String mode = commissionType != null ? commissionType.trim().toUpperCase(Locale.ROOT) : "PERCENTAGE";

        BigDecimal gatewayFee = gross.multiply(gwPct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee = switch (mode) {
            case "FIXED" -> fixed.setScale(2, RoundingMode.HALF_UP);
            case "HYBRID" -> gross.multiply(rate).add(fixed).setScale(2, RoundingMode.HALF_UP);
            default -> gross.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        };

        if (gatewayFee.add(platformFee).compareTo(gross) > 0) {
            throw new IllegalArgumentException("La suma de comisiones supera el importe bruto de la operación.");
        }
        BigDecimal gymNet = gross.subtract(gatewayFee).subtract(platformFee).setScale(2, RoundingMode.HALF_UP);
        return new CommissionBreakdown(gross, gatewayFee, platformFee, gymNet);
    }

    public record SimulatorProjection(
            int activeGyms,
            BigDecimal avgMonthlySaaSFeePen,
            BigDecimal avgMonthlyMembersPerGym,
            BigDecimal avgMemberTicketPen,
            BigDecimal digitalSharePct,
            BigDecimal platformCommissionPct,
            BigDecimal gatewayCostPct,
            BigDecimal monthlyInfraCostPen,
            BigDecimal fixedOpsCostPen,
            BigDecimal supportCostPerGymPen,
            BigDecimal mrrPen,
            BigDecimal arrPen,
            BigDecimal gymsGrossDigitalVolumePen,
            BigDecimal grossCommissionRevenuePen,
            BigDecimal totalPlatformRevenuePen,
            BigDecimal totalDirectAndFixedCostsPen,
            BigDecimal operatingMarginPen,
            BigDecimal breakEvenGyms,
            List<String> alerts
    ) {}

    public static SimulatorProjection simulateB2B2C(
            int activeGyms,
            BigDecimal avgMonthlySaaSFeePen,
            int avgMembersPerGym,
            BigDecimal avgTicketPen,
            BigDecimal digitalSharePct,
            BigDecimal platformCommissionPct,
            BigDecimal gatewayCostPct,
            BigDecimal monthlyInfraCostPen,
            BigDecimal fixedOpsCostPen,
            BigDecimal supportCostPerGymPen
    ) {
        if (activeGyms < 0 || avgMembersPerGym < 0) {
            throw new IllegalArgumentException("La cantidad de gimnasios y socios debe ser cero o positiva.");
        }
        BigDecimal gymsBd = BigDecimal.valueOf(activeGyms);
        BigDecimal fee = nonNegative(avgMonthlySaaSFeePen);
        BigDecimal members = BigDecimal.valueOf(avgMembersPerGym);
        BigDecimal ticket = nonNegative(avgTicketPen);
        BigDecimal digitalShare = nonNegative(digitalSharePct);
        BigDecimal commPct = nonNegative(platformCommissionPct);
        BigDecimal gwPct = nonNegative(gatewayCostPct);
        BigDecimal infra = nonNegative(monthlyInfraCostPen);
        BigDecimal ops = nonNegative(fixedOpsCostPen);
        BigDecimal supportPerGym = nonNegative(supportCostPerGymPen);

        BigDecimal mrr = gymsBd.multiply(fee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal digitalVolumePerGym = members.multiply(ticket).multiply(digitalShare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDigitalVolume = gymsBd.multiply(digitalVolumePerGym).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commPerGym = digitalVolumePerGym.multiply(commPct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCommRevenue = totalDigitalVolume.multiply(commPct).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPlatformRev = mrr.add(totalCommRevenue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalSupport = gymsBd.multiply(supportPerGym).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCosts = infra.add(ops).add(totalSupport).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margin = totalPlatformRev.subtract(totalCosts).setScale(2, RoundingMode.HALF_UP);

        BigDecimal contributionPerGym = fee.add(commPerGym).subtract(supportPerGym);
        BigDecimal fixedTotal = infra.add(ops);
        BigDecimal breakEven = contributionPerGym.compareTo(BigDecimal.ZERO) > 0
                ? fixedTotal.divide(contributionPerGym, 1, RoundingMode.CEILING)
                : BigDecimal.valueOf(-1);

        List<String> alerts = new ArrayList<>();
        if (margin.compareTo(BigDecimal.ZERO) < 0) {
            alerts.add("Operación bajo punto de equilibrio: el ingreso mensual SaaS + comisiones aún no cubre costos fijos y soporte.");
        }
        if (gwPct.compareTo(BigDecimal.valueOf(0.05)) > 0) {
            alerts.add("El costo referencial de pasarela supera 5%; revisa acuerdos o métodos locales de menor tarifa (Yape/Plin).");
        }
        if (contributionPerGym.compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add("La contribución unitaria por gimnasio es negativa o nula; ajusta fee mensual o costo de soporte por cuenta.");
        }

        return new SimulatorProjection(
                activeGyms,
                fee.setScale(2, RoundingMode.HALF_UP),
                members,
                ticket.setScale(2, RoundingMode.HALF_UP),
                digitalShare,
                commPct,
                gwPct,
                infra.setScale(2, RoundingMode.HALF_UP),
                ops.setScale(2, RoundingMode.HALF_UP),
                supportPerGym.setScale(2, RoundingMode.HALF_UP),
                mrr,
                arr,
                totalDigitalVolume,
                totalCommRevenue,
                totalPlatformRev,
                totalCosts,
                margin,
                breakEven,
                alerts
        );
    }

    public static BigDecimal calculateGroupDiscountedPrice(BigDecimal basePricePen, BigDecimal discountPercent) {
        if (basePricePen == null || basePricePen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio base del plan debe ser mayor a cero.");
        }
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0
                || discountPercent.compareTo(BigDecimal.valueOf(60)) > 0) {
            throw new IllegalArgumentException("El descuento grupal debe ubicarse entre 0.01% y 60%.");
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );
        return basePricePen.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public record EducationalBmiResult(
            BigDecimal bmiValue,
            String classification,
            String disclaimer
    ) {}

    public static EducationalBmiResult calculateEducationalBmi(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null) {
            return new EducationalBmiResult(null, null,
                    "Perfil físico opcional sin datos de estatura/peso registrados.");
        }
        if (heightCm.compareTo(BigDecimal.valueOf(100)) < 0 || heightCm.compareTo(BigDecimal.valueOf(250)) > 0) {
            throw new IllegalArgumentException("La estatura referencial debe estar entre 100 y 250 cm.");
        }
        if (weightKg.compareTo(BigDecimal.valueOf(30)) < 0 || weightKg.compareTo(BigDecimal.valueOf(300)) > 0) {
            throw new IllegalArgumentException("El peso referencial debe estar entre 30 y 300 kg.");
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal heightSq = heightM.multiply(heightM);
        BigDecimal bmi = weightKg.divide(heightSq, 2, RoundingMode.HALF_UP);

        String category;
        if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0) {
            category = "Bajo peso (referencia OMS)";
        } else if (bmi.compareTo(BigDecimal.valueOf(25.0)) < 0) {
            category = "Peso saludable (referencia OMS)";
        } else if (bmi.compareTo(BigDecimal.valueOf(30.0)) < 0) {
            category = "Sobrepeso (referencia OMS)";
        } else if (bmi.compareTo(BigDecimal.valueOf(35.0)) < 0) {
            category = "Obesidad grado I (referencia OMS)";
        } else if (bmi.compareTo(BigDecimal.valueOf(40.0)) < 0) {
            category = "Obesidad grado II (referencia OMS)";
        } else {
            category = "Obesidad grado III (referencia OMS)";
        }

        return new EducationalBmiResult(
                bmi,
                category,
                "Indicador referencial e informativo basado en criterios OMS. No distingue masa muscular de grasa corporal y no constituye diagnóstico médico ni prescripción nutricional."
        );
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Los valores numéricos no pueden ser negativos.");
        }
        return value;
    }
}
