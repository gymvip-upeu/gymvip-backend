package com.gymvip.service.impl;

import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.enums.IdentityLookupStatus;
import com.gymvip.service.IdentityLookupProvider;
import com.gymvip.service.IdentityLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementación del servicio de identidades con validación de formato,
 * caché en memoria para ahorro de cuota y control de consultas duplicadas.
 */
@Service
public class IdentityLookupServiceImpl implements IdentityLookupService {

    private static final Logger log = LoggerFactory.getLogger(IdentityLookupServiceImpl.class);
    private static final Pattern DNI_PATTERN = Pattern.compile("^\\d{8}$");
    private static final long CACHE_TTL_SECONDS = 3600; // 1 hora de vigencia en caché local

    private final IdentityLookupProvider identityLookupProvider;
    private final Map<String, CachedIdentity> cache = new ConcurrentHashMap<>();

    public IdentityLookupServiceImpl(IdentityLookupProvider identityLookupProvider) {
        this.identityLookupProvider = identityLookupProvider;
    }

    @Override
    public IdentityLookupResponse lookupDni(String rawDni) {
        if (rawDni == null || rawDni.trim().isEmpty()) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.DNI_INVALIDO,
                    "",
                    "El número de DNI es obligatorio."
            );
        }

        String dni = rawDni.trim();

        if (!DNI_PATTERN.matcher(dni).matches()) {
            return IdentityLookupResponse.error(
                    IdentityLookupStatus.DNI_INVALIDO,
                    dni,
                    "El número de DNI debe constar exactamente de 8 dígitos numéricos."
            );
        }

        // Revisar caché en memoria para evitar consumo redundante de cuota
        CachedIdentity cached = cache.get(dni);
        if (cached != null && !cached.isExpired()) {
            log.debug("Consulta de DNI resuelta desde caché local");
            return cached.response();
        }

        // Delegar al proveedor externo configurado
        IdentityLookupResponse response = identityLookupProvider.lookupDni(dni);

        // Almacenar en caché únicamente resultados concluyentes (ENCONTRADO o NO_ENCONTRADO)
        if (response.status() == IdentityLookupStatus.ENCONTRADO ||
            response.status() == IdentityLookupStatus.NO_ENCONTRADO) {
            cache.put(dni, new CachedIdentity(response, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        }

        return response;
    }

    private record CachedIdentity(IdentityLookupResponse response, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
