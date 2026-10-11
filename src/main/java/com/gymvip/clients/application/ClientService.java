package com.gymvip.clients.application;

import com.gymvip.audit.application.AuditService;
import com.gymvip.clients.domain.Client;
import com.gymvip.clients.domain.ConsentRecord;
import com.gymvip.clients.domain.Document;
import com.gymvip.clients.infrastructure.ClientRepository;
import com.gymvip.clients.infrastructure.DocumentRepository;
import com.gymvip.shared.exceptions.ConflictException;
import com.gymvip.shared.exceptions.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    public ClientService(ClientRepository clientRepository,
                         DocumentRepository documentRepository,
                         AuditService auditService) {
        this.clientRepository = clientRepository;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Client> searchClients(String query, Pageable pageable) {
        return clientRepository.searchClients(query, pageable);
    }

    @Transactional(readOnly = true)
    public Client findById(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Socio no encontrado con ID: " + id));
    }

    @Transactional
    public Client registerAdultClient(String firstName,
                                      String lastName,
                                      LocalDate birthDate,
                                      String countryCode,
                                      String documentType,
                                      String documentNumber,
                                      String phone,
                                      String email,
                                      boolean termsAccepted,
                                      boolean privacyAccepted,
                                      boolean marketingEmailAccepted,
                                      boolean marketingWhatsAppAccepted,
                                      String clientIp,
                                      String actorEmail) {
        // 1. Adult verification rule
        if (birthDate == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria");
        }
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (age < 18) {
            throw new IllegalArgumentException("El registro de menores de edad requiere representante legal validado (bloqueado temporalmente hasta acuerdo de políticas).");
        }

        // 2. Document uniqueness rule
        String normDocNum = documentNumber.trim();
        String normCountry = countryCode != null ? countryCode.trim().toUpperCase() : "PE";
        String normDocType = documentType.trim().toUpperCase();

        if (documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(normCountry, normDocType, normDocNum)) {
            throw new ConflictException("Ya existe un cliente registrado con el documento " + normDocType + " " + normDocNum + " (" + normCountry + ").");
        }

        // 3. Mandatory legal consent validation
        if (!termsAccepted) {
            throw new IllegalArgumentException("Es obligatorio aceptar los Términos del Servicio");
        }
        if (!privacyAccepted) {
            throw new IllegalArgumentException("Es obligatorio acusar recibo y lectura del Aviso de Privacidad");
        }

        // 4. Create and persist client
        Client client = new Client(firstName.trim(), lastName.trim(), birthDate, phone != null ? phone.trim() : null, email != null ? email.trim() : null);

        Document doc = new Document(normCountry, normDocType, normDocNum);
        client.addDocument(doc);

        // Versioned evidence
        client.addConsent(new ConsentRecord("TERMS", "v1.0", true, "WEB_REGISTRATION", clientIp));
        client.addConsent(new ConsentRecord("PRIVACY", "v1.0", true, "WEB_REGISTRATION", clientIp));
        client.addConsent(new ConsentRecord("MARKETING_EMAIL", "v1.0", marketingEmailAccepted, "WEB_REGISTRATION", clientIp));
        client.addConsent(new ConsentRecord("MARKETING_WHATSAPP", "v1.0", marketingWhatsAppAccepted, "WEB_REGISTRATION", clientIp));

        Client savedClient = clientRepository.save(client);

        auditService.recordEvent(actorEmail, "REGISTER_CLIENT", "Client", savedClient.getId().toString(),
                "Cliente registrado: " + savedClient.getFirstName() + " " + savedClient.getLastName() + " - " + normDocType + " " + normDocNum);

        return savedClient;
    }

    @Transactional
    public Client updateContacts(UUID clientId, String phone, String email, String actorEmail) {
        Client client = findById(clientId);
        String oldContacts = "Tel: " + client.getPhone() + ", Correo: " + client.getEmail();

        if (phone != null && !phone.equals(client.getPhone())) {
            client.setPhone(phone.trim());
            client.setPhoneVerified(false); // Pending verification
        }
        if (email != null && !email.equalsIgnoreCase(client.getEmail())) {
            client.setEmail(email.trim());
            client.setEmailVerified(false); // Pending verification
        }
        client.setUpdatedAt(LocalDateTime.now());
        Client saved = clientRepository.save(client);

        auditService.recordEvent(actorEmail, "UPDATE_CONTACTS", "Client", saved.getId().toString(),
                "Contactos actualizados de [" + oldContacts + "] a [Tel: " + client.getPhone() + ", Correo: " + client.getEmail() + "]");

        return saved;
    }
}
