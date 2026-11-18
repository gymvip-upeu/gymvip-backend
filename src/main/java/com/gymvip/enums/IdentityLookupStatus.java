package com.gymvip.enums;

/**
 * Estados de resultado para la consulta externa de identidades y documentos (ApiPeruDev / DNI).
 */
public enum IdentityLookupStatus {
    ENCONTRADO,
    NO_ENCONTRADO,
    DNI_INVALIDO,
    CUOTA_AGOTADA,
    NO_AUTORIZADO,
    SERVICIO_NO_DISPONIBLE,
    RESPUESTA_INCOMPLETA
}
