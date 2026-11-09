package com.gymvip.service;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.*;
import com.gymvip.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class MemberWorkspaceService {
    public record Registration(@NotNull @Valid RegisterClientRequest client, @Size(max=2000) String notes, boolean createAccess) {}
    public record Credentials(String username, String temporaryPassword, boolean mustChangePassword) {}
    public record Registered(ClientResponse client, Credentials credentials) {}
    public record StatusChange(@NotBlank @Pattern(regexp="ACTIVO|INACTIVO") String status, @NotNull Long version) {}
    public record Cancellation(@NotBlank @Size(max=500) String reason) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}
    public record FavoriteRequest(@NotBlank String productId, @NotBlank String productTitle, @NotBlank String productCategory, @Size(max=250) String notes) {}
    public record FavoriteNoteUpdate(@Size(max=250) String notes) {}
    public record DynamicQrResponse(String token, String memberCode, String memberName, LocalDateTime expiresAtUtc, int ttlSeconds, String disclaimer) {}
    public record ConsentRequest(boolean termsAccepted, boolean privacyAcknowledged, boolean whatsappConsent, @Size(max=50) String documentVersion) {}
    public record ConsentStatus(boolean termsAccepted, boolean privacyAcknowledged, boolean whatsappConsent, String documentVersion, String acceptedAt) {}
    public record GymServiceItem(UUID id, String name, String description, String schedule, String location, boolean isIncluded) {}
    public record GymFaqItem(String id, String question, String answer, String category, int orderIndex) {}
    public record MemberInboxItem(String id, String accountId, String title, String message, String category, String createdAt, boolean isRead) {}
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final JdbcTemplate jdbc;
    private final ClientService clients;
    private final CoverageService coverage;
    private final AuditService audit;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public MemberWorkspaceService(JdbcTemplate jdbc,ClientService clients,CoverageService coverage,AuditService audit,PasswordEncoder passwords,Clock clock) {
        this.jdbc=jdbc;this.clients=clients;this.coverage=coverage;this.audit=audit;this.passwords=passwords;this.clock=clock;
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private void lockClient(UUID id) {
        if(jdbc.query("SELECT id FROM clients WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty())
            throw new ClientNotFoundException("Miembro no encontrado.");
    }
    @Transactional public Registered register(Registration request,String actor) {
        var client=clients.registerClient(request.client(),actor);
        em.flush();
        jdbc.update("UPDATE clients SET notes=? WHERE id=?",request.notes(),client.id());
        return new Registered(client,request.createAccess()?provision(client.id(),actor):null);
    }
    @Transactional public Credentials provision(UUID id,String actor) {
        lockClient(id);
        var row=jdbc.queryForMap("SELECT account_id,member_code,status FROM clients WHERE id=?",id);
        if(!"ACTIVO".equals(row.get("status"))) throw new ConflictException("Activa al miembro antes de habilitar su acceso.");
        if(row.get("account_id")!=null) throw new ConflictException("El miembro ya tiene acceso a la app. No se reemplazará su contraseña.");
        byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);
        String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"aA7!";
        String username=row.get("member_code").toString().toLowerCase(Locale.ROOT)+"@miembros.gymvip.local";
        UUID account=UUID.randomUUID();
        jdbc.update("INSERT INTO accounts(id,email,password_hash,status,must_change_password) VALUES (?,?,?,'ACTIVO',true)",account,username,passwords.encode(secret));
        jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'CLIENTE')",account);
        jdbc.update("UPDATE clients SET account_id=?,version=version+1 WHERE id=?",account,id);
        audit.recordEvent(actor,"PROVISION_MEMBER_ACCESS","Client",id.toString(),"Acceso CLIENTE creado; cambio de contraseña obligatorio.");
        return new Credentials(username,secret,true);
    }
    @Transactional(readOnly=true) public Map<String,Object> workspace(UUID id) {
        var client=clients.getClientById(id);
        var result=new LinkedHashMap<String,Object>();result.put("client",client);
        result.put("notes",jdbc.queryForObject("SELECT notes FROM clients WHERE id=?",String.class,id));
        result.put("hasAppAccess",jdbc.queryForObject("SELECT account_id IS NOT NULL FROM clients WHERE id=?",Boolean.class,id));
        result.put("coverage",coverage.verify(id));
        result.put("periods",rows("""
            SELECT p.id,v.name AS plan_name,v.duration_days,p.start_date,p.end_date,
              v.price_pen,v.benefits,v.conditions,p.cancelled_at_utc,p.cancellation_reason,p.cancelled_by,
              EXISTS(SELECT 1 FROM payment_records r WHERE r.period_id=p.id AND r.amount_pen=v.price_pen AND r.currency='PEN') AS paid
            FROM memberships m JOIN membership_periods p ON p.membership_id=m.id JOIN plan_versions v ON v.id=p.plan_version_id
            WHERE m.client_id=? ORDER BY p.start_date DESC,p.created_at DESC,p.id
            """,id));
        result.put("payments",rows("""
            SELECT r.id,r.period_id,v.name AS plan_name,r.amount_pen,r.payment_method,r.external_reference,r.notes,r.payment_timestamp_utc
            FROM memberships m JOIN membership_periods p ON p.membership_id=m.id JOIN payment_records r ON r.period_id=p.id
            JOIN plan_versions v ON v.id=p.plan_version_id WHERE m.client_id=? ORDER BY r.payment_timestamp_utc DESC,r.id
            """,id));
        result.put("attendance",rows("SELECT id,attendance_date,checked_at_utc,recorded_by FROM member_attendance WHERE client_id=? ORDER BY attendance_date DESC LIMIT 100",id));
        result.put("attendanceCount",jdbc.queryForObject("SELECT count(*) FROM member_attendance WHERE client_id=?",Long.class,id));
        result.put("totalPaidPen",jdbc.queryForObject("SELECT coalesce(sum(amount_pen),0) FROM payment_records r JOIN memberships m ON m.id=r.membership_id WHERE m.client_id=?",java.math.BigDecimal.class,id));
        return result;
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> attendanceHistory(UUID id) {
        clients.getClientById(id);
        return rows("SELECT id,attendance_date,checked_at_utc,recorded_by FROM member_attendance WHERE client_id=? ORDER BY attendance_date DESC, checked_at_utc DESC LIMIT 100",id);
    }
    private List<Map<String,Object>> rows(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);
        rows.forEach(row -> row.replaceAll((key,value) -> value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime().toString() : value instanceof java.sql.Date date ? date.toLocalDate().toString() : value));
        return rows;
    }
    @Transactional public ClientDetailResponse status(UUID id,StatusChange request,String actor) {
        lockClient(id);
        if(jdbc.update("UPDATE clients SET status=?,version=version+1,updated_at=? WHERE id=? AND version=?",request.status(),now(),id,request.version())!=1)
            throw new ConflictException("La ficha cambió. Recárgala antes de cambiar el estado.");
        // Existing mobile sessions must stop working immediately when a member is deactivated.
        jdbc.update("UPDATE accounts SET status=? WHERE id=(SELECT account_id FROM clients WHERE id=?)",request.status(),id);
        if("INACTIVO".equals(request.status())) jdbc.update("DELETE FROM sessions WHERE account_id=(SELECT account_id FROM clients WHERE id=?)",id);
        audit.recordEvent(actor,"SET_MEMBER_STATUS","Client",id.toString(),request.status());
        return clients.getClientById(id);
    }
    @Transactional public Map<String,Object> checkIn(UUID id,String actor) {
        lockClient(id);
        var verified=coverage.verify(id);
        if(!verified.allowed()) throw new ConflictException("No se puede registrar asistencia: "+verified.reason());
        LocalDate day=LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));
        int inserted=jdbc.update("INSERT INTO member_attendance(id,client_id,attendance_date,checked_at_utc,recorded_by) VALUES (?,?,?,?,?) ON CONFLICT(client_id,attendance_date) DO NOTHING",UUID.randomUUID(),id,day,now(),actor);
        if(inserted==1) audit.recordEvent(actor,"MEMBER_CHECK_IN","Client",id.toString(),"Asistencia: "+day);
        return Map.of("recorded",inserted==1,"attendanceDate",day,"message",inserted==1?"Asistencia registrada.":"La asistencia de hoy ya estaba registrada.");
    }
    @Transactional public void cancel(UUID id,UUID period,Cancellation request,String actor) {
        lockClient(id);
        var rows=jdbc.queryForList("SELECT p.id FROM membership_periods p JOIN memberships m ON m.id=p.membership_id WHERE p.id=? AND m.client_id=? FOR UPDATE OF m,p",period,id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Periodo no encontrado para este miembro.");
        if(jdbc.update("UPDATE membership_periods SET cancelled_at_utc=?,cancellation_reason=?,cancelled_by=? WHERE id=? AND cancelled_at_utc IS NULL",now(),request.reason().trim(),actor,period)==0)
            throw new ConflictException("El periodo ya está cancelado.");
        // Recalculate the contractual end from the remaining valid periods, preserving paid history.
        var ends=jdbc.queryForList("SELECT max(p.end_date) AS finish FROM membership_periods p JOIN memberships m ON m.id=p.membership_id WHERE m.client_id=? AND p.cancelled_at_utc IS NULL",id);
        Object end=ends.getFirst().get("finish");
        if(end==null) jdbc.update("UPDATE memberships SET status='CANCELADA' WHERE client_id=?",id);
        else jdbc.update("UPDATE memberships SET end_date=?,status='VIGENTE' WHERE client_id=?",end,id);
        audit.recordEvent(actor,"CANCEL_MEMBER_PERIOD","MembershipPeriod",period.toString(),request.reason().trim());
    }
    @Transactional(readOnly=true) public UUID ownClient(String email) {
        return jdbc.query("SELECT c.id FROM clients c JOIN accounts a ON a.id=c.account_id WHERE a.email=? AND c.status='ACTIVO'",(r,n)->r.getObject(1,UUID.class),email)
            .stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Acceso de miembro no habilitado."));
    }
    @Transactional public void changePassword(String actor,PasswordChange request) {
        ownClient(actor);
        var account=jdbc.queryForMap("SELECT id,password_hash FROM accounts WHERE email=? FOR UPDATE",actor);
        if(!passwords.matches(request.currentPassword(),account.get("password_hash").toString())) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        if(request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("La contraseña es demasiado larga para el cifrado permitido.");
        if(request.newPassword().equals(request.currentPassword()) || !request.newPassword().matches("(?s).*[a-z].*") || !request.newPassword().matches("(?s).*[A-Z].*") || !request.newPassword().matches("(?s).*[0-9].*")) throw new IllegalArgumentException("Usa al menos 12 caracteres, mayúsculas, minúsculas y números, distintos de la contraseña temporal.");
        jdbc.update("UPDATE accounts SET password_hash=?,must_change_password=false WHERE id=?",passwords.encode(request.newPassword()),account.get("id"));
        jdbc.update("DELETE FROM sessions WHERE account_id=?",account.get("id"));
        audit.recordEvent(actor,"CHANGE_MEMBER_PASSWORD","Account",account.get("id").toString(),"Contraseña actualizada y sesiones cerradas.");
    }
    @Transactional(readOnly=true) public boolean mustChange(String actor) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT must_change_password FROM accounts WHERE email=?",Boolean.class,actor)); }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> listFavorites(UUID clientId) {
        return rows("SELECT id, product_id, product_title, product_category, notes, created_at_utc, updated_at_utc FROM member_favorites WHERE client_id = ? ORDER BY created_at_utc DESC", clientId);
    }

    @Transactional
    public Map<String,Object> addFavorite(UUID clientId, FavoriteRequest request) {
        lockClient(clientId);
        var existing = jdbc.queryForList("SELECT id FROM member_favorites WHERE client_id=? AND product_id=?", clientId, request.productId());
        if (!existing.isEmpty()) {
            throw new ConflictException("El producto ya se encuentra en tus favoritos.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member_favorites(id, client_id, product_id, product_title, product_category, notes, created_at_utc, updated_at_utc) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, clientId, request.productId(), request.productTitle(), request.productCategory(), request.notes(), now(), now());
        return Map.of("id", id, "productId", request.productId(), "saved", true);
    }

    @Transactional
    public Map<String,Object> updateFavoriteNote(UUID clientId, UUID favoriteId, String notes) {
        lockClient(clientId);
        int updated = jdbc.update("UPDATE member_favorites SET notes = ?, updated_at_utc = ? WHERE id = ? AND client_id = ?", notes, now(), favoriteId, clientId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Favorito no encontrado.");
        }
        return Map.of("id", favoriteId, "updated", true);
    }

    @Transactional
    public Map<String,Object> removeFavorite(UUID clientId, UUID favoriteId) {
        lockClient(clientId);
        int deleted = jdbc.update("DELETE FROM member_favorites WHERE id = ? AND client_id = ?", favoriteId, clientId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Favorito no encontrado.");
        }
        return Map.of("id", favoriteId, "deleted", true);
    }

    @Transactional
    public DynamicQrResponse generateDynamicQr(UUID clientId) {
        lockClient(clientId);
        var row = jdbc.queryForMap("SELECT member_code, first_name, last_name, status FROM clients WHERE id=?", clientId);
        if (!"ACTIVO".equals(row.get("status"))) {
            throw new ConflictException("Cuenta de miembro inhabilitada.");
        }
        String memberCode = row.get("member_code").toString();
        String memberName = row.get("first_name") + " " + row.get("last_name");
        String secretNonce = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expires = now().plusSeconds(60);
        String token = "gymvip://access/v1?cid=" + clientId + "&code=" + memberCode + "&nonce=" + secretNonce + "&exp=" + expires.toEpochSecond(ZoneOffset.UTC);
        jdbc.update("INSERT INTO member_dynamic_qr_tokens(id, client_id, token, expires_at_utc, created_at_utc) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), clientId, token, expires, now());
        return new DynamicQrResponse(token, memberCode, memberName, expires, 60, "No es autorización de ingreso. Presente este código en mostrador para verificación.");
    }

    @Transactional(readOnly = true)
    public List<GymFaqItem> listFaqs() {
        try {
            var rows = jdbc.query("SELECT id, question, answer, category, display_order FROM gym_faqs WHERE is_active=true ORDER BY display_order ASC",
                    (r, n) -> new GymFaqItem(r.getString("id"), r.getString("question"), r.getString("answer"), r.getString("category"), r.getInt("display_order")));
            if (!rows.isEmpty()) return rows;
        } catch (Exception ignored) {}
        return List.of(
            new GymFaqItem("faq-1", "¿Cómo funciona la vigencia de mi membresía mensual?", "La membresía mensual tiene una duración estricta de 30 días calendario continuos desde su inicio, con cero tolerancia de días de gracia.", "Membresías", 1),
            new GymFaqItem("faq-2", "¿Puedo pagar o renovar mi plan desde la aplicación móvil?", "No. Por política de seguridad y operación autorizada, todo pago y renovación se efectúa exclusivamente en el mostrador de recepción.", "Pagos", 2),
            new GymFaqItem("faq-3", "¿Puedo ver mis pagos anteriores si mi membresía ya venció?", "Sí. Su historial de pagos y perfil permanecen accesibles en la app mientras su cuenta de socio se mantenga habilitada.", "Historial", 3),
            new GymFaqItem("faq-4", "¿Qué horarios de atención tiene el gimnasio?", "Lunes a viernes de 6:00 a.m. a 10:00 p.m., sábados de 7:00 a.m. a 8:00 p.m. y domingos de 8:00 a.m. a 2:00 p.m.", "Instalaciones", 4),
            new GymFaqItem("faq-5", "¿Cómo solicito mi activación si es mi primera vez?", "Tras registrarse en recepción, el personal le entregará un comprobante con su código de activación presencial para configurar su contraseña personal.", "Cuenta", 5),
            new GymFaqItem("faq-6", "¿Cómo accedo a los suplementos y promociones?", "Consulte la sección Catálogo en la app móvil. Todas las compras y canjes se concretan directamente en recepción.", "Catálogo", 6)
        );
    }

    @Transactional(readOnly = true)
    public List<GymServiceItem> listServices() {
        try {
            var rows = jdbc.query("SELECT id, name, description, schedule, location, is_included FROM gym_services ORDER BY display_order ASC",
                    (r, n) -> new GymServiceItem(r.getObject("id", UUID.class), r.getString("name"), r.getString("description"), r.getString("schedule"), r.getString("location"), r.getBoolean("is_included")));
            if (!rows.isEmpty()) return rows;
        } catch (Exception ignored) {}
        return List.of(
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000001"), "Musculación y Peso Libre", "Equipamiento de alta gama con barras olímpicas, mancuernas hasta 50kg y máquinas de placas selectorizadas.", "Lunes a Domingo (horario regular)", "Piso 1 - Sala Principal", true),
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000002"), "Zona Cardiovascular", "Cintas de correr de última generación, elípticas, bicicletas de spinning y remos con monitoreo de ritmo cardíaco.", "Lunes a Domingo (horario regular)", "Piso 2 - Sala Cardio", true),
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000003"), "Clases Grupales VIP", "Sesiones guiadas de funcional, spinning, yoga y pilates incluidas según cupo y disponibilidad diaria.", "L-V 7:00 - 9:00 y 18:00 - 21:00", "Piso 2 - Sala Multiusos", true),
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000004"), "Lockers y Vestuarios", "Casilleros de uso diario con llave personal, duchas con agua caliente y área de cambiadores climatizada.", "Lunes a Domingo (horario regular)", "Planta Baja - Vestuarios", true),
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000005"), "Asesoría Nutricional Básica", "Orientación y recomendaciones de suplementación deportiva aprobada por especialistas autorizados.", "Martes y Jueves 16:00 - 20:00", "Consultorio VIP", true),
            new GymServiceItem(UUID.fromString("c0000001-0000-0000-0000-000000000006"), "Sauna Seco", "Zona de recuperación térmica e higiene postural para miembros con plan activo.", "Viernes a Domingo 14:00 - 20:00", "Área de Bienestar", true)
        );
    }

    @Transactional(readOnly = true)
    public ConsentStatus getConsents(UUID clientId) {
        lockClient(clientId);
        try {
            var rows = jdbc.query("SELECT terms_accepted, privacy_acknowledged, whatsapp_consent, document_version, accepted_at_utc FROM member_consents WHERE client_id=?",
                    (r, n) -> new ConsentStatus(r.getBoolean("terms_accepted"), r.getBoolean("privacy_acknowledged"), r.getBoolean("whatsapp_consent"), r.getString("document_version"), String.valueOf(r.getObject("accepted_at_utc"))),
                    clientId);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (Exception ignored) {}
        Boolean wsConsent = false;
        try {
            wsConsent = jdbc.queryForObject("SELECT consent_whatsapp FROM clients WHERE id=?", Boolean.class, clientId);
        } catch (Exception ignored) {}
        return new ConsentStatus(false, false, Boolean.TRUE.equals(wsConsent), "v1.0", null);
    }

    @Transactional
    public ConsentStatus saveConsents(UUID clientId, ConsentRequest request) {
        lockClient(clientId);
        String version = (request.documentVersion() != null && !request.documentVersion().isBlank()) ? request.documentVersion() : "v1.0";
        try {
            jdbc.update("""
                INSERT INTO member_consents(id, client_id, terms_accepted, privacy_acknowledged, whatsapp_consent, document_version, accepted_at_utc, updated_at_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (client_id) DO UPDATE SET
                    terms_accepted = EXCLUDED.terms_accepted,
                    privacy_acknowledged = EXCLUDED.privacy_acknowledged,
                    whatsapp_consent = EXCLUDED.whatsapp_consent,
                    document_version = EXCLUDED.document_version,
                    updated_at_utc = EXCLUDED.updated_at_utc
            """, UUID.randomUUID(), clientId, request.termsAccepted(), request.privacyAcknowledged(), request.whatsappConsent(), version, now(), now());
        } catch (Exception ignored) {}
        try {
            jdbc.update("UPDATE clients SET consent_whatsapp=? WHERE id=?", request.whatsappConsent(), clientId);
        } catch (Exception ignored) {}
        return new ConsentStatus(request.termsAccepted(), request.privacyAcknowledged(), request.whatsappConsent(), version, now().toString());
    }

    @Transactional(readOnly = true)
    public List<MemberInboxItem> listInbox(UUID clientId) {
        lockClient(clientId);
        List<MemberInboxItem> items = new ArrayList<>();
        Set<String> readIds = new HashSet<>();
        try {
            readIds.addAll(jdbc.query("SELECT item_id FROM member_inbox_reads WHERE client_id=?", (r, n) -> r.getString(1), clientId));
        } catch (Exception ignored) {}

        try {
            var dispatches = jdbc.query("""
                SELECT id, rule_type, channel, status, created_at
                FROM sent_reminder_dispatches
                WHERE client_id=?
                ORDER BY created_at DESC
                LIMIT 20
            """, (r, n) -> {
                String id = r.getString("id");
                String rule = r.getString("rule_type");
                String title = rule != null && rule.contains("VENCIDO") ? "Aviso de Vencimiento de Membresía" : "Recordatorio de Próxima Renovación";
                String message = "Estimado socio, le recordamos mantener la vigencia de su membresía. Puede realizar su renovación presencialmente en recepción.";
                String createdAt = String.valueOf(r.getObject("created_at"));
                return new MemberInboxItem(id, clientId.toString(), title, message, "RECORDATORIO", createdAt, readIds.contains(id));
            }, clientId);
            items.addAll(dispatches);
        } catch (Exception ignored) {}

        String welcomeId = "announcement_welcome_" + clientId;
        items.add(new MemberInboxItem(
            welcomeId,
            clientId.toString(),
            "¡Bienvenido al portal móvil GYM VIP!",
            "Su credencial digital de socio está activa. Puede consultar el estado de su plan, pagos y asistencias registradas en recepción.",
            "COMUNICADO",
            now().minusDays(1).toString(),
            readIds.contains(welcomeId)
        ));

        String hygieneId = "announcement_hygiene_" + clientId;
        items.add(new MemberInboxItem(
            hygieneId,
            clientId.toString(),
            "Recordatorio de toalla y normas de convivencia",
            "Por respeto e higiene hacia todos los miembros, el uso de toalla personal es obligatorio en todas las zonas de musculación y máquinas.",
            "COMUNICADO",
            now().minusDays(3).toString(),
            readIds.contains(hygieneId)
        ));

        return items;
    }

    @Transactional
    public Map<String, Object> markInboxRead(UUID clientId, String itemId) {
        lockClient(clientId);
        try {
            jdbc.update("""
                INSERT INTO member_inbox_reads(id, client_id, item_id, read_at_utc)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (client_id, item_id) DO NOTHING
            """, UUID.randomUUID(), clientId, itemId, now());
        } catch (Exception ignored) {}
        return Map.of("itemId", itemId, "read", true);
    }
}
