package com.gymvip.identity.infrastructure.persistence.adapter;

import com.gymvip.identity.application.port.out.AccountPersistencePort;
import com.gymvip.identity.domain.model.Account;
import com.gymvip.identity.infrastructure.persistence.entity.AccountJpaEntity;
import com.gymvip.identity.infrastructure.persistence.mapper.AccountPersistenceMapper;
import com.gymvip.identity.infrastructure.persistence.repository.SpringDataAccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AccountPersistenceAdapter implements AccountPersistencePort {

    private final SpringDataAccountRepository accountRepository;

    public AccountPersistenceAdapter(SpringDataAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = AccountPersistenceMapper.toEntity(account);
        AccountJpaEntity saved = accountRepository.save(entity);
        return AccountPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return accountRepository.findById(id).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Account> findByEmailIgnoreCase(String email) {
        return accountRepository.findByEmailIgnoreCase(email).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return accountRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public List<Account> findAll() {
        return accountRepository.findAll().stream()
                .map(AccountPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveAdmins() {
        return accountRepository.countActiveAdmins();
    }
}
