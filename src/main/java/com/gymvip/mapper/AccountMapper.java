package com.gymvip.mapper;

import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.Account;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class AccountMapper {

    public StaffAccountResponse toResponse(Account account) {
        return new StaffAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getRoles() != null ? new HashSet<>(account.getRoles()) : new HashSet<>(),
                account.getStatus(),
                account.isMfaEnabled(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
