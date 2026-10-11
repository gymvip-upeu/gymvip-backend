package com.gymvip.payments.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gymvip.memberships.domain.Membership;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    @JsonIgnore
    private Membership membership;

    @Column(name = "amount_pen", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPen;

    @Column(nullable = false, length = 10)
    private String currency = "PEN";

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod; // EFECTIVO, YAPE, PLIN, TRANSFERENCIA, POS

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "registered_by_account_id")
    private UUID registeredByAccountId;

    @Column(name = "payment_timestamp_utc", nullable = false)
    private LocalDateTime paymentTimestampUtc = LocalDateTime.now();

    @Column(length = 500)
    private String notes;

    public PaymentRecord() {}

    public PaymentRecord(BigDecimal amountPen, String paymentMethod, String externalReference, UUID registeredByAccountId, String notes) {
        this.amountPen = amountPen;
        this.paymentMethod = paymentMethod;
        this.externalReference = externalReference;
        this.registeredByAccountId = registeredByAccountId;
        this.notes = notes;
        this.currency = "PEN";
    }

    public UUID getId() { return id; }
    public Membership getMembership() { return membership; }
    public void setMembership(Membership membership) { this.membership = membership; }
    public BigDecimal getAmountPen() { return amountPen; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getExternalReference() { return externalReference; }
    public UUID getRegisteredByAccountId() { return registeredByAccountId; }
    public LocalDateTime getPaymentTimestampUtc() { return paymentTimestampUtc; }
    public String getNotes() { return notes; }
}
