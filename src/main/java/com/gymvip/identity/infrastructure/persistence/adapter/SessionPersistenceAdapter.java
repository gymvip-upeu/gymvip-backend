package com.gymvip.identity.infrastructure.persistence.adapter;

import com.gymvip.identity.application.port.out.SessionPersistencePort;
import com.gymvip.identity.domain.model.Session;
import com.gymvip.identity.infrastructure.persistence.entity.SessionJpaEntity;
import com.gymvip.identity.infrastructure.persistence.mapper.AccountPersistenceMapper;
import com.gymvip.identity.infrastructure.persistence.repository.SpringDataSessionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SessionPersistenceAdapter implements SessionPersistencePort {

    private final SpringDataSessionRepository sessionRepository;

    public SessionPersistenceAdapter(SpringDataSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Session save(Session session) {
        SessionJpaEntity entity = AccountPersistenceMapper.toEntitySession(session);
        SessionJpaEntity saved = sessionRepository.save(entity);
        return AccountPersistenceMapper.toDomainSession(saved);
    }

    @Override
    public Optional<Session> findByToken(String token) {
        return sessionRepository.findById(token).map(AccountPersistenceMapper::toDomainSession);
    }

    @Override
    public void deleteByToken(String token) {
        sessionRepository.deleteById(token);
    }

    @Override
    public void deleteByAccountId(UUID accountId) {
        sessionRepository.deleteByAccountId(accountId);
    }
}
