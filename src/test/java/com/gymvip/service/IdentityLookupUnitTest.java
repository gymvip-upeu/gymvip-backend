package com.gymvip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.enums.IdentityLookupStatus;
import com.gymvip.service.impl.ApiPeruDevIdentityProvider;
import com.gymvip.service.impl.IdentityLookupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IdentityLookupUnitTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder().baseUrl("https://api.apiperu.dev");
    }

    @Test
    @DisplayName("Debe validar formato DNI: longitud menor a 8 dígitos produce DNI_INVALIDO")
    void testDniTooShort() {
        IdentityLookupProvider dummyProvider = new IdentityLookupProvider() {
            @Override public IdentityLookupResponse lookupDni(String dni) { return null; }
            @Override public boolean isAvailable() { return true; }
        };
        IdentityLookupService service = new IdentityLookupServiceImpl(dummyProvider);

        IdentityLookupResponse response = service.lookupDni("1234567");
        assertEquals(IdentityLookupStatus.DNI_INVALIDO, response.status());
        assertEquals("1234567", response.dni());
    }

    @Test
    @DisplayName("Debe validar formato DNI: caracteres no numéricos produce DNI_INVALIDO")
    void testDniNonNumeric() {
        IdentityLookupProvider dummyProvider = new IdentityLookupProvider() {
            @Override public IdentityLookupResponse lookupDni(String dni) { return null; }
            @Override public boolean isAvailable() { return true; }
        };
        IdentityLookupService service = new IdentityLookupServiceImpl(dummyProvider);

        IdentityLookupResponse response = service.lookupDni("7281920A");
        assertEquals(IdentityLookupStatus.DNI_INVALIDO, response.status());
    }

    @Test
    @DisplayName("Debe procesar exitosamente datos encontrados en ApiPeruDev con separación estricta de nombres y apellidos")
    void testLookupSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String jsonResponse = """
            {
              "success": true,
              "code": "found",
              "data": {
                "numero": "72819201",
                "nombres": "MARIANA",
                "apellido_paterno": "TORRES",
                "apellido_materno": "RAMOS"
              }
            }
            """;

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token-123"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819201");

        assertEquals(IdentityLookupStatus.ENCONTRADO, response.status());
        assertEquals("72819201", response.dni());
        assertEquals("MARIANA", response.nombres());
        assertEquals("TORRES", response.apellidoPaterno());
        assertEquals("RAMOS", response.apellidoMaterno());
        assertEquals("TORRES RAMOS", response.apellidos());
        assertEquals("ApiPeruDev", response.fuente());
        server.verify();
    }

    @Test
    @DisplayName("Debe manejar respuesta con solo apellido paterno conservando apellidos válidos")
    void testLookupSuccessOnlyPaterno() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String jsonResponse = """
            {
              "success": true,
              "code": "found",
              "data": {
                "numero": "72819202",
                "nombres": "CARLOS ALBERTO",
                "apellido_paterno": "QUISPE",
                "apellido_materno": ""
              }
            }
            """;

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819202");

        assertEquals(IdentityLookupStatus.ENCONTRADO, response.status());
        assertEquals("CARLOS ALBERTO", response.nombres());
        assertEquals("QUISPE", response.apellidoPaterno());
        assertEquals("", response.apellidoMaterno());
        assertEquals("QUISPE", response.apellidos());
        server.verify();
    }

    @Test
    @DisplayName("Debe clasificar HTTP 404 como NO_ENCONTRADO")
    void testLookupNotFound404() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("00000000");

        assertEquals(IdentityLookupStatus.NO_ENCONTRADO, response.status());
        assertEquals("00000000", response.dni());
        assertNull(response.nombres());
        server.verify();
    }

    @Test
    @DisplayName("Debe clasificar HTTP 429 como CUOTA_AGOTADA")
    void testLookupQuotaExceeded429() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819201");

        assertEquals(IdentityLookupStatus.CUOTA_AGOTADA, response.status());
        server.verify();
    }

    @Test
    @DisplayName("Debe clasificar HTTP 401 como NO_AUTORIZADO sin exponer tokens")
    void testLookupUnauthorized401() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "bad-token", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819201");

        assertEquals(IdentityLookupStatus.NO_AUTORIZADO, response.status());
        assertFalse(response.mensaje().contains("bad-token"));
        server.verify();
    }

    @Test
    @DisplayName("Debe clasificar HTTP 503 como SERVICIO_NO_DISPONIBLE")
    void testLookupServiceUnavailable503() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819201");

        assertEquals(IdentityLookupStatus.SERVICIO_NO_DISPONIBLE, response.status());
        server.verify();
    }

    @Test
    @DisplayName("Debe marcar RESPUESTA_INCOMPLETA si el DNI recibido no coincide con el solicitado")
    void testDniMismatch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.apiperu.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String jsonResponse = """
            {
              "success": true,
              "code": "found",
              "data": {
                "numero": "99999999",
                "nombres": "INTRUSO",
                "apellido_paterno": "FALSO",
                "apellido_materno": "FALSO"
              }
            }
            """;

        server.expect(requestTo("https://api.apiperu.dev/dni"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ApiPeruDevIdentityProvider provider = new ApiPeruDevIdentityProvider(
                restClient, "test-token-123", "https://api.apiperu.dev", true
        );

        IdentityLookupResponse response = provider.lookupDni("72819201");

        assertEquals(IdentityLookupStatus.RESPUESTA_INCOMPLETA, response.status());
        server.verify();
    }

    @Test
    @DisplayName("Debe usar caché local para evitar consultas repetidas al proveedor")
    void testCacheReusesSuccessfulResponse() {
        final int[] providerCalls = {0};
        IdentityLookupProvider countingProvider = new IdentityLookupProvider() {
            @Override
            public IdentityLookupResponse lookupDni(String dni) {
                providerCalls[0]++;
                return IdentityLookupResponse.encontrado(dni, "MARIANA", "TORRES", "RAMOS");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        IdentityLookupService service = new IdentityLookupServiceImpl(countingProvider);

        IdentityLookupResponse r1 = service.lookupDni("72819201");
        IdentityLookupResponse r2 = service.lookupDni("72819201");

        assertEquals(1, providerCalls[0], "El proveedor debió llamarse exactamente una vez debido al caché");
        assertEquals(IdentityLookupStatus.ENCONTRADO, r1.status());
        assertEquals(IdentityLookupStatus.ENCONTRADO, r2.status());
    }
}
