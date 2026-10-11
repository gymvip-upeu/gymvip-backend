package com.gymvip.identity.infrastructure;

import com.gymvip.identity.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {
    Optional<Session> findByToken(String token);
    void deleteByToken(String token);
}
