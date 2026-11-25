package com.gymvip.config;

import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.PlanRepository;
import com.gymvip.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    @Test
    void inProdWhenStaffAccountsAlreadyExistSkipsCreationWithoutErrors() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PlanService planService = mock(PlanService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        when(accountRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        when(planRepository.findAll()).thenReturn(List.of());

        DataInitializer initializer = new DataInitializer(accountRepository, planRepository, planService, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "activeProfile", "prod");
        ReflectionTestUtils.setField(initializer, "devDefaultsEnabled", false);
        ReflectionTestUtils.setField(initializer, "adminInitialPassword", "");
        ReflectionTestUtils.setField(initializer, "gerenteInitialPassword", "");
        ReflectionTestUtils.setField(initializer, "recepcionInitialPassword", "");

        initializer.run();

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void inProdWithWeakOrMissingPasswordsDoesNotCreateDefaultAccounts() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PlanService planService = mock(PlanService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        when(accountRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(List.of());

        DataInitializer initializer = new DataInitializer(accountRepository, planRepository, planService, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "activeProfile", "prod");
        ReflectionTestUtils.setField(initializer, "devDefaultsEnabled", false);
        ReflectionTestUtils.setField(initializer, "adminInitialPassword", "Admin123!");
        ReflectionTestUtils.setField(initializer, "gerenteInitialPassword", "short");
        ReflectionTestUtils.setField(initializer, "recepcionInitialPassword", "");

        initializer.run();

        verify(accountRepository, never()).save(any(Account.class));
    }
}
