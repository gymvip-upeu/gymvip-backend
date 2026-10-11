package com.gymvip.memberships.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gymvip.clients.domain.Client;
import com.gymvip.payments.domain.PaymentRecord;
import com.gymvip.plans.domain.PlanVersion;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uk_membership_idempotency", columnNames = {"idempotency_key"})
})
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_version_id", nullable = false)
    private PlanVersion planVersionSnapshot;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 30)
    private String status = "VIGENTE";

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PaymentRecord> paymentRecords = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Membership() {}

    public Membership(Client client, PlanVersion planVersionSnapshot, LocalDate startDate, LocalDate endDate, String idempotencyKey) {
        this.client = client;
        this.planVersionSnapshot = planVersionSnapshot;
        this.startDate = startDate;
        this.endDate = endDate;
        this.idempotencyKey = idempotencyKey;
        this.status = "VIGENTE";
    }

    public void addPayment(PaymentRecord payment) {
        paymentRecords.add(payment);
        payment.setMembership(this);
    }

    public UUID getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public PlanVersion getPlanVersionSnapshot() { return planVersionSnapshot; }
    public void setPlanVersionSnapshot(PlanVersion planVersionSnapshot) { this.planVersionSnapshot = planVersionSnapshot; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public List<PaymentRecord> getPaymentRecords() { return paymentRecords; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
