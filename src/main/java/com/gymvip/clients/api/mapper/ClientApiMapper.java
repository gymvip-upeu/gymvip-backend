package com.gymvip.clients.api.mapper;

import com.gymvip.clients.api.dto.ClientDetailResponse;
import com.gymvip.clients.api.dto.ClientResponse;
import com.gymvip.clients.domain.model.Client;

import java.util.List;
import java.util.stream.Collectors;

public final class ClientApiMapper {

    private ClientApiMapper() {}

    public static ClientResponse toResponse(Client client) {
        if (client == null) return null;

        List<ClientResponse.DocumentDto> docs = client.getDocuments() != null
                ? client.getDocuments().stream()
                .map(d -> new ClientResponse.DocumentDto(d.getId(), d.getCountryCode(), d.getDocumentType(), d.getDocumentNumber(), d.isPrimary()))
                .collect(Collectors.toList())
                : List.of();

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
                docs,
                client.getCreatedAt()
        );
    }

    public static ClientDetailResponse toDetailResponse(Client client) {
        if (client == null) return null;

        List<ClientResponse.DocumentDto> docs = client.getDocuments() != null
                ? client.getDocuments().stream()
                .map(d -> new ClientResponse.DocumentDto(d.getId(), d.getCountryCode(), d.getDocumentType(), d.getDocumentNumber(), d.isPrimary()))
                .collect(Collectors.toList())
                : List.of();

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
                docs,
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
