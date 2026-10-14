package com.gymvip.identity.application.port.out;

import com.gymvip.identity.domain.model.Session;

import java.util.Optional;
import java.util.UUID;

public interface SessionPersistencePort {

    Session save(Session session);

    Optional<Session> findByToken(String token);

    void deleteByToken(String token);

    void deleteByAccountId(UUID accountId);
}
