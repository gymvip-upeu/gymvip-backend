package com.gymvip.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Servicio puro Java de autenticación multifactor TOTP (RFC 6238 / HMAC-SHA1)
 * y códigos de recuperación de un solo uso para cuentas privilegiadas.
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long STEP_SECONDS = 30L;
    private static final int DIGITS = 6;

    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String buildProvisioningUri(String email, String secret) {
        String safeIssuer = "GYMVIP";
        String cleanEmail = email != null ? email.trim() : "usuario@vipcenterfit.pe";
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                safeIssuer, cleanEmail, secret, safeIssuer, DIGITS, STEP_SECONDS
        );
    }

    public boolean verifyCode(String base32Secret, String code) {
        return verifyCode(base32Secret, code, Instant.now());
    }

    public boolean verifyCode(String base32Secret, String code, Instant now) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.matches("^[0-9]{6}$")) {
            return false;
        }
        long currentStep = now.getEpochSecond() / STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            String candidate = generateCodeForStep(base32Secret, currentStep + offset);
            if (trimmed.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public String generateCodeForStep(String base32Secret, long step) {
        byte[] key = decodeBase32(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar código TOTP", ex);
        }
    }

    public List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int p1 = 1000 + RANDOM.nextInt(9000);
            int p2 = 1000 + RANDOM.nextInt(9000);
            codes.add(p1 + "-" + p2);
        }
        return codes;
    }

    public String hashRecoveryCode(String rawCode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawCode.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar hash de código de recuperación", ex);
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    private static byte[] decodeBase32(String base32) {
        String clean = base32.trim().toUpperCase(Locale.ROOT).replace("=", "").replaceAll("\\s+", "");
        int buffer = 0;
        int bitsLeft = 0;
        byte[] result = new byte[(clean.length() * 5) / 8];
        int index = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(clean.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Secreto TOTP Base32 inválido");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
