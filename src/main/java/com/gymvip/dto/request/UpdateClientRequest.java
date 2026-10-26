package com.gymvip.dto.request;

import jakarta.validation.constraints.*;

public record UpdateClientRequest(
        @NotBlank @Size(max=100) String firstName,
        @NotBlank @Size(max=100) String lastName,
        @NotBlank @Size(max=10) String countryCode,
        @NotBlank @Size(max=30) String documentType,
        @NotBlank @Size(max=50) String documentNumber,
        @Pattern(regexp="^$|^[+0-9 -]{7,20}$", message="Celular inválido") String phone,
        @Email @Size(max=150) String email,
        @NotNull @PositiveOrZero Long version
) {}
