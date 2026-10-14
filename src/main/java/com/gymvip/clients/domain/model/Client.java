package com.gymvip.clients.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Modelo de dominio puro para el Socio / Cliente de GYM VIP.
 * Contiene invariantes de negocio y reglas de validación.
 * Sin dependencias de Spring, JPA ni Jackson.
 */
public class Client {

    private UUID id;
    private String memberCode;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String phone;
    private boolean phoneVerified;
    private String email;
    private boolean emailVerified;
    private String status;
    private UUID accountId;
    private List<Document> documents = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Client() {
        this.status = "ACTIVO";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Client(UUID id, String memberCode, String firstName, String lastName, LocalDate birthDate,
                  String phone, String email, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.memberCode = memberCode;
        this.firstName = firstName != null ? firstName.trim() : null;
        this.lastName = lastName != null ? lastName.trim() : null;
        this.birthDate = birthDate;
        this.phone = phone != null ? phone.trim() : null;
        this.email = email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        this.status = status != null ? status : "ACTIVO";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getAge() {
        if (birthDate == null) return 0;
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public boolean isAdult() {
        return getAge() >= 18;
    }

    public void addDocument(Document document) {
        if (document != null) {
            this.documents.add(document);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public List<Document> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
