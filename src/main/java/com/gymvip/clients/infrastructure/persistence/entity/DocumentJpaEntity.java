package com.gymvip.clients.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_document_country_type_num", columnNames = {"country_code", "document_type", "document_number"})
})
public class DocumentJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode = "PE";

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentJpaEntity() {}

    public DocumentJpaEntity(UUID id, ClientJpaEntity client, String countryCode, String documentType, String documentNumber, boolean isPrimary, LocalDateTime createdAt) {
        this.id = id;
        this.client = client;
        this.countryCode = countryCode;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.isPrimary = isPrimary;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ClientJpaEntity getClient() { return client; }
    public void setClient(ClientJpaEntity client) { this.client = client; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
