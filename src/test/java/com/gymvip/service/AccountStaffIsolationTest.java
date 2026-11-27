package com.gymvip.service;

import com.gymvip.entity.Account;
import com.gymvip.exception.AccountNotFoundException;
import com.gymvip.mapper.AccountMapper;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AccountStaffIsolationTest {
    @Test
    void personnelOperationsCannotTargetMemberAccounts() {
        var accounts = mock(AccountRepository.class);
        var sessions = mock(SessionRepository.class);
        var mapper = mock(AccountMapper.class);
        var passwords = mock(PasswordEncoder.class);
        var audit = mock(AuditService.class);
        var service = new AccountServiceImpl(accounts, sessions, mapper, passwords, audit);
        var member = mock(Account.class);
        var memberId = UUID.randomUUID();
        when(member.getRoles()).thenReturn(Set.of("CLIENTE"));
        when(accounts.findById(memberId)).thenReturn(Optional.of(member));

        when(accounts.findByEmailIgnoreCase("admin@gymvip.pe")).thenReturn(Optional.of(
                new Account(UUID.randomUUID(), "admin@gymvip.pe", "hash", Set.of("ADMIN_EMPRESA"), "ACTIVO", false)));
        assertThrows(AccountNotFoundException.class, () -> service.getById(memberId, "admin@gymvip.pe"));
        assertThrows(AccountNotFoundException.class, () -> service.revokeSessions(memberId, "admin@gymvip.pe"));
        verify(sessions, never()).deleteByAccountId(any());
        verify(accounts, never()).save(any());
    }
}
