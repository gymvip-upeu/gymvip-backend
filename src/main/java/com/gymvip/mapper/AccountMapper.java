package com.gymvip.mapper;

import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.Account;
import com.gymvip.security.PermissionCatalog;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AccountMapper {

    public StaffAccountResponse toResponse(Account account) {
        return new StaffAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getRoles() == null ? Set.of() : account.getRoles().stream()
                        .map(PermissionCatalog::canonicalRole).collect(Collectors.toSet()),
                account.getStatus(),
                account.isMfaEnabled(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getFirstName(),
                account.getLastName(),
                account.getPhone()
        );
    }
}
