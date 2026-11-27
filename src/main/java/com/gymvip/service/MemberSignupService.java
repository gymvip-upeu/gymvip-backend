package com.gymvip.service;

import com.gymvip.controller.MemberSignupController.Registration;
import com.gymvip.controller.MemberSignupController.Verification;
import com.gymvip.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MemberSignupService {
    private static final String TERMS_VERSION = "v1.0";
    private static final int EXPIRATION_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final CommsServiceClient comms;
    private final Clock clock;
    private final boolean enabled;

    public MemberSignupService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder passwords,
            CommsServiceClient comms, Clock clock, @Value("${MEMBER_SIGNUP_ENABLED:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.passwords = passwords;
        this.comms = comms;
        this.clock = clock;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    public record Started(UUID challengeId, int expiresInMinutes) {}
    private record Dispatch(UUID id, String email, String password) {
        @Override public String toString() { return "Dispatch[credencial omitida]"; }
    }
    private record Verified(Dispatch dispatch) {}

    public Started start(Registration request, String ip) {
        requireEnabled();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));
        if (!request.acceptedTerms() || request.birthDate().isAfter(today.minusYears(18))
                || request.birthDate().isBefore(today.minusYears(120))) {
            throw new IllegalArgumentException("Confirma los términos y registra una fecha de nacimiento válida de una persona mayor de edad.");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (email.endsWith(".local") || email.endsWith(".invalid")) {
            throw new IllegalArgumentException("Usa un correo al que tengas acceso.");
        }
        String ipKey = MemberAccessService.hash(java.util.Objects.toString(ip, "unknown"));
        String emailCode = code();
        String whatsappCode = code();
        while (emailCode.equals(whatsappCode)) whatsappCode = code();
        String phoneCode = whatsappCode;
        UUID id = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            List.of("email:" + email, "phone:" + request.phone(), "ip:" + ipKey).stream().sorted().forEach(this::lock);
            Integer recent = jdbc.queryForObject("""
                SELECT count(*) FROM member_signup_challenges
                WHERE (email=? OR phone=?) AND created_at>NOW()-INTERVAL '1 minute'
                """, Integer.class, email, request.phone());
            Integer hourly = jdbc.queryForObject("""
                SELECT count(*) FROM member_signup_challenges
                WHERE (email=? OR phone=?) AND created_at>NOW()-INTERVAL '1 hour'
                """, Integer.class, email, request.phone());
            Integer connection = jdbc.queryForObject("""
                SELECT count(*) FROM member_signup_challenges
                WHERE ip_key=? AND created_at>NOW()-INTERVAL '1 hour'
                """, Integer.class, ipKey);
            if (recent > 0) throw new TooManyRequestsException("Espera un minuto para volver a enviar códigos.", 60);
            if (hourly >= 3 || connection >= 40) throw new TooManyRequestsException("Intenta el registro más tarde.", 3600);
            jdbc.update("""
                INSERT INTO member_signup_challenges
                    (id,email,phone,ip_key,first_name,last_name,dni,birth_date,email_hash,whatsapp_hash,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,NOW()+ (? * INTERVAL '1 minute'))
                """, id, email, request.phone(), ipKey, request.firstName().strip(), request.lastName().strip(),
                    request.dni(), request.birthDate(), passwords.encode(emailCode), passwords.encode(phoneCode), EXPIRATION_MINUTES);
        });

        boolean emailSent = comms.sendAccountSecurityEmail(email, "Verifica tu correo en GYM VIP", "member_signup_code",
                Map.of("code", emailCode, "idempotencyKey", id + "-email")).dispatched();
        if (!emailSent || !comms.sendSignupWhatsAppCode(request.phone(), phoneCode)) {
            jdbc.update("UPDATE member_signup_challenges SET email_hash=NULL, whatsapp_hash=NULL WHERE id=?", id);
            throw deliveryUnavailable();
        }
        jdbc.update("UPDATE member_signup_challenges SET dispatched=TRUE WHERE id=?", id);
        return new Started(id, EXPIRATION_MINUTES);
    }

    public void verify(UUID id, Verification request) {
        requireEnabled();
        Verified result = transactions.execute(status -> complete(id, request));
        if (result == null) throw new IllegalArgumentException("Los códigos son incorrectos, ya se usaron o vencieron.");
        Dispatch dispatch = result.dispatch();
        if (dispatch != null && !comms.sendAccountSecurityEmail(dispatch.email(),
                "Tus datos de acceso a GYM VIP", "member_signup_password",
                Map.of("username", dispatch.email(), "temporaryPassword", dispatch.password(),
                       "idempotencyKey", dispatch.id() + "-credential")).dispatched()) {
            throw deliveryUnavailable();
        }
    }

    private Verified complete(UUID id, Verification request) {
        var entries = jdbc.queryForList("""
            SELECT * FROM member_signup_challenges
            WHERE id=? AND dispatched AND completed_at IS NULL AND expires_at>NOW() AND attempts<?
            FOR UPDATE
            """, id, MAX_ATTEMPTS);
        if (entries.isEmpty()) return null;
        var row = entries.getFirst();
        boolean emailMatches = passwords.matches(request.emailCode(), (String) row.get("email_hash"));
        boolean phoneMatches = passwords.matches(request.whatsappCode(), (String) row.get("whatsapp_hash"));
        if (!emailMatches || !phoneMatches) {
            jdbc.update("""
                UPDATE member_signup_challenges SET attempts=attempts+1,
                    email_hash=CASE WHEN attempts+1>=? THEN NULL ELSE email_hash END,
                    whatsapp_hash=CASE WHEN attempts+1>=? THEN NULL ELSE whatsapp_hash END
                WHERE id=?
                """, MAX_ATTEMPTS, MAX_ATTEMPTS, id);
            return null;
        }
        jdbc.update("""
            UPDATE member_signup_challenges SET completed_at=NOW(), email_hash=NULL, whatsapp_hash=NULL WHERE id=?
            """, id);

        String email = (String) row.get("email");
        lock("email:" + email);
        var account = jdbc.queryForList("""
            SELECT a.id, a.first_access_password, a.must_change_password, a.directory_only, a.phone,
                   mi.declared_dni, mi.birth_date
            FROM accounts a LEFT JOIN member_identities mi ON mi.account_id=a.id
            WHERE LOWER(a.email)=? AND a.status='ACTIVO'
            FOR UPDATE OF a
            """, email);
        byte[] secret = new byte[18];
        RANDOM.nextBytes(secret);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret) + "Aa7!";
        String hash = passwords.encode(password);
        if (account.isEmpty()) {
            UUID accountId = UUID.randomUUID();
            int inserted = jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,status,first_name,last_name,phone,
                        must_change_password,first_access_password,directory_only,temporary_password_expires_at)
                SELECT ?,?,?,'ACTIVO',?,?,?,TRUE,TRUE,TRUE,(NOW() AT TIME ZONE 'UTC')+INTERVAL '24 hours'
                WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE LOWER(email)=?)
                  AND NOT EXISTS (SELECT 1 FROM clients WHERE LOWER(email)=?)
                ON CONFLICT (email) DO NOTHING
                """, accountId, email, hash, row.get("first_name"), row.get("last_name"), row.get("phone"), email, email);
            if (inserted == 0) return new Verified(null);
            jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'CLIENTE')", accountId);
            jdbc.update("""
                INSERT INTO member_identities(account_id,declared_dni,birth_date,email_verified_at,whatsapp_verified_at,terms_version,consented_at)
                VALUES (?,?,?,NOW(),NOW(),?,?)
                """, accountId, row.get("dni"), row.get("birth_date"), TERMS_VERSION, row.get("created_at"));
        } else {
            var current = account.getFirst();
            if (!Boolean.TRUE.equals(current.get("directory_only")) || !Boolean.TRUE.equals(current.get("must_change_password"))
                    || !Boolean.TRUE.equals(current.get("first_access_password"))
                    || !row.get("phone").equals(current.get("phone")) || !row.get("dni").equals(current.get("declared_dni"))
                    || !row.get("birth_date").equals(current.get("birth_date"))) return new Verified(null);
            jdbc.update("""
                UPDATE accounts SET password_hash=?, first_access_original_hash=NULL,
                    first_access_password_consumed_at=NULL, temporary_password_expires_at=(NOW() AT TIME ZONE 'UTC')+INTERVAL '24 hours'
                WHERE id=?
                """, hash, current.get("id"));
            jdbc.update("DELETE FROM sessions WHERE account_id=?", current.get("id"));
        }
        return new Verified(new Dispatch(id, email, password));
    }

    @Scheduled(cron = "0 15 * * * *")
    public void purgeExpiredChallenges() {
        jdbc.update("DELETE FROM member_signup_challenges WHERE created_at < NOW()-INTERVAL '24 hours'");
    }

    private void lock(String key) {
        jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 527500))", Integer.class, key);
    }

    private void requireEnabled() {
        if (!enabled) throw new Unavailable("El registro por la app aún no está disponible.");
    }

    private static Unavailable deliveryUnavailable() {
        return new Unavailable("No se pudo enviar el mensaje de registro. Intenta más tarde.");
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }

    private static String code() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }
}
