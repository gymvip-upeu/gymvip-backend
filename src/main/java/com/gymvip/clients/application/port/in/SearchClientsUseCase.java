package com.gymvip.clients.application.port.in;

import com.gymvip.clients.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SearchClientsUseCase {
    Page<Client> searchClients(String query, Pageable pageable);
    Client getClientById(UUID id);
}
