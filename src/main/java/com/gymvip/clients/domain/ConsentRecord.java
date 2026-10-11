package com.gymvip.clients.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "policy_type", nullable = false, length = 50)
    private String policyType; // TERMS, PRIVACY, MARKETING_EMAIL, MARKETING_WHATSAPP

    @Column(name = "policy_version", nullable = false, length = 30)
    private String policyVersion;

    @Column(nullable = false)
    private boolean choice;

    @Column(length = 50)
    private String channel;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc = LocalDateTime.now();

    public ConsentRecord() {}

    public ConsentRecord(String policyType, String policyVersion, boolean choice, String channel, String ipAddress) {
        this.policyType = policyType;
        this.policyVersion = policyVersion;
        this.choice = choice;
        this.channel = channel;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getPolicyType() { return policyType; }
    public String getPolicyVersion() { return policyVersion; }
    public boolean isChoice() { return choice; }
    public String getChannel() { return channel; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getTimestampUtc() { return timestampUtc; }
}
