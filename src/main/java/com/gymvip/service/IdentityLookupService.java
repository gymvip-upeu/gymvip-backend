package com.gymvip.service;

import com.gymvip.dto.response.IdentityLookupResponse;

/**
 * Servicio de aplicación para la consulta y validación de identidades.
 */
public interface IdentityLookupService {

    /**
     * Valida el formato y consulta los datos del DNI a través del proveedor configurado.
     *
     * @param dni DNI de 8 dígitos numéricos
     * @return Respuesta formal tipificada
     */
    IdentityLookupResponse lookupDni(String dni);
}
