package com.gymvip.clients.infrastructure.persistence.mapper;

import com.gymvip.clients.domain.model.Client;
import com.gymvip.clients.domain.model.Document;
import com.gymvip.clients.infrastructure.persistence.entity.ClientJpaEntity;
import com.gymvip.clients.infrastructure.persistence.entity.DocumentJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ClientPersistenceMapper {

    private ClientPersistenceMapper() {}

    public static Client toDomain(ClientJpaEntity entity) {
        if (entity == null) return null;

        Client domain = new Client(
                entity.getId(),
                entity.getMemberCode(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getBirthDate(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getStatus()
        );
        domain.setPhoneVerified(entity.isPhoneVerified());
        domain.setEmailVerified(entity.isEmailVerified());
        domain.setAccountId(entity.getAccountId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getDocuments() != null) {
            List<Document> docs = entity.getDocuments().stream()
                    .map(d -> new Document(
                            d.getId(),
                            d.getCountryCode(),
                            d.getDocumentType(),
                            d.getDocumentNumber(),
                            d.isPrimary(),
                            d.getCreatedAt()
                    ))
                    .collect(Collectors.toList());
            domain.setDocuments(docs);
        }

        return domain;
    }

    public static ClientJpaEntity toEntity(Client domain) {
        if (domain == null) return null;

        ClientJpaEntity entity = new ClientJpaEntity(
                domain.getId(),
                domain.getMemberCode(),
                domain.getFirstName(),
                domain.getLastName(),
                domain.getBirthDate(),
                domain.getPhone(),
                domain.getEmail(),
                domain.getStatus()
        );
        entity.setPhoneVerified(domain.isPhoneVerified());
        entity.setEmailVerified(domain.isEmailVerified());
        entity.setAccountId(domain.getAccountId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        if (domain.getDocuments() != null) {
            List<DocumentJpaEntity> docs = new ArrayList<>();
            for (Document d : domain.getDocuments()) {
                DocumentJpaEntity docEntity = new DocumentJpaEntity(
                        d.getId(),
                        entity,
                        d.getCountryCode(),
                        d.getDocumentType(),
                        d.getDocumentNumber(),
                        d.isPrimary(),
                        d.getCreatedAt()
                );
                docs.add(docEntity);
            }
            entity.setDocuments(docs);
        }

        return entity;
    }
}
