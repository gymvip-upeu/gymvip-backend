package com.gymvip.clients.infrastructure.persistence.adapter;

import com.gymvip.clients.application.port.out.ClientPersistencePort;
import com.gymvip.clients.domain.exception.DocumentDuplicateException;
import com.gymvip.clients.domain.model.Client;
import com.gymvip.clients.infrastructure.persistence.entity.ClientJpaEntity;
import com.gymvip.clients.infrastructure.persistence.mapper.ClientPersistenceMapper;
import com.gymvip.clients.infrastructure.persistence.repository.SpringDataClientRepository;
import com.gymvip.clients.infrastructure.persistence.repository.SpringDataDocumentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ClientPersistenceAdapter implements ClientPersistencePort {

    private final SpringDataClientRepository clientRepository;
    private final SpringDataDocumentRepository documentRepository;

    public ClientPersistenceAdapter(SpringDataClientRepository clientRepository,
                                  SpringDataDocumentRepository documentRepository) {
        this.clientRepository = clientRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public Client save(Client client) {
        try {
            ClientJpaEntity entity = ClientPersistenceMapper.toEntity(client);
            ClientJpaEntity saved = clientRepository.save(entity);
            return ClientPersistenceMapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DocumentDuplicateException("Conflicto de unicidad en base de datos: el documento o código de socio ya se encuentra registrado.");
        }
    }

    @Override
    public Optional<Client> findById(UUID id) {
        return clientRepository.findById(id).map(ClientPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByDocument(String countryCode, String documentType, String documentNumber) {
        return documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(countryCode, documentType, documentNumber);
    }

    @Override
    public boolean existsByMemberCode(String memberCode) {
        return clientRepository.existsByMemberCode(memberCode);
    }

    @Override
    public Page<Client> searchClients(String query, Pageable pageable) {
        return clientRepository.searchClients(query, pageable).map(ClientPersistenceMapper::toDomain);
    }
}
