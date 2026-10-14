package com.gymvip.clients.application.usecase;

import com.gymvip.clients.application.port.in.SearchClientsUseCase;
import com.gymvip.clients.application.port.out.ClientPersistencePort;
import com.gymvip.clients.domain.exception.ClientNotFoundException;
import com.gymvip.clients.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchClientsService implements SearchClientsUseCase {

    private final ClientPersistencePort clientPersistencePort;

    public SearchClientsService(ClientPersistencePort clientPersistencePort) {
        this.clientPersistencePort = clientPersistencePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Client> searchClients(String query, Pageable pageable) {
        return clientPersistencePort.searchClients(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Client getClientById(UUID id) {
        return clientPersistencePort.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("Socio no encontrado con ID: " + id));
    }
}
