package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.*;
import com.gymvip.dto.response.*;
import com.gymvip.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JorgeIntegrationTest.TimeConfig.class)
@WithMockUser(username="recepcion@vipcenterfit.pe",roles="RECEPCION")
class JorgeIntegrationTest {
    static class MutableClock extends Clock {
        volatile Instant now=Instant.parse("2026-10-26T17:00:00Z");
        public ZoneId getZone() {return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone) {return Clock.fixed(now,zone);}
        public Instant instant() {return now;}
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock jorgeClock() {return new MutableClock();}
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired ClientEditingService editing;
    @Autowired ClientService clients;
    @Autowired CoverageService coverage;
    @Autowired MembershipService memberships;
    @Autowired PlanService plans;
    UUID client,plan; String document,code;
    @BeforeEach void fixtures() {
        assertEquals("gymvip_test_jorge",jdbc.queryForObject("SELECT current_schema()",String.class));
        clock.now=Instant.parse("2026-10-26T17:00:00Z");
        client=UUID.randomUUID();code="JOR-"+client.toString().substring(0,8);
        document=String.format("%08d",Math.floorMod(client.hashCode(),100000000));
        jdbc.update("INSERT INTO clients(id,first_name,last_name,birth_date,member_code,phone,email) VALUES (?,'Jorge','Prueba','1990-01-01',?,'999888777','antes@example.com')",client,code);
        jdbc.update("INSERT INTO documents(id,client_id,country_code,document_type,document_number) VALUES (?,?,'PE','DNI',?)",UUID.randomUUID(),client,document);
        plan=plans.createPlan(new CreatePlanRequest("Mensual Jorge "+UUID.randomUUID(),"Fixture",30,new BigDecimal("100.00"),"Base"),"admin@vipcenterfit.pe").id();
    }
    UpdateClientRequest edit(long version,String name,String doc) {
        return new UpdateClientRequest(name,"Actualizado","PE","DNI",doc,"999888777",null,version);
    }
    MembershipSaleResponse sell(MembershipQuoteRequest.Operation operation) {
        var quote=memberships.quote(new MembershipQuoteRequest(client,plan,operation));
        return memberships.sell(new MembershipSaleRequest(client,plan,operation,quote.fingerprint(),quote.pricePen(),"EFECTIVO",null),UUID.randomUUID().toString(),"recepcion@vipcenterfit.pe");
    }
    @Test void receptionEditsAndAdminSeesExactHistory() throws Exception {
        mvc.perform(put("/api/v1/clients/"+client).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0,"Nuevo",document))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.email").doesNotExist());
        assertEquals("Nuevo",clients.getClientById(client).firstName());
        mvc.perform(get("/api/v1/clients/"+client+"/changes")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clients/"+client+"/changes").with(user("admin@vipcenterfit.pe").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].actor").value("recepcion@vipcenterfit.pe"));
        assertEquals("Jorge",jdbc.queryForObject("SELECT previous_value FROM client_changes WHERE client_id=? AND field_name='firstName'",String.class,client));
        assertEquals("Nuevo",jdbc.queryForObject("SELECT new_value FROM client_changes WHERE client_id=? AND field_name='firstName'",String.class,client));
    }
    @Test void invalidDuplicateAndStaleEditsAreRejectedWithoutPartialWrites() throws Exception {
        mvc.perform(put("/api/v1/clients/"+client).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0," ",document))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/clients/"+client).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0,"Nuevo","ABC"))))
                .andExpect(status().isBadRequest());
        UUID other=UUID.randomUUID();String otherDoc=String.format("%08d",Math.floorMod(other.hashCode(),100000000));
        jdbc.update("INSERT INTO clients(id,first_name,last_name,birth_date) VALUES (?,'Otro','Cliente','1990-01-01')",other);
        jdbc.update("INSERT INTO documents(id,client_id,country_code,document_type,document_number) VALUES (?,?,'PE','DNI',?)",UUID.randomUUID(),other,otherDoc);
        mvc.perform(put("/api/v1/clients/"+client).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0,"Nuevo",otherDoc))))
                .andExpect(status().isConflict());
        assertEquals("Jorge",clients.getClientById(client).firstName());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM client_changes WHERE client_id=?",Long.class,client));
        editing.update(client,edit(0,"Primero",document),"recepcion@vipcenterfit.pe");
        mvc.perform(put("/api/v1/clients/"+client).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0,"Segundo",document))))
                .andExpect(status().isConflict());
        assertEquals("Primero",clients.getClientById(client).firstName());
    }
    @Test void simultaneousEditsHaveOneWinnerAndOneAuditTransaction() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{gate.await();try {editing.update(client,edit(0,Thread.currentThread().getName(),document),"recepcion@vipcenterfit.pe");return true;}
                catch(com.gymvip.exception.ConflictException | org.springframework.orm.ObjectOptimisticLockingFailureException e){return false;}};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(1,clients.getClientById(client).version());
            assertEquals(1L,jdbc.queryForObject("SELECT count(DISTINCT change_id) FROM client_changes WHERE client_id=?",Long.class,client));
        } finally {pool.shutdownNow();}
    }
    @Test void documentOnlyEditStillAdvancesVersionUnderFixedClock() {
        var first=editing.update(client,edit(0,"Nuevo",document),"recepcion@vipcenterfit.pe");
        String secondDoc=String.format("%08d",Math.floorMod(UUID.randomUUID().hashCode(),100000000));
        var second=editing.update(client,edit(first.version(),"Nuevo",secondDoc),"recepcion@vipcenterfit.pe");
        assertEquals(first.version()+1,second.version());
    }
    @Test void paidCoverageExpiresAtExactLimaCutoffAndRenewalUpdatesList() throws Exception {
        sell(MembershipQuoteRequest.Operation.ENROLL);
        clock.now=Instant.parse("2026-11-25T04:59:59Z");
        mvc.perform(get("/api/v1/coverage/clients/"+client)).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.state").value("POR_VENCER"));
        clock.now=Instant.parse("2026-11-25T05:00:00Z");
        assertFalse(coverage.verify(client).allowed());assertEquals("VENCIDO",coverage.verify(client).state());
        sell(MembershipQuoteRequest.Operation.RENEW);
        assertTrue(coverage.verify(client).allowed());
        mvc.perform(get("/api/v1/coverage").param("query",code).param("state","TODOS").param("planId",plan.toString())
                        .param("from","2026-12-24").param("to","2026-12-24"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastIncludedDate").value("2026-12-24"));
        mvc.perform(get("/api/v1/coverage").param("query",code).param("state","ALERTAS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void unpaidCancelledAndFuturePeriodsDoNotGrantEntry() {
        assertEquals("SIN_MEMBRESIA",coverage.verify(client).state());
        var sale=sell(MembershipQuoteRequest.Operation.ENROLL);
        jdbc.update("UPDATE memberships SET status='CANCELADA' WHERE id=?",sale.membershipId());
        assertEquals("CANCELADA",coverage.verify(client).state());assertFalse(coverage.verify(client).allowed());
        jdbc.update("UPDATE memberships SET status='VIGENTE' WHERE id=?",sale.membershipId());
        clock.now=Instant.parse("2026-10-25T17:00:00Z");
        assertEquals("PENDIENTE",coverage.verify(client).state());assertFalse(coverage.verify(client).allowed());
    }
    @Test void partialOrUnlinkedPaymentsNeverAuthorizeCoverage() {
        var sale=sell(MembershipQuoteRequest.Operation.ENROLL);
        jdbc.update("UPDATE payment_records SET amount_pen=1 WHERE id=?",sale.paymentId());
        assertFalse(coverage.verify(client).allowed());
        jdbc.update("UPDATE payment_records SET amount_pen=100,period_id=NULL WHERE id=?",sale.paymentId());
        assertFalse(coverage.verify(client).allowed());
    }
    @Test void lateRenewalLeavesGapAndContiguousRenewalsDoNotDuplicateClients() {
        sell(MembershipQuoteRequest.Operation.ENROLL);
        clock.now=Instant.parse("2026-12-10T17:00:00Z");sell(MembershipQuoteRequest.Operation.RENEW);
        clock.now=Instant.parse("2026-12-01T17:00:00Z");assertFalse(coverage.verify(client).allowed());
        clock.now=Instant.parse("2026-12-10T17:00:00Z");sell(MembershipQuoteRequest.Operation.RENEW);
        assertEquals(LocalDate.of(2027,2,8),coverage.verify(client).endExclusive());
        assertEquals(1,coverage.list(code,"TODOS",null,null,null,0,20).getTotalElements());
        assertEquals(0,coverage.list("missing-"+code,"ALERTAS",null,null,null,0,20).getTotalElements());
    }
    @Test void unauthorizedInvalidAndMissingRequestsAreHandled() throws Exception {
        String path="/api/v1/coverage/clients/"+client;
        mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("cliente").roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/clients/"+client).with(user("cliente").roles("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(0,"Nuevo",document)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coverage/clients/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/coverage").param("state","INVALIDO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/coverage").param("from","2026-12-01").param("to","2026-10-01")).andExpect(status().isBadRequest());
    }
}
