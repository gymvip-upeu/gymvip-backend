package com.gymvip.service;

import com.gymvip.dto.response.IdentityLookupResponse;

/**
 * Puerto de proveedor de consulta externa de identidades (DNI / ApiPeruDev).
 */
public interface IdentityLookupProvider {

    /**
     * Consulta los datos de una persona natural por su DNI (8 dígitos).
     *
     * @param dni Número de documento de identidad
     * @return Respuesta formal con el estado tipificado y los datos si fueron encontrados
     */
    IdentityLookupResponse lookupDni(String dni);

    /**
     * Indica si el proveedor está habilitado y cuenta con configuración mínima.
     */
    boolean isAvailable();
}
