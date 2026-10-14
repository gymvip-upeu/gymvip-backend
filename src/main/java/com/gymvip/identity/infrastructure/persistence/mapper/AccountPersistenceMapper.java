package com.gymvip.identity.infrastructure.persistence.mapper;

import com.gymvip.identity.domain.model.Account;
import com.gymvip.identity.domain.model.Session;
import com.gymvip.identity.infrastructure.persistence.entity.AccountJpaEntity;
import com.gymvip.identity.infrastructure.persistence.entity.SessionJpaEntity;

import java.util.HashSet;

public final class AccountPersistenceMapper {

    private AccountPersistenceMapper() {}

    public static Account toDomain(AccountJpaEntity entity) {
        if (entity == null) return null;
        Account account = new Account(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getRoles(),
                entity.getStatus(),
                entity.isMfaEnabled()
        );
        account.setCreatedAt(entity.getCreatedAt());
        account.setUpdatedAt(entity.getUpdatedAt());
        return account;
    }

    public static AccountJpaEntity toEntity(Account domain) {
        if (domain == null) return null;
        AccountJpaEntity entity = new AccountJpaEntity(
                domain.getId(),
                domain.getEmail(),
                domain.getPasswordHash(),
                new HashSet<>(domain.getRoles()),
                domain.getStatus(),
                domain.isMfaEnabled()
        );
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public static Session toDomainSession(SessionJpaEntity entity) {
        if (entity == null) return null;
        return new Session(
                entity.getToken(),
                entity.getAccountId(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUserAgent(),
                entity.getIpAddress()
        );
    }

    public static SessionJpaEntity toEntitySession(Session domain) {
        if (domain == null) return null;
        return new SessionJpaEntity(
                domain.getToken(),
                domain.getAccountId(),
                domain.getExpiresAt(),
                domain.getCreatedAt(),
                domain.getUserAgent(),
                domain.getIpAddress()
        );
    }
}
