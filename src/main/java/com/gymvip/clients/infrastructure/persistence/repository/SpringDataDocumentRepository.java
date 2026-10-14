package com.gymvip.clients.infrastructure.persistence.repository;

import com.gymvip.clients.infrastructure.persistence.entity.DocumentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataDocumentRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    boolean existsByCountryCodeAndDocumentTypeAndDocumentNumber(String countryCode, String documentType, String documentNumber);
}
