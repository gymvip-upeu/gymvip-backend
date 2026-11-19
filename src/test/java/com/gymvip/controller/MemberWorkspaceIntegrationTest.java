package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.*;
import com.gymvip.service.*;
import com.gymvip.exception.ConflictException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@WithMockUser(username="admin@vipcenterfit.pe",roles="ADMIN")
class MemberWorkspaceIntegrationTest {
    @Autowired MemberWorkspaceService workspace;
    @Autowired MembershipService memberships;
    @Autowired ReminderService reminders;
    @Autowired PlanService plans;
    @Autowired ClientService clients;
    @Autowired CoverageService coverage;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    private static final String ACTOR="admin@vipcenterfit.pe";
    MemberWorkspaceService.Registered member;
    UUID plan;
    @BeforeEach void createFixtures() {
        String doc=String.valueOf(10000000+new java.security.SecureRandom().nextInt(89999999));
        var request=new RegisterClientRequest("DemoMiembro",UUID.randomUUID().toString().substring(0,8),LocalDate.of(1990,1,1),"PE","DNI",doc,"999888777",null);
        member=workspace.register(new MemberWorkspaceService.Registration(request,"Nota inicial",false),ACTOR);
        plan=plans.createPlan(new CreatePlanRequest("Trimestral miembro "+UUID.randomUUID(),"",90,new BigDecimal("320.00"),"Pesas","Horario regular"),ACTOR).id();
    }
    com.gymvip.dto.response.MembershipSaleResponse enroll() {
        var quote=memberships.quote(new MembershipQuoteRequest(member.client().id(),plan,MembershipQuoteRequest.Operation.ENROLL));
        return memberships.sell(new MembershipSaleRequest(member.client().id(),plan,MembershipQuoteRequest.Operation.ENROLL,quote.fingerprint(),quote.pricePen(),"EFECTIVO",null,"Pago de prueba",null),UUID.randomUUID().toString(),ACTOR);
    }
    @Test void registrationNotesAndClientRoleArePersistedWithoutLeakingPasswordHash() {
        assertEquals("Nota inicial",workspace.workspace(member.client().id()).get("notes"));
        var access=workspace.provision(member.client().id(),ACTOR);
        assertTrue(access.mustChangePassword());assertTrue(access.temporaryPassword().length()>=12);
        String hash=jdbc.queryForObject("SELECT password_hash FROM accounts WHERE email=?",String.class,access.username());
        assertNotEquals(access.temporaryPassword(),hash);
        assertThrows(ConflictException.class,()->workspace.provision(member.client().id(),ACTOR));
        var login=auth.login(new LoginRequest(access.username(),access.temporaryPassword()),"127.0.0.1","test");
        assertEquals(Set.of("CLIENTE"),login.roles());
    }
    @Test void temporaryAccessCannotReadDataUntilPasswordChangeAndNeverGetsStaffAccess() throws Exception {
        var access=workspace.provision(member.client().id(),ACTOR);
        var login=auth.login(new LoginRequest(access.username(),access.temporaryPassword()),"127.0.0.1","test");
        mvc.perform(get("/api/v1/member-app/me").header("Authorization","Bearer "+login.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/plans").header("Authorization","Bearer "+login.token())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/member-app/password").header("Authorization","Bearer "+login.token()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new MemberWorkspaceService.PasswordChange(access.temporaryPassword(),"NuevaClave1234!")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/member-app/session").header("Authorization","Bearer "+login.token()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
        var fresh=auth.login(new LoginRequest(access.username(),"NuevaClave1234!"),"127.0.0.1","test");
        mvc.perform(get("/api/v1/member-app/me").header("Authorization","Bearer "+fresh.token())).andExpect(status().isOk()).andExpect(jsonPath("$.client.id").value(member.client().id().toString()));
        mvc.perform(get("/api/v1/members/"+member.client().id()).header("Authorization","Bearer "+fresh.token())).andExpect(status().isForbidden());
    }
    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void authorizedMemberWithExpiredMembershipReadsPublishedOffers() throws Exception {
        enroll();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));
        jdbc.update("""
                UPDATE membership_periods SET start_date = ?, end_date = ?
                WHERE membership_id IN (SELECT id FROM memberships WHERE client_id = ?)
                """, today.minusDays(31), today.minusDays(1), member.client().id());

        var supplement = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO supplements(id, name, brand, category, presentation, net_content, description,
                    price_pen, availability, created_at_utc, updated_at_utc)
                VALUES (?, ?, 'Marca QA', 'PROTEINA', 'Pote', '900 g', 'Detalle QA',
                        150, 'AGOTADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, supplement, "Suplemento QA " + supplement);
        jdbc.update("""
                INSERT INTO supplement_promotions(id, supplement_id, name, description, conditions,
                    reference_price_pen, offer_price_pen, start_date, end_date, created_at_utc, updated_at_utc)
                VALUES (?, ?, 'Oferta QA', 'Descripción QA', 'Solo en recepción',
                        150, 135, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), supplement, today.minusDays(1), today.plusDays(1));

        var access = workspace.provision(member.client().id(), ACTOR);
        workspace.changePassword(access.username(),
                new MemberWorkspaceService.PasswordChange(access.temporaryPassword(), "NuevaClave1234!"));
        var login = auth.login(new LoginRequest(access.username(), "NuevaClave1234!"), "127.0.0.1", "qa-test");
        String product = "$[?(@.id == '" + supplement + "')]";
        mvc.perform(get("/api/v1/catalog/products").header("Authorization", "Bearer " + login.token())
                        .param("size", "60").param("page", findCatalogPage(supplement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(product + ".availability").value(org.hamcrest.Matchers.contains("AGOTADO")))
                .andExpect(jsonPath(product + ".offer.name").value(org.hamcrest.Matchers.contains("Oferta QA")))
                .andExpect(jsonPath(product + ".offer.description").value(org.hamcrest.Matchers.contains("Descripción QA")))
                .andExpect(jsonPath(product + ".offer.conditions").value(org.hamcrest.Matchers.contains("Solo en recepción")))
                .andExpect(jsonPath(product + ".offer.pricePen").value(org.hamcrest.Matchers.contains(135.0)));
        var current = clients.getClientById(member.client().id());
        workspace.status(current.id(), new MemberWorkspaceService.StatusChange("INACTIVO", current.version()), ACTOR);
        mvc.perform(get("/api/v1/catalog/products").header("Authorization", "Bearer " + login.token())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/catalog/products").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private String findCatalogPage(UUID supplement) {
        Long index = jdbc.queryForObject("""
                SELECT count(*) FROM supplements p CROSS JOIN supplements item
                WHERE item.id = ? AND p.published
                AND (lower(p.name), lower(p.brand), p.id) < (lower(item.name), lower(item.brand), item.id)
                """, Long.class, supplement);
        return Long.toString(index / 60);
    }

    @Test void attendanceRequiresPaidCoverageAndDuplicateDayDoesNotCreateAnotherRecord() {
        assertThrows(ConflictException.class,()->workspace.checkIn(member.client().id(),ACTOR));
        enroll();assertEquals(true,workspace.checkIn(member.client().id(),ACTOR).get("recorded"));
        assertEquals(false,workspace.checkIn(member.client().id(),ACTOR).get("recorded"));
        assertEquals(1L,workspace.workspace(member.client().id()).get("attendanceCount"));
    }
    @Test void deactivationStopsAttendanceAndKeepsPaymentHistory() {
        enroll();var detail=clients.getClientById(member.client().id());
        workspace.status(member.client().id(),new MemberWorkspaceService.StatusChange("INACTIVO",detail.version()),ACTOR);
        assertFalse(coverage.verify(member.client().id()).allowed());assertThrows(ConflictException.class,()->workspace.checkIn(member.client().id(),ACTOR));
        assertEquals(1,((List<?>)workspace.workspace(member.client().id()).get("payments")).size());
    }
    @Test void cancelledPeriodStopsCoverageButKeepsPaymentAndAllowsReplacement() {
        var sale=enroll();workspace.cancel(member.client().id(),sale.periodId(),new MemberWorkspaceService.Cancellation("Plan asignado por error"),ACTOR);
        assertFalse(coverage.verify(member.client().id()).allowed());
        assertEquals("CANCELADA",coverage.verify(member.client().id()).state());
        assertEquals(1,((List<?>)workspace.workspace(member.client().id()).get("payments")).size());
        enroll();assertTrue(coverage.verify(member.client().id()).allowed());
        assertEquals(2,((List<?>)workspace.workspace(member.client().id()).get("periods")).size());
    }
    @Test @WithMockUser(username="recepcion@vipcenterfit.pe",roles="RECEPCION")
    void receptionCannotCancelOrDeactivateMember() throws Exception {
        var sale=enroll();
        mvc.perform(post("/api/v1/members/"+member.client().id()+"/periods/"+sale.periodId()+"/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Corrección\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/members/"+member.client().id()+"/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVO\",\"version\":0}")).andExpect(status().isForbidden());
        assertTrue(coverage.verify(member.client().id()).allowed());
    }
    @Test void futureStartUsesPlanDurationAndPaymentNotesAreStored() {
        LocalDate start=LocalDate.now(clock.withZone(ZoneId.of("America/Lima"))).plusDays(5);
        var quote=memberships.quote(new MembershipQuoteRequest(member.client().id(),plan,MembershipQuoteRequest.Operation.ENROLL,start));
        assertEquals(start.plusDays(90),quote.endExclusive());
        memberships.sell(new MembershipSaleRequest(member.client().id(),plan,MembershipQuoteRequest.Operation.ENROLL,quote.fingerprint(),quote.pricePen(),"YAPE","OP-123","Pago anticipado",start),UUID.randomUUID().toString(),ACTOR);
        assertEquals("PENDIENTE",coverage.verify(member.client().id()).state());
        assertEquals("Pago anticipado",jdbc.queryForObject("SELECT notes FROM payment_records r JOIN memberships m ON m.id=r.membership_id WHERE m.client_id=?",String.class,member.client().id()));
    }
    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void firstAccessCanReadAndVerifyConsentsWithoutWriteLock() throws Exception {
        var access = workspace.provision(member.client().id(), ACTOR);
        var login = auth.login(new LoginRequest(access.username(), access.temporaryPassword()), "127.0.0.1", "qa-test");
        mvc.perform(get("/api/v1/member-app/consents").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.termsAccepted").value(false));
        mvc.perform(post("/api/v1/member-app/consents").header("Authorization", "Bearer " + login.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        new MemberWorkspaceService.ConsentRequest(true, true, true, "borrador-mobile-v1"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/member-app/consents").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.termsAccepted").value(true))
                .andExpect(jsonPath("$.privacyAcknowledged").value(true)).andExpect(jsonPath("$.whatsappConsent").value(true))
                .andExpect(jsonPath("$.documentVersion").value("borrador-mobile-v1"));
        mvc.perform(get("/api/v1/member-app/inbox").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isForbidden());
    }
    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void inboxReadPersistsForOwnClientAndStaffCannotAccess() throws Exception {
        var access = workspace.provision(member.client().id(), ACTOR);
        workspace.changePassword(access.username(), new MemberWorkspaceService.PasswordChange(access.temporaryPassword(), "NuevaClave1234!"));
        var login = auth.login(new LoginRequest(access.username(), "NuevaClave1234!"), "127.0.0.1", "qa-test");
        String item = "announcement_welcome_" + member.client().id();
        mvc.perform(get("/api/v1/member-app/inbox").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + item + "')].isRead").value(org.hamcrest.Matchers.contains(false)));
        mvc.perform(post("/api/v1/member-app/inbox/" + item + "/read").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/member-app/inbox").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + item + "')].isRead").value(org.hamcrest.Matchers.contains(true)));
        for (String role : List.of("ADMIN", "RECEPCION")) {
            var staff = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("staff").roles(role);
            mvc.perform(get("/api/v1/member-app/inbox").with(staff)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/member-app/consents").with(staff)).andExpect(status().isForbidden());
        }
    }
    @Test
    void appReminderUsesConsentCurrentPeriodAndPrivateInbox() {
        enroll();
        UUID id = member.client().id();
        LocalDate expiry = LocalDate.now(clock.withZone(ZoneId.of("America/Lima"))).plusDays(7);
        jdbc.update("""
                UPDATE membership_periods SET end_date = ?
                WHERE membership_id IN (SELECT id FROM memberships WHERE client_id = ?)
                """, expiry, id);
        var original = reminders.getConfig();
        try {
            reminders.updateConfig(new ReminderConfigRequest(true, List.of(7), 5, List.of("APP")), ACTOR);
            reminders.evaluateReminders(ACTOR);
            assertEquals(0, appReminders(id).size());

            workspace.saveConsents(id, new MemberWorkspaceService.ConsentRequest(true, true, false, "qa-app"));
            reminders.evaluateReminders(ACTOR);
            reminders.evaluateReminders(ACTOR);

            var notices = appReminders(id);
            assertEquals(1, notices.size());
            assertTrue(notices.getFirst().message().contains("7 días restantes"));
            assertTrue(notices.getFirst().message().contains(expiry.toString()));
            assertEquals(id.toString(), notices.getFirst().accountId());
        } finally {
            reminders.updateConfig(new ReminderConfigRequest(original.enabled(), original.anticipationDays(),
                    original.postExpiryDays(), original.channels()), ACTOR);
        }
    }

    private List<MemberWorkspaceService.MemberInboxItem> appReminders(UUID client) {
        return workspace.listInbox(client).stream()
                .filter(item -> "RECORDATORIO".equals(item.category())).toList();
    }

    @Test void memberStatusRejectsConcurrentVersion() {
        var current=clients.getClientById(member.client().id());
        workspace.status(member.client().id(),new MemberWorkspaceService.StatusChange("INACTIVO",current.version()),ACTOR);
        assertThrows(ConflictException.class,()->workspace.status(member.client().id(),new MemberWorkspaceService.StatusChange("ACTIVO",current.version()),ACTOR));
    }
}
