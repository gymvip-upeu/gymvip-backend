package com.gymvip.clients.domain.model;

import java.security.SecureRandom;

/**
 * Generador de Código de Socio estable y legible para clientes de GYM VIP.
 * Formato: Prefijo de 3 letras normalizadas del nombre + '-' + 6 caracteres alfanuméricos aleatorios.
 * Ejemplo: Mariana -> MAR-8Q2L7K
 */
public final class MemberCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Sin 0, O, 1, I para máxima legibilidad
    private static final SecureRandom RANDOM = new SecureRandom();

    private MemberCodeGenerator() {}

    public static String generate(String firstName) {
        String prefix = "VIP";
        if (firstName != null && !firstName.isBlank()) {
            String cleaned = firstName.trim().toUpperCase()
                    .replaceAll("[^A-Z]", "");
            if (cleaned.length() >= 3) {
                prefix = cleaned.substring(0, 3);
            } else if (!cleaned.isEmpty()) {
                prefix = (cleaned + "VIP").substring(0, 3);
            }
        }

        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return prefix + "-" + suffix;
    }
}
