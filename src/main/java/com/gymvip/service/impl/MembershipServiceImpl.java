package com.gymvip.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.*;
import com.gymvip.dto.response.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.service.*;
import com.gymvip.util.MembershipDates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class MembershipServiceImpl implements MembershipService {
    private static final Set<String> SUPPORTED = Set.of("EFECTIVO", "YAPE", "PLIN", "TRANSFERENCIA", "POS");
    public static final String POLICY = "Días consecutivos según el plan contratado, sin gracia. Inicio incluido y fin excluido en America/Lima. " +
            "Pago completo presencial. Renovación vigente desde el fin contratado; vencida desde el día del pago. " +
            "El cambio de plan se aplica al periodo añadido. Constancia interna, no comprobante tributario.";
    private final MembershipRepository repository;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final List<String> methods;

    public MembershipServiceImpl(MembershipRepository repository, AuditService audit, ObjectMapper json, Clock clock,
            @Value("${MEMBERSHIP_PAYMENT_METHODS:EFECTIVO,YAPE,PLIN,TRANSFERENCIA}") String enabled) {
        this.repository = repository; this.audit = audit; this.json = json; this.clock = clock;
        methods = Arrays.stream(enabled.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
        if (methods.isEmpty() || !SUPPORTED.containsAll(methods)) throw new IllegalArgumentException("Medios de pago inválidos.");
    }
    @Override public List<String> paymentMethods() { return methods; }

    @Override @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MembershipQuoteResponse quote(MembershipQuoteRequest request) { return buildQuote(request, false); }

    private MembershipQuoteResponse buildQuote(MembershipQuoteRequest request, boolean lock) {
        if (!"ACTIVO".equals(repository.clientStatus(request.clientId(), lock)))
            throw new IllegalArgumentException("El cliente está inactivo.");
        var contract = repository.contract(request.clientId()).orElse(null);
        if (request.operation() == MembershipQuoteRequest.Operation.ENROLL && contract != null)
            throw new ConflictException("El cliente ya tiene contrato. Utilice renovar.");
        if (request.operation() == MembershipQuoteRequest.Operation.RENEW && contract == null)
            throw new ConflictException("El cliente todavía no tiene membresía. Utilice contratar.");
        if (contract != null && !Set.of("VIGENTE", "VENCIDO", "POR_VENCER").contains(contract.status()))
            throw new ConflictException("No se puede renovar un contrato cancelado o no habilitado.");
        var plan = repository.plan(request.planId(), lock);
        LocalDate today = MembershipDates.today(clock);
        LocalDate previousEnd = contract == null ? null : contract.end();
        LocalDate start = MembershipDates.start(today, previousEnd);
        LocalDate end = start.plusDays(plan.duration());
        UUID membership = contract == null ? null : contract.id();
        // Include the local date: a summary cannot silently survive the midnight cutoff.
        String fingerprint = hash(Arrays.asList(request, membership, plan, previousEnd, today, start, end, POLICY));
        return new MembershipQuoteResponse(request.clientId(), request.planId(), membership, plan.id(), plan.name(),
                plan.benefits(), plan.price(), previousEnd, start, end.minusDays(1), end,
                MembershipDates.ZONE.getId(), POLICY, fingerprint);
    }

    @Override @Transactional
    public MembershipSaleResponse sell(MembershipSaleRequest request, String key, String actorEmail) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}"))
            throw new IllegalArgumentException("Idempotency-Key debe contener entre 8 y 100 letras, números, guiones o guiones bajos.");
        String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        String reference = request.reference() == null || request.reference().isBlank() ? null : request.reference().trim();
        var normalized = new MembershipSaleRequest(request.clientId(), request.planId(), request.operation(),
                request.quoteFingerprint(), request.amountPen().setScale(2), method, reference);
        String requestHash = hash(normalized);
        UUID actor = repository.actor(actorEmail);
        repository.lockOperation(actor, key);
        var saved = repository.operation(actor, key);
        if (saved.isPresent()) {
            if (!saved.get().hash().equals(requestHash)) throw new ConflictException("La clave ya se utilizó con otra solicitud.");
            return read(saved.get().response());
        }
        if (!methods.contains(method)) throw new IllegalArgumentException("Medio de pago no habilitado.");
        if (!"EFECTIVO".equals(method) && reference == null)
            throw new IllegalArgumentException("La referencia es obligatoria para pagos no efectivos.");
        var quote = buildQuote(new MembershipQuoteRequest(request.clientId(), request.planId(), request.operation()), true);
        if (!quote.fingerprint().equals(request.quoteFingerprint()))
            throw new ConflictException("Las fechas o el plan cambiaron. Revise un nuevo resumen antes de cobrar.");
        if (quote.pricePen().compareTo(request.amountPen()) != 0)
            throw new IllegalArgumentException("El cobro debe coincidir con el precio completo autorizado por el backend.");
        UUID contract = quote.membershipId() == null ? UUID.randomUUID() : quote.membershipId();
        if (quote.membershipId() == null) repository.createContract(contract, request.clientId(), quote.planVersionId(), quote.startDate(), quote.endExclusive());
        else repository.extendContract(contract, quote.endExclusive());
        UUID payment = UUID.randomUUID();
        var result = new MembershipSaleResponse(contract, UUID.randomUUID(), payment, request.clientId(), quote.planVersionId(),
                quote.planName(), quote.benefits(), quote.policy(), quote.startDate(), quote.lastIncludedDate(), quote.endExclusive(),
                quote.pricePen(), "PEN", method, reference, actorEmail,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), "VIP-" + payment);
        repository.addPeriod(result.periodId(), contract, quote.planVersionId(), quote.startDate(), quote.endExclusive(), result.paidAtUtc(), quote.policy());
        repository.addPayment(result, actor);
        audit.recordEvent(actorEmail, request.operation().name() + "_MEMBERSHIP", "Membership", contract.toString(),
                "Periodo=" + result.periodId() + "; pago=" + payment + "; PEN=" + result.amountPen());
        repository.saveOperation(actor, key, requestHash, write(result));
        return result;
    }
    @Override @Transactional(readOnly = true)
    public List<MembershipSaleResponse> history(UUID client) {
        repository.clientStatus(client, false);
        return repository.history(client);
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo serializar la operación.", ex); }
    }
    private MembershipSaleResponse read(String value) {
        try { return json.readValue(value, MembershipSaleResponse.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo recuperar la operación.", ex); }
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
