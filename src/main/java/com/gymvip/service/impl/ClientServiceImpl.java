package com.gymvip.service.impl;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientDetailResponse;
import com.gymvip.dto.response.ClientResponse;
import com.gymvip.entity.Client;
import com.gymvip.exception.ClientNotFoundException;
import com.gymvip.exception.ClientValidationException;
import com.gymvip.exception.DocumentDuplicateException;
import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import com.gymvip.repository.DocumentRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.ClientService;
import com.gymvip.util.MemberCodeGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final DocumentRepository documentRepository;
    private final ClientMapper clientMapper;
    private final AuditService auditService;

    public ClientServiceImpl(ClientRepository clientRepository,
                             DocumentRepository documentRepository,
                             ClientMapper clientMapper,
                             AuditService auditService) {
        this.clientRepository = clientRepository;
        this.documentRepository = documentRepository;
        this.clientMapper = clientMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public ClientResponse registerClient(RegisterClientRequest request, String actorEmail) {
        if (request.birthDate() == null) {
            throw new ClientValidationException("La fecha de nacimiento es obligatoria");
        }
        int age = Period.between(request.birthDate(), LocalDate.now()).getYears();
        if (age < 18) {
            throw new ClientValidationException("El registro de menores de edad requiere representante legal validado (mínimo 18 años).");
        }

        String country = (request.countryCode() != null && !request.countryCode().isBlank())
                ? request.countryCode().trim().toUpperCase()
                : "PE";
        String docType = request.documentType() != null ? request.documentType().trim().toUpperCase() : "";
        String docNum = request.documentNumber() != null ? request.documentNumber().trim().toUpperCase() : "";

        if (documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(country, docType, docNum)) {
            throw new DocumentDuplicateException("Ya existe un cliente registrado con el documento especificado: " + docType + " - " + docNum);
        }

        String memberCode;
        int attempts = 0;
        do {
            memberCode = MemberCodeGenerator.generate(request.firstName());
            attempts++;
            if (attempts > 15) {
                memberCode = "VIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
                break;
            }
        } while (clientRepository.existsByMemberCode(memberCode));

        Client client = clientMapper.toEntity(request, memberCode);
        Client saved = clientRepository.save(client);

        auditService.recordEvent(
                actorEmail,
                "REGISTER_CLIENT",
                "Client",
                saved.getId().toString(),
                "Cliente registrado: " + saved.getFullName() + " | Código: " + saved.getMemberCode() + " | Doc: " + docType + " " + docNum
        );

        return clientMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientResponse> searchClients(String query, Pageable pageable) {
        String trimmedQuery = query != null ? query.trim() : "";
        return clientRepository.searchClients(trimmedQuery, pageable)
                .map(clientMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDetailResponse getClientById(UUID id) {
        return clientRepository.findById(id)
                .map(clientMapper::toDetailResponse)
                .orElseThrow(() -> new ClientNotFoundException("Cliente no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDetailResponse getClientByMemberCode(String memberCode) {
        return clientRepository.findByMemberCode(memberCode)
                .map(clientMapper::toDetailResponse)
                .orElseThrow(() -> new ClientNotFoundException("Cliente no encontrado con código de socio: " + memberCode));
    }
}
