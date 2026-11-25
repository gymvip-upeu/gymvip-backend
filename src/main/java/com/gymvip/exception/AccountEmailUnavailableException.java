package com.gymvip.exception;

public class AccountEmailUnavailableException extends RuntimeException {
    public AccountEmailUnavailableException(){super("No se pudo enviar el correo. Intenta nuevamente.");}
}
