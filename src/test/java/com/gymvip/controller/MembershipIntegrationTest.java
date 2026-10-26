package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.*;
import com.gymvip.dto.response.*;
import com.gymvip.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MembershipIntegrationTest.TimeConfig.class)
@WithMockUser(username = "recepcion@vipcenterfit.pe", roles = "RECEPCION")
class MembershipIntegrationTest {
    private static final String ACTOR = "recepcion@vipcenterfit.pe";
    @Autowired MembershipService memberships;
    @Autowired PlanService plans;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @SpyBean AuditService audit;
    UUID client, plan;

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-26T17:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testMembershipClock() { return new MutableClock(); }
    }
    @BeforeEach void fixtures() {
        assertEquals("gymvip_test_jorge", jdbc.queryForObject("SELECT current_schema()", String.class));
        clock.now = Instant.parse("2026-10-26T17:00:00Z");
        client = UUID.randomUUID();
        jdbc.update("INSERT INTO clients(id,first_name,last_name,birth_date,member_code) VALUES (?, 'Prueba', 'Alexander', '1990-01-01', ?)",
                client, "ALEX-" + client.toString().substring(0, 8));
        plan = plans.createPlan(new CreatePlanRequest("Mensual " + UUID.randomUUID(), "Prueba aislada", 30,
                new BigDecimal("100.00"), "Condiciones originales"), "admin@vipcenterfit.pe").id();
    }
    MembershipQuoteResponse quote(MembershipQuoteRequest.Operation op) {
        return memberships.quote(new MembershipQuoteRequest(client, plan, op));
    }
    MembershipSaleRequest sale(MembershipQuoteResponse q) {
        return new MembershipSaleRequest(client, plan, q.membershipId() == null ? MembershipQuoteRequest.Operation.ENROLL : MembershipQuoteRequest.Operation.RENEW,
                q.fingerprint(), q.pricePen(), "EFECTIVO", null);
    }
    MembershipSaleResponse enroll() {
        return memberships.sell(sale(quote(MembershipQuoteRequest.Operation.ENROLL)), UUID.randomUUID().toString(), ACTOR);
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " +
            (table.equals("memberships") ? "client_id=?" : "membership_id IN (SELECT id FROM memberships WHERE client_id=?)"), Long.class, client); }

    @Test void salePersistsSnapshotReceiptAndReplaysSameResult() throws Exception {
        var q = quote(MembershipQuoteRequest.Operation.ENROLL);
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(sale(q));
        String first = mvc.perform(post("/api/v1/memberships/sales").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPen").value(100))
                .andExpect(jsonPath("$.lastIncludedDate").value("2026-11-24"))
                .andReturn().getResponse().getContentAsString();
        String again = mvc.perform(post("/api/v1/memberships/sales").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first), json.readTree(again));
        assertEquals(1, count("memberships")); assertEquals(1, count("membership_periods")); assertEquals(1, count("payment_records"));
        plans.updatePlan(plan, new UpdatePlanRequest("Mensual actualizado " + UUID.randomUUID(), "Otro", 30,
                new BigDecimal("150.00"), "Nuevas condiciones"), "admin@vipcenterfit.pe");
        var history = memberships.history(client);
        assertEquals(new BigDecimal("100.00"), history.getFirst().amountPen());
        assertEquals("Condiciones originales", history.getFirst().benefits());
        mvc.perform(get("/api/v1/memberships/clients/" + client + "/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].receiptNumber").isNotEmpty());
    }
    @Test void sameKeyDifferentBodyConflicts() throws Exception {
        var request = sale(quote(MembershipQuoteRequest.Operation.ENROLL)); String key = UUID.randomUUID().toString();
        memberships.sell(request, key, ACTOR);
        var changed = new MembershipSaleRequest(client, plan, request.operation(), request.quoteFingerprint(), request.amountPen(), "YAPE", "ABC");
        mvc.perform(post("/api/v1/memberships/sales").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(changed))).andExpect(status().isConflict());
        assertEquals(1, count("payment_records"));
    }
    @Test void failureAfterPaymentRollsBackEverythingAndKeyCanRetry() {
        var request = sale(quote(MembershipQuoteRequest.Operation.ENROLL)); String key = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("Fallo simulado antes del commit")).when(audit)
                .recordEvent(eq(ACTOR), eq("ENROLL_MEMBERSHIP"), anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> memberships.sell(request, key, ACTOR));
        assertEquals(0, count("memberships")); assertEquals(0, count("membership_periods")); assertEquals(0, count("payment_records"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM membership_operations WHERE operation_key=?", Integer.class, key));
        reset(audit);
        memberships.sell(request, key, ACTOR);
        assertEquals(1, count("payment_records"));
    }
    @Test void earlyRenewalAndDifferentValidOperationsAccumulate() {
        var first = enroll();
        var second = memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), UUID.randomUUID().toString(), ACTOR);
        assertEquals(first.endExclusive(), second.startDate());
        assertEquals(first.endExclusive().plusDays(30), second.endExclusive());
        var third = memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), UUID.randomUUID().toString(), ACTOR);
        assertEquals(second.endExclusive(), third.startDate());
        assertEquals(3, memberships.history(client).size()); assertEquals(3, count("payment_records"));
    }
    @Test void expiredRenewalStartsAtPaymentDateAndKeepsOldPeriod() {
        var first = enroll(); clock.now = Instant.parse("2026-12-10T17:00:00Z");
        var second = memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), UUID.randomUUID().toString(), ACTOR);
        assertEquals(LocalDate.of(2026, 12, 10), second.startDate());
        assertEquals(LocalDate.of(2027, 1, 9), second.endExclusive());
        assertEquals(first, memberships.history(client).getLast());
    }
    @Test void exactEndAndLastIncludedDayHaveCorrectRenewalBoundary() {
        var first = enroll(); clock.now = first.lastIncludedDate().atTime(17, 0).toInstant(ZoneOffset.UTC);
        assertEquals(first.endExclusive(), quote(MembershipQuoteRequest.Operation.RENEW).startDate());
        clock.now = first.endExclusive().atTime(17, 0).toInstant(ZoneOffset.UTC);
        assertEquals(first.endExclusive(), quote(MembershipQuoteRequest.Operation.RENEW).startDate());
    }
    @Test void midnightAndPriceChangeInvalidateSummary() {
        var request = sale(quote(MembershipQuoteRequest.Operation.ENROLL));
        clock.now = Instant.parse("2026-10-27T05:00:00Z");
        assertThrows(com.gymvip.exception.ConflictException.class,
                () -> memberships.sell(request, UUID.randomUUID().toString(), ACTOR));
        var second = sale(quote(MembershipQuoteRequest.Operation.ENROLL));
        plans.updatePlan(plan, new UpdatePlanRequest("Mensual cambiado " + UUID.randomUUID(), "", 30,
                new BigDecimal("120.00"), "Condiciones cambiadas"), "admin@vipcenterfit.pe");
        assertThrows(com.gymvip.exception.ConflictException.class,
                () -> memberships.sell(second, UUID.randomUUID().toString(), ACTOR));
        assertEquals(0, count("memberships"));
    }
    @Test void concurrentSameKeyCreatesOnePayment() throws Exception {
        var request = sale(quote(MembershipQuoteRequest.Operation.ENROLL)); String key = UUID.randomUUID().toString();
        var results = concurrent(() -> memberships.sell(request, key, ACTOR), () -> memberships.sell(request, key, ACTOR));
        assertEquals(results.get(0), results.get(1)); assertEquals(1, count("payment_records"));
        var renewal = sale(quote(MembershipQuoteRequest.Operation.RENEW));
        String renewalKey = UUID.randomUUID().toString();
        var renewed = concurrent(() -> memberships.sell(renewal, renewalKey, ACTOR), () -> memberships.sell(renewal, renewalKey, ACTOR));
        assertEquals(renewed.get(0), renewed.get(1)); assertEquals(2, count("payment_records"));
    }
    @Test void concurrentDifferentKeysCannotSellOverlappingRenewals() throws Exception {
        enroll(); var request = sale(quote(MembershipQuoteRequest.Operation.RENEW));
        Callable<Object> call = () -> {
            try { return memberships.sell(request, UUID.randomUUID().toString(), ACTOR); }
            catch (com.gymvip.exception.ConflictException e) { return "CONFLICT"; }
        };
        var results = concurrent(call, call);
        assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
        assertEquals(2, count("payment_records"));
        memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), UUID.randomUUID().toString(), ACTOR);
        assertEquals(3, count("payment_records"));
    }
    private List<Object> concurrent(Callable<?> a, Callable<?> b) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var one = pool.submit(() -> { gate.await(); return a.call(); });
            var two = pool.submit(() -> { gate.await(); return b.call(); }); gate.countDown();
            return Arrays.asList(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        }
    }
    @Test void invalidAmountReferenceAndDisabledPosDoNotActivateMembership() throws Exception {
        var q = quote(MembershipQuoteRequest.Operation.ENROLL);
        for (var req : List.of(
                new MembershipSaleRequest(client, plan, MembershipQuoteRequest.Operation.ENROLL, q.fingerprint(), new BigDecimal("99.00"), "EFECTIVO", null),
                new MembershipSaleRequest(client, plan, MembershipQuoteRequest.Operation.ENROLL, q.fingerprint(), new BigDecimal("0"), "EFECTIVO", null),
                new MembershipSaleRequest(client, plan, MembershipQuoteRequest.Operation.ENROLL, q.fingerprint(), q.pricePen(), "YAPE", " "),
                new MembershipSaleRequest(client, plan, MembershipQuoteRequest.Operation.ENROLL, q.fingerprint(), q.pricePen(), "POS", "REF"))) {
            mvc.perform(post("/api/v1/memberships/sales").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req))).andExpect(status().isBadRequest());
        }
        assertEquals(0, count("memberships"));
    }
    @Test void configuredNonCashMethodsStoreReferenceAndWorker() {
        for (String method : List.of("YAPE", "PLIN", "TRANSFERENCIA")) {
            var q = quote(method.equals("YAPE") ? MembershipQuoteRequest.Operation.ENROLL : MembershipQuoteRequest.Operation.RENEW);
            var req = new MembershipSaleRequest(client, plan, q.membershipId() == null ? MembershipQuoteRequest.Operation.ENROLL : MembershipQuoteRequest.Operation.RENEW,
                    q.fingerprint(), q.pricePen(), method, "  REF-" + method + "  ");
            var result = memberships.sell(req, UUID.randomUUID().toString(), ACTOR);
            assertEquals("REF-" + method, result.reference()); assertEquals(ACTOR, result.registeredBy());
        }
        assertEquals(3, count("payment_records"));
    }
    @Test void cancelledContractInactivePlanAndMissingClientReject() throws Exception {
        var request = new MembershipQuoteRequest(UUID.randomUUID(), plan, MembershipQuoteRequest.Operation.ENROLL);
        mvc.perform(post("/api/v1/memberships/quote").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE plans SET status='INACTIVO' WHERE id=?", plan);
        assertThrows(com.gymvip.exception.InvalidPlanException.class, () -> quote(MembershipQuoteRequest.Operation.ENROLL));
        jdbc.update("UPDATE plans SET status='ACTIVO' WHERE id=?", plan);
        var first = enroll(); jdbc.update("UPDATE memberships SET status='CANCELADA' WHERE id=?", first.membershipId());
        assertThrows(com.gymvip.exception.ConflictException.class, () -> quote(MembershipQuoteRequest.Operation.RENEW));
    }
    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clientCannotAccessAdministrativeSales() throws Exception {
        mvc.perform(get("/api/v1/memberships/clients/" + client + "/history")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/memberships/quote").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new MembershipQuoteRequest(client, plan, MembershipQuoteRequest.Operation.ENROLL))))
                .andExpect(status().isForbidden());
    }
    @Test void unauthenticatedRejectedAndMissingKeyIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/memberships/payment-methods").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/memberships/sales").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(sale(quote(MembershipQuoteRequest.Operation.ENROLL))))).andExpect(status().isBadRequest());
    }
    @Test void failedRenewalDoesNotExtendCoverageAndNewPlanAffectsOnlyNewPeriod() {
        var original = enroll();
        var q = quote(MembershipQuoteRequest.Operation.RENEW);
        var bad = new MembershipSaleRequest(client, plan, MembershipQuoteRequest.Operation.RENEW, q.fingerprint(),
                new BigDecimal("1.00"), "EFECTIVO", null);
        assertThrows(IllegalArgumentException.class, () -> memberships.sell(bad, UUID.randomUUID().toString(), ACTOR));
        assertEquals(original.endExclusive(), quote(MembershipQuoteRequest.Operation.RENEW).startDate());
        assertEquals(1, count("payment_records"));
        plan = plans.createPlan(new CreatePlanRequest("Nuevo mensual " + UUID.randomUUID(), "", 30,
                new BigDecimal("180.00"), "Otro servicio contratado"), "admin@vipcenterfit.pe").id();
        var changed = memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), UUID.randomUUID().toString(), ACTOR);
        assertEquals(original.endExclusive(), changed.startDate());
        assertEquals(new BigDecimal("180.00"), changed.amountPen());
        assertEquals(original, memberships.history(client).getLast());
    }
    @Test void idempotencyIsScopedToWorkerAndReplaysAfterDateChange() {
        String key = UUID.randomUUID().toString();
        var request = sale(quote(MembershipQuoteRequest.Operation.ENROLL));
        var first = memberships.sell(request, key, ACTOR);
        var second = memberships.sell(sale(quote(MembershipQuoteRequest.Operation.RENEW)), key, "admin@vipcenterfit.pe");
        assertNotEquals(first.paymentId(), second.paymentId());
        clock.now = Instant.parse("2027-01-01T17:00:00Z");
        assertEquals(first, memberships.sell(request, key, ACTOR));
        assertEquals(2, count("payment_records"));
    }
    @Test void inactiveClientAndNonMonthlyPlanCannotBeSold() {
        jdbc.update("UPDATE clients SET status='INACTIVO' WHERE id=?", client);
        assertThrows(IllegalArgumentException.class, () -> quote(MembershipQuoteRequest.Operation.ENROLL));
        jdbc.update("UPDATE clients SET status='ACTIVO' WHERE id=?", client);
        plan = plans.createPlan(new CreatePlanRequest("Pase diario " + UUID.randomUUID(), "", 1,
                new BigDecimal("20.00"), ""), "admin@vipcenterfit.pe").id();
        assertThrows(com.gymvip.exception.InvalidPlanException.class, () -> quote(MembershipQuoteRequest.Operation.ENROLL));
        assertEquals(0, count("memberships"));
    }
}
