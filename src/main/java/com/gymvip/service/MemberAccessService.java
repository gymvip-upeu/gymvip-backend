package com.gymvip.service;

import com.gymvip.exception.ConflictException;
import com.gymvip.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class MemberAccessService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String activationUrl;
    public MemberAccessService(JdbcTemplate jdbc,PasswordEncoder passwords,AuditService audit,Clock clock,
            ApplicationEventPublisher events,@Value("${MEMBER_ACCESS_ACTIVATION_URL:${app.member-access.activation-url:http://127.0.0.1:5173/activar-cuenta}}") String activationUrl) {
        this.jdbc=jdbc; this.passwords=passwords; this.audit=audit; this.clock=clock; this.events=events;
        this.activationUrl=activationUrl;
    }
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    public record SecurityEmail(UUID invitationId,String to,String subject,String template,Map<String,Object> variables) {
        @Override public String toString(){return "SecurityEmail[template="+template+", contenido privado omitido]";}
    }

    @Transactional
    public MemberWorkspaceService.Credentials provision(UUID clientId,String actor) {
        var rows=jdbc.queryForList("SELECT id,account_id,email,first_name,status FROM clients WHERE id=? FOR UPDATE",clientId);
        if(rows.isEmpty()) throw new com.gymvip.exception.ClientNotFoundException("Miembro no encontrado.");
        var client=rows.getFirst();
        if(!"ACTIVO".equals(client.get("status"))) throw new ConflictException("Activa al miembro antes de habilitar su acceso.");
        if(client.get("account_id")!=null) throw new ConflictException("El miembro ya tiene acceso a la app. No se reemplazará su contraseña.");
        String email=Objects.toString(client.get("email"),"").trim().toLowerCase(Locale.ROOT);
        if(email.length()>150 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.endsWith(".local"))
            throw new IllegalArgumentException("Registra un correo real y válido antes de generar el acceso a la app.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM accounts WHERE lower(email)=?)",Boolean.class,email)))
            throw new ConflictException("Este correo ya tiene una cuenta. Utiliza otro correo propio del miembro o recupera su acceso existente.");
        UUID accountId=UUID.randomUUID(), invitationId=UUID.randomUUID();
        String temporary=random(18)+"aA7!", token=random(32);
        LocalDateTime expires=now().plusHours(24);
        jdbc.update("""
            INSERT INTO accounts(id,email,password_hash,status,must_change_password,temporary_password_expires_at)
            VALUES (?,?,?,'PENDIENTE_ACTIVACION',true,?)
            """,accountId,email,passwords.encode(temporary),expires);
        jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'CLIENTE')",accountId);
        jdbc.update("UPDATE clients SET account_id=?,email=?,email_verified=false,version=version+1 WHERE id=?",accountId,email,clientId);
        jdbc.update("INSERT INTO member_access_invitations(id,account_id,token_hash,expires_at,created_at) VALUES (?,?,?,?,?)",
            invitationId,accountId,hash(token),expires,now());
        events.publishEvent(new SecurityEmail(invitationId,email,"Activa tu cuenta — VIP CENTER FIT","account_activation",Map.of(
            "username",email,"temporaryPassword",temporary,"recipientName",Objects.toString(client.get("first_name"),"Socio"),
            "link",activationUrl+"#token="+token,"expirationHours",24,"idempotencyKey","access-"+invitationId)));
        audit.recordEvent(actor,"PROVISION_MEMBER_ACCESS","Client",clientId.toString(),"Acceso pendiente de activación; credenciales temporales con vencimiento de 24 horas.");
        return new MemberWorkspaceService.Credentials(email,temporary,true,expires.toString()+"Z",invitationId.toString(),"QUEUED");
    }

    @Transactional
    public void activate(String token,String newPassword) {
        AccountPasswordPolicy.validate(newPassword);
        if(token==null || token.length()>200) throw invalid();
        var rows=jdbc.queryForList("""
            SELECT i.id,i.account_id,a.email,a.password_hash FROM member_access_invitations i
            JOIN accounts a ON a.id=i.account_id
            WHERE i.token_hash=? AND i.consumed_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>?
                AND a.must_change_password=true AND a.status IN ('ACTIVO','PENDIENTE_ACTIVACION')
            FOR UPDATE OF a,i
            """,hash(token),now());
        if(rows.size()!=1) throw invalid();
        var row=rows.getFirst();
        if(passwords.matches(newPassword,Objects.toString(row.get("password_hash"))))
            throw new IllegalArgumentException("Elige una contraseña diferente de la temporal.");
        UUID account=(UUID)row.get("account_id");
        jdbc.update("UPDATE accounts SET password_hash=? WHERE id=?",passwords.encode(newPassword),account);
        completeFirstPassword(account,Objects.toString(row.get("email")));
    }

    @Transactional
    public MemberWorkspaceService.Credentials resend(UUID invitationId,String actor) {
        var rows=jdbc.queryForList("""
            SELECT a.id,a.email,a.must_change_password,c.first_name FROM member_access_invitations i
            JOIN accounts a ON a.id=i.account_id JOIN clients c ON c.account_id=a.id
            WHERE i.id=? AND c.status='ACTIVO' AND a.status IN ('ACTIVO','PENDIENTE_ACTIVACION') FOR UPDATE OF a
            """,invitationId);
        if(rows.size()!=1) throw new ConflictException("No se puede renovar esta invitación.");
        var row=rows.getFirst(); UUID account=(UUID)row.get("id");
        if(!Boolean.TRUE.equals(row.get("must_change_password"))) throw new ConflictException("La cuenta ya fue activada. Utiliza recuperación de contraseña.");
        int recent=jdbc.queryForObject("SELECT count(*) FROM member_access_invitations WHERE account_id=? AND created_at>?",Integer.class,account,now().minusMinutes(1));
        int hourly=jdbc.queryForObject("SELECT count(*) FROM member_access_invitations WHERE account_id=? AND created_at>?",Integer.class,account,now().minusHours(1));
        if(recent>0 || hourly>=3) throw new ConflictException("Espera un minuto antes de reenviar. Se permiten hasta tres invitaciones por hora.");
        String email=Objects.toString(row.get("email")),temporary=random(18)+"aA7!",token=random(32);
        UUID next=UUID.randomUUID(); LocalDateTime expires=now().plusHours(24);
        jdbc.update("UPDATE member_access_invitations SET revoked_at=? WHERE account_id=? AND consumed_at IS NULL AND revoked_at IS NULL",now(),account);
        jdbc.update("UPDATE accounts SET password_hash=?,temporary_password_expires_at=?,status='PENDIENTE_ACTIVACION' WHERE id=?",passwords.encode(temporary),expires,account);
        jdbc.update("DELETE FROM sessions WHERE account_id=?",account);
        jdbc.update("INSERT INTO member_access_invitations(id,account_id,token_hash,expires_at,created_at) VALUES (?,?,?,?,?)",next,account,hash(token),expires,now());
        events.publishEvent(new SecurityEmail(next,email,"Activa tu cuenta — VIP CENTER FIT","account_activation",Map.of(
            "username",email,"temporaryPassword",temporary,"recipientName",Objects.toString(row.get("first_name"),"Socio"),
            "link",activationUrl+"#token="+token,"expirationHours",24,"idempotencyKey","access-"+next)));
        audit.recordEvent(actor,"REISSUE_MEMBER_ACCESS","Account",account.toString(),"Invitación renovada; credenciales y sesiones anteriores revocadas.");
        return new MemberWorkspaceService.Credentials(email,temporary,true,expires.toString()+"Z",next.toString(),"QUEUED");
    }

    @Transactional
    public void createFirstPassword(String email,String newPassword) {
        AccountPasswordPolicy.validate(newPassword);
        var rows=jdbc.queryForList("SELECT id,password_hash,status,must_change_password,temporary_password_expires_at FROM accounts WHERE email=? FOR UPDATE",email);
        if(rows.isEmpty()) throw invalid();
        var account=rows.getFirst();
        if(!Boolean.TRUE.equals(account.get("must_change_password")) ||
            !Set.of("PENDIENTE_ACTIVACION","ACTIVO").contains(account.get("status"))) throw invalid();
        var expiration=account.get("temporary_password_expires_at");
        if(expiration instanceof java.sql.Timestamp timestamp && !timestamp.toLocalDateTime().isAfter(now())) throw invalid();
        if(passwords.matches(newPassword,account.get("password_hash").toString()))
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la temporal.");
        UUID id=(UUID)account.get("id");
        jdbc.update("UPDATE accounts SET password_hash=? WHERE id=?",passwords.encode(newPassword),id);
        completeFirstPassword(id,email);
    }

    /** Called inside the same transaction after validating the temporary session, password or email token. */
    public void completeFirstPassword(UUID account,String email) {
        jdbc.update("UPDATE accounts SET must_change_password=false,status='ACTIVO',temporary_password_expires_at=NULL,updated_at=? WHERE id=?",now(),account);
        jdbc.update("UPDATE clients SET email_verified=true WHERE account_id=? AND lower(email)=lower(?)",account,email);
        jdbc.update("UPDATE member_access_invitations SET consumed_at=? WHERE account_id=? AND consumed_at IS NULL AND revoked_at IS NULL",now(),account);
        jdbc.update("DELETE FROM sessions WHERE account_id=?",account);
        passwordChanged(account,email);
        audit.recordEvent(email,"ACTIVATE_MEMBER_ACCOUNT","Account",account.toString(),"Contraseña propia establecida; credenciales temporales e invitación invalidadas.");
    }

    public void passwordChanged(UUID account,String email) {
        events.publishEvent(new SecurityEmail(null,email,"Tu contraseña fue actualizada — VIP CENTER FIT","password_changed",
            Map.of("idempotencyKey","password-changed-"+UUID.randomUUID())));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> deliveryStatus(UUID invitationId) {
        var rows=jdbc.queryForList("SELECT email_status AS \"status\",expires_at AS \"expiresAt\" FROM member_access_invitations WHERE id=?",invitationId);
        if(rows.isEmpty()) throw new com.gymvip.exception.ResourceNotFoundException("Invitación no encontrada.");
        return rows.getFirst();
    }
    private static String random(int count) {byte[] bytes=new byte[count];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static InvalidCredentialsException invalid(){return new InvalidCredentialsException("La invitación no es válida, ya fue utilizada o venció. Solicita una nueva a Recepción.");}
}
