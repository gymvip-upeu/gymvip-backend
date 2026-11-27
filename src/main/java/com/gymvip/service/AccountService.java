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
    List<StaffAccountResponse> listStaffAccounts(String actorEmail);
    StaffAccountResponse getById(UUID id, String actorEmail);
    StaffAccountResponse createStaffAccount(CreateStaffAccountRequest request, String actorEmail);
    StaffAccountResponse updateRole(UUID id, UpdateStaffRoleRequest request, String actorEmail);
    StaffAccountResponse updatePrivilegedRole(UUID id, UpdateStaffRoleRequest request, String actorEmail);
    StaffAccountResponse updateStatus(UUID id, UpdateStaffStatusRequest request, String actorEmail);
    StaffAccountResponse updateProfile(UUID id, UpdateStaffProfileRequest request, String actorEmail);
    void revokeSessions(UUID id, String actorEmail);

    default List<AuditEvent> listAccountAuditHistory(UUID accountId, String actorEmail) {
        return List.of();
    }

    default List<AuditEvent> listRecentStaffAuditEvents(String actorEmail) {
        return List.of();
    }
}
