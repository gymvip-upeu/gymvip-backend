package com.gymvip.clients.application.port.out;

import com.gymvip.clients.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ClientPersistencePort {
    Client save(Client client);
    Optional<Client> findById(UUID id);
    boolean existsByDocument(String countryCode, String documentType, String documentNumber);
    boolean existsByMemberCode(String memberCode);
    Page<Client> searchClients(String query, Pageable pageable);
}
