package com.gymvip.security;

import com.gymvip.entity.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Servicio de emisión, firma criptográfica y validación estricta de tokens JWT (RFC 7519).
 * Algoritmo: HMAC-SHA256 (HS256).
 * Valida firma, vigencia (exp), emisor/sujeto (sub) y sesión activa (jti).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    // Clave de desarrollo fallback (solo para perfiles no productivos locales)
    private static final String DEV_FALLBACK_SECRET = "GymVipDevSecretKeyForTestingPurposesOnlyMustBeAtLeast256Bits2026SecureKey!";

    @Value("${jwt.secret:${JWT_SECRET:}}")
    private String configuredSecret;

    @Value("${jwt.expiration-hours:${JWT_EXPIRATION_HOURS:24}}")
    private int expirationHours;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private SecretKey signingKey;

    public JwtService() {
        // Inicialización segura para instanciación en tests sin inyección de dependencias Spring
        this.signingKey = Keys.hmacShaKeyFor(DEV_FALLBACK_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    public void init() {
        String secretToUse = configuredSecret != null ? configuredSecret.trim() : "";

        if (secretToUse.length() < 32) {
            if ("prod".equalsIgnoreCase(activeProfile)) {
                throw new IllegalStateException(
                        "SEGURIDAD CRÍTICA: En el perfil 'prod' es obligatorio configurar una variable JWT_SECRET segura " +
                        "de al menos 256 bits (32 caracteres). El servidor no iniciará con claves ausentes o inseguras."
                );
            }
            log.warn("ADVERTENCIA DE SEGURIDAD: JWT_SECRET no configurado o menor a 32 caracteres en perfil '{}'. " +
                     "Usando clave temporal de desarrollo. Configure JWT_SECRET en .env para entornos reales.", activeProfile);
            secretToUse = DEV_FALLBACK_SECRET;
        }

        this.signingKey = Keys.hmacShaKeyFor(secretToUse.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emite un token JWT firmado criptográficamente con algoritmo HS256.
     *
     * @param account Cuenta de colaborador o socio autenticado
     * @param sessionId Identificador de sesión persistida (jti) para permitir revocación inmediata
     * @param expiresAt Fecha y hora de caducidad
     * @return Cadena JWT compacta
     */
    public String generateToken(Account account, String sessionId, LocalDateTime expiresAt) {
        Date issuedAt = new Date();
        Date expiration = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant());

        return Jwts.builder()
                .subject(account.getEmail())
                .id(sessionId) // Claim 'jti': identificador unívoco de sesión trazable y revocable
                .claim("accountId", account.getId().toString())
                .claim("roles", account.getRoles())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida la firma criptográfica, vigencia temporal y estructura del token JWT.
     *
     * @param token Token JWT recibido en cabecera Authorization Bearer
     * @return Optional con Claims si es legítimo, o vacío si fue alterado, expiró o es malformado
     */
    public Optional<Claims> validateAndExtractClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();

            // Verificaciones complementarias de integridad
            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                log.debug("JWT rechazado: claim 'subject' ausente o vacío");
                return Optional.empty();
            }

            if (claims.getId() == null || claims.getId().isBlank()) {
                log.debug("JWT rechazado: claim 'jti' (sessionId) ausente");
                return Optional.empty();
            }

            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                log.debug("JWT rechazado: token expirado");
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // Rechazo seguro: cualquier firma alterada, algoritmo modificado o payload corrupto
            log.debug("Validación JWT fallida: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extrae el sessionId (jti) de un token si la firma criptográfica es auténtica,
     * incluso si el token ha expirado temporalmente.
     * Si la firma fue manipulada o es inválida, retorna vacío.
     */
    public Optional<String> extractSessionIdIfValidSignature(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            return Optional.ofNullable(claims.getId());
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            // La firma es auténtica aunque haya expirado en tiempo
            return Optional.ofNullable(ex.getClaims().getId());
        } catch (Exception ex) {
            // Firma alterada o formato ilegítimo
            return Optional.empty();
        }
    }

    public int getExpirationHours() {
        return expirationHours > 0 ? expirationHours : 24;
    }
}
