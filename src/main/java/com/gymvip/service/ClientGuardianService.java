package com.gymvip.service;

import com.gymvip.dto.guardian.ClientGuardianDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ClientGuardianService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    public ClientGuardianService(JdbcClient jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GuardianDto> listGuardians(UUID clientId) {
        return jdbc.sql("""
                SELECT id, client_id, full_name, document_type, document_number, relationship,
                       phone, email, emergency_contact, authorized_at_utc, consent_reference
                FROM client_guardians
                WHERE client_id = :cid
                ORDER BY full_name ASC
                """)
                .param("cid", clientId)
                .query((rs, rowNum) -> new GuardianDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("client_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("document_type"),
                        rs.getString("document_number"),
                        rs.getString("relationship"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getBoolean("emergency_contact"),
                        rs.getTimestamp("authorized_at_utc").toInstant(),
                        rs.getString("consent_reference")
                ))
                .list();
    }

    public GuardianDto addGuardian(UUID clientId, GuardianCreateRequest req, String actor) {
        // Verificar existencia del cliente
        var clientExists = jdbc.sql("SELECT count(*) FROM clients WHERE id = :id")
                .param("id", clientId)
                .query(Long.class)
                .single() > 0;

        if (!clientExists) {
            throw new ResourceNotFoundException("Cliente no encontrado.");
        }

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();

        try {
            jdbc.sql("""
                    INSERT INTO client_guardians (
                        id, client_id, full_name, document_type, document_number, relationship,
                        phone, email, emergency_contact, authorized_at_utc, consent_reference,
                        created_at_utc, created_by
                    ) VALUES (
                        :id, :cid, :name, :docType, :docNum, :rel,
                        :phone, :email, :emerg, :now, :consentRef,
                        :now, :actor
                    )
                    """)
                    .param("id", id)
                    .param("cid", clientId)
                    .param("name", req.fullName().trim())
                    .param("docType", req.documentType().trim().toUpperCase())
                    .param("docNum", req.documentNumber().trim())
                    .param("rel", req.relationship().trim().toUpperCase())
                    .param("phone", req.phone().trim())
                    .param("email", req.email() != null && !req.email().isBlank() ? req.email().trim().toLowerCase() : null)
                    .param("emerg", req.emergencyContact())
                    .param("consentRef", req.consentReference() != null ? req.consentReference().trim() : null)
                    .param("now", java.sql.Timestamp.from(now))
                    .param("actor", actor)
                    .update();

            audit.recordEvent(actor, "ADD_CLIENT_GUARDIAN", "ClientGuardian", id.toString(),
                    "Apoderado registrado para socio " + clientId + ": " + req.fullName() + " (" + req.relationship() + ")");

            return new GuardianDto(
                    id, clientId, req.fullName().trim(), req.documentType().trim().toUpperCase(),
                    req.documentNumber().trim(), req.relationship().trim().toUpperCase(), req.phone().trim(),
                    req.email() != null ? req.email().trim().toLowerCase() : null, req.emergencyContact(),
                    now, req.consentReference()
            );
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("uk_client_guardian_doc")) {
                throw new ConflictException("Ya existe un apoderado registrado con el mismo tipo y número de documento para este miembro.");
            }
            throw e;
        }
    }

    public void removeGuardian(UUID clientId, UUID guardianId, String actor) {
        int deleted = jdbc.sql("DELETE FROM client_guardians WHERE id = :id AND client_id = :cid")
                .param("id", guardianId)
                .param("cid", clientId)
                .update();

        if (deleted == 0) {
            throw new ResourceNotFoundException("Apoderado no encontrado.");
        }

        audit.recordEvent(actor, "REMOVE_CLIENT_GUARDIAN", "ClientGuardian", guardianId.toString(),
                "Apoderado retirado del socio " + clientId);
    }
}
