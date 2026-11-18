package com.gymvip.dto.response;

import com.gymvip.enums.IdentityLookupStatus;

/**
 * Respuesta formal del servicio de consulta de identidad (ApiPeruDev).
 * Mantiene la estricta separación de nombres y apellidos requerida por las reglas de negocio.
 */
public record IdentityLookupResponse(
        IdentityLookupStatus status,
        String dni,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String apellidos,
        String fuente,
        String mensaje
) {
    public static IdentityLookupResponse encontrado(String dni, String nombres, String apPaterno, String apMaterno) {
        String cleanNombres = normalize(nombres);
        String cleanApPaterno = normalize(apPaterno);
        String cleanApMaterno = normalize(apMaterno);

        String combinedApellidos = "";
        if (!cleanApPaterno.isEmpty() && !cleanApMaterno.isEmpty()) {
            combinedApellidos = cleanApPaterno + " " + cleanApMaterno;
        } else if (!cleanApPaterno.isEmpty()) {
            combinedApellidos = cleanApPaterno;
        } else if (!cleanApMaterno.isEmpty()) {
            combinedApellidos = cleanApMaterno;
        }

        return new IdentityLookupResponse(
                IdentityLookupStatus.ENCONTRADO,
                dni,
                cleanNombres,
                cleanApPaterno,
                cleanApMaterno,
                combinedApellidos,
                "ApiPeruDev",
                "Datos encontrados en la fuente consultada."
        );
    }

    public static IdentityLookupResponse noEncontrado(String dni, String mensaje) {
        return new IdentityLookupResponse(
                IdentityLookupStatus.NO_ENCONTRADO,
                dni,
                null, null, null, null,
                "ApiPeruDev",
                mensaje != null ? mensaje : "No se encontraron datos asociados a este DNI en las fuentes disponibles de ApiPeruDev."
        );
    }

    public static IdentityLookupResponse error(IdentityLookupStatus status, String dni, String mensaje) {
        return new IdentityLookupResponse(
                status,
                dni,
                null, null, null, null,
                "ApiPeruDev",
                mensaje
        );
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ");
    }
}
