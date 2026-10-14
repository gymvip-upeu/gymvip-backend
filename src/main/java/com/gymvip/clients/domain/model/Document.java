package com.gymvip.clients.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dominio para Documento de Identidad del Cliente.
 * Inmutable y libre de frameworks.
 */
public class Document {

    private final UUID id;
    private final String countryCode;
    private final String documentType;
    private final String documentNumber;
    private final boolean isPrimary;
    private final LocalDateTime createdAt;

    public Document(UUID id, String countryCode, String documentType, String documentNumber, boolean isPrimary, LocalDateTime createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.countryCode = countryCode != null ? countryCode.trim().toUpperCase() : "PE";
        this.documentType = documentType != null ? documentType.trim().toUpperCase() : "DNI";
        this.documentNumber = documentNumber != null ? documentNumber.trim() : "";
        this.isPrimary = isPrimary;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public static Document create(String countryCode, String documentType, String documentNumber) {
        return new Document(UUID.randomUUID(), countryCode, documentType, documentNumber, true, LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public String getCountryCode() { return countryCode; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public boolean isPrimary() { return isPrimary; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getFormatted() {
        return documentType + " " + documentNumber + " (" + countryCode + ")";
    }
}
