package com.gymvip.mapper;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientDetailResponse;
import com.gymvip.dto.response.ClientResponse;
import com.gymvip.entity.Client;
import com.gymvip.entity.Document;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ClientMapper {

    public Client toEntity(RegisterClientRequest request, String memberCode) {
        Client client = new Client(
                UUID.randomUUID(),
                memberCode,
                request.firstName().trim(),
                request.lastName().trim(),
                request.birthDate(),
                request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null,
                request.email() != null && !request.email().isBlank() ? request.email().trim().toLowerCase() : null,
                "ACTIVO"
        );

        String country = (request.countryCode() != null && !request.countryCode().isBlank())
                ? request.countryCode().trim().toUpperCase()
                : "PE";

        Document document = new Document(
                UUID.randomUUID(),
                client,
                country,
                request.documentType().trim().toUpperCase(),
                request.documentNumber().trim().toUpperCase(),
                true,
                null
        );

        client.addDocument(document);
        return client;
    }

    public ClientResponse toResponse(Client client) {
        List<ClientResponse.DocumentDto> docDtos = client.getDocuments() != null
                ? client.getDocuments().stream()
                .map(d -> new ClientResponse.DocumentDto(
                        d.getId(),
                        d.getCountryCode(),
                        d.getDocumentType(),
                        d.getDocumentNumber(),
                        d.isPrimary()))
                .collect(Collectors.toList())
                : Collections.emptyList();

        return new ClientResponse(
                client.getId(),
                client.getMemberCode(),
                client.getFirstName(),
                client.getLastName(),
                client.getFullName(),
                client.getBirthDate(),
                client.getAge(),
                client.getPhone(),
                client.getEmail(),
                client.getStatus(),
                docDtos,
                client.getCreatedAt()
        );
    }

    public ClientDetailResponse toDetailResponse(Client client) {
        List<ClientResponse.DocumentDto> docDtos = client.getDocuments() != null
                ? client.getDocuments().stream()
                .map(d -> new ClientResponse.DocumentDto(
                        d.getId(),
                        d.getCountryCode(),
                        d.getDocumentType(),
                        d.getDocumentNumber(),
                        d.isPrimary()))
                .collect(Collectors.toList())
                : Collections.emptyList();

        return new ClientDetailResponse(
                client.getId(),
                client.getMemberCode(),
                client.getFirstName(),
                client.getLastName(),
                client.getFullName(),
                client.getBirthDate(),
                client.getAge(),
                client.isAdult(),
                client.getPhone(),
                client.isPhoneVerified(),
                client.getEmail(),
                client.isEmailVerified(),
                client.getStatus(),
                docDtos,
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
