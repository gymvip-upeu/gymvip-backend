package com.gymvip.service;

import java.nio.charset.StandardCharsets;

public final class AccountPasswordPolicy {
    private AccountPasswordPolicy() {}
    public static void validate(String password) {
        if(password!=null && password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("La contraseña es demasiado larga. El máximo permitido es 72 bytes.");
        if (password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72 ||
            !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[0-9].*"))
            throw new IllegalArgumentException("Usa entre 12 y 72 caracteres, con mayúsculas, minúsculas y números (máximo 72 bytes).");
    }
}
