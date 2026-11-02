package com.gymvip.repository;

import com.gymvip.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    boolean existsByCountryCodeAndDocumentTypeAndDocumentNumber(String countryCode, String documentType, String documentNumber);
}
