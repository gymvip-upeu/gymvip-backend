package com.gymvip.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.enums.IdentityLookupStatus;
import com.gymvip.service.IdentityLookupProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Adaptador oficial para consultas de identidad en ApiPeruDev (POST /dni).
 * Cumple con la especificación de https://docs.apiperu.dev/referencia/consultar-dni/
 */
@Component
public class ApiPeruDevIdentityProvider implements IdentityLookupProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiPeruDevIdentityProvider.class);

    private final RestClient restClient;
    private final String token;
    private final String baseUrl;
    private final boolean enabled;

    @Autowired
    public ApiPeruDevIdentityProvider(
            @Value("${app.apiperudev.base-url:https://api.apiperu.dev}") String baseUrl,
            @Value("${app.apiperudev.token:${APIPERUDEV_TOKEN:}}") String token,
            @Value("${app.apiperudev.enabled:true}") boolean enabled,
            @Value("${app.apiperudev.timeout-ms:5000}") int timeoutMs
    ) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.apiperu.dev";
        this.token = token != null ? token.trim() : "";
        this.enabled = enabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // Constructor para pruebas unitarias con RestClient inyectado
    public ApiPeruDevIdentityProvider(RestClient restClient, String token, String baseUrl, boolean enabled) {
        this.restClient = restClient;
        this.token = token != null ? token.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.apiperu.dev";
        this.enabled = enabled;
    }

    @Override
    public boolean isAvailable() {
        return enabled && !token.isEmpty();
    }

    @Override
    public IdentityLookupResponse lookupDni(String dni) {
        if (!enabled) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "El proveedor de consulta de identidades se encuentra deshabilitado por configuración."
            );
        }

        if (token.isEmpty()) {
            log.warn("Consulta de DNI cancelada: Token de ApiPeruDev no configurado en el entorno");
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.NO_AUTORIZADO,
                    dni,
                    "Token de ApiPeruDev no configurado. Configure APIPERUDEV_TOKEN en las variables de entorno."
            );
        }

        try {
            ApiPeruDevPayload requestBody = new ApiPeruDevPayload(dni);

            ApiPeruDevResponse response = restClient.post()
                    .uri("/dni")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 404) {
                            throw new DocumentNotFoundException("Documento no encontrado en ApiPeruDev");
                        } else if (code == 429) {
                            throw new QuotaExceededException("Límite de cuota o rate limit alcanzado en ApiPeruDev");
                        } else if (code == 401 || code == 403) {
                            throw new UnauthorizedProviderException("Token inválido o acceso no autorizado a ApiPeruDev");
                        }
                    })
                    .body(ApiPeruDevResponse.class);

            return processApiResponse(dni, response);

        } catch (DocumentNotFoundException ex) {
            log.info("DNI no encontrado en ApiPeruDev");
            return IdentityLookupResponse.noEncontrado(dni, "DNI no encontrado en el padrón consultado.");

        } catch (QuotaExceededException ex) {
            log.warn("Cuota de ApiPeruDev agotada (HTTP 429)");
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.CUOTA_AGOTADA,
                    dni,
                    "Se ha alcanzado el límite de cuota del proveedor ApiPeruDev. Intente más tarde o consulte con el administrador."
            );

        } catch (UnauthorizedProviderException ex) {
            log.error("Error de autenticación con ApiPeruDev (HTTP 401/403)");
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.NO_AUTORIZADO,
                    dni,
                    "El proveedor de consulta rechazó la solicitud por credenciales inválidas."
            );

        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                return IdentityLookupResponse.noEncontrado(dni, "DNI no encontrado en el padrón consultado.");
            } else if (ex.getStatusCode().value() == 429) {
                return IdentityLookupResponse.error(
                        IdentityLookupStatus.CUOTA_AGOTADA,
                        dni,
                        "Se ha alcanzado el límite de consultas del proveedor."
                );
            }
            log.warn("Error 4xx en consulta ApiPeruDev: {}", ex.getStatusCode());
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "Error al consultar el proveedor de identidad: " + ex.getStatusCode()
            );

        } catch (HttpServerErrorException ex) {
            log.error("Error 5xx en ApiPeruDev: {}", ex.getStatusCode());
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "El servicio de consulta no está disponible temporalmente (código " + ex.getStatusCode().value() + ")."
            );

        } catch (ResourceAccessException ex) {
            log.warn("Timeout o falla de red al consultar proveedor externo de identidad");
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "El servicio de consulta no está disponible temporalmente. Tiempo de espera agotado."
            );

        } catch (HttpMessageConversionException ex) {
            log.error("Error al deserializar respuesta de ApiPeruDev: formato inesperado");
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.RESPUESTA_INCOMPLETA,
                    dni,
                    "La respuesta del servicio de identidad no pudo ser procesada."
            );

        } catch (RestClientResponseException ex) {
            log.error("Error HTTP al consultar ApiPeruDev: status={}", ex.getStatusCode());
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "Error al comunicarse con el proveedor de identidad (HTTP " + ex.getStatusCode().value() + ")."
            );

        } catch (Exception ex) {
            log.error("Excepción inesperada en consulta ApiPeruDev: {}", ex.getMessage());
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.SERVICIO_NO_DISPONIBLE,
                    dni,
                    "El servicio de consulta no está disponible temporalmente. Puede reintentar o ingresar los datos manualmente."
            );
        }
    }

    private IdentityLookupResponse processApiResponse(String requestedDni, ApiPeruDevResponse response) {
        if (response == null) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.RESPUESTA_INCOMPLETA,
                    requestedDni,
                    "El proveedor no retornó información legible."
            );
        }

        // Si la respuesta indica éxito false o código document_not_found
        if (Boolean.FALSE.equals(response.success()) || "document_not_found".equalsIgnoreCase(response.code())) {
            return IdentityLookupResponse.noEncontrado(
                    requestedDni,
                    response.message() != null ? response.message() : "DNI no encontrado en las fuentes de ApiPeruDev."
            );
        }

        ApiPeruDevData data = response.data();
        if (data == null) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.RESPUESTA_INCOMPLETA,
                    requestedDni,
                    "La respuesta del proveedor no contiene el bloque de datos de la persona."
            );
        }

        // Validar que el DNI retornado coincida con el solicitado
        if (data.numero() != null && !data.numero().trim().equals(requestedDni.trim())) {
            log.warn("Inconsistencia en DNI: solicitado={}, recibido={}", maskDni(requestedDni), maskDni(data.numero()));
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.RESPUESTA_INCOMPLETA,
                    requestedDni,
                    "Inconsistencia en el número de documento recibido del proveedor."
            );
        }

        String nombres = data.nombres() != null ? data.nombres().trim() : "";
        String apPaterno = data.apellidoPaterno() != null ? data.apellidoPaterno().trim() : "";
        String apMaterno = data.apellidoMaterno() != null ? data.apellidoMaterno().trim() : "";

        // Si no hay nombres o no hay ningún apellido, clasificar como incompleta
        if (nombres.isEmpty() || (apPaterno.isEmpty() && apMaterno.isEmpty())) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.RESPUESTA_INCOMPLETA,
                    requestedDni,
                    "La fuente de datos retornó un registro incompleto de nombres y apellidos."
            );
        }

        return IdentityLookupResponse.encontrado(requestedDni, nombres, apPaterno, apMaterno);
    }

    private String maskDni(String dni) {
        if (dni == null || dni.length() < 4) return "****";
        return dni.substring(0, 2) + "****" + dni.substring(dni.length() - 2);
    }

    // Modelos internos JSON con tolerancia a campos desconocidos del proveedor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPeruDevPayload(String dni) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPeruDevData(
            String numero,
            String nombres,
            @JsonProperty("apellido_paterno") String apellidoPaterno,
            @JsonProperty("apellido_materno") String apellidoMaterno
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPeruDevResponse(
            Boolean success,
            String code,
            String message,
            ApiPeruDevData data
    ) {}

    // Excepciones internas de control de flujo
    private static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String message) { super(message); }
    }
    private static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) { super(message); }
    }
    private static class UnauthorizedProviderException extends RuntimeException {
        public UnauthorizedProviderException(String message) { super(message); }
    }
}
