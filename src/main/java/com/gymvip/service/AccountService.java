package com.gymvip.service;

import com.gymvip.dto.request.CreateStaffAccountRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.request.UpdateStaffStatusRequest;
import com.gymvip.dto.request.UpdateStaffProfileRequest;
import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    List<StaffAccountResponse> listStaffAccounts();
    StaffAccountResponse getById(UUID id);
    StaffAccountResponse createStaffAccount(CreateStaffAccountRequest request, String actorEmail);
    StaffAccountResponse updateRole(UUID id, UpdateStaffRoleRequest request, String actorEmail);
    StaffAccountResponse updateStatus(UUID id, UpdateStaffStatusRequest request, String actorEmail);
    StaffAccountResponse updateProfile(UUID id, UpdateStaffProfileRequest request, String actorEmail);
    void revokeSessions(UUID id, String actorEmail);

    default List<AuditEvent> listAccountAuditHistory(UUID accountId) {
        return List.of();
    }

    default List<AuditEvent> listRecentStaffAuditEvents() {
        return List.of();
    }
}
