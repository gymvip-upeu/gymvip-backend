package com.gymvip.identity.application.port.in;

import com.gymvip.identity.domain.model.Account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StaffAccountUseCase {

    StaffAccountDto createStaffAccount(CreateStaffCommand command, String actorEmail);

    StaffAccountDto updateRole(UUID accountId, String newRole, String actorEmail);

    StaffAccountDto updateStatus(UUID accountId, String newStatus, String actorEmail);

    void revokeSessions(UUID accountId, String actorEmail);

    List<StaffAccountDto> listStaffAccounts();

    StaffAccountDto getById(UUID id);

    record CreateStaffCommand(
            String email,
            String rawPassword,
            String role
    ) {}

    record StaffAccountDto(
            UUID id,
            String email,
            Set<String> roles,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static StaffAccountDto from(Account a) {
            return new StaffAccountDto(
                    a.getId(),
                    a.getEmail(),
                    a.getRoles(),
                    a.getStatus(),
                    a.getCreatedAt(),
                    a.getUpdatedAt()
            );
        }
    }
}
