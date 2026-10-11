package com.gymvip.clients.infrastructure;

import com.gymvip.clients.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    boolean existsByCountryCodeAndDocumentTypeAndDocumentNumber(String countryCode, String documentType, String documentNumber);
    Optional<Document> findByCountryCodeAndDocumentTypeAndDocumentNumber(String countryCode, String documentType, String documentNumber);
}
