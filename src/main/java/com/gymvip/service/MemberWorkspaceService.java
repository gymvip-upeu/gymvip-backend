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
}
