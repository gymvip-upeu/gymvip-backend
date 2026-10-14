package com.gymvip.clients.application.usecase;

import com.gymvip.audit.application.port.in.AuditUseCase;
import com.gymvip.clients.application.port.in.RegisterClientUseCase;
import com.gymvip.clients.application.port.out.ClientPersistencePort;
import com.gymvip.clients.domain.exception.ClientValidationException;
import com.gymvip.clients.domain.exception.DocumentDuplicateException;
import com.gymvip.clients.domain.model.Client;
import com.gymvip.clients.domain.model.Document;
import com.gymvip.clients.domain.model.MemberCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Service
public class RegisterClientService implements RegisterClientUseCase {

    private final ClientPersistencePort clientPersistencePort;
    private final AuditUseCase auditUseCase;

    public RegisterClientService(ClientPersistencePort clientPersistencePort, AuditUseCase auditUseCase) {
        this.clientPersistencePort = clientPersistencePort;
        this.auditUseCase = auditUseCase;
    }

    @Override
    @Transactional
    public Client registerClient(RegisterClientCommand command, String actorEmail, String clientIp) {
        // 1. Invariante de negocio: validación estricta de mayoría de edad (>= 18 años)
        if (command.birthDate() == null) {
            throw new ClientValidationException("La fecha de nacimiento es obligatoria");
        }
        int age = Period.between(command.birthDate(), LocalDate.now()).getYears();
        if (age < 18) {
            throw new ClientValidationException("El registro de menores de edad requiere representante legal validado (bloqueado temporalmente hasta acuerdo de políticas).");
        }

        // 2. Normalización de documento y validación de unicidad compuesta
        String country = command.countryCode() != null && !command.countryCode().isBlank()
                ? command.countryCode().trim().toUpperCase() : "PE";
        String docType = command.documentType() != null ? command.documentType().trim().toUpperCase() : "DNI";
        String docNum = command.documentNumber() != null ? command.documentNumber().trim() : "";

        if (docNum.isBlank()) {
            throw new ClientValidationException("El número de documento es obligatorio");
        }

        if (clientPersistencePort.existsByDocument(country, docType, docNum)) {
            throw new DocumentDuplicateException("Ya existe un cliente registrado con el documento " + docType + " " + docNum + " (" + country + ").");
        }

        // 3. Generación de código de socio único y controlado con reintentos
        String memberCode = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidateCode = MemberCodeGenerator.generate(command.firstName());
            if (!clientPersistencePort.existsByMemberCode(candidateCode)) {
                memberCode = candidateCode;
                break;
            }
        }
        if (memberCode == null) {
            memberCode = "VIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }

        // 4. Creación de modelo de dominio puro
        Client client = new Client(
                UUID.randomUUID(),
                memberCode,
                command.firstName(),
                command.lastName(),
                command.birthDate(),
                command.phone(),
                command.email(),
                "ACTIVO"
        );

        Document document = Document.create(country, docType, docNum);
        client.addDocument(document);

        // 5. Persistencia mediante el puerto
        Client saved = clientPersistencePort.save(client);

        // 6. Registro de auditoría
        auditUseCase.recordEvent(
                actorEmail,
                "REGISTER_CLIENT",
                "Client",
                saved.getId().toString(),
                "Socio registrado: " + saved.getFullName() + " | Código: " + saved.getMemberCode() + " | Doc: " + docType + " " + docNum
        );

        return saved;
    }
}
