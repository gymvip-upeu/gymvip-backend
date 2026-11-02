package com.gymvip.service;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientDetailResponse;
import com.gymvip.dto.response.ClientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClientService {
    ClientResponse registerClient(RegisterClientRequest request, String actorEmail);
    Page<ClientResponse> searchClients(String query, Pageable pageable);
    ClientDetailResponse getClientById(UUID id);
    ClientDetailResponse getClientByMemberCode(String memberCode);
}
