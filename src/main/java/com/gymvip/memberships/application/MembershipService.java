package com.gymvip.memberships.application;

import com.gymvip.audit.application.AuditService;
import com.gymvip.clients.application.ClientService;
import com.gymvip.clients.domain.Client;
import com.gymvip.memberships.domain.Membership;
import com.gymvip.memberships.infrastructure.MembershipRepository;
import com.gymvip.payments.domain.PaymentRecord;
import com.gymvip.payments.infrastructure.PaymentRecordRepository;
import com.gymvip.plans.application.PlanService;
import com.gymvip.plans.domain.Plan;
import com.gymvip.plans.domain.PlanVersion;
import com.gymvip.shared.exceptions.ConflictException;
import com.gymvip.shared.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final ClientService clientService;
    private final PlanService planService;
    private final AuditService auditService;
    private final ZoneId limaZone = ZoneId.of("America/Lima");

    public MembershipService(MembershipRepository membershipRepository,
                             PaymentRecordRepository paymentRecordRepository,
                             ClientService clientService,
                             PlanService planService,
                             AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.clientService = clientService;
        this.planService = planService;
        this.auditService = auditService;
    }

    @Transactional
    public Membership assignMembershipWithPayment(UUID clientId,
                                                  UUID planId,
                                                  LocalDate startDate,
                                                  BigDecimal amountPen,
                                                  String paymentMethod,
                                                  String externalReference,
                                                  String notes,
                                                  String idempotencyKey,
                                                  UUID registeredByAccountId,
                                                  String actorEmail) {
        // 1. Check idempotency key if provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Membership> existing = membershipRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // 2. Validate client
        Client client = clientService.findById(clientId);

        // 3. Validate plan and obtain latest version snapshot
        Plan plan = planService.findById(planId);
        if (!"ACTIVO".equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalArgumentException("El plan seleccionado está inactivo");
        }
        PlanVersion snapshot = planService.getLatestVersion(planId);

        // 4. Validate payment amount equals plan price
        if (amountPen.compareTo(snapshot.getPricePen()) != 0) {
            throw new IllegalArgumentException("El monto del pago (S/ " + amountPen + ") debe coincidir con el precio del plan (S/ " + snapshot.getPricePen() + ")");
        }

        // 5. Calculate dates
        LocalDate effectiveStartDate = (startDate != null) ? startDate : LocalDate.now(limaZone);
        LocalDate endDate = effectiveStartDate.plusDays(snapshot.getDurationDays());

        // 6. Check for active membership conflicts
        Optional<Membership> activeMembership = membershipRepository.findFirstByClientIdAndStatusOrderByEndDateDesc(clientId, "VIGENTE");
        if (activeMembership.isPresent() && activeMembership.get().getEndDate().isAfter(effectiveStartDate)) {
            throw new ConflictException("El cliente ya posee una membresía vigente que finaliza el " + activeMembership.get().getEndDate());
        }

        // 7. Create Membership
        Membership membership = new Membership(client, snapshot, effectiveStartDate, endDate, idempotencyKey);

        // 8. Create Payment Record (presential only)
        PaymentRecord payment = new PaymentRecord(amountPen, paymentMethod.toUpperCase(), externalReference, registeredByAccountId, notes);
        membership.addPayment(payment);

        Membership saved = membershipRepository.save(membership);

        auditService.recordEvent(actorEmail, "ASSIGN_MEMBERSHIP_WITH_PAYMENT", "Membership", saved.getId().toString(),
                "Membresía asignada: " + snapshot.getName() + " a " + client.getFirstName() + " " + client.getLastName() +
                ". Pago presencial registrado: S/ " + amountPen + " vía " + paymentMethod);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Membership> findByClientId(UUID clientId) {
        return membershipRepository.findByClientIdOrderByStartDateDesc(clientId);
    }

    @Transactional(readOnly = true)
    public Membership findById(UUID id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con ID: " + id));
    }

    public String calculateCurrentStatus(Membership membership, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(limaZone));
        if (today.isBefore(membership.getStartDate())) {
            return "POR_INICIAR";
        }
        if (today.isAfter(membership.getEndDate())) {
            return "VENCIDA";
        }
        if (today.isAfter(membership.getEndDate().minusDays(5))) {
            return "PROXIMA_A_VENCER";
        }
        return "VIGENTE";
    }
}
